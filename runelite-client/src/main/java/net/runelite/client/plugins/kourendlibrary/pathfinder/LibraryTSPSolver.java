/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.kourendlibrary.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Traveling Salesman Problem solver using the Held-Karp dynamic programming algorithm.
 * This provides an optimal solution in O(n^2 * 2^n) time, which is efficient for
 * the small number of books in the library (max 16).
 */
public class LibraryTSPSolver
{
	private static final int INF = Integer.MAX_VALUE / 2;

	/**
	 * Solves the TSP problem and returns the optimal order to visit all nodes.
	 *
	 * @param distances Distance matrix where distances[i][j] is the distance from node i to node j.
	 *                  Node 0 is the starting position (player location).
	 * @return List of node indices in optimal visiting order (excluding the starting node 0)
	 */
	public static List<Integer> solve(int[][] distances)
	{
		int n = distances.length;

		if (n <= 1)
		{
			return new ArrayList<>();
		}

		if (n == 2)
		{
			// Only one destination besides start
			List<Integer> result = new ArrayList<>();
			result.add(1);
			return result;
		}

		// dp[mask][i] = minimum distance to reach node i having visited nodes in mask
		// mask is a bitmask where bit j is set if node j has been visited
		int[][] dp = new int[1 << n][n];
		int[][] parent = new int[1 << n][n];

		for (int[] row : dp)
		{
			Arrays.fill(row, INF);
		}
		for (int[] row : parent)
		{
			Arrays.fill(row, -1);
		}

		// Start at node 0
		dp[1][0] = 0;

		// Iterate through all subsets
		for (int mask = 1; mask < (1 << n); mask++)
		{
			// Only consider subsets that include the starting node
			if ((mask & 1) == 0)
			{
				continue;
			}

			for (int last = 0; last < n; last++)
			{
				// Check if 'last' is in the current subset
				if ((mask & (1 << last)) == 0)
				{
					continue;
				}

				if (dp[mask][last] == INF)
				{
					continue;
				}

				// Try to extend to each unvisited node
				for (int next = 1; next < n; next++)
				{
					// Skip if next is already visited
					if ((mask & (1 << next)) != 0)
					{
						continue;
					}

					int newMask = mask | (1 << next);
					int newDist = dp[mask][last] + distances[last][next];

					if (newDist < dp[newMask][next])
					{
						dp[newMask][next] = newDist;
						parent[newMask][next] = last;
					}
				}
			}
		}

		// Find the best final node (we don't need to return to start)
		int fullMask = (1 << n) - 1;
		int bestEnd = 1;
		int bestDist = dp[fullMask][1];

		for (int i = 2; i < n; i++)
		{
			if (dp[fullMask][i] < bestDist)
			{
				bestDist = dp[fullMask][i];
				bestEnd = i;
			}
		}

		// Reconstruct the path
		List<Integer> path = new ArrayList<>();
		int mask = fullMask;
		int current = bestEnd;

		while (current != 0)
		{
			path.add(0, current);
			int prev = parent[mask][current];
			mask = mask ^ (1 << current);
			current = prev;
		}

		return path;
	}

	/**
	 * Solves TSP using nearest neighbor heuristic.
	 * Faster but not guaranteed optimal. Use as fallback for very large inputs.
	 *
	 * @param distances Distance matrix
	 * @return List of node indices in visiting order (excluding start node 0)
	 */
	public static List<Integer> solveNearestNeighbor(int[][] distances)
	{
		int n = distances.length;

		if (n <= 1)
		{
			return new ArrayList<>();
		}

		boolean[] visited = new boolean[n];
		List<Integer> path = new ArrayList<>();

		visited[0] = true; // Start node
		int current = 0;

		for (int step = 1; step < n; step++)
		{
			int nearest = -1;
			int nearestDist = INF;

			for (int next = 1; next < n; next++)
			{
				if (!visited[next] && distances[current][next] < nearestDist)
				{
					nearest = next;
					nearestDist = distances[current][next];
				}
			}

			if (nearest != -1)
			{
				visited[nearest] = true;
				path.add(nearest);
				current = nearest;
			}
		}

		return path;
	}
}
