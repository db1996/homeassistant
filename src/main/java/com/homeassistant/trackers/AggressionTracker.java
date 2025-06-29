package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
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
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class AggressionTracker
{
    private static final int AGGRESSION_TIMEOUT_TICKS = 1000; // ~10 minutes (600 seconds)
    private static final int RESET_DISTANCE = 11; // Distance to trigger reset (>10 tiles)

    private final EventBus eventBus;
    private final Client client;
    private final HomeassistantConfig config;

    @Getter
    private int ticksLeft = 0;

    @Getter
    private boolean active = false;

    // The game remembers exactly 2 tiles
    private WorldPoint tile1;
    private WorldPoint tile2;
    private boolean initialized = false;
    private boolean wasActive = false;

    public int previousFiredEvent;

    @Inject
    public AggressionTracker(Client client, EventBus eventBus, HomeassistantConfig config)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;

        this.previousFiredEvent = 0;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if(!config.aggressionTimer()){
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        WorldPoint currentLocation = player.getWorldLocation();

        if (!initialized)
        {
            // Initialize both tiles to current location
            initialize(currentLocation);
            return;
        }

        // Check distance from both remembered tiles
        int distanceFromTile1 = getDistance(currentLocation, tile1);
        int distanceFromTile2 = getDistance(currentLocation, tile2);
        if (this.previousFiredEvent  > 0){
            this.previousFiredEvent--;
        }

        // If more than 10 tiles away from BOTH tiles, reset occurs
        if (distanceFromTile1 >= RESET_DISTANCE && distanceFromTile2 >= RESET_DISTANCE)
        {
            // Reset: move the oldest tile (tile1) to current position
            resetAggroTimer(currentLocation);
        }
        else
        {
            // Still within range of at least one tile - continue countdown
            if (ticksLeft > 0)
            {
                ticksLeft--;
                if(previousFiredEvent == 0 && ticksLeft > 0){
                    aggroTick();
                }

                if (ticksLeft == 0)
                {
                    aggroEnded();
                }
            }
        }
    }

    private void aggroTick(){
        if(previousFiredEvent == 0) {
            previousFiredEvent = config.aggressionTimerDelay();

            List<Map<String, Object>> entities = new ArrayList<>();
            active = true;
            wasActive = true;

            int seconds = (int)(ticksLeft * 0.6f);

            String entityId = String.format("sensor.runelite_%s_aggression", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);
            attributes.put("status", AggressionStatus.ACTIVE.getId());
            attributes.put("seconds", seconds);
            attributes.put("ticks", ticksLeft);
            entities.add(attributes);

            eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
        }
    }

    private void aggroEnded(){
        previousFiredEvent = config.aggressionTimerDelay();

        List<Map<String, Object>> entities = new ArrayList<>();
        active = false;

        String entityId = String.format("sensor.runelite_%s_aggression", getUsername());
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
        if(!config.aggressionTimer()){
            return;
        }

        GameState gameState = event.getGameState();
        if (gameState == GameState.LOADING || gameState == GameState.HOPPING || gameState == GameState.LOGIN_SCREEN)
        {
            reset();
        }
    }

    private void initialize(WorldPoint location)
    {
        tile1 = location;
        tile2 = location;
        ticksLeft = AGGRESSION_TIMEOUT_TICKS;
        active = false;
        wasActive = false;
        initialized = true;
    }

    private void resetAggroTimer(WorldPoint newLocation)
    {
        boolean wasActiveBeforeReset = active;

        // Move oldest tile (tile1) to current location, tile2 becomes the new tile1
        tile1 = tile2;
        tile2 = newLocation;

        // Reset timer
        ticksLeft = AGGRESSION_TIMEOUT_TICKS;
        active = false;
        wasActive = false;

        // Only post reset event if we were previously active
        if (wasActiveBeforeReset)
        {

        }
    }

    /**
     * Calculate the maximum distance between two points (Chebyshev distance)
     * This matches how OSRS calculates tile distance
     */
    private int getDistance(WorldPoint point1, WorldPoint point2)
    {
        if (point1 == null || point2 == null)
        {
            return Integer.MAX_VALUE;
        }

        int deltaX = Math.abs(point1.getX() - point2.getX());
        int deltaY = Math.abs(point1.getY() - point2.getY());

        // Use Chebyshev distance (max of x and y differences)
        return Math.max(deltaX, deltaY);
    }

    private void reset()
    {
        initialized = false;
        ticksLeft = 0;
        active = false;
        wasActive = false;
        tile1 = null;
        tile2 = null;
    }

    private String getUsername() {
        try {
            return Objects.requireNonNull(client.getLocalPlayer().getName())
                    .toLowerCase()
                    .replace(" ", "_");
        } catch (NullPointerException e) {
            log.error("Error fetching username: {}", e.getMessage());
            return null;
        }
    }
}