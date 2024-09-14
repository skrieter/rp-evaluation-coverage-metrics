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
package de.featjar.analysis.sat4j.computation;

import de.featjar.base.data.BinomialCalculator;
import de.featjar.base.data.MultiLexicographicIterator;
import de.featjar.formula.assignment.BooleanAssignment;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MultiCombinationSpecification implements ICombinationSpecification {

    private final int totalSteps;
    private final int[] tValues;

    private final int[][] literalSets;

    public MultiCombinationSpecification(List<BooleanAssignment> variables) {
        this(variables, IntStream.generate(() -> 1).limit(variables.size()).toArray());
    }

    public MultiCombinationSpecification(List<BooleanAssignment> variables, int[] tValues) {
        if (variables.size() != tValues.length) {
            throw new IllegalArgumentException(String.format(
                    "Number of variable sets (%d) must be the same as number of t values (%d)",
                    variables.size(), tValues.length));
        }
        for (int t : tValues) {
            if (t < 1) {
                throw new IllegalArgumentException(
                        "Values for t must be greater than 0. Values were " + Arrays.toString(tValues));
            }
        }

        literalSets = new int[tValues.length][];
        int intermediateTotelSteps = 0;
        for (int i = 0; i < tValues.length; i++) {
            int t = tValues[i];
            int[] literals = variables.get(i).get();
            literalSets[i] = literals;
            if (literals.length < t) {
                throw new IllegalArgumentException(
                        String.format("Value for t must be grater than number of variables", t, literals.length));
            }
            intermediateTotelSteps *= (int) (BinomialCalculator.computeBinomial(literals.length, t));
        }
        totalSteps = intermediateTotelSteps;

        this.tValues = tValues;
    }

    @Override
    public Stream<int[]> stream() {
        return MultiLexicographicIterator.stream(literalSets, tValues).map(combo -> combo.select());
    }

    @Override
    public int getTotalSteps() {
        return totalSteps;
    }

    @Override
    public void shuffle(Random random) {
        final long seed = random.nextLong();
        for (int[] literalSet : literalSets) {
            Random curRandom = new Random(seed);
            for (int i = literalSet.length - 1; i >= 0; --i) {
                int swapIndex = curRandom.nextInt(literalSet.length);
                int temp = literalSet[i];
                literalSet[i] = literalSet[swapIndex];
                literalSet[swapIndex] = temp;
            }
        }
    }
}
