/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.analysis.sat4j.solver;

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Result;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolution;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import org.sat4j.core.VecInt;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

/**
 * Base class for solvers using Sat4J.
 *
 * @author anonymous
 * @author anonymous
 */
public abstract class SAT4JSolver implements de.featjar.analysis.ISolver {
    protected final ISolver internalSolver = newInternalSolver();
    protected final SAT4JClauseList clauseList;
    protected final SAT4JAssignment assignment = new SAT4JAssignment();
    protected Duration timeout = Duration.ZERO;
    protected boolean globalTimeout;

    protected boolean isTimeoutOccurred;
    protected boolean trivialContradictionFound;

    /**
     * Replaces all values in {@code model} that are different in {@code otherModel}
     * with zero. Does not modify {@code otherModel}. Assumes that {@code model} and
     * {@code otherModel} have the same length.
     *
     * @param model      First model
     * @param otherModel Second model, is not modified
     */
    public static void zeroConflicts(int[] model, int[] otherModel) {
        assert model.length == otherModel.length;
        for (int i = 0; i < model.length; i++) {
            final int literal = model[i];
            if (literal != 0 && literal != otherModel[i]) {
                model[i] = 0;
            }
        }
    }

    public SAT4JSolver(BooleanClauseList clauseList) {
        internalSolver.setDBSimplificationAllowed(true);
        internalSolver.setKeepSolverHot(true);
        internalSolver.setVerbose(false);
        this.clauseList = new SAT4JClauseList(this, clauseList);

        final int size = clauseList.getVariableCount();
        try {
            if (!clauseList.isEmpty()) {
                internalSolver.setExpectedNumberOfClauses(clauseList.size() + 1);
            }
            if (size > 0) {
                // due to a bug in SAT4J, each variable must be mentioned at least once
                // so, add a single pseudo-clause that is tautological and mentions every
                // variable
                final VecInt pseudoClause = new VecInt(size + 1);
                for (int i = 1; i <= size; i++) {
                    pseudoClause.push(i);
                }
                pseudoClause.push(-1);
                internalSolver.addClause(pseudoClause);
            }
        } catch (ContradictionException ignored) {
            trivialContradictionFound = true;
        }
    }

    protected abstract ISolver newInternalSolver();

    public SAT4JClauseList getClauseList() {
        return clauseList;
    }

    public SAT4JAssignment getAssignment() {
        return assignment;
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(Duration timeout) {
        Objects.requireNonNull(timeout);
        FeatJAR.log().debug("setting timeout to " + timeout);
        this.timeout = timeout;
        if (!timeout.isZero()) internalSolver.setTimeoutMs(timeout.toMillis());
        else internalSolver.expireTimeout();
    }

    public boolean isGlobalTimeout() {
        return globalTimeout;
    }

    public void setGlobalTimeout(boolean globalTimeout) {
        this.globalTimeout = globalTimeout;
    }

    @Override
    public boolean isTimeoutOccurred() {
        return isTimeoutOccurred;
    }

    public boolean isTrivialContradictionFound() {
        return trivialContradictionFound;
    }

    public Result<BooleanSolution> findSolution() {
        final Result<Boolean> hasSolution = hasSolution();
        return hasSolution.isPresent()
                ? hasSolution().get() ? Result.of(getSolution()) : Result.empty()
                : Result.empty(hasSolution.getProblems());
    }

    public Result<Boolean> hasSolution(int... integers) {
        return hasSolution(new VecInt(integers));
    }

    protected Result<Boolean> hasSolution(VecInt integers) {
        if (trivialContradictionFound) {
            return Result.of(Boolean.FALSE);
        }

        try {
            FeatJAR.log().debug("calling SAT4J");
            if (internalSolver.isSatisfiable(integers, globalTimeout)) {
                FeatJAR.log().debug("has solution");
                return Result.of(Boolean.TRUE);
            } else {
                FeatJAR.log().debug("no solution");
                return Result.of(Boolean.FALSE);
            }
        } catch (final TimeoutException e) {
            FeatJAR.log().debug("solver timeout occurred");
            isTimeoutOccurred = true;
            return Result.empty(de.featjar.analysis.ISolver.getTimeoutProblem(null));
        }
    }

    public Result<Boolean> hasSolution() {
        return hasSolution(assignment.getIntegers());
    }

    /**
     * Does only consider the given {@code assignment} and <b>not</b> the global
     * assignment variable of the solver.
     */
    public Result<Boolean> hasSolution(ABooleanAssignment assignment) {
        return hasSolution(new VecInt(assignment.get()));
    }

    public BooleanSolution getSolution() {
        int[] internalSolution = getInternalSolution();
        final int[] sortedIntegers = new int[internalSolution.length];
        Arrays.stream(internalSolution)
                .filter(integer -> integer != 0)
                .forEach(integer -> sortedIntegers[Math.abs(integer) - 1] = integer);
        return new BooleanSolution(sortedIntegers, false);
    }

    public int[] getInternalSolution() {
        return internalSolver.model();
    }
}
