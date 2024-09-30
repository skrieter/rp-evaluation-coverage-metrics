/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-evaluation-coverage-metrics.
 *
 * evaluation-coverage-metrics is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * evaluation-coverage-metrics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with evaluation-coverage-metrics. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatJAR> for further information.
 */
package de.featjar.evaluation.coverage;

import de.featjar.analysis.sat4j.twise.SampleBitIndex;
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
public class TWisePartialCountComputation extends AComputation<long[]> {

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
        private long[] statistic = new long[sampleSize];

        public long[] getStatistic() {
            return statistic;
        }
    }

    public TWisePartialCountComputation(
            @SuppressWarnings("rawtypes") IComputation<? extends ABooleanAssignmentList> sample) {
        super(
                sample,
                Computations.of(2), //
                Computations.of(new BooleanAssignment()), //
                Computations.of(new CombinationList(List.of())));
    }

    public TWisePartialCountComputation(TWisePartialCountComputation other) {
        super(other);
    }

    private ArrayList<Environment> statisticList = new ArrayList<>();
    private int t, sampleSize;

    @SuppressWarnings("unchecked")
    @Override
    public Result<long[]> compute(List<Object> dependencyList, Progress progress) {
        List<? extends ABooleanAssignment> sample = SAMPLE.get(dependencyList).getAll();

        if (sample.isEmpty()) {
            return Result.of(new long[0]);
        }

        List<int[]> filterCombinations = COMBINATION_FILTER.get(dependencyList).set;

        sampleSize = sample.size();
        final int size = sample.get(0).size();

        t = T.get(dependencyList);

        final int[] literals = Ints.filteredList(size, VARIABLE_FILTER.get(dependencyList));
        final int[] gray = Ints.grayCode(t);

        SampleBitIndex coverageChecker = new SampleBitIndex(sample, size);

        LexicographicIterator.parallelStream(t, literals.length, this::createStatistic)
                .forEach(combo -> {
                    int[] select = combo.getSelection(literals);
                    for (int g : gray) {
                        int index = coverageChecker.index(select);
                        if (index > 0) {
                            combo.environment.statistic[index - 1]++;
                        }
                        select[g] = -select[g];
                    }
                });

        long[] result = new long[sampleSize];

        filterCombinations.forEach(combo -> {
            int index = coverageChecker.index(combo);
            if (index > 0) {
                result[index - 1]--;
            }
        });
        statisticList.forEach(env -> {
            long[] statistic = env.getStatistic();
            for (int i = 0; i < result.length; i++) {
                result[i] += statistic[i];
            }
        });

        return Result.of(result);
    }

    private Environment createStatistic() {
        Environment env = new Environment();
        synchronized (statisticList) {
            statisticList.add(env);
        }
        return env;
    }
}
