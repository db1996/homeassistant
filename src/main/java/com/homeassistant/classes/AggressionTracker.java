package com.homeassistant.classes;

import com.homeassistant.HomeassistantConfig;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;

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

                // Check if we just became active
                if (!wasActive)
                {
                    active = true;
                    wasActive = true;
                    previousFiredEvent = 0;
                    eventBus.post(new AggressionEvent.AggroStarted());
                }
                if(previousFiredEvent == 0){
                    previousFiredEvent = config.aggressionTimerDelay();
                    eventBus.post(new AggressionEvent.AggroTick(ticksLeft));
                }

                if (ticksLeft == 0)
                {
                    // Timer expired - NPCs are no longer aggressive
                    active = false;
                    eventBus.post(new AggressionEvent.AggroEnded());
                }
            }
        }
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
            eventBus.post(new AggressionEvent.AggroReset());
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

    /**
     * Returns true if NPCs should be aggressive (timer is active and counting down)
     */
    public boolean isAggroActive()
    {
        return active && ticksLeft > 0;
    }

    /**
     * Returns the number of seconds left on the aggression timer
     */
    public int getSecondsLeft()
    {
        return ticksLeft > 0 ? (int) Math.ceil(ticksLeft * 0.6) : 0;
    }

    /**
     * Returns the current tracked tiles (for debugging purposes)
     */
    public String getAreaInfo()
    {
        if (!initialized)
        {
            return "Not initialized";
        }

        if (tile1 == null || tile2 == null)
        {
            return "Invalid tile data";
        }

        return String.format("Tile1: (%d, %d), Tile2: (%d, %d)",
                tile1.getX(), tile1.getY(), tile2.getX(), tile2.getY());
    }
}