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

import de.featjar.analysis.sat4j.solver.ModalImplicationGraph;
import de.featjar.base.data.ExpandableIntegerList;
import de.featjar.formula.assignment.ABooleanAssignment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Calculates statistics regarding t-wise feature coverage of a set of
 * solutions.
 *
 * @author anonymous
 */
public class SampleListIndex implements Predicate<int[]> {

    private final ArrayList<ExpandableIntegerList> indexedSolutions;
    private final ExpandableIntegerList[] selectedIndexedSolutions;

    public SampleListIndex(List<? extends ABooleanAssignment> sample, final int size, final int t) {
        indexedSolutions = new ArrayList<>(2 * size);
        for (int i = 2 * size; i >= 0; --i) {
            indexedSolutions.add(new ExpandableIntegerList());
        }
        int configurationIndex = 0;
        for (ABooleanAssignment configuration : sample) {
            final int[] literals = configuration.get();
            for (int i = 0; i < literals.length; i++) {
                final int literal = literals[i];
                if (literal != 0) {
                    indexedSolutions
                            .get(ModalImplicationGraph.getVertexIndex(literal))
                            .add(configurationIndex);
                }
            }
            configurationIndex++;
        }
        selectedIndexedSolutions = new ExpandableIntegerList[t];
    }

    @Override
    public boolean test(int[] literals) {
        if (literals.length < 2) {
            return !indexedSolutions
                    .get(ModalImplicationGraph.getVertexIndex(literals[0]))
                    .isEmpty();
        }
        for (int i = 0; i < literals.length; i++) {
            final ExpandableIntegerList indexedSolution =
                    indexedSolutions.get(ModalImplicationGraph.getVertexIndex(literals[i]));
            if (indexedSolution.size() == 0) {
                return false;
            }
            selectedIndexedSolutions[i] = indexedSolution;
        }
        Arrays.sort(selectedIndexedSolutions, (a, b) -> a.size() - b.size());
        final int[] ix = new int[literals.length - 1];

        final ExpandableIntegerList i0 = selectedIndexedSolutions[0];
        final int[] ia0 = i0.toArray();
        loop:
        for (int i = 0; i < i0.size(); i++) {
            int id0 = ia0[i];
            for (int j = 1; j < literals.length; j++) {
                final ExpandableIntegerList i1 = selectedIndexedSolutions[j];
                int binarySearch = Arrays.binarySearch(i1.toArray(), ix[j - 1], i1.size(), id0);
                if (binarySearch < 0) {
                    ix[j - 1] = -binarySearch - 1;
                    continue loop;
                } else {
                    ix[j - 1] = binarySearch;
                }
            }
            return true;
        }
        return false;
    }
}
