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

import de.featjar.base.computation.AComputation;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.base.computation.Progress;
import de.featjar.base.data.Ints;
import de.featjar.base.data.LexicographicIterator;
import de.featjar.base.data.Result;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.ABooleanAssignmentList;
import de.featjar.formula.assignment.BooleanAssignment;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates statistics regarding t-wise feature coverage of a set of
 * solutions.
 *
 * @author anonymous
 */
public class TWiseCountComputation extends AComputation<Long> {

    public static class CombinationList {
        private List<int[]> set;

        private CombinationList(List<int[]> set) {
            this.set = set;
        }

        public static CombinationList of(List<int[]> set) {
            return new CombinationList(set);
        }
    }

    @SuppressWarnings("rawtypes")
    public static final Dependency<ABooleanAssignmentList> SAMPLE =
            Dependency.newDependency(ABooleanAssignmentList.class);

    public static final Dependency<Integer> T = Dependency.newDependency(Integer.class);
    public static final Dependency<BooleanAssignment> VARIABLE_FILTER =
            Dependency.newDependency(BooleanAssignment.class);
    public static final Dependency<CombinationList> COMBINATION_FILTER =
            Dependency.newDependency(CombinationList.class);

    public class Environment {
        private long statistic = 0;

        public long getStatistic() {
            return statistic;
        }
    }

    public TWiseCountComputation(@SuppressWarnings("rawtypes") IComputation<? extends ABooleanAssignmentList> sample) {
        super(
                sample,
                Computations.of(2), //
                Computations.of(new BooleanAssignment()), //
                Computations.of(new CombinationList(List.of())));
    }

    public TWiseCountComputation(TWiseCountComputation other) {
        super(other);
    }

    private ArrayList<Environment> statisticList = new ArrayList<>();
    private int t;

    @SuppressWarnings("unchecked")
    @Override
    public Result<Long> compute(List<Object> dependencyList, Progress progress) {
        List<? extends ABooleanAssignment> sample = SAMPLE.get(dependencyList).getAll();

        if (sample.isEmpty()) {
            return Result.of(0L);
        }

        List<int[]> filterCombinations = COMBINATION_FILTER.get(dependencyList).set;

        final int size = sample.get(0).size();

        t = T.get(dependencyList);

        final int[] literals = Ints.filteredList(size, VARIABLE_FILTER.get(dependencyList));
        final int[] gray = Ints.grayCode(t);

        SampleBitIndex coverageChecker = new SampleBitIndex(sample, size);

        LexicographicIterator.parallelStream(t, literals.length, this::createStatistic)
                .forEach(combo -> {
                    int[] select = combo.getSelection(literals);
                    for (int i = 0; i < gray.length; i++) {
                        if (coverageChecker.test(select)) {
                            combo.environment.statistic++;
                        }
                        int g = gray[i];
                        select[g] = -select[g];
                    }
                });

        long filterCombinationsCount =
                filterCombinations.parallelStream().filter(coverageChecker).count();
        return Result.ofOptional(statisticList.stream() //
                .map(Environment::getStatistic) //
                .reduce((s1, s2) -> s1 + s2)
                .map(s -> s - filterCombinationsCount));
    }

    private Environment createStatistic() {
        Environment env = new Environment();
        synchronized (statisticList) {
            statisticList.add(env);
        }
        return env;
    }
}
