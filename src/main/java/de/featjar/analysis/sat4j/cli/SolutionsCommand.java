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

import de.featjar.analysis.sat4j.computation.ComputeSolutionsSAT4J;
import de.featjar.analysis.sat4j.solver.ISelectionStrategy;
import de.featjar.base.cli.Option;
import de.featjar.base.cli.OptionList;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import java.util.Optional;

/**
 * Computes solutions for a given formula using SAT4J.
 *
 * @author anonymous
 * @author anonymous
 */
public class SolutionsCommand extends ASAT4JAnalysisCommand<BooleanSolutionList, BooleanSolutionList> {

    /**
     * Maximum number of configurations to be generated.
     */
    public static final Option<Integer> LIMIT_OPTION = Option.newOption("n", Option.IntegerParser) //
            .setDescription("Maximum number of configurations to be generated.") //
            .setDefaultValue(1);

    /**
     * Strategy to use for generating each configuration (%s).
     */
    public static final Option<ISelectionStrategy.Strategy> SELECTION_STRATEGY_OPTION = Option.newOption(
                    "strategy", Option.valueOf(ISelectionStrategy.Strategy.class)) //
            .setDescription(String.format(
                    "Strategy to use for generating each configuration (%s).",
                    Option.possibleValues(ISelectionStrategy.Strategy.class))) //
            .setDefaultValue(ISelectionStrategy.Strategy.ORIGINAL);

    /**
     * Forbid duplicate configurations to be generated.
     */
    public static final Option<Boolean> FORBID_DUPLICATES_OPTION = Option.newFlag("no-dublicates") //
            .setDescription("Forbid dublicate configurations to be generated.");

    @Override
    public Optional<String> getDescription() {
        return Optional.of("Computes solutions for a given formula using SAT4J.");
    }

    @Override
    public IComputation<BooleanSolutionList> newAnalysis(OptionList optionParser, ComputeBooleanClauseList formula) {
        return formula.map(Computations::getKey)
                .map(ComputeSolutionsSAT4J::new)
                .set(
                        ComputeSolutionsSAT4J.FORBID_DUPLICATES,
                        optionParser.getResult(FORBID_DUPLICATES_OPTION).get())
                .set(
                        ComputeSolutionsSAT4J.LIMIT,
                        optionParser.getResult(LIMIT_OPTION).get())
                .set(
                        ComputeSolutionsSAT4J.SELECTION_STRATEGY,
                        optionParser.getResult(SELECTION_STRATEGY_OPTION).get())
                .set(
                        ComputeSolutionsSAT4J.RANDOM_SEED,
                        optionParser.getResult(RANDOM_SEED_OPTION).get())
                .set(
                        ComputeSolutionsSAT4J.SAT_TIMEOUT,
                        optionParser.getResult(SAT_TIMEOUT_OPTION).get());
    }

    @Override
    public String serializeResult(BooleanSolutionList list) {
        return list.print();
    }

    @Override
    public Optional<String> getShortName() {
        return Optional.of("solutions-sat4j");
    }
}
