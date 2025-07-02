package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.trackers.events.HomeassistantEvents;
import com.homeassistant.trackers.events.InternalEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class VarbitTracker {

    private final Client client;
    private final EventBus eventBus;
    private final HomeassistantConfig config;

    private final Map<Integer, Integer> previousVarbitValues = new HashMap<>();
    private final List<Integer> watchedVarbitIds = new ArrayList<>();

    private final Map<String, List<Integer>> internalVarbitIds = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> previousInternalVarbitIds = new HashMap<>();

    @Inject
    public VarbitTracker(Client client, EventBus eventBus, HomeassistantConfig config) {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;

        parseWatchedVarbits();
    }

    public void setInternalWatch(String id, List<Integer> varbitIds) {
        setInternalWatch(id, varbitIds, null); // default to null
    }

    public void setInternalWatch(String id, List<Integer> varbitIds, Integer overrideValue){
        Map<Integer, Integer> values = new HashMap<>();
        for (int varbitId : varbitIds) {
            if(!previousInternalVarbitIds.containsKey(id)){
                values.put(varbitId, overrideValue);
            }else{
                values.put(varbitId, previousInternalVarbitIds.get(id).get(varbitId));
            }
        }

        internalVarbitIds.put(id, varbitIds);
        previousInternalVarbitIds.put(id, values);

        log.info("Watching internal varbits: {}, {}", id, varbitIds);
    }

    private void parseWatchedVarbits() {
        watchedVarbitIds.clear();
        previousVarbitValues.clear();

        String input = config.varbitIdsEvent();
        log.debug("input {}",input);
        if (input == null || input.isBlank()) return;

        for (String part : input.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());

                watchedVarbitIds.add(id);
                previousVarbitValues.put(id, null); // initialize value
            } catch (NumberFormatException e) {
                log.warn("Invalid varbit ID in config: {}", part.trim());
            }
        }

        log.info("Watching varbits: {}", watchedVarbitIds);
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        if (!watchedVarbitIds.isEmpty()) {
            for (int varbitId : watchedVarbitIds) {
                int current = client.getVarbitValue(varbitId);
                Integer previous = previousVarbitValues.get(varbitId);

                if (previous != null && current != previous) {
                    log.debug("Varbit {} changed: {} -> {}", varbitId, previous, current);

                    Map<String, Object> data = new HashMap<>();
                    data.put("varbit_id", varbitId);
                    data.put("new_value", current);
                    data.put("old_value", previous);

                    eventBus.post(new HomeassistantEvents.SendEvent(data, "trigger_varbit_change_notify"));
                }

                previousVarbitValues.put(varbitId, current);
            }
        }

        if(!internalVarbitIds.isEmpty()){
            for(String internalId : internalVarbitIds.keySet()){
                List<Integer> values = internalVarbitIds.get(internalId);
                if (!values.isEmpty()){
                    for (int varbitId : values) {

                        int current = client.getVarbitValue(varbitId);
                        Integer previous = previousInternalVarbitIds.get(internalId).get(varbitId);

                        if (previous != null && previous != current) {
                            log.debug("Internal Varbit {} changed: {} -> {}", internalId, previous, current);
                            eventBus.post(new InternalEvents.VarbitUpdate(internalId, varbitId, previous, current));
                        }

                        previousInternalVarbitIds.get(internalId).put(varbitId, current);
                    }
                }

            }
        }
    }

    @Subscribe
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event) {
        if (event.getGroup().equals(HomeassistantConfig.CONFIG_GROUP)) {
            parseWatchedVarbits();
        }
    }
}
