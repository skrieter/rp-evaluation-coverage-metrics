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

import de.featjar.formula.assignment.ABooleanAssignment;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Calculates statistics regarding t-wise feature coverage of a set of
 * solutions.
 *
 * @author anonymous
 */
public class SampleBitIndex implements Predicate<int[]> {

    private final BitSet[] bitSetReference;
    private final int numberOfVariables;
    private int sampleSize;

    public SampleBitIndex(final int numberOfVariables) {
        this.numberOfVariables = numberOfVariables;
        bitSetReference = new BitSet[2 * numberOfVariables + 1];

        sampleSize = 0;
        for (int j = 0; j < bitSetReference.length; j++) {
            bitSetReference[j] = new BitSet();
        }
    }

    public SampleBitIndex(List<? extends ABooleanAssignment> sample, final int numberOfVariables) {
        this(numberOfVariables);
        sample.forEach(this::addConfiguration);
    }

    public void addConfiguration(ABooleanAssignment config) {
        int i = sampleSize++;

        for (int l : config.get()) {
            if (l != 0) {
                bitSetReference[numberOfVariables + l].set(i);
            }
        }
    }

    public void clear(int index) {
    	for (int j = 0; j < bitSetReference.length; j++) {
            bitSetReference[j].clear(index);
        }
    }

    public void set(int index, ABooleanAssignment config) {
        for (int l : config.get()) {
            set(index, l);
        }
    }

    public void set(int index, int literal) {
        if (literal != 0) {
            bitSetReference[numberOfVariables - literal].clear(index);
            bitSetReference[numberOfVariables + literal].set(index);
        }
    }

    private BitSet getBitSet(int[] literals) {
        BitSet first = bitSetReference[numberOfVariables + literals[0]];
        BitSet bitSet = new BitSet(first.size());
        bitSet.xor(first);
        for (int k = 1; k < literals.length; k++) {
            bitSet.and(bitSetReference[numberOfVariables + literals[k]]);
        }
        return bitSet;
    }

    @Override
    public boolean test(int[] literals) {
        return !getBitSet(literals).isEmpty();
    }

    public int index(int[] literals) {
        return getBitSet(literals).length();
    }

    public int size(int[] literals) {
        return getBitSet(literals).cardinality();
    }
}
