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
import de.featjar.analysis.sat4j.computation.ComputeCoreSAT4J;
import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Result;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.Evaluator;
import de.featjar.evaluation.util.FileReader;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.IBooleanRepresentation;
import de.featjar.formula.io.FormulaFormats;
import de.featjar.formula.structure.IFormula;
import java.io.IOException;

/**
 * Measures time for core and atomic set analysis.
 *
 * @author anonymous
 */
public class MeasureAnalysisTimePhase extends Evaluator {

    private FileReader<IFormula> modelReader;
    private CSVFile timeCSV;
    private int modelID, algorithmIteration;
    private BooleanClauseList cnf;

    @Override
    public void runEvaluation() {
        try {
            timeCSV = new CSVFile(csvPath.resolve("analysis_time.csv"));
            timeCSV.setHeaderFields("SystemID", "Iteration", "core", "atomic");
            timeCSV.flush();
            modelReader = new FileReader<>(modelPath, FormulaFormats.getInstance(), "model", "xml");
            optionCombiner.loopOverOptions(this::optionLoop, systemsOption, algorithmIterationsOption);
        } catch (IOException e) {
            FeatJAR.log().error(e);
        }
    }

    public int optionLoop(int lastChanged) {
        switch (lastChanged) {
            case 0:
                String modelName = optionCombiner.getValue(0);
                modelID = getSystemId(modelName);
                Result<IFormula> model = modelReader.read(modelName);
                if (model.isEmpty()) {
                    FeatJAR.log().problems(model.getProblems());
                    return 0;
                }
                cnf = IBooleanRepresentation.toBooleanClauseList(model.get()).compute();
            case 1:
                algorithmIteration = optionCombiner.getValue(1);
        }

        FeatJAR.cache().clear();
        long start, end;
        start = System.nanoTime();
        Computations.of(cnf).map(ComputeCoreSAT4J::new).compute();
        end = System.nanoTime();
        long coreTime = end - start;

        start = System.nanoTime();
        Computations.of(cnf).map(ComputeAtomicSetsSAT4J::new).compute();

        end = System.nanoTime();
        long atomicTime = end - start;

        CSVFile.writeCSV(timeCSV, w -> {
            w.add(modelID);
            w.add(algorithmIteration);
            w.add(coreTime);
            w.add(atomicTime);
        });
        return -1;
    }
}
