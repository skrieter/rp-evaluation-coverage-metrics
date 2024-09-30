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
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanSolutionList;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates statistics regarding t-wise feature coverage of a set of
 * solutions.
 *
 * @author anonymous
 */
public class RelativeTWiseCoverageComputation extends AComputation<CoverageStatistic> {
    public static final Dependency<BooleanSolutionList> REFERENCE_SAMPLE =
            Dependency.newDependency(BooleanSolutionList.class);
    public static final Dependency<Integer> T = Dependency.newDependency(Integer.class);
    public static final Dependency<BooleanSolutionList> SAMPLE = Dependency.newDependency(BooleanSolutionList.class);
    public static final Dependency<BooleanAssignment> FILTER = Dependency.newDependency(BooleanAssignment.class);

    public class Environment {
        private SampleListIndex sampleIndex = new SampleListIndex(sample.getAll(), size, t);
        private final CoverageStatistic statistic = new CoverageStatistic();

        public CoverageStatistic getStatistic() {
            return statistic;
        }
    }

    public RelativeTWiseCoverageComputation(IComputation<BooleanSolutionList> reference) {
        super(
                reference,
                Computations.of(2), //
                Computations.of(new BooleanSolutionList()), //
                Computations.of(new BooleanAssignment()));
    }

    public RelativeTWiseCoverageComputation(RelativeTWiseCoverageComputation other) {
        super(other);
    }

    private ArrayList<Environment> statisticList = new ArrayList<>();
    private BooleanSolutionList sample;
    private int t, size;

    @Override
    public Result<CoverageStatistic> compute(List<Object> dependencyList, Progress progress) {
        sample = SAMPLE.get(dependencyList);

        if (!sample.isEmpty()) {
            BooleanSolutionList referenceSample = REFERENCE_SAMPLE.get(dependencyList);
            assert referenceSample.isEmpty()
                    || sample.get(0).get().size()
                            >= referenceSample.get(0).get().size();

            t = T.get(dependencyList);
            size = sample.get(0).get().size();

            SampleBitIndex referenceIndex = new SampleBitIndex(referenceSample.getAll(), size);

            final int[] literals = Ints.filteredList(size, FILTER.get(dependencyList));
            final int[] gray = Ints.grayCode(t);

            LexicographicIterator.parallelStream(t, literals.length, this::createStatistic)
                    .forEach(combo -> {
                        int[] select = combo.getSelection(literals);
                        for (int i = 0; i < gray.length; i++) {
                            if (referenceIndex.test(select)) {
                                if (combo.environment.sampleIndex.test(select)) {
                                    combo.environment.statistic.incNumberOfCoveredConditions();
                                } else {
                                    combo.environment.statistic.incNumberOfUncoveredConditions();
                                }
                            } else {
                                combo.environment.statistic.incNumberOfInvalidConditions();
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

    private Environment createStatistic() {
        Environment env = new Environment();
        synchronized (statisticList) {
            statisticList.add(env);
        }
        return env;
    }
}
