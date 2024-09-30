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
package de.featjar.analysis.sat4j.solver;

import de.featjar.analysis.RuntimeContradictionException;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Adjacency list implementation based on arrays. Intended to use for faster
 * traversion.
 *
 * @author anonymous
 */
public class ModalImplicationGraph {

    private final int size;

    private final int[] core;

    private final int[][] strong;

    private final int[][] clauseIndices;
    private final int[] clauses;

    // TODO join both index arrays
    private final int[][] clauseLengthsIndices;
    private final int[] clauseLengths;

    public class Visitor {

        private final int[] clauseCounts;
        private final int[] model;
        private final int[] addedLiterals;

        private int addedLiteralCount;

        public Visitor(int[] model) {
            this.model = model;
            for (int l : core) {
                model[Math.abs(l) - 1] = l;
            }
            addedLiterals = new int[size - core.length];
            clauseCounts = Arrays.copyOf(clauseLengths, clauseLengths.length);
        }

        public Visitor() {
            this(new int[size]);
        }

        public Visitor(Visitor oldVisitor, int[] model) {
            clauseCounts = Arrays.copyOf(oldVisitor.clauseCounts, oldVisitor.clauseCounts.length);
            addedLiterals = new int[size - core.length];
            System.arraycopy(oldVisitor.addedLiterals, 0, addedLiterals, 0, oldVisitor.addedLiteralCount);
            this.model = model;
            addedLiteralCount = oldVisitor.addedLiteralCount;
        }

        public ModalImplicationGraph getVisitorProvider() {
            return ModalImplicationGraph.this;
        }

        public int[] getModel() {
            return model;
        }

        public int[] getAddedLiterals() {
            return addedLiterals;
        }

        public int getAddedLiteralCount() {
            return addedLiteralCount;
        }

        public void propagate(int... literals) throws RuntimeContradictionException {
            for (int l : literals) {
                if (l != 0) {
                    processLiteral(l);
                }
            }
        }

        // TODO implement proper traversal
        private boolean[] traverseWeak(int literal) {
            final ArrayDeque<Integer> queue = new ArrayDeque<>();
            final boolean[] mark = new boolean[2 * size];
            mark[ModalImplicationGraph.getVertexIndex(literal)] = true;
            queue.add(literal);

            while (!queue.isEmpty()) {
                final int vertexLiteral = queue.removeFirst();
                final int vertexIndex = ModalImplicationGraph.getVertexIndex(vertexLiteral);
                int[] vertexClauseIndices = clauseIndices[vertexIndex];
                int[] vertexLengthsIndices = clauseLengthsIndices[vertexIndex];
                for (int i = 0; i < vertexClauseIndices.length; i++) {
                    int clauseIndex = vertexClauseIndices[i];
                    int clauseLength = vertexLengthsIndices[i];
                    for (int j = clauseIndex; j < clauseLength + clauseLength; j++) {
                        int clauseLiteral = vertexLengthsIndices[j];
                        if (clauseLiteral != -vertexLiteral) {
                            // TODO
                        }
                    }
                }
            }
            return mark;
        }

        public boolean isContradiction(int... literals) {
            final int oldModelCount = addedLiteralCount;
            try {
                propagate(literals);
                return false;
            } catch (RuntimeContradictionException e) {
                return true;
            } finally {
                reset(oldModelCount);
            }
        }

        public void reset() {
            for (int i = 0; i < addedLiteralCount; i++) {
                final int l = addedLiterals[i];
                model[Math.abs(l) - 1] = 0;
                addedLiterals[i] = 0;
                for (int clauseCountIndex : clauseLengthsIndices[getVertexIndex(l)]) {
                    clauseCounts[clauseCountIndex] = clauseLengths[clauseCountIndex];
                }
            }
            addedLiteralCount = 0;
        }

        public void reset(int keep) {
            for (int i = 0; i < addedLiteralCount; i++) {
                for (int clauseCountIndex : clauseLengthsIndices[getVertexIndex(addedLiterals[i])]) {
                    clauseCounts[clauseCountIndex] = clauseLengths[clauseCountIndex];
                }
            }
            for (int i = keep; i < addedLiteralCount; i++) {
                model[Math.abs(addedLiterals[i]) - 1] = 0;
                addedLiterals[i] = 0;
            }
            addedLiteralCount = keep;
            for (int i = 0; i < addedLiteralCount; i++) {
                for (int j : clauseLengthsIndices[getVertexIndex(addedLiterals[i])]) {
                    --clauseCounts[j];
                }
            }
        }

        private void processLiteral(int l) {
            final int varIndex = Math.abs(l) - 1;
            final int setL = model[varIndex];
            if (setL == 0) {
                model[varIndex] = l;
                addedLiterals[addedLiteralCount++] = l;

                final int i = getVertexIndex(l);

                for (int strongL : strong[i]) {
                    final int varIndex1 = Math.abs(strongL) - 1;
                    final int setL1 = model[varIndex1];
                    if (setL1 == 0) {
                        model[varIndex1] = strongL;
                        addedLiterals[addedLiteralCount++] = strongL;
                        processWeak(getVertexIndex(strongL));
                    } else if (setL1 != strongL) {
                        throw new RuntimeContradictionException();
                    }
                }

                processWeak(i);
            } else if (setL != l) {
                throw new RuntimeContradictionException();
            }
        }

        private void processWeak(final int index) {
            final int[] clauseCountIndexList = clauseLengthsIndices[index];
            weakLoop:
            for (int j = 0; j < clauseCountIndexList.length; j++) {
                final int clauseCountIndex = clauseCountIndexList[j];
                final int count = --clauseCounts[clauseCountIndex];
                if (count <= 1) {
                    if (count == 1) {
                        int clauseIndex = clauseIndices[index][j];
                        for (int end = clauseIndex + clauseLengths[clauseCountIndex], k = clauseIndex; k < end; k++) {
                            final int newL = clauses[k];
                            final int modelL = model[Math.abs(newL) - 1];
                            if (modelL == 0 || modelL == newL) {
                                processLiteral(newL);
                                continue weakLoop;
                            }
                        }
                    }
                    throw new RuntimeContradictionException();
                }
            }
        }
    }

    public static int getVertexIndex(int literal) {
        return literal < 0 ? (-literal - 1) << 1 : ((literal - 1) << 1) + 1;
    }

    public ModalImplicationGraph(
            int size,
            int[] core,
            int[][] strong,
            int[][] clauseIndices,
            int[] clauses,
            int[][] clauseLengthIndices,
            int[] clauseLength) {
        this.size = size;
        this.core = core;
        this.strong = strong;
        this.clauseIndices = clauseIndices;
        this.clauses = clauses;
        this.clauseLengthsIndices = clauseLengthIndices;
        this.clauseLengths = clauseLength;
    }

    public Visitor getVisitor() {
        return new Visitor();
    }

    public Visitor getVisitor(int[] model) {
        return new Visitor(model);
    }

    public int[] getCore() {
        return core;
    }

    public int size() {
        return size;
    }
}
