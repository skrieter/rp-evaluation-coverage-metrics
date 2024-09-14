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

import de.featjar.evaluation.process.EvaluationAlgorithm;
import java.nio.file.Path;

public class YASASampleAlgorithm extends EvaluationAlgorithm {

    private final int t;
    private final int iterations;
    private final boolean incremental;
    private final long seed;

    public YASASampleAlgorithm(
            String jarName, Path input, Path output, int t, int iterations, boolean incremental, long seed) {
        super(jarName, "de.featjar.analysis.sat4j.cli.TWiseCommand", input, output);
        this.t = t;
        this.iterations = iterations;
        this.incremental = incremental;
        this.seed = seed;
    }

    @Override
    public String getName() {
        return "yasa-sampler";
    }

    @Override
    public String getParameterSettings() {
        return "t" + t + "_i" + iterations + (incremental ? "_inc" : "_non");
    }

    @Override
    protected void addCommandElements() throws Exception {
        super.addCommandElements();
        commandElements.add("--t");
        commandElements.add(String.valueOf(t));
        commandElements.add("--incremental");
        commandElements.add(String.valueOf(incremental));
        commandElements.add("--i");
        commandElements.add(String.valueOf(iterations));
        commandElements.add("--seed");
        commandElements.add(String.valueOf(seed));
    }
}
