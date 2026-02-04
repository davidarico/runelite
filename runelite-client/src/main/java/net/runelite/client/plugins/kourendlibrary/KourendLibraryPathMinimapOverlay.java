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
package net.runelite.client.plugins.kourendlibrary;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.kourendlibrary.pathfinder.LibraryPathfinderService;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Overlay that renders the optimal book collection path on the minimap.
 */
class KourendLibraryPathMinimapOverlay extends Overlay
{
	private final Client client;
	private final KourendLibraryConfig config;
	private final KourendLibraryPlugin plugin;

	@Inject
	private KourendLibraryPathMinimapOverlay(Client client, KourendLibraryConfig config, KourendLibraryPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.LOW);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showOptimalPath() || !config.showPathOnMinimap())
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		WorldPoint playerLoc = player.getWorldLocation();
		if (playerLoc.getRegionID() != KourendLibraryPlugin.REGION)
		{
			return null;
		}

		LibraryPathfinderService pathfinderService = plugin.getPathfinderService();
		List<WorldPoint> path = pathfinderService.getOptimalPath();
		List<Bookcase> bookcaseOrder = pathfinderService.getOptimalBookcaseOrder();

		if (path == null || path.isEmpty())
		{
			return null;
		}

		Color pathColor = config.pathColor();
		Color nextBookcaseColor = config.nextBookcaseColor();

		// Find the next bookcase to visit
		Bookcase nextBookcase = null;
		if (bookcaseOrder != null && !bookcaseOrder.isEmpty())
		{
			for (Bookcase bc : bookcaseOrder)
			{
				if (playerLoc.distanceTo(bc.getLocation()) > 2)
				{
					nextBookcase = bc;
					break;
				}
			}
		}

		// Draw path on minimap
		Point prevPoint = null;
		for (WorldPoint wp : path)
		{
			// Only render tiles on the current floor
			if (wp.getPlane() != client.getPlane())
			{
				prevPoint = null;
				continue;
			}

			LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp == null)
			{
				prevPoint = null;
				continue;
			}

			Point minimapPoint = Perspective.localToMinimap(client, lp);
			if (minimapPoint == null)
			{
				prevPoint = null;
				continue;
			}

			// Draw a small dot at each path point
			g.setColor(pathColor);
			g.fillOval(minimapPoint.getX() - 1, minimapPoint.getY() - 1, 3, 3);

			// Draw line connecting path points
			if (prevPoint != null)
			{
				g.drawLine(prevPoint.getX(), prevPoint.getY(), minimapPoint.getX(), minimapPoint.getY());
			}
			prevPoint = minimapPoint;
		}

		// Highlight the next bookcase on the minimap
		if (nextBookcase != null && nextBookcase.getLocation().getPlane() == client.getPlane())
		{
			LocalPoint lp = LocalPoint.fromWorld(client, nextBookcase.getLocation());
			if (lp != null)
			{
				Point minimapPoint = Perspective.localToMinimap(client, lp);
				if (minimapPoint != null)
				{
					g.setColor(nextBookcaseColor);
					g.fillOval(minimapPoint.getX() - 3, minimapPoint.getY() - 3, 7, 7);
					g.setColor(nextBookcaseColor.darker());
					g.drawOval(minimapPoint.getX() - 3, minimapPoint.getY() - 3, 7, 7);
				}
			}
		}

		return null;
	}
}
