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
package de.featjar.analysis.sat4j.cli;

import de.featjar.analysis.sat4j.computation.YASA;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.io.format.IFormat;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import de.featjar.formula.io.csv.BooleanSolutionListCSVFormat;
import java.util.List;
import java.util.Optional;

/**
 * Computes solutions for a given formula using SAT4J.
 *
 * @author Sebastian Krieter
 * @author Andreas Gerasimow
 */
public class TWiseCommand extends ASAT4JAnalysisCommand<BooleanSolutionList, BooleanSolutionList> {

    /**
     * Maximum number of configurations to be generated.
     */
    public static final Option<Integer> LIMIT_OPTION = Option.newOption("n", Option.IntegerParser) //
            .setDescription("Maximum number of configurations to be generated.") //
            .setDefaultValue(Integer.MAX_VALUE);

    /**
     * Value of t.
     */
    public static final Option<Integer> T_OPTION = Option.newOption("t", Option.IntegerParser) //
            .setDescription("Value of parameter t.") //
            .setDefaultValue(2);

    /**
     * Number of iterations.
     */
    public static final Option<Integer> ITERATIONS_OPTION = Option.newOption("i", Option.IntegerParser) //
            .setDescription("Number of iterations.") //
            .setDefaultValue(1);

    @Override
    public Optional<String> getDescription() {
        return Optional.of("Computes solutions for a given formula using SAT4J");
    }

    @Override
    public IComputation<BooleanSolutionList> newAnalysis(OptionList optionParser, ComputeBooleanClauseList formula) {
        return formula.map(Computations::getKey)
                .map(YASA::new)
                .set(YASA.T, optionParser.get(T_OPTION))
                .set(YASA.CONFIGURATION_LIMIT, optionParser.get(LIMIT_OPTION))
                .set(YASA.ITERATIONS, optionParser.get(ITERATIONS_OPTION))
                .set(YASA.RANDOM_SEED, optionParser.get(RANDOM_SEED_OPTION))
                .set(YASA.SAT_TIMEOUT, optionParser.get(SAT_TIMEOUT_OPTION));
    }

    @Override
    protected Object getOuputObject(BooleanSolutionList list) {
        return new BooleanAssignmentGroups(VariableMap.of(inputFormula), List.of(list.getAll()));
    }

    @Override
    protected IFormat<?> getOuputFormat() {
        return new BooleanSolutionListCSVFormat();
    }

    @Override
    public String serializeResult(BooleanSolutionList assignments) {
        return assignments.serialize();
    }

    @Override
    public Optional<String> getShortName() {
        return Optional.of("t-wise-sat4j");
    }
}
