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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.kourendlibrary.Book;
import net.runelite.client.plugins.kourendlibrary.Bookcase;
import net.runelite.client.plugins.kourendlibrary.Library;

/**
 * Service for computing optimal paths through the library to collect books.
 */
@Singleton
@Slf4j
public class LibraryPathfinderService
{
	private static final int INF = Integer.MAX_VALUE / 2;

	private final Client client;
	private final Library library;

	private CollisionMap collisionMap;
	private Map<WorldPoint, List<WorldPoint>> transports;

	// Cache for path distances between locations
	private final Map<PathKey, Integer> distanceCache = new ConcurrentHashMap<>();
	private final Map<PathKey, List<WorldPoint>> pathCache = new ConcurrentHashMap<>();

	@Getter
	private List<WorldPoint> optimalPath;

	@Getter
	private List<Bookcase> optimalBookcaseOrder;

	private volatile boolean computing = false;

	@Inject
	public LibraryPathfinderService(Client client, Library library)
	{
		this.client = client;
		this.library = library;
	}

	public void initialize()
	{
		if (collisionMap != null)
		{
			return;
		}

		loadCollisionMap();
		transports = LibraryTransports.getTransports();
	}

	private void loadCollisionMap()
	{
		Map<SplitFlagMap.Position, byte[]> compressedRegions = new HashMap<>();

		try (ZipInputStream in = new ZipInputStream(getClass().getResourceAsStream("collision-map.zip")))
		{
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null)
			{
				String[] n = entry.getName().split("_");
				compressedRegions.put(
					new SplitFlagMap.Position(Integer.parseInt(n[0]), Integer.parseInt(n[1])),
					readAllBytes(in)
				);
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}

		collisionMap = new CollisionMap(64, compressedRegions);
	}

	private static byte[] readAllBytes(InputStream in) throws IOException
	{
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];

		while (true)
		{
			int read = in.read(buffer, 0, buffer.length);
			if (read == -1)
			{
				return result.toByteArray();
			}
			result.write(buffer, 0, read);
		}
	}

	/**
	 * Clears the distance cache. Should be called when the library resets.
	 */
	public void clearCache()
	{
		distanceCache.clear();
		pathCache.clear();
		optimalPath = null;
		optimalBookcaseOrder = null;
	}

	/**
	 * Computes the optimal path to collect all specified books.
	 *
	 * @param playerLocation Current player location
	 * @param booksToCollect List of bookcases containing books to collect
	 * @return true if computation started, false if already computing
	 */
	public boolean computeOptimalPath(WorldPoint playerLocation, List<Bookcase> booksToCollect)
	{
		if (computing || booksToCollect.isEmpty())
		{
			return false;
		}

		if (collisionMap == null)
		{
			initialize();
		}

		computing = true;

		// Run computation in background thread to avoid blocking game thread
		new Thread(() -> {
			try
			{
				computeOptimalPathInternal(playerLocation, booksToCollect);
			}
			catch (Exception e)
			{
				log.error("Error computing optimal path", e);
			}
			finally
			{
				computing = false;
			}
		}).start();

		return true;
	}

	private void computeOptimalPathInternal(WorldPoint playerLocation, List<Bookcase> booksToCollect)
	{
		int n = booksToCollect.size() + 1; // +1 for player start position
		int[][] distances = new int[n][n];

		// Build list of all locations (player first, then bookcases)
		List<WorldPoint> locations = new ArrayList<>();
		locations.add(playerLocation);
		for (Bookcase bc : booksToCollect)
		{
			locations.add(bc.getLocation());
		}

		// Compute distance matrix
		for (int i = 0; i < n; i++)
		{
			for (int j = i + 1; j < n; j++)
			{
				int distance = getDistance(locations.get(i), locations.get(j));
				distances[i][j] = distance;
				distances[j][i] = distance;
			}
		}

		// Solve TSP
		List<Integer> order;
		if (n <= 12)
		{
			// Use optimal Held-Karp for small inputs
			order = LibraryTSPSolver.solve(distances);
		}
		else
		{
			// Use nearest neighbor heuristic for larger inputs
			order = LibraryTSPSolver.solveNearestNeighbor(distances);
		}

		// Build the optimal bookcase order
		List<Bookcase> orderedBookcases = new ArrayList<>();
		for (int idx : order)
		{
			orderedBookcases.add(booksToCollect.get(idx - 1)); // -1 because index 0 is player
		}
		this.optimalBookcaseOrder = orderedBookcases;

		// Build the complete path
		List<WorldPoint> fullPath = new ArrayList<>();
		WorldPoint current = playerLocation;

		for (Bookcase bc : orderedBookcases)
		{
			List<WorldPoint> segment = getPath(current, bc.getLocation());
			if (segment != null && !segment.isEmpty())
			{
				// Avoid duplicating the starting point of each segment
				if (!fullPath.isEmpty() && !segment.isEmpty() && fullPath.get(fullPath.size() - 1).equals(segment.get(0)))
				{
					segment = segment.subList(1, segment.size());
				}
				fullPath.addAll(segment);
			}
			current = bc.getLocation();
		}

		this.optimalPath = fullPath;
		log.info("Computed optimal path with {} waypoints to collect {} books", fullPath.size(), booksToCollect.size());
	}

	/**
	 * Gets the walking distance between two points.
	 * Results are cached for performance.
	 */
	private int getDistance(WorldPoint from, WorldPoint to)
	{
		if (from.equals(to))
		{
			return 0;
		}

		PathKey key = new PathKey(from, to);
		Integer cached = distanceCache.get(key);
		if (cached != null)
		{
			return cached;
		}

		Pathfinder pathfinder = new Pathfinder(collisionMap, transports, from, to);
		int distance = pathfinder.findDistance();
		if (distance < 0)
		{
			distance = INF;
		}

		distanceCache.put(key, distance);
		distanceCache.put(new PathKey(to, from), distance);

		return distance;
	}

	/**
	 * Gets the path between two points.
	 * Results are cached for performance.
	 */
	public List<WorldPoint> getPath(WorldPoint from, WorldPoint to)
	{
		if (from.equals(to))
		{
			return new ArrayList<>();
		}

		PathKey key = new PathKey(from, to);
		List<WorldPoint> cached = pathCache.get(key);
		if (cached != null)
		{
			return new ArrayList<>(cached);
		}

		if (collisionMap == null)
		{
			initialize();
		}

		Pathfinder pathfinder = new Pathfinder(collisionMap, transports, from, to);
		List<WorldPoint> path = pathfinder.find();
		if (path == null)
		{
			path = new ArrayList<>();
		}

		pathCache.put(key, path);

		return new ArrayList<>(path);
	}

	/**
	 * Checks if optimal path computation is in progress.
	 */
	public boolean isComputing()
	{
		return computing;
	}

	/**
	 * Gets the next bookcase in the optimal order that the player should visit.
	 */
	public Bookcase getNextBookcase(WorldPoint playerLocation)
	{
		if (optimalBookcaseOrder == null || optimalBookcaseOrder.isEmpty())
		{
			return null;
		}

		// Find the first bookcase in the order that the player hasn't reached yet
		for (Bookcase bc : optimalBookcaseOrder)
		{
			if (playerLocation.distanceTo(bc.getLocation()) > 1)
			{
				return bc;
			}
		}

		return null;
	}

	/**
	 * Key for caching path calculations.
	 */
	private static class PathKey
	{
		private final WorldPoint from;
		private final WorldPoint to;

		PathKey(WorldPoint from, WorldPoint to)
		{
			this.from = from;
			this.to = to;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof PathKey))
			{
				return false;
			}
			PathKey other = (PathKey) o;
			return from.equals(other.from) && to.equals(other.to);
		}

		@Override
		public int hashCode()
		{
			return from.hashCode() * 31 + to.hashCode();
		}
	}
}
