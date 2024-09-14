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
package de.featjar.evaluation.coverage.phase;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.ListOption;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.RangeOption;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Ints;
import de.featjar.base.data.LexicographicIterator;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.Evaluator;
import de.featjar.evaluation.coverage.TWisePartialCountComputation;
import de.featjar.formula.assignment.ABooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanClause;
import de.featjar.formula.assignment.BooleanSolution;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.io.csv.BooleanAssignmentGroupsCSVFormat;
import de.featjar.formula.io.csv.BooleanSolutionListCSVFormat;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Computes coverage of all samples, using different metrics.
 *
 * @author Sebastian Krieter
 */
public class PartialCoveragePhase extends Evaluator {

    public static enum Atomic {
        none,
        features,
        literals
    }

    public static enum Abstract {
        none,
        abstrakt,
        concrete
    }

    public static final ListOption<Integer> tOption = Option.newListOption("t", Option.IntegerParser);
    public static final RangeOption shuffleIterationsOption = Option.newRangeOption("shuffleIterations");

    public static final ListOption<Boolean> coreOption = Option.newListOption("filter_core", Option.BooleanParser);
    public static final ListOption<Boolean> deadOption = Option.newListOption("filter_dead", Option.BooleanParser);
    public static final ListOption<Abstract> abstractOption =
            Option.newListOption("filter_abstract", s -> Abstract.valueOf(s.toLowerCase()));
    public static final ListOption<Atomic> atomicOption =
            Option.newListOption("filter_atomic", s -> Atomic.valueOf(s.toLowerCase()));

    public static final ListOption<Boolean> pcOption =
            Option.newListOption("filter_parent_child", Option.BooleanParser);
    public static final ListOption<Boolean> equalOption =
            Option.newListOption("filter_equal_interactions", Option.BooleanParser);

    private String modelName;
    private Path modelPath;
    private int metricID, modelID, coverageID, modelIteration, shuffleIteration, t;
    private CSVFile coverageCSV, metricCSV, partialCoverageCSV;
    private BooleanSolutionList sample;
    private List<BooleanSolution> shuffledSample;

    private boolean core, dead, pc, equal;
    private Atomic atomic;
    private Abstract abstrakt;
    private ABooleanAssignment coreLiterals, deadLiterals, abstractLiterals, concreteLiterals;
    private List<? extends ABooleanAssignment> atomicLiterals, atomicFeatures, pcs;
    private long[] coveredInteractions;

    private LinkedHashMap<String, Integer> metricMap = new LinkedHashMap<>();

    @Override
    public void runEvaluation() {
        try {
            metricCSV = new CSVFile(csvPath.resolve("metric.csv"));
            metricCSV.setHeaderFields("MetricID", "Core", "Dead", "Abstract", "Atomic", "PC", "Equal");
            metricCSV.flush();

            metricID = 0;
            optionCombiner.loopOverOptions(
                    this::metricOptionLoop,
                    coreOption,
                    deadOption,
                    abstractOption,
                    atomicOption,
                    pcOption,
                    equalOption);

            coverageCSV = new CSVFile(csvPath.resolve("system_to_metric.csv"));
            coverageCSV.setHeaderFields(
                    "SystemID",
                    "T",
                    "SystemIteration",
                    "ShuffleIteration",
                    "MetricID",
                    "FilteredVariableCount",
                    "CoverageID",
                    "CoverageTime");

            partialCoverageCSV = new CSVFile(csvPath.resolve("partial_coverage.csv"));
            partialCoverageCSV.setHeaderFields("CoverageID", "PartialSampleSize", "CoveredInteractions");

            coverageCSV.flush();
            partialCoverageCSV.flush();

            coverageID = 0;
            optionCombiner.loopOverOptions(
                    this::optionLoop,
                    systemsOption,
                    tOption,
                    systemIterationsOption,
                    shuffleIterationsOption,
                    coreOption,
                    deadOption,
                    abstractOption,
                    atomicOption,
                    pcOption,
                    equalOption);
        } catch (IOException e) {
            FeatJAR.log().error(e);
        }
    }

    private int metricOptionLoop(int lastChanged) {
        switch (lastChanged) {
            case 0:
                core = optionCombiner.getValue(0);
            case 1:
                dead = optionCombiner.getValue(1);
            case 2:
                abstrakt = optionCombiner.getValue(2);
            case 3:
                atomic = optionCombiner.getValue(3);
            case 4:
                pc = optionCombiner.getValue(4);
            case 5:
                equal = optionCombiner.getValue(5);

                metricID++;
                metricMap.put(getMetricKey(), metricID);

                CSVFile.writeCSV(metricCSV, w -> {
                    w.add(metricID);
                    w.add(core);
                    w.add(dead);
                    w.add(abstrakt);
                    w.add(atomic);
                    w.add(pc);
                    w.add(equal);
                });
                break;
            default:
                throw new IllegalStateException();
        }
        return -1;
    }

    private int optionLoop(int lastChanged) {
        switch (lastChanged) {
            case 0: {
                modelName = optionCombiner.getValue(0);
                modelID = getSystemId(modelName);
                modelPath = genPath.resolve(modelName);

                List<BooleanAssignment> coreDeadGroup = loadGroup("core");
                List<BooleanAssignment> concreteAbstractGroup = loadGroup("concrete");
                coreLiterals = coreDeadGroup.get(0);
                deadLiterals = coreDeadGroup.get(1);
                abstractLiterals = concreteAbstractGroup.get(0);
                concreteLiterals = concreteAbstractGroup.get(1);
                atomicLiterals = loadGroup("atomic_literals");
                atomicFeatures = loadGroup("atomic_features");
                pcs = loadGroup("parent_child");
            }
            case 1:
                t = optionCombiner.getValue(1);
            case 2:
                modelIteration = optionCombiner.getValue(2);

                sample = new BooleanSolutionList(IO
                        .load(
                                modelPath.resolve(String.format("sample_t%d_mi%d.csv", t, modelIteration)),
                                new BooleanSolutionListCSVFormat())
                        .orElseThrow()
                        .getFirstGroup()
                        .stream()
                        .map(ABooleanAssignment::toSolution)
                        .collect(Collectors.toList()));
            case 3:
                shuffleIteration = optionCombiner.getValue(3);
                shuffledSample = sample.getAll();
                Collections.shuffle(shuffledSample, new Random(optionParser.get(randomSeed) + shuffleIteration));
            case 4:
                core = optionCombiner.getValue(4);
            case 5:
                dead = optionCombiner.getValue(5);
            case 6:
                abstrakt = optionCombiner.getValue(6);
            case 7:
                atomic = optionCombiner.getValue(7);
            case 8:
                pc = optionCombiner.getValue(8);
            case 9:
                equal = optionCombiner.getValue(9);

                metricID = metricMap.get(getMetricKey());

                long start = System.nanoTime();
                BooleanAssignment variableFilter = new BooleanAssignment();
                if (core) variableFilter = variableFilter.addAll(coreLiterals);
                if (dead) variableFilter = variableFilter.addAll(deadLiterals);
                if (abstrakt == Abstract.abstrakt) variableFilter = variableFilter.addAll(abstractLiterals);
                if (abstrakt == Abstract.concrete) variableFilter = variableFilter.addAll(concreteLiterals);
                if (atomic == Atomic.literals) variableFilter = filterAtomic(variableFilter, atomicLiterals);
                if (atomic == Atomic.features) variableFilter = filterAtomic(variableFilter, atomicFeatures);

                int[] filteredVariables = new BooleanAssignment(
                                IntStream.rangeClosed(1, sample.get(0).get().size())
                                        .toArray())
                        .removeAll(variableFilter)
                        .get();
                int n = filteredVariables.length;

                LinkedHashMap<BooleanClause, int[]> interactionFilter = new LinkedHashMap<>();
                BooleanAssignment filter = variableFilter;
                List<ABooleanAssignment> filteredPCs = pcs.stream()
                        .map(pc -> pc.removeAllVariables(filter))
                        .filter(pc -> pc.size() == 2)
                        .collect(Collectors.toList());
                if (pc) {
                    if (t == 2) {
                        for (ABooleanAssignment pc : filteredPCs) {
                            BooleanClause clause = pc.toClause();
                            interactionFilter.put(clause, clause.get());
                        }
                    } else if (t > 2) {
                        final int[] gray = Ints.grayCode(t - 2);
                        LexicographicIterator.stream(t - 2, n).forEach(combo -> {
                            int[] select = combo.getSelection(filteredVariables);
                            for (ABooleanAssignment pc : filteredPCs) {
                                if (!pc.containsAnyVariable(select)) {
                                    int[] pcLiterals = pc.get();
                                    for (int i = 0; i < gray.length; i++) {
                                        int[] pcInteraction = new int[t];
                                        for (int j = 0; j < pcLiterals.length; j++) {
                                            pcInteraction[j] = pcLiterals[j];
                                        }
                                        for (int j = 0; j < select.length; j++) {
                                            pcInteraction[j + pcLiterals.length] = select[j];
                                        }
                                        BooleanClause clause = new BooleanClause(pcInteraction);
                                        interactionFilter.put(clause, clause.get());
                                        int g = gray[i];
                                        select[g] = -select[g];
                                    }
                                }
                            }
                        });
                    }
                }

                coveredInteractions = Computations.of(sample)
                        .map(TWisePartialCountComputation::new)
                        .set(TWisePartialCountComputation.T, t)
                        .set(TWisePartialCountComputation.VARIABLE_FILTER, variableFilter)
                        .set(
                                TWisePartialCountComputation.COMBINATION_FILTER,
                                TWisePartialCountComputation.CombinationList.of(
                                        new ArrayList<>(interactionFilter.values())))
                        .compute();

                long end = System.nanoTime();

                coverageID++;

                CSVFile.writeCSV(coverageCSV, w -> {
                    w.add(modelID);
                    w.add(t);
                    w.add(modelIteration);
                    w.add(shuffleIteration);
                    w.add(metricID);
                    w.add(n);
                    w.add(coverageID);
                    w.add(end - start);
                });

                long interactionSum = 0;
                for (int i = 0; i < coveredInteractions.length; i++) {
                    int index = i + 1;
                    interactionSum += coveredInteractions[coveredInteractions.length - index];
                    long sum = interactionSum;
                    partialCoverageCSV.newLine();
                    partialCoverageCSV.add(coverageID);
                    partialCoverageCSV.add(index);
                    partialCoverageCSV.add(sum);
                }
                try {
                    partialCoverageCSV.flush();
                } catch (Exception e) {
                    FeatJAR.log().error(e);
                }

                break;
            default:
                throw new IllegalStateException();
        }
        return -1;
    }

    private String getMetricKey() {
        return String.format("%s_%s_%s_%s_%s_%s", core, dead, abstrakt, atomic, pc, equal);
    }

    private List<BooleanAssignment> loadGroup(String name) {
        BooleanAssignmentGroupsCSVFormat format = new BooleanAssignmentGroupsCSVFormat();
        BooleanAssignmentGroups group = IO.load(
                        modelPath.resolve("group_" + name + "." + format.getFileExtension()), format)
                .orElseThrow();
        List<? extends List<? extends ABooleanAssignment>> groups = group.getGroups();
        return groups.isEmpty()
                ? List.of()
                : groups.get(0).stream().map(ABooleanAssignment::toAssignment).collect(Collectors.toList());
    }

    private BooleanAssignment filterAtomic(
            BooleanAssignment variableFilter, List<? extends ABooleanAssignment> atomicSets) {
        for (ABooleanAssignment atomicSet : atomicSets) {
            BooleanAssignment absoluteValues = new BooleanAssignment(atomicSet.getAbsoluteValues());
            if (abstrakt == Abstract.abstrakt) {
                absoluteValues.removeAll(abstractLiterals);
            } else if (abstrakt == Abstract.concrete) {
                absoluteValues.removeAll(concreteLiterals);
            }
            if (absoluteValues.size() > 1) {
                int replacement = absoluteValues.get(0);
                int[] remove = absoluteValues.removeAll(replacement);
                variableFilter = new BooleanAssignment(variableFilter.addAll(remove));
            }
        }
        return variableFilter;
    }
}
