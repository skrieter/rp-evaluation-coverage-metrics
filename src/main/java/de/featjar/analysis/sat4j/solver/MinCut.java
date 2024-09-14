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
// import java.util.Arrays;
// import java.util.HashSet;
// import java.util.Set;
//
// public class MinCut {
//	
//	public static class MinCutResult {
//		int minCut;
//		Set<Integer> partition1;
//		Set<Integer> partition2;
//
//		public MinCutResult(int minCut, Set<Integer> partition1, Set<Integer> partition2) {
//			this.minCut = minCut;
//			this.partition1 = partition1;
//			this.partition2 = partition2;
//		}
//	}
//
//	private int n; // number of vertices
//	private ModalImplicationGraph2 graph; // adjacency matrix of the graph
//
//	public MinCut(ModalImplicationGraph2 graph) {
//		this.n = graph.size();
//		this.graph = graph;
//	}
//
//	public MinCutResult minCut() {
//		int minCut = Integer.MAX_VALUE;
//		boolean[] exist = new boolean[n];
//		Arrays.fill(exist, true);
//		Set<Integer>[] bestPartition = new Set[2];
//
//		for (int phase = 0; phase < n - 1; phase++) {
//			boolean[] used = new boolean[n];
//			int[] weight = new int[n];
//			int prev = -1, last = 0;
//
//			for (int i = 0; i < n; i++) {
//				int select = -1;
//				for (int j = 0; j < n; j++) {
//					if (exist[j] && !used[j] && (select == -1 || weight[j] > weight[select])) {
//						select = j;
//					}
//				}
//
//				if (i == n - phase - 1) {
//					if (weight[select] < minCut) {
//						minCut = weight[select];
//						bestPartition[0] = new HashSet<>();
//						bestPartition[1] = new HashSet<>();
//						for (int k = 0; k < n; k++) {
//							if (exist[k]) {
//								if (used[k]) {
//									bestPartition[0].add(k);
//								} else {
//									bestPartition[1].add(k);
//								}
//							}
//						}
//					}
//					for (int j = 0; j < n; j++) {
//						graph[prev][j] += graph[select][j];
//						graph[j][prev] = graph[prev][j];
//					}
//					exist[select] = false;
//				} else {
//					used[select] = true;
//					prev = last;
//					last = select;
//					for (int j = 0; j < n; j++) {
//						weight[j] += graph[select][j];
//					}
//				}
//			}
//		}
//		return new MinCutResult(minCut, bestPartition[0], bestPartition[1]);
//	}
//
//	public static void main(String[] args) {
//		int[][] graph = { { 0, 2, 3, 3 }, { 2, 0, 4, 5 }, { 3, 4, 0, 1 }, { 3, 5, 1, 0 } };
//		MinCut stoerWagner = new MinCut(graph);
//		MinCutResult result = stoerWagner.minCut();
//		System.out.println("The minimum cut of the graph is: " + result.minCut);
//		System.out.println("Partition 1: " + result.partition1);
//		System.out.println("Partition 2: " + result.partition2);
//	}
//
// }
