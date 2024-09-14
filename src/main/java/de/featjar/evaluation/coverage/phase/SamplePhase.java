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
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.Evaluator;
import de.featjar.evaluation.coverage.YASASampleAlgorithm;
import de.featjar.evaluation.process.EvaluationAlgorithm;
import de.featjar.evaluation.process.ProcessResult;
import de.featjar.evaluation.process.ProcessRunner;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.io.csv.BooleanSolutionListCSVFormat;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Creates t-wise samples per given model and t.
 *
 * @author Sebastian Krieter
 */
public class SamplePhase extends Evaluator {

    public static final ListOption<Integer> tOption = Option.newListOption("t", Option.IntegerParser);
    public static final Option<String> jarNameOption = Option.newOption("jar-name", Option.StringParser);

    private String modelName;
    private Path modelPath;
    private int modelID, modelIteration, t, sampleSize;
    private CSVFile sampleCSV;
    private boolean errorOccured, timeoutOccured;
    private String jarName;

    @Override
    public void runEvaluation() {
        try {
            sampleCSV = new CSVFile(csvPath.resolve("samples.csv"));
            sampleCSV.setHeaderFields("SystemID", "T", "SystemIteration", "Error", "Timeout", "Size");
            sampleCSV.flush();

            jarName = optionParser.get(jarNameOption);

            optionCombiner.init(systemsOption, tOption, systemIterationsOption);
            optionCombiner.loopOverOptions(this::optionLoop, this::errorLoop);
        } catch (IOException e) {
            FeatJAR.log().error(e);
        }
    }

    private int optionLoop(int lastChanged) {
        switch (lastChanged) {
            case 0:
                modelName = optionCombiner.getValue(0);
                modelID = getSystemId(modelName);
                modelPath = genPath.resolve(modelName);
            case 1:
                t = optionCombiner.getValue(1);
            case 2:
                modelIteration = optionCombiner.getValue(2);

                errorOccured = true;
                timeoutOccured = false;
                sampleSize = -1;

                Path cnfFile = modelPath.resolve("cnf.dimacs");
                Path sampleFile = modelPath.resolve(String.format("sample_t%d_mi%d.csv", t, modelIteration));

                EvaluationAlgorithm algorithm =
                        new YASASampleAlgorithm(jarName, cnfFile, sampleFile, t, 5, true, modelIteration);

                algorithm.setMemory(optionParser.get(memory));

                ProcessRunner runner = new ProcessRunner();
                runner.setTimeout(optionParser.get(timeout));
                ProcessResult<Void> result = runner.run(algorithm);

                errorOccured = !result.isNoError();
                timeoutOccured = !result.isTerminatedInTime();

                Result<BooleanAssignmentGroups> load = IO.load(sampleFile, new BooleanSolutionListCSVFormat());
                if (load.isEmpty()) {
                    FeatJAR.log().problems(load.getProblems());
                    writeSampleEntry();
                    return 0;
                }

                sampleSize = load.get().getFirstGroup().size();

                writeSampleEntry();
                break;
            default:
                throw new IllegalStateException();
        }
        return -1;
    }

    private void errorLoop(int lastChanged) {
        switch (lastChanged) {
            case 0:
                modelName = optionCombiner.getValue(0);
                modelID = getSystemId(modelName);
                modelPath = genPath.resolve(modelName);
            case 1:
                t = optionCombiner.getValue(1);
            case 2:
                modelIteration = optionCombiner.getValue(2);
                writeSampleEntry();
        }
    }

    private void writeSampleEntry() {
        CSVFile.writeCSV(sampleCSV, w -> {
            w.add(modelID);
            w.add(t);
            w.add(modelIteration);
            w.add(errorOccured);
            w.add(timeoutOccured);
            w.add(sampleSize);
        });
    }
}
