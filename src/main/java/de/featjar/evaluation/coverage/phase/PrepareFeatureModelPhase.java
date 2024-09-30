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

import de.featjar.analysis.sat4j.computation.ComputeAtomicSetsSAT4J;
import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.base.io.format.IFormatSupplier;
import de.featjar.base.io.text.StringTextFormat;
import de.featjar.evaluation.Evaluator;
import de.featjar.evaluation.util.FileReader;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.BooleanClause;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.IBooleanRepresentation;
import de.featjar.formula.io.FormulaFormats;
import de.featjar.formula.io.csv.BooleanAssignmentGroupsCSVFormat;
import de.featjar.formula.io.dimacs.BooleanAssignmentGroupsDimacsFormat;
import de.featjar.formula.structure.IFormula;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts model files into cnf and computes sets of features, such as core, dead, and atomic sets.
 *
 * @author anonymous
 */
public class PrepareFeatureModelPhase extends Evaluator {

    private FileReader<IFormula> modelReader;
    private FileReader<String> featureReader;
    private CSVFile modelCSV;

    @Override
    public void runEvaluation() {
        try {
            modelCSV = new CSVFile(csvPath.resolve("systems.csv"));
            modelCSV.setHeaderFields("SystemID", "SystemName", "VariableCount", "ClauseCount");
            modelCSV.flush();
            modelReader = new FileReader<>(modelPath, FormulaFormats.getInstance(), "model", "xml");
            featureReader =
                    new FileReader<>(modelPath, IFormatSupplier.of(new StringTextFormat()), "abstract_features", "txt");
            optionCombiner.loopOverOptions(this::optionLoop, systemsOption);
        } catch (IOException e) {
            FeatJAR.log().error(e);
        }
    }

    public int optionLoop(int lastChanged) {
        String modelName = optionCombiner.getValue(0);
        int modelID = getSystemId(modelName);
        Result<IFormula> model = modelReader.read(modelName);
        if (model.isEmpty()) {
            FeatJAR.log().problems(model.getProblems());
            return 0;
        } else {
            IFormula formula = model.get();
            VariableMap variables =
                    IBooleanRepresentation.toVariableMap(formula).compute();
            BooleanClauseList cnf =
                    IBooleanRepresentation.toBooleanClauseList(formula).compute();

            List<BooleanAssignment> concreteGroup = new ArrayList<>();
            List<BooleanAssignment> coreGroup = new ArrayList<>();
            List<BooleanAssignment> atomicLiteralsGroup = new ArrayList<>();
            List<BooleanAssignment> atomicFeaturesGroup = new ArrayList<>();
            List<BooleanAssignment> pcFeaturesGroup = new ArrayList<>();

            for (BooleanClause booleanClause : cnf) {
                if (booleanClause.size() == 2) {
                    int l1 = booleanClause.get(0);
                    int l2 = booleanClause.get(1);
                    if (Math.signum(l1) != Math.signum(l2)) {
                        if (l1 < 0) {
                            pcFeaturesGroup.add(new BooleanAssignment(Math.abs(l1), l2));
                        } else {
                            pcFeaturesGroup.add(new BooleanAssignment(Math.abs(l2), l1));
                        }
                    }
                }
            }

            BooleanAssignmentList atomic =
                    Computations.of(cnf).map(ComputeAtomicSetsSAT4J::new).compute();

            Iterator<BooleanAssignment> iterator = atomic.iterator();
            BooleanAssignment core = iterator.next();
            addToGroup(atomicLiteralsGroup, core);
            addToGroup(atomicFeaturesGroup, new BooleanAssignment(core.getPositiveValues()));
            addToGroup(atomicFeaturesGroup, new BooleanAssignment(core.getNegativeValues()).inverse());
            coreGroup.add(new BooleanAssignment(core.getPositiveValues()));
            coreGroup.add(new BooleanAssignment(core.getNegativeValues()).inverse());
            while (iterator.hasNext()) {
                BooleanAssignment atomicSet = iterator.next();
                addToGroup(atomicLiteralsGroup, atomicSet);
                addToGroup(atomicFeaturesGroup, new BooleanAssignment(atomicSet.getNegativeValues()).inverse());
                addToGroup(atomicFeaturesGroup, new BooleanAssignment(atomicSet.getPositiveValues()));
            }
            Result<String> abstractFeature = featureReader.read(modelName);
            BooleanAssignment allVariables = new BooleanAssignment(variables.getVariableIndices());
            BooleanAssignment abstractVariables = abstractFeature.isPresent()
                    ? new BooleanAssignment(variables.getVariableIndices(
                            abstractFeature.get().lines().collect(Collectors.toList())))
                    : new BooleanAssignment();
            BooleanAssignment concreteVariables = allVariables.removeAll(abstractVariables);
            concreteGroup.add(abstractVariables);
            concreteGroup.add(concreteVariables);

            try {
                IO.save(
                        new BooleanAssignmentGroups(variables, List.of(cnf.getAll())),
                        genPath.resolve(modelName).resolve("cnf.dimacs"),
                        new BooleanAssignmentGroupsDimacsFormat());
                saveGroup(modelName, variables, "core", coreGroup);
                saveGroup(modelName, variables, "concrete", concreteGroup);
                saveGroup(modelName, variables, "atomic_literals", atomicLiteralsGroup);
                saveGroup(modelName, variables, "atomic_features", atomicFeaturesGroup);
                saveGroup(modelName, variables, "parent_child", pcFeaturesGroup);

                CSVFile.writeCSV(modelCSV, w -> {
                    w.add(modelID);
                    w.add(modelName);
                    w.add(variables.getVariableCount());
                    w.add(cnf.size());
                });
            } catch (IOException e) {
                FeatJAR.log().error(e);
                return 0;
            }
        }
        return -1;
    }

    private void saveGroup(String modelName, VariableMap variables, String name, List<BooleanAssignment> group)
            throws IOException {
        BooleanAssignmentGroupsCSVFormat format = new BooleanAssignmentGroupsCSVFormat();
        IO.save(
                new BooleanAssignmentGroups(variables, List.of(group)),
                genPath.resolve(modelName).resolve("group_" + name + "." + format.getFileExtension()),
                format);
    }

    private void addToGroup(List<BooleanAssignment> group, BooleanAssignment list) {
        if (!list.isEmpty()) {
            group.add(list);
        }
    }
}
