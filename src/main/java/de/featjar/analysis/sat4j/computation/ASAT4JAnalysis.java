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
package de.featjar.analysis.sat4j.computation;

import de.featjar.analysis.sat4j.solver.SAT4JExplanationSolver;
import de.featjar.analysis.sat4j.solver.SAT4JSolutionSolver;
import de.featjar.analysis.sat4j.solver.SAT4JSolver;
import de.featjar.base.FeatJAR;
import de.featjar.base.computation.AComputation;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanClauseList;
import java.time.Duration;
import java.util.List;

public abstract class ASAT4JAnalysis<T> extends AComputation<T> {
    public static final Dependency<BooleanClauseList> BOOLEAN_CLAUSE_LIST =
            Dependency.newDependency(BooleanClauseList.class);
    public static final Dependency<ABooleanAssignment> ASSUMED_ASSIGNMENT =
            Dependency.newDependency(ABooleanAssignment.class);
    public static final Dependency<BooleanClauseList> ASSUMED_CLAUSE_LIST =
            Dependency.newDependency(BooleanClauseList.class);
    public static final Dependency<Duration> SAT_TIMEOUT = Dependency.newDependency(Duration.class);
    public static final Dependency<Long> RANDOM_SEED = Dependency.newDependency(Long.class);

    public ASAT4JAnalysis(IComputation<BooleanClauseList> booleanClauseList, Object... computations) {
        super(
                booleanClauseList,
                Computations.of(new BooleanAssignment()),
                Computations.of(new BooleanClauseList(-1)),
                Computations.of(Duration.ZERO),
                Computations.of(1L),
                computations);
    }

    protected ASAT4JAnalysis(ASAT4JAnalysis<T> other) {
        super(other);
    }

    protected abstract SAT4JSolver newSolver(BooleanClauseList clauseList);

    @SuppressWarnings("unchecked")
    public <U extends SAT4JSolver> U initializeSolver(List<Object> dependencyList, boolean empty) {
        BooleanClauseList clauseList = BOOLEAN_CLAUSE_LIST.get(dependencyList);
        ABooleanAssignment assumedAssignment = ASSUMED_ASSIGNMENT.get(dependencyList);
        BooleanClauseList assumedClauseList = ASSUMED_CLAUSE_LIST.get(dependencyList);
        Duration timeout = SAT_TIMEOUT.get(dependencyList);
        FeatJAR.log().debug("initializing SAT4J");
        FeatJAR.log().debug("clauses %s", clauseList);
        FeatJAR.log().debug("assuming %s", assumedAssignment);
        FeatJAR.log().debug("assuming %s", assumedClauseList);
        U solver = (U) newSolver(empty ? new BooleanClauseList(clauseList.getVariableCount()) : clauseList);
        solver.getClauseList().addAll(assumedClauseList);
        solver.getAssignment().addAll(assumedAssignment);
        solver.setTimeout(timeout);
        solver.setGlobalTimeout(true);
        return solver;
    }

    public <U extends SAT4JSolver> U initializeSolver(List<Object> dependencyList) {
        return initializeSolver(dependencyList, false);
    }

    public abstract static class Solution<T> extends ASAT4JAnalysis<T> {
        public Solution(IComputation<BooleanClauseList> booleanClauseList, Object... computations) {
            super(booleanClauseList, computations);
        }

        protected Solution(Solution<T> other) {
            super(other);
        }

        @Override
        protected SAT4JSolutionSolver newSolver(BooleanClauseList clauseList) {
            return new SAT4JSolutionSolver(clauseList);
        }
    }

    abstract static class Explanation<T> extends ASAT4JAnalysis<T> {
        public Explanation(IComputation<BooleanClauseList> booleanClauseList) {
            super(booleanClauseList);
        }

        @Override
        protected SAT4JExplanationSolver newSolver(BooleanClauseList clauseList) {
            return new SAT4JExplanationSolver(clauseList);
        }
    }
}
