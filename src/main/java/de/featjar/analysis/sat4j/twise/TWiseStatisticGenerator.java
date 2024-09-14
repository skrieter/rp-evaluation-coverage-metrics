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

import de.featjar.analysis.sat4j.computation.ASAT4JAnalysis;
import de.featjar.analysis.sat4j.computation.ComputeCoreSAT4J;
import de.featjar.analysis.sat4j.solver.ISelectionStrategy;
import de.featjar.analysis.sat4j.solver.SAT4JSolutionSolver;
import de.featjar.analysis.sat4j.solver.SAT4JSolver;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Dependency;
import de.featjar.base.computation.IComputation;
import de.featjar.base.computation.Progress;
import de.featjar.base.data.Result;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.assignment.BooleanSolutionList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Calculates statistics regarding t-wise feature coverage of a set of
 * solutions.
 *
 * @author Sebastian Krieter
 */
public class TWiseStatisticGenerator extends ASAT4JAnalysis<CoverageStatistic> {
    public static final Dependency<Integer> T = Dependency.newDependency(Integer.class);
    public static final Dependency<BooleanAssignment> CORE = Dependency.newDependency(BooleanAssignment.class);
    public static final Dependency<BooleanSolutionList> SAMPLE = Dependency.newDependency(BooleanSolutionList.class);

    public TWiseStatisticGenerator(IComputation<BooleanClauseList> booleanClauseList) {
        super(
                booleanClauseList,
                Computations.of(2),
                new ComputeCoreSAT4J(booleanClauseList),
                Computations.of(new BooleanSolutionList()));
    }

    public TWiseStatisticGenerator(TWiseStatisticGenerator other) {
        super(other);
    }

    private List<BitSet> sampleConfigs;
    private List<BitSet> randomConfigs;

    public static final int GLOBAL_SOLUTION_LIMIT = 10_000;
    private Random random;
    private int randomSolutionCount = 0;

    private boolean isCombinationValidSAT(SAT4JSolutionSolver solver, int[] literals) {
        final int orgAssignmentLength = solver.getAssignment().size();
        try {
            solver.getAssignment().addAll(literals);
            Result<Boolean> hasSolution = solver.hasSolution();
            if (hasSolution.valueEquals(Boolean.TRUE)) {
                if (randomSolutionCount < GLOBAL_SOLUTION_LIMIT) {
                    synchronized (this) {
                        if (randomSolutionCount < GLOBAL_SOLUTION_LIMIT) {
                            randomConfigs.add(convertToBitSet(solver.getInternalSolution()));
                            randomSolutionCount++;
                        }
                    }
                    solver.shuffleOrder(random);
                }
            } else {
                return false;
            }
        } finally {
            solver.getAssignment().clear(orgAssignmentLength);
        }
        return true;
    }

    long[] covered, uncovered, invalid;

    private static BitSet convertToBitSet(BooleanSolution configuration) {
        return convertToBitSet(configuration.get());
    }

    private static BitSet convertToBitSet(int[] configuration) {
        final BitSet config = new BitSet(configuration.length);
        for (int i = 0; i < configuration.length; i++) {
            config.set(i, configuration[i] > 0);
        }
        return config;
    }

    @Override
    public Result<CoverageStatistic> compute(List<Object> dependencyList, Progress progress) {
        random = new Random(RANDOM_SEED.get(dependencyList));
        BooleanSolutionList sample = SAMPLE.get(dependencyList);
        BooleanAssignment deadCoreFeatures = CORE.get(dependencyList);
        int t = T.get(dependencyList);

        sampleConfigs =
                sample.stream().map(TWiseStatisticGenerator::convertToBitSet).collect(Collectors.toList());
        randomConfigs = new ArrayList<>();

        if (!sampleConfigs.isEmpty()) {
            final int n = sample.get(0).get().size();
            final int t2 = (n < t) ? n : t;
            final int n2 = n - t2 + 1;
            final int pow = (int) Math.pow(2, t2);

            boolean[][] masks = new boolean[pow][t2];
            for (int i = 0; i < masks.length; i++) {
                boolean[] p = masks[i];
                for (int j = 0; j < t2; j++) {
                    p[j] = (i >> j & 1) == 0;
                }
            }

            invalid = new long[pow];
            covered = new long[pow];
            uncovered = new long[pow];

            int[] sampleIndex0 = IntStream.range(0, sampleConfigs.size()).toArray();
            int[] randomIndex0 = IntStream.range(0, GLOBAL_SOLUTION_LIMIT).toArray();
            IntStream.range(0, pow) //
                    .parallel() //
                    .forEach(maskIndex -> {
                        boolean[] mask = new boolean[t2];
                        for (int j = 0; j < t2; j++) {
                            mask[j] = (maskIndex >> j & 1) == 0;
                        }

                        int[][] sampleIndex = new int[t2][];
                        sampleIndex[0] = sampleIndex0;
                        for (int i = 1; i < sampleIndex.length; i++) {
                            sampleIndex[i] = new int[sampleConfigs.size()];
                        }
                        int[][] randomIndex = new int[t2][];
                        randomIndex[0] = randomIndex0;
                        for (int i = 1; i < randomIndex.length; i++) {
                            randomIndex[i] = new int[GLOBAL_SOLUTION_LIMIT];
                        }

                        int[] literals = new int[t2];
                        int liSample = 0;
                        int liRandom = 0;

                        final int[] c = new int[t2];
                        for (int i = 0; i < t2; i++) {
                            c[i] = i;
                        }
                        int i = 0;

                        SAT4JSolutionSolver solver = initializeSolver(dependencyList);
                        boolean addSolutions = true;

                        combinationLoop:
                        while (true) {

                            liRandom = Math.min(liRandom, i);
                            liSample = Math.min(liSample, i);

                            for (int k = 0; k < t2; k++) {
                                int literal = mask[k] ? (c[k] + 1) : -(c[k] + 1);
                                if (deadCoreFeatures.containsAnyVariable(Math.abs(literal))) {
                                    i = k;
                                    for (; i >= 0; i--) {
                                        final int ci = ++c[i];
                                        if (ci < (n2 + i)) {
                                            break;
                                        }
                                    }
                                    if (i == -1) {
                                        break combinationLoop;
                                    }
                                    for (int j = i + 1; j < t2; j++) {
                                        c[j] = c[j - 1] + 1;
                                    }
                                    continue combinationLoop;
                                }
                                literals[k] = literal;
                            }

                            final int t3 = t2 - 1;

                            d:
                            {
                                int curRandomSolutionCount = randomSolutionCount;

                                extracted(mask, randomIndex, c, t3, randomConfigs, liRandom, curRandomSolutionCount);
                                liRandom = i;

                                if (!findConfig(
                                        c[t3], mask[t3], randomIndex[t3], randomConfigs, curRandomSolutionCount)) {
                                    if (addSolutions && curRandomSolutionCount >= GLOBAL_SOLUTION_LIMIT) {
                                        addSolutions = false;
                                        solver.setSelectionStrategy(ISelectionStrategy.original());
                                    }
                                    if (!isCombinationValidSAT(solver, literals)) {
                                        invalid[maskIndex]++;
                                        break d;
                                    }
                                }

                                extracted(mask, sampleIndex, c, t3, sampleConfigs, liSample, sampleConfigs.size());
                                liSample = i;

                                if (findConfig(c[t3], mask[t3], sampleIndex[t3], sampleConfigs, sampleConfigs.size())) {
                                    covered[maskIndex]++;
                                } else {
                                    uncovered[maskIndex]++;
                                }
                            }

                            i = t3;
                            for (; i >= 0; i--) {
                                final int ci = ++c[i];
                                if (ci < (n2 + i)) {
                                    break;
                                }
                            }

                            if (i == -1) {
                                break;
                            }
                            for (int j = i + 1; j < t2; j++) {
                                c[j] = c[j - 1] + 1;
                            }
                        }
                    });

            long invalidSum = LongStream.of(invalid).sum();
            long coveredSum = LongStream.of(covered).sum();
            long uncoveredSum = LongStream.of(uncovered).sum();

            CoverageStatistic statistic = new CoverageStatistic();
            statistic.setNumberOfCoveredConditions(coveredSum);
            statistic.setNumberOfInvalidConditions(invalidSum);
            statistic.setNumberOfUncoveredConditions(uncoveredSum);

            return Result.of(statistic);
        }

        CoverageStatistic statistic = new CoverageStatistic();
        statistic.setNumberOfCoveredConditions(0);
        statistic.setNumberOfInvalidConditions(0);
        statistic.setNumberOfUncoveredConditions(1);
        return Result.of(statistic);
    }

    private void extracted(
            boolean[] mask,
            int[][] sampleIndex,
            final int[] c,
            final int t3,
            List<BitSet> configurations,
            int ci,
            int configLength) {
        for (int k = ci; k < t3; k++) {
            final int index = c[k];
            final boolean maskValue = mask[k];
            int[] sampleIndex1 = sampleIndex[k];
            int[] sampleIndex2 = sampleIndex[k + 1];

            int sindex2 = 0;
            for (int sindex1 : sampleIndex1) {
                if (sindex1 == -1) {
                    break;
                }
                if (sindex1 >= configLength) {
                    break;
                }
                BitSet bitSet = configurations.get(sindex1);
                if (bitSet.get(index) == maskValue) {
                    sampleIndex2[sindex2++] = sindex1;
                }
            }
            if (sindex2 < sampleIndex2.length) {
                sampleIndex2[sindex2] = -1;
            }
        }
    }

    private boolean findConfig(
            int index, boolean maskValue, int[] sampleIndexK, List<BitSet> configurations, int configLength) {
        for (int l = 0; l < sampleIndexK.length; l++) {
            int sindexK = sampleIndexK[l];
            if (sindexK == -1 || sindexK >= configLength) {
                break;
            }
            if (configurations.get(sindexK).get(index) == maskValue) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected SAT4JSolver newSolver(BooleanClauseList clauseList) {
        SAT4JSolutionSolver solver = new SAT4JSolutionSolver(clauseList);
        solver.setTimeout(Duration.ofSeconds(100));
        solver.setSelectionStrategy(ISelectionStrategy.random(random));
        return solver;
    }
}
