package com.homeassistant.trackers;

import java.time.Instant;
import java.util.*;

import com.homeassistant.HomeassistantConfig;

import com.homeassistant.classes.Utils;
import com.homeassistant.enums.PatchStatus;
import com.homeassistant.runelite.farming.*;
import com.homeassistant.trackers.events.HomeassistantEvents;
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
public class FarmingTracker {

    private com.homeassistant.runelite.farming.FarmingTracker farmingTracker;
    private FarmingContractManager farmingContractManager;
    private final EventBus eventBus;
    private final Client client;
    private final HomeassistantConfig config;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final Notifier notifier;
    private static final List<Tab> IGNORE_TABS = List.of(
            Tab.GRAPE,
            Tab.TIME_OFFSET,
            Tab.ANIMA,
            Tab.SPECIAL,
            Tab.BELLADONNA,
            Tab.CALQUAT,
            Tab.CELASTRUS,
            Tab.CRYSTAL,
            Tab.BIRD_HOUSE,
            Tab.OVERVIEW,
            Tab.HOPS,
            Tab.CLOCK
    );
    private int farmingTickOffset = 0;
    private int previousFarmingTickOffset = 0;

    private final Map<Tab, Long> farmingCompletionTimes = new EnumMap<>(Tab.class);
    private long farmingContractCompletionTime = -2L;
    private long previousFarmingContractCompletionTime = -2L;

    private final Map<Tab, Long> previousFarmingCompletionTimes = new EnumMap<>(Tab.class);
    private boolean hasChecked = false;

    @Inject
    public FarmingTracker(
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
        GameState gameState = gameStateChanged.getGameState();

        if (gameState != GameState.LOGGED_IN){
            return;
        }

        checkAll();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        String group = event.getGroup();
        if (!group.equals(TimeTrackingConfig.CONFIG_GROUP) && !group.equals(HomeassistantConfig.CONFIG_GROUP)) {
            return;
        }

        checkAll();
    }

    @Subscribe
    public void onGameTick(GameTick event){
        if(!hasChecked && Utils.GetUserName(client) != null){
            hasChecked = true;
            checkAll();
        }
    }
    
    private void checkEntities(){
        List<Map<String, Object>> entities = new ArrayList<>();

        try {
            // Farming Patches
            if(config.farmingPatches()) {
                for (Tab tab : Tab.values()) {
                    if (!Objects.equals(previousFarmingCompletionTimes.get(tab), farmingCompletionTimes.get(tab))) {
                        if(IGNORE_TABS.contains(tab)){
                            continue;
                        }
                        String entityId = generateFarmingPatchEntityId(tab);
                        if (entityId == null) continue;

                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put("entity_id", entityId);

                        long completionTime = farmingCompletionTimes.get(tab);
                        PatchStatus patchStatus = PatchStatus.READY;

                        if (completionTime > 0) {
                            patchStatus = PatchStatus.IN_PROGRESS;
                            attributes.put("completion_time", Instant.ofEpochSecond(completionTime).toString());
                        } else if (completionTime == -1) {
                            patchStatus = PatchStatus.NEVER_PLANTED;
                        }

                        attributes.put("status", patchStatus.getName());

                        entities.add(attributes);
                        previousFarmingCompletionTimes.put(tab, farmingCompletionTimes.get(tab));
                    }
                }
            }

            // Farming Contract
            if(config.farmingContract()) {
                if (previousFarmingContractCompletionTime != farmingContractCompletionTime) {
                    String entityId = generateFarmingContractEntityId();
                    if (entityId != null) {

                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put("entity_id", entityId);
                        attributes.put("status", PatchStatus.IN_PROGRESS.getName());
                        String contractName = farmingContractManager.getContractName();
                        Tab tab = farmingContractManager.getContractTab();
                        if (tab != null){
                            try {
                                attributes.put("patch_type", tab.name().toLowerCase());
                                attributes.put("crop_type", contractName);
                            }catch (NullPointerException e){
                                log.debug("Error getting contract name or tab: {}", e.getMessage());
                            }

                            log.info("Farming contract completion time: {}", farmingContractCompletionTime);
                            PatchStatus patchStatus = PatchStatus.READY;
                            if (farmingContractCompletionTime > 0) {
                                patchStatus = PatchStatus.IN_PROGRESS;
                                if(farmingContractCompletionTime == Long.MAX_VALUE){
                                    patchStatus = PatchStatus.OTHER;
                                } else {
                                    //				log.info("Farming contract completion time: {}", Instant.ofEpochSecond(completionTime).toString());
                                    attributes.put("completion_time", Instant.ofEpochSecond(farmingContractCompletionTime).toString());
                                }
                            }else if(farmingContractCompletionTime == -1){
                                patchStatus = PatchStatus.NEVER_PLANTED;
                            }
                            attributes.put("status", patchStatus.getName());

                            entities.add(attributes);
                            previousFarmingContractCompletionTime = farmingContractCompletionTime;
                        }
                    }
                }
            }
        } catch (Exception e){
            log.error(e.getMessage());
        }

        if(farmingTickOffset != previousFarmingTickOffset){
            Map<String, Object> farmingTickAttributes = new HashMap<>();
            farmingTickAttributes.put("entity_id", GenerateFarmingTickEntityId());
            farmingTickAttributes.put("farming_tick_offset", farmingTickOffset);

            entities.add(farmingTickAttributes);
        }

        resetPrevious();

        if(entities.isEmpty()){
            return;
        }

        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }


    private void checkAll() {
        if(Utils.GetUserName(client) == null){
            return;
        }

        resetPrevious();
        if(config.farmingPatches()) {
            farmingTracker.setIgnoreFarmingGuild(config.ignoreFarmingGuild());
            farmingTracker.loadCompletionTimes();
            for (Tab tab : Tab.values()) {
                if(IGNORE_TABS.contains(tab)){
                    continue;
                }
                farmingCompletionTimes.put(tab, farmingTracker.getCompletionTime(tab));
            }
        }

        if (config.farmingContract()) {
            farmingContractManager.loadContractFromConfig();
            farmingContractCompletionTime = farmingContractManager.getCompletionTime();
        }

        if(config.farmingTickOffset()) {
            int offset = configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, TimeTrackingConfig.FARM_TICK_OFFSET, int.class);
            offset = offset * -1;

            if (offset != farmingTickOffset) {
                farmingTickOffset = offset;
            }
        }

        checkEntities();
    }

    public void reset(){
        TimeTrackingConfig timeTrackingConfig = configManager.getConfig(TimeTrackingConfig.class);
        FarmingWorld farmingWorld = new FarmingWorld();

        CompostTracker compostTracker = new CompostTracker(
                this.client,
                farmingWorld,
                this.configManager
        );

        PaymentTracker paymentTracker = new PaymentTracker(
                this.client,
                this.configManager,
                farmingWorld
        );

        farmingTracker = new com.homeassistant.runelite.farming.FarmingTracker(
                this.client,
                this.itemManager,
                this.configManager,
                timeTrackingConfig,
                farmingWorld,
                this.notifier,
                compostTracker,
                paymentTracker
        );
        farmingTracker.setIgnoreFarmingGuild(config.ignoreFarmingGuild());

        farmingContractManager = new FarmingContractManager(
                this.client,
                this.itemManager,
                this.configManager,
                timeTrackingConfig,
                farmingWorld,
                farmingTracker
        );

        for (Tab tab : Tab.values()) {
            if(IGNORE_TABS.contains(tab)){
                continue;
            }
            farmingCompletionTimes.put(tab, -2L);
        }
        farmingContractCompletionTime = -2L;
        farmingTickOffset = 0;
        hasChecked = false;

        resetPrevious();
    }

    public void resetPrevious(){
        for (Tab tab : Tab.values()) {
            if(IGNORE_TABS.contains(tab)){
                continue;
            }
            previousFarmingCompletionTimes.put(tab, farmingCompletionTimes.get(tab));
        }
        previousFarmingContractCompletionTime = farmingContractCompletionTime;
        previousFarmingTickOffset = farmingTickOffset;
    }



    private String generateFarmingPatchEntityId(Tab tab) {
        try {
            if(tab == Tab.BIG_COMPOST){
                return String.format("sensor.runelite_%s_compost_bin", Utils.GetUserName(client));
            }
            return String.format("sensor.runelite_%s_%s_patch", Utils.GetUserName(client), tab.name().toLowerCase());
        }catch (NullPointerException e){
            log.error("Error generating entity id for {}: {}", tab.name(), e.getMessage());
            return null;
        }
    }

    private String generateFarmingContractEntityId() {
        try{
            return String.format("sensor.runelite_%s_farming_contract", Utils.GetUserName(client));
        }catch (NullPointerException e){
            log.error("Error generating entity id for farming contract: {}", e.getMessage());
            return null;
        }
    }

    private String GenerateFarmingTickEntityId() {
        try{
            return String.format("sensor.runelite_%s_farming_tick_offset", Utils.GetUserName(client));
        }catch (NullPointerException e){
            log.error("Error generating entity id for farming tick offset: {}", e.getMessage());
            return null;
        }
    }
}
