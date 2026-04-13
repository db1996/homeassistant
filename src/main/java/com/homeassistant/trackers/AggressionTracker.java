package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.enums.AggressionStatus;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class AggressionTracker
{
    private static final int AGGRESSION_TIMEOUT_TICKS = 1000;
    private static final int RESET_DISTANCE = 11;

    private final EventBus eventBus;
    private final Client client;
    private final HomeassistantConfig config;
    private final ConfigManager configManager;

    @Getter
    private int ticksLeft = 0;

    @Getter
    private boolean active = false;

    @Getter
    private WorldPoint tile1;
    @Getter
    private WorldPoint tile2;
    private WorldPoint previousUnknownCenter;

    private boolean initialized = false;
    private boolean wasActive = false;
    private boolean isLoaded = false;

    public int previousFiredEvent;

    @Inject
    public AggressionTracker(Client client, EventBus eventBus, HomeassistantConfig config, ConfigManager configManager)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;
        this.configManager = configManager;

        this.previousFiredEvent = 0;
    }

    private void saveToConfig()
    {
        log.debug("Saving config, {}, {}, {}, {}", tile1, tile2, ticksLeft, active);
        if (tile1 != null && tile2 != null)
        {
            configManager.setConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile1X", tile1.getX());
            configManager.setConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile1Y", tile1.getY());
            configManager.setConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile2X", tile2.getX());
            configManager.setConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile2Y", tile2.getY());
            configManager.setConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTicksLeft", ticksLeft);
        }
    }

    private void loadFromConfig()
    {
        if(isLoaded){
            return;
        }

        Long x1Long = configManager.getConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile1X", Long.class);
        Long y1Long = configManager.getConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile1Y", Long.class);
        Long x2Long = configManager.getConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile2X", Long.class);
        Long y2Long = configManager.getConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTile2Y", Long.class);
        Long ticksLong = configManager.getConfiguration(HomeassistantConfig.CONFIG_GROUP, "aggressionTicksLeft", Long.class);

        log.debug("Loaded config: {}, {}, {}, {}, {}", x1Long, y1Long, x2Long, y2Long, ticksLong);

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        WorldPoint currentLocation = player.getWorldLocation();

        if ((x1Long == null || y1Long == null) && (x2Long == null || y2Long == null))
        {
            log.debug("Aggression config missing; nothing to load");
            return;
        }

        // Reconstruct saved tiles
        if (x1Long != null && y1Long != null)
        {
            tile1 = new WorldPoint(x1Long.intValue(), y1Long.intValue(), currentLocation.getPlane());
        }
        if (x2Long != null && y2Long != null)
        {
            tile2 = new WorldPoint(x2Long.intValue(), y2Long.intValue(), currentLocation.getPlane());
        }

        int savedTicks = ticksLong != null ? ticksLong.intValue() : 0;

        // Measure distance to see if we are still in the same zone
        int distanceFromTile1 = getDistance(currentLocation, tile1);
        int distanceFromTile2 = getDistance(currentLocation, tile2);

        if (distanceFromTile1 < RESET_DISTANCE || distanceFromTile2 < RESET_DISTANCE)
        {
            // Still in same zone
            if (savedTicks <= 0)
            {
                log.debug("Previously expired and still in same zone. Staying inactive.");
                ticksLeft = 0;
                active = false;
                initialized = true;
                isLoaded = true;
                return;
            }
            else
            {
                log.debug("Aggression timer still valid. Resuming from saved state.");
                ticksLeft = savedTicks;
                initialized = true;
                isLoaded = true;
                return;
            }
        }
        else
        {
            log.debug("Logged in outside saved area. Staying inactive until movement resets aggression.");
            aggroEnded();
            previousUnknownCenter = currentLocation;
            isLoaded = true;
            return;
        }
    }


    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!config.aggressionTimer())
        {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        WorldPoint currentLocation = player.getWorldLocation();

        if(currentLocation == null){
            log.debug("Aggression config missing; nothing to load");
            return;
        }



        if (!isLoaded)
        {
            loadFromConfig();

            // If we still failed to load config, and nothing is initialized, start tracking movement
            if (!isLoaded && !initialized && previousUnknownCenter == null)
            {
                previousUnknownCenter = player.getWorldLocation();
                log.debug("Aggression config not loaded. Tracking movement from {}", previousUnknownCenter);
                isLoaded = true;
                return;
            }
        }

        if (previousUnknownCenter != null)
        {
            int distance = getDistance(currentLocation, previousUnknownCenter);

            if (distance >= RESET_DISTANCE * 2)
            {
                log.debug("Moved far from unknown center ({} tiles). Starting new aggression zone.", distance);
                startNewAggressionZone(currentLocation);
                previousUnknownCenter = null;
            }else{
                previousUnknownCenter = currentLocation;
            }
            return;
        }

        if(!initialized){
            return;
        }

        int dist1 = getDistance(currentLocation, tile1);
        int dist2 = getDistance(currentLocation, tile2);

        if (previousFiredEvent > 0)
        {
            previousFiredEvent--;
        }

        if (dist1 >= RESET_DISTANCE && dist2 >= RESET_DISTANCE)
        {
            resetAggroTimer(currentLocation);
        }
        else
        {
            if (ticksLeft > 0)
            {
                ticksLeft--;
                if (previousFiredEvent == 0 && ticksLeft > 0)
                {
                    aggroTick();
                }

                if (ticksLeft == 0)
                {
                    aggroEnded();
                }
            }
        }
    }

    private void startNewAggressionZone(WorldPoint center)
    {
        tile1 = center;
        tile2 = center;
        ticksLeft = AGGRESSION_TIMEOUT_TICKS;
        active = true;
        wasActive = true;
        initialized = true;

        saveToConfig();
        log.debug("Started new aggression zone at {}", center);
    }


    private void aggroTick()
    {
        previousFiredEvent = config.aggressionTimerDelay();

        List<Map<String, Object>> entities = new ArrayList<>();
        active = true;
        wasActive = true;

        int seconds = (int) (ticksLeft * 0.6f);

        String entityId = String.format("sensor.runelite_%s_aggression", Utils.GetUserName(client));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id", entityId);
        attributes.put("status", AggressionStatus.ACTIVE.getId());
        attributes.put("seconds", seconds);
        attributes.put("ticks", ticksLeft);
        entities.add(attributes);

        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    private void aggroEnded()
    {
        previousFiredEvent = config.aggressionTimerDelay();
        active = false;

        List<Map<String, Object>> entities = new ArrayList<>();

        String entityId = String.format("sensor.runelite_%s_aggression", Utils.GetUserName(client));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id", entityId);
        attributes.put("status", AggressionStatus.SAFE.getId());
        attributes.put("seconds", 0);
        attributes.put("ticks", 0);
        entities.add(attributes);

        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!config.aggressionTimer())
        {
            return;
        }

        GameState state = event.getGameState();
        if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
        {
            saveToConfig();
            isLoaded = false;
        }
    }

    private void initialize(WorldPoint loc)
    {
        tile1 = loc;
        tile2 = loc;
        ticksLeft = AGGRESSION_TIMEOUT_TICKS;
        active = false;
        wasActive = false;
        initialized = true;
    }

    private void resetAggroTimer(WorldPoint newLocation)
    {
        boolean wasActiveBefore = active;

        tile1 = tile2;
        tile2 = newLocation;

        ticksLeft = AGGRESSION_TIMEOUT_TICKS;
        active = false;
        wasActive = false;

        if (wasActiveBefore)
        {
            log.debug("Aggression timer reset from movement.");
        }
    }

    private void reset()
    {
        ticksLeft = 0;
        active = false;
        wasActive = false;
        tile1 = null;
        tile2 = null;
        initialized = false;
    }

    private int getDistance(WorldPoint a, WorldPoint b)
    {
        if (a == null || b == null || a.getPlane() != b.getPlane())
        {
            return Integer.MAX_VALUE;
        }
        return a.distanceTo(b);
    }
}
