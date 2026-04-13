package com.homeassistant.overlays;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.trackers.AggressionTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class AggressionOverlay extends Overlay
{
    private static final int AGGRESSION_RADIUS = 10;

    private final Client client;
    private final AggressionTracker tracker;
    private final HomeassistantConfig config;

    @Inject
    public AggressionOverlay(Client client, AggressionTracker tracker, HomeassistantConfig config)
    {
        this.client = client;
        this.tracker = tracker;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if(!config.aggressionShowArea() || !config.aggressionTimer()){
            return null;
        }

        renderAggressionArea(graphics, tracker.getTile1());
        renderAggressionArea(graphics, tracker.getTile2());
        return null;
    }

    private void renderAggressionArea(Graphics2D graphics, WorldPoint center)
    {
        if (center == null)
        {
            return;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if(worldView == null){
            return;
        }

        for (int dx = -10; dx <= 10; dx++)
        {
            for (int dy = -10; dy <= 10; dy++)
            {
                // Only draw the border tiles
                if (dx != -10 && dx != 10 && dy != -10 && dy != 10)
                {
                    continue;
                }

                WorldPoint wp = center.dx(dx).dy(dy);
                if (wp.getPlane() != worldView.getPlane())
                {
                    continue;
                }

                LocalPoint lp = LocalPoint.fromWorld(worldView, wp);
                if (lp == null)
                {
                    continue;
                }

                Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                if (poly != null)
                {
                    Color color = new Color(255,255,255,75);
                    OverlayUtil.renderPolygon(graphics, poly, color);
                }
            }
        }
    }
}
