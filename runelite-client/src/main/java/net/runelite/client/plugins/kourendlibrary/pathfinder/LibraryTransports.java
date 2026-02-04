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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Defines transport connections within the Kourend Library.
 * This includes staircases connecting the three floors.
 */
public class LibraryTransports
{
	private static final Map<WorldPoint, List<WorldPoint>> TRANSPORTS = new HashMap<>();

	static
	{
		// Center staircase (main library staircase connecting all floors)
		// Ground floor (0) to Middle floor (1)
		addBidirectionalTransport(1633, 3808, 0, 1633, 3808, 1);
		addBidirectionalTransport(1632, 3808, 0, 1632, 3808, 1);
		addBidirectionalTransport(1633, 3807, 0, 1633, 3807, 1);
		addBidirectionalTransport(1632, 3807, 0, 1632, 3807, 1);

		// Middle floor (1) to Top floor (2)
		addBidirectionalTransport(1633, 3808, 1, 1633, 3808, 2);
		addBidirectionalTransport(1632, 3808, 1, 1632, 3808, 2);
		addBidirectionalTransport(1633, 3807, 1, 1633, 3807, 2);
		addBidirectionalTransport(1632, 3807, 1, 1632, 3807, 2);

		// Northwest section stairs (if present)
		// These connect the northwest reading rooms
		addBidirectionalTransport(1616, 3825, 0, 1616, 3825, 1);
		addBidirectionalTransport(1617, 3825, 0, 1617, 3825, 1);
		addBidirectionalTransport(1616, 3825, 1, 1616, 3825, 2);
		addBidirectionalTransport(1617, 3825, 1, 1617, 3825, 2);

		// Southwest section stairs
		addBidirectionalTransport(1616, 3792, 0, 1616, 3792, 1);
		addBidirectionalTransport(1617, 3792, 0, 1617, 3792, 1);
		addBidirectionalTransport(1616, 3792, 1, 1616, 3792, 2);
		addBidirectionalTransport(1617, 3792, 1, 1617, 3792, 2);

		// Northeast section stairs (customer area)
		addBidirectionalTransport(1649, 3825, 0, 1649, 3825, 1);
		addBidirectionalTransport(1650, 3825, 0, 1650, 3825, 1);
		addBidirectionalTransport(1649, 3825, 1, 1649, 3825, 2);
		addBidirectionalTransport(1650, 3825, 1, 1650, 3825, 2);
	}

	private static void addBidirectionalTransport(int x1, int y1, int z1, int x2, int y2, int z2)
	{
		WorldPoint from = new WorldPoint(x1, y1, z1);
		WorldPoint to = new WorldPoint(x2, y2, z2);

		TRANSPORTS.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
		TRANSPORTS.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
	}

	/**
	 * Gets the map of all transport connections within the library.
	 * Key is the starting position, value is a list of possible destinations.
	 */
	public static Map<WorldPoint, List<WorldPoint>> getTransports()
	{
		return TRANSPORTS;
	}
}
