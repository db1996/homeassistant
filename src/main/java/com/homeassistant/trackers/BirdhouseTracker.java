package com.homeassistant.trackers;

import java.time.Instant;
import java.util.*;

import com.google.gson.Gson;
import com.homeassistant.HomeassistantConfig;

import com.homeassistant.enums.PatchStatus;
import com.homeassistant.runelite.farming.*;
import com.homeassistant.runelite.hunter.BirdHouseTracker;
import com.homeassistant.trackers.events.UpdateEntitiesEvent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class BirdhouseTracker {

    private final EventBus eventBus;
    private final Client client;
    private final HomeassistantConfig config;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final Notifier notifier;

    private BirdHouseTracker birdHouseTracker;

    private long birdhouseCompletionTime = -2L;
    private long previousBirdhouseCompletionTime = -2L;
    private boolean hasChecked = false;

    @Inject
    public BirdhouseTracker(
            EventBus eventBus,
            Client client,
            HomeassistantConfig config,
            ConfigManager configManager,
            ItemManager itemManager,
            Notifier notifier
    ) {
        this.eventBus = eventBus;
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.notifier = notifier;
        reset();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if(!config.birdHouses()){
            return;
        }
        GameState gameState = gameStateChanged.getGameState();

        if (gameState != GameState.LOGGED_IN){
            return;
        }

        checkAll();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if(!config.birdHouses()){
            return;
        }
        String group = event.getGroup();
        if (!group.equals(TimeTrackingConfig.CONFIG_GROUP) && !group.equals(HomeassistantConfig.CONFIG_GROUP)) {
            return;
        }

        checkAll();
    }

    @Subscribe
    public void onGameTick(GameTick event){
        if(!config.birdHouses()){
            return;
        }
        if(!hasChecked && getUsername() != null){
            hasChecked = true;
            checkAll();
        }
    }

    private void checkAll() {
        if(getUsername() == null || !config.birdHouses()){
            hasChecked = false;
            return;
        }

        birdHouseTracker.loadFromConfig();
        birdhouseCompletionTime = birdHouseTracker.getCompletionTime();

        List<Map<String, Object>> entities = new ArrayList<>();
        if (previousBirdhouseCompletionTime != birdhouseCompletionTime) {
            String entityId = generateBirdhouseEntityId();
            if (entityId != null) {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("entity_id", entityId);
                attributes.put("status", PatchStatus.IN_PROGRESS.getName());
                attributes.put("completion_time", Instant.ofEpochSecond(birdhouseCompletionTime).toString());

                entities.add(attributes); // ✅ add to array
                previousBirdhouseCompletionTime = birdhouseCompletionTime;
            }
        }
        resetPrevious();

        if(entities.isEmpty()){
            return;
        }

        eventBus.post(new UpdateEntitiesEvent.UpdateEntities(entities));
    }

    private void reset() {
        birdhouseCompletionTime = -1L;
        resetPrevious();

        TimeTrackingConfig timeTrackingConfig = configManager.getConfig(TimeTrackingConfig.class);

        birdHouseTracker = new BirdHouseTracker(
                client,
                itemManager,
                configManager,
                timeTrackingConfig,
                notifier
        );
    }

    private void resetPrevious() {
        previousBirdhouseCompletionTime = birdhouseCompletionTime;
    }

    private String generateBirdhouseEntityId() {
        try{
            return String.format("sensor.runelite_%s_birdhouses", getUsername());
        }catch (NullPointerException e){
            log.error("Error generating entity id for birdhouses: {}", e.getMessage());
            return null;
        }

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