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

import de.featjar.analysis.sat4j.solver.ISelectionStrategy;
import de.featjar.analysis.sat4j.solver.ISelectionStrategy.Strategy;
import de.featjar.analysis.sat4j.solver.SAT4JSolutionSolver;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.base.computation.Progress;
import de.featjar.base.data.Result;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.assignment.BooleanSolutionList;
import java.util.List;
import java.util.Random;

public class ComputeSolutionsSAT4J extends ASAT4JAnalysis.Solution<BooleanSolutionList> {
    public static final Dependency<ISelectionStrategy.Strategy> SELECTION_STRATEGY =
            Dependency.newDependency(ISelectionStrategy.Strategy.class);
    public static final Dependency<Integer> LIMIT = Dependency.newDependency(Integer.class);
    public static final Dependency<Boolean> FORBID_DUPLICATES = Dependency.newDependency(Boolean.class);

    public ComputeSolutionsSAT4J(IComputation<BooleanClauseList> booleanClauseList) {
        super(
                booleanClauseList,
                Computations.of(ISelectionStrategy.Strategy.ORIGINAL),
                Computations.of(Integer.MAX_VALUE),
                Computations.of(true));
    }

    protected ComputeSolutionsSAT4J(ComputeSolutionsSAT4J other) {
        super(other);
    }

    @Override
    public Result<BooleanSolutionList> compute(List<Object> dependencyList, Progress progress) {
        SAT4JSolutionSolver solver = (SAT4JSolutionSolver) initializeSolver(dependencyList);
        int limit = LIMIT.get(dependencyList);
        boolean forbid = FORBID_DUPLICATES.get(dependencyList);
        final Strategy strategy = SELECTION_STRATEGY.get(dependencyList);
        Random random = null;
        switch (strategy) {
            case FAST_RANDOM:
                random = new Random(RANDOM_SEED.get(dependencyList));
                solver.setSelectionStrategy(ISelectionStrategy.random(random));
                break;
            case NEGATIVE:
                solver.setSelectionStrategy(ISelectionStrategy.negative());
                break;
            case ORIGINAL:
                break;
            case POSITIVE:
                solver.setSelectionStrategy(ISelectionStrategy.positive());
                break;
            default:
                break;
        }
        BooleanSolutionList solutionList = new BooleanSolutionList();
        while (solutionList.size() < limit) {
            Result<BooleanSolution> solution = solver.findSolution();
            if (solution.isEmpty()) {
                break;
            }
            solutionList.add(solution.get());
            if (forbid) {
                solver.getClauseList().add(solution.get().toClause().inverse());
            }
            if (strategy == Strategy.FAST_RANDOM) {
                solver.shuffleOrder(random);
            }
        }
        return solver.createResult(solutionList, "result is a subset");
    }
}
