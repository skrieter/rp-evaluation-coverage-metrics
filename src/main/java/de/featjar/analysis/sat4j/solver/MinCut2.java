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
// package de.featjar.analysis.sat4j.solver;
//
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Set;
//
// import de.featjar.analysis.sat4j.solver.ModalImplicationGraph2.Vertex;
//
// public class MinCut2 {
//	
//	 public class MinCut {
//	        private final int[] first;
//	        private final int[] second;
//	        private final int minCutWeight;
//
//	        public MinCut(int[] first,
//	        		int[] second,
//	                      int minCutWeight) {
//	            this.first = first;
//	            this.second = second;
//	            this.minCutWeight = minCutWeight;
//	        }
//
//	        public int getMinCutWeight() {
//	            return minCutWeight;
//	        }
//
//	    }
//
//	    static class CutOfThePhase {
//	        final int s;
//	        final int t;
//	        final int weight;
//
//	        public CutOfThePhase(int s, int t, int weight) {
//	            this.s = s;
//	            this.t = t;
//	            this.weight = weight;
//	        }
//
//	        @Override
//	        public String toString() {
//	            return "CutOfThePhase{" +
//	                    "s=" + s +
//	                    ", t=" + t +
//	                    ", weight=" + weight +
//	                    '}';
//	        }
//	    }
//
//	    /**
//	     * Computes the minimum cut (MinCut) of the given non-negatively weighted graph.
//	     * Running the algorithm results in two disjoint subgraphs, so that the sum of
//	     * weights between these two subgraphs is minimal.
//	     *
//	     * @param g weighted graph you want to cut in half
//	     * @return a @{@link MinCut} that contains both the first and second disjoint graph,
//	     * the removed edges that lead to that partition and their summed-up weight.
//	     */
//	    public MinCut computeMinCut(ModalImplicationGraph2 g) {
//	    	ModalImplicationGraph2 originalGraph = g;
//	        Set<Integer> currentPartition = new HashSet<>();
//	        Set<Integer> currentBestPartition = null;
//	        CutOfThePhase currentBestCut = null;
//	        while (g.size() > 1) {
//	            CutOfThePhase cutOfThePhase = maximumAdjacencySearch(g);
//	            if (currentBestCut == null || cutOfThePhase.weight < currentBestCut.weight) {
//	                currentBestCut = cutOfThePhase;
//	                currentBestPartition = new HashSet<>(currentPartition);
//	                currentBestPartition.add(cutOfThePhase.t);
//	            }
//	            currentPartition.add(cutOfThePhase.t);
//	            // merge s and t and their edges together
//	            g = mergeVerticesFromCut(g, cutOfThePhase);
//	        }
//
//	        return constructMinCutResult(originalGraph, currentBestPartition);
//	    }
//
//	    // we do a two-pass algorithm to reconstruct the sub-graphs:
//	    // - first we distribute the vertices into their respective graph
//	    // - second we align edges: if they cross graphs they will end in the list, otherwise in the respective graph
//	    private MinCut constructMinCutResult(ModalImplicationGraph2 originalGraph,
//	                                         Set<Integer> partition) {
//	    	ModalImplicationGraph2 first = new AdjacencyList<>();
//	    	ModalImplicationGraph2 second = new AdjacencyList<>();
//	        List<Tuple<Vertex<VERTEX_ID, VERTEX_VALUE>, Edge<VERTEX_ID, Integer>>> cuttingEdges = new ArrayList<>();
//	        int cutWeight = 0;
//
//	        for (int v : originalGraph.getVertexIDSet()) {
//	            if (partition.contains(v)) {
//	                first.addVertex(new VertexImpl<>(v, originalGraph.getVertex(v).getVertexValue()));
//	            } else {
//	                second.addVertex(new VertexImpl<>(v, originalGraph.getVertex(v).getVertexValue()));
//	            }
//	        }
//
//	        // we need to dedupe the edges for directed graphs, so we don't double-count the weights
//	        HashSet<OrderedTuple<VERTEX_ID>> edgeSet = new HashSet<>();
//	        for (int v : originalGraph.getVertexIDSet()) {
//	            Set<Edge<VERTEX_ID, Integer>> edges = originalGraph.getEdges(v);
//	            for (Edge<VERTEX_ID, Integer> e : edges) {
//	                if (first.getVertexIDSet().contains(v) &&
// first.getVertexIDSet().contains(e.getDestinationVertexID())) {
//	                    first.addEdge(v, new Edge<>(e.getDestinationVertexID(), e.getValue()));
//	                } else if (second.getVertexIDSet().contains(v) &&
// second.getVertexIDSet().contains(e.getDestinationVertexID())) {
//	                    second.addEdge(v, new Edge<>(e.getDestinationVertexID(), e.getValue()));
//	                } else {
//	                    cuttingEdges.add(new Tuple<>(originalGraph.getVertex(v), new Edge<>(e.getDestinationVertexID(),
// e.getValue())));
//	                    if (edgeSet.add(new OrderedTuple<>(v, e.getDestinationVertexID()))) {
//	                        cutWeight += e.getValue();
//	                    }
//	                }
//	            }
//	        }
//
//	        return new MinCut(first, second, cuttingEdges, cutWeight);
//	    }
//
//	    // bascially we're copying the whole graph into a new one, we leave the vertex "t" out of it (including the edge
// between "s" and "t")
//	    // and merge all other edges that "s" and "t" pointed together towards by summing their weight.
//	    // there might be left-over edges pointing from "t" but not through "s", we have to attach them to "s" as well.
//	    Graph<VERTEX_ID, VERTEX_VALUE, Integer> mergeVerticesFromCut(Graph<VERTEX_ID, VERTEX_VALUE, Integer> g,
// CutOfThePhase<VERTEX_ID> cutOfThePhase) {
//	        Graph<VERTEX_ID, VERTEX_VALUE, Integer> toReturn = new AdjacencyList<>();
//
//	        for (int v : g.getVertexIDSet()) {
//	            boolean isS = cutOfThePhase.s.equals(v);
//	            boolean isT = cutOfThePhase.t.equals(v);
//	            // plain copy
//	            if (!isS && !isT) {
//	                toReturn.addVertex(g.getVertex(v));
//	                for (Edge<VERTEX_ID, Integer> e : g.getEdges(v)) {
//	                    if (!e.getDestinationVertexID().equals(cutOfThePhase.s) &&
// !e.getDestinationVertexID().equals(cutOfThePhase.t)) {
//	                        toReturn.addEdge(v, new Edge<>(e.getDestinationVertexID(), e.getValue()));
//	                    }
//	                }
//	            }
//
//	            // on hitting "s" we are checking for the summation case (similar edge coming from "t"), otherwise just
// copy it over
//	            if (isS) {
//	                toReturn.addVertex(g.getVertex(v));
//	                for (Edge<VERTEX_ID, Integer> e : g.getEdges(v)) {
//	                    if (e.getDestinationVertexID().equals(cutOfThePhase.t)) {
//	                        continue;
//	                    }
//	                    Edge<VERTEX_ID, Integer> mergableEdge = g.getEdge(cutOfThePhase.t, e.getDestinationVertexID());
//	                    if (mergableEdge != null) {
//	                        toReturn.addEdge(v, new Edge<>(e.getDestinationVertexID(), e.getValue() +
// mergableEdge.getValue()));
//	                        toReturn.addEdge(e.getDestinationVertexID(), new Edge<>(v, e.getValue() +
// mergableEdge.getValue()));
//	                    } else {
//	                        toReturn.addEdge(v, new Edge<>(e.getDestinationVertexID(), e.getValue()));
//	                        toReturn.addEdge(e.getDestinationVertexID(), new Edge<>(v, e.getValue()));
//	                    }
//	                }
//	            }
//	        }
//
//	        // for all edges from "t" that we haven't transferred to "s" yet, but do not go towards "s"
//	        for (Edge<VERTEX_ID, Integer> e : g.getEdges(cutOfThePhase.t)) {
//	            if (e.getDestinationVertexID().equals(cutOfThePhase.s)) {
//	                continue;
//	            }
//	            Edge<VERTEX_ID, Integer> transferEdge = g.getEdge(cutOfThePhase.s, e.getDestinationVertexID());
//	            if (transferEdge == null) {
//	                toReturn.addEdge(cutOfThePhase.s, new Edge<>(e.getDestinationVertexID(), e.getValue()));
//	                toReturn.addEdge(e.getDestinationVertexID(), new Edge<>(cutOfThePhase.s, e.getValue()));
//	            }
//	        }
//
//
//	        return toReturn;
//	    }
//
//	    private CutOfThePhase maximumAdjacencySearch(ModalImplicationGraph2 g) {
//	        return maximumAdjacencySearch(g, null);
//	    }
//
//	    /**
//	     * This iterates the given graph starting from "start" in a maximum adjacency fashion.
//	     * That means, it will always connect to the next vertex whose inlinks are having the largest edge weight sum.
//	     * This ordering implicitly gives us a "cut of the phase", as the last two added vertices are the ones with least
// inlink weights.
//	     */
//	    CutOfThePhase maximumAdjacencySearch(ModalImplicationGraph2 g, int start) {
//	        if (start == null) {
//	            start = g.getVertexIDSet().iterator().next();
//	        }
//	        List<Integer> maxAdjacencyOrderedList = new ArrayList<>(Collections.singletonList(start));
//	        List<Integer> cutWeight = new ArrayList<>();
//	        Set<Integer> candidates = new HashSet<>(g.getVertexIDSet());
//	        candidates.remove(start);
//
//	        while (!candidates.isEmpty()) {
//	        	int maxNextVertex = null;
//	            int maxWeight = Integer.MIN_VALUE;
//	            for (int next : candidates) {
//	                // compute the inlink weight sum from all vertices in "maxAdjacencyOrderedList" towards vertex "next"
//	                int weightSum = 0;
//	                for (int s : maxAdjacencyOrderedList) {
//	                    Edge<VERTEX_ID, Integer> edge = g.getEdge(next, s);
//	                    if (edge != null) {
//	                        weightSum += edge.getValue();
//	                    }
//	                }
//
//	                if (weightSum > maxWeight) {
//	                    maxNextVertex = next;
//	                    maxWeight = weightSum;
//	                }
//	            }
//
//	            candidates.remove(maxNextVertex);
//	            maxAdjacencyOrderedList.add(maxNextVertex);
//	            cutWeight.add(maxWeight);
//	        }
//
//	        // we take the last two vertices in that list and their weight as a cut of the phase
//	        int n = maxAdjacencyOrderedList.size();
//	        return new CutOfThePhase(
//	                maxAdjacencyOrderedList.get(n - 2), // that's "s" in the literature and will remain as a merged
// vertex with "t"
//	                maxAdjacencyOrderedList.get(n - 1), // that's "t" and will be removed afterwards
//	                cutWeight.get(cutWeight.size() - 1)); // that's "w" to compute the minimum cut on later
//	    }
//
// }
