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
package de.featjar.analysis.sat4j.twise;

import de.featjar.analysis.RuntimeContradictionException;
import de.featjar.analysis.sat4j.computation.ASAT4JAnalysis;
import de.featjar.analysis.sat4j.computation.MIGBuilder;
import de.featjar.analysis.sat4j.solver.ISelectionStrategy;
import de.featjar.analysis.sat4j.solver.ModalImplicationGraph;
import de.featjar.analysis.sat4j.solver.SAT4JSolutionSolver;
import de.featjar.analysis.sat4j.solver.SAT4JSolver;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.base.computation.Progress;
import de.featjar.base.data.Ints;
import de.featjar.base.data.LexicographicIterator;
import de.featjar.base.data.Result;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.assignment.BooleanSolutionList;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Calculates statistics regarding t-wise feature coverage of a set of
 * solutions.
 *
 * @author anonymous
 */
public class TWiseCoverageComputation extends ASAT4JAnalysis<CoverageStatistic> {
    public static final Dependency<Integer> T = Dependency.newDependency(Integer.class);
    public static final Dependency<ModalImplicationGraph> MIG = Dependency.newDependency(ModalImplicationGraph.class);
    public static final Dependency<BooleanAssignment> FILTER = Dependency.newDependency(BooleanAssignment.class);
    public static final Dependency<BooleanSolutionList> SAMPLE = Dependency.newDependency(BooleanSolutionList.class);

    public class Environment {
        private final CoverageStatistic statistic = new CoverageStatistic();
        private final SAT4JSolutionSolver solver = initializeSolver(dependencyList);
        private final ModalImplicationGraph.Visitor visitor =
                MIG.get(dependencyList).getVisitor();
        private SampleListIndex sampleIndex = new SampleListIndex(sample, size, t);
        private SampleListIndex randomIndex = new SampleListIndex(randomSample, size, t);

        public CoverageStatistic getStatistic() {
            return statistic;
        }
    }

    public TWiseCoverageComputation(IComputation<BooleanClauseList> booleanClauseList) {
        super(
                booleanClauseList, //
                Computations.of(2), //
                new MIGBuilder(booleanClauseList), //
                Computations.of(new BooleanAssignment()), //
                Computations.of(new BooleanSolutionList()));
    }

    public TWiseCoverageComputation(TWiseCoverageComputation other) {
        super(other);
    }

    private ArrayList<Environment> statisticList = new ArrayList<>();

    private List<Object> dependencyList;
    private List<BooleanSolution> sample, randomSample;
    private int t, size;

    @Override
    public Result<CoverageStatistic> compute(List<Object> dependencyList, Progress progress) {
        this.dependencyList = dependencyList;
        sample = SAMPLE.get(dependencyList).getAll();
        t = T.get(dependencyList);

        if (!sample.isEmpty()) {
            size = sample.get(0).size();

            createRandomSample(dependencyList);

            final int[] literals = Ints.filteredList(size, FILTER.get(dependencyList));
            final int[] gray = Ints.grayCode(t);

            LexicographicIterator.parallelStream(t, literals.length, this::createStatistic)
                    .forEach(combo -> {
                        int[] select = combo.getSelection(literals);
                        for (int i = 0; i < gray.length; i++) {
                            if (combo.environment.sampleIndex.test(select)) {
                                combo.environment.statistic.incNumberOfCoveredConditions();
                            } else if (isCombinationInvalidMIG(combo.environment, select)) {
                                combo.environment.statistic.incNumberOfInvalidConditions();
                            } else if (combo.environment.randomIndex.test(select)) {
                                combo.environment.statistic.incNumberOfUncoveredConditions();
                            } else if (isCombinationInvalidSAT(combo.environment, select)) {
                                combo.environment.statistic.incNumberOfInvalidConditions();
                            } else {
                                combo.environment.statistic.incNumberOfUncoveredConditions();
                            }
                            int g = gray[i];
                            select[g] = -select[g];
                        }
                    });
        }
        return Result.ofOptional(statisticList.stream() //
                .map(Environment::getStatistic) //
                .reduce((s1, s2) -> s1.merge(s2)));
    }

    private void createRandomSample(List<Object> dependencyList) {
        randomSample = new ArrayList<>();
        SAT4JSolutionSolver solver = initializeSolver(dependencyList);
        Random random = new Random(RANDOM_SEED.get(dependencyList));
        int limit = (int) Math.ceil(30 * Math.log(size));
        solver.setSelectionStrategy(ISelectionStrategy.random(random));
        for (int j = 0; j < limit; j++) {
            if (solver.hasSolution().valueEquals(Boolean.TRUE)) {
                randomSample.add(new BooleanSolution(solver.getInternalSolution(), false));
                solver.shuffleOrder(random);
            } else {
                break;
            }
        }
    }

    private Environment createStatistic() {
        Environment env = new Environment();
        synchronized (statisticList) {
            statisticList.add(env);
        }
        return env;
    }

    private boolean isCombinationInvalidMIG(Environment env, int[] select) {
        try {
            env.visitor.propagate(select);
        } catch (RuntimeContradictionException e) {
            return true;
        } finally {
            env.visitor.reset();
        }
        return false;
    }

    private boolean isCombinationInvalidSAT(Environment env, int[] select) {
        final int orgAssignmentLength = env.solver.getAssignment().size();
        try {
            env.solver.getAssignment().addAll(select);
            return env.solver.hasSolution().valueEquals(Boolean.FALSE);
        } finally {
            env.solver.getAssignment().clear(orgAssignmentLength);
        }
    }

    @Override
    protected SAT4JSolver newSolver(BooleanClauseList clauseList) {
        return new SAT4JSolutionSolver(clauseList);
    }
}
