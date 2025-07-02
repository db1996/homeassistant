package com.homeassistant.trackers;

import java.util.*;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.enums.DailyTask;

import com.homeassistant.trackers.events.HomeassistantEvents;
import com.homeassistant.trackers.events.InternalEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class DailyTracker extends BaseTracker {
    protected final String TRACKER_ID = "dailyTracker";
    protected final List<Integer> TRACKED_VARBITS = List.of(
        VarbitID.NZONE_HERBBOXES_PURCHASED,
        VarbitID.ZAFF_LAST_CLAIMED,
        VarbitID.ARDOUGNE_FREE_ESSENCE,
        VarbitID.LUNDAIL_LAST_CLAIMED,
        VarbitID.YANILLE_SAND_CLAIMED,
        VarbitID.SEERS_FREE_FLAX,
        VarbitID.WESTERN_RANTZ_ARROWS,
        VarbitID.KOUREND_FREE_DYNAMITE
    );
    private static final int HERB_BOX_MAX = 15;
    private static final int HERB_BOX_COST = 9500;
    private static final int SAND_QUEST_COMPLETE = 160;

    private final Map<DailyTask, Integer> dailyStatuses = new EnumMap<>(DailyTask.class);
    private final Map<DailyTask, Integer> previousDailyStatuses = new EnumMap<>(DailyTask.class);

    @Inject
    public DailyTracker(Client client, EventBus eventBus, HomeassistantConfig config, VarbitTracker varbitTracker)
    {
        super(client, eventBus, config, varbitTracker);
        reset();
    }

    @Override
    protected String getVarbitTrackerId() {
        return TRACKER_ID;
    }

    @Override
    protected List<Integer> getTrackedVarbits() {
        return TRACKED_VARBITS;
    }

    @Subscribe
    public void onVarbitUpdate(InternalEvents.VarbitUpdate event) {
        if (Objects.equals(event.getTrackerId(), TRACKER_ID)){
            checkAll();
        }
    }

    private void reset(){
        dailyStatuses.clear();

        for (DailyTask tab : DailyTask.values()) {
            dailyStatuses.put(tab, -1);
        }

        resetPrevious();
        this.setVarbitTracker(config.dailies(), -1);
    }

    private void resetPrevious(){

        previousDailyStatuses.clear();
        for (DailyTask tab : DailyTask.values()) {
            previousDailyStatuses.put(tab, dailyStatuses.get(tab));
        }
    }

    private void checkAll() {
        if(!config.dailies()){
            return;
        }
        checkHerbBoxes();
        checkStaves();
        checkEssence();
        checkRunes();
        checkSand();
        checkFlax();
        checkArrows();
        checkDynamite();

        List<Map<String, Object>> entities = new ArrayList<>();
        if(config.dailies()) {
            try {
                dailyStatuses.forEach((name, status) -> {
                    if (!previousDailyStatuses.get(name).equals(status)) {
                        String entityId = generateDailyEntityId(name.getId());
                        if (entityId == null) {
                            return;
                        }

                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put("entity_id", entityId);
                        attributes.put("state", status);
                        entities.add(attributes);
                    }
                });
                resetPrevious();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        resetPrevious();

        if(entities.isEmpty()){
            return;
        }

        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(HomeassistantConfig.CONFIG_GROUP)) return;

        this.setVarbitTracker(config.dailies(), -1);
    }

    private void checkHerbBoxes()
    {
        if(client.getVarbitValue(VarbitID.IRONMAN) != 0 || client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS) < HERB_BOX_COST){
            dailyStatuses.put(DailyTask.HERB_BOXES, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.NZONE_HERBBOXES_PURCHASED) < HERB_BOX_MAX)
        {
            dailyStatuses.put(DailyTask.HERB_BOXES, 0);
        } else {
            dailyStatuses.put(DailyTask.HERB_BOXES, 1);
        }
    }

    private void checkStaves()
    {
        if (client.getVarbitValue(VarbitID.VARROCK_DIARY_EASY_COMPLETE) != 1) {
            dailyStatuses.put(DailyTask.STAVES, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.ZAFF_LAST_CLAIMED) == 0) {
            dailyStatuses.put(DailyTask.STAVES, 0);
        } else {
            dailyStatuses.put(DailyTask.STAVES, 1);
        }
    }

    private void checkEssence()
    {
        if (client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) != 1) {
            dailyStatuses.put(DailyTask.ESSENCE, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.ARDOUGNE_FREE_ESSENCE) == 0) {
            dailyStatuses.put(DailyTask.ESSENCE, 0);

        } else {
            dailyStatuses.put(DailyTask.ESSENCE, 1);
        }
    }

    private void checkRunes()
    {
        if(client.getVarbitValue(VarbitID.WILDERNESS_DIARY_EASY_COMPLETE) == 1){
            dailyStatuses.put(DailyTask.RUNES, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.LUNDAIL_LAST_CLAIMED) == 0) {
            dailyStatuses.put(DailyTask.RUNES, 0);
        } else {
            dailyStatuses.put(DailyTask.RUNES, 1);
        }
    }

    private void checkSand()
    {
        if (client.getVarbitValue(VarbitID.IRONMAN) == 2 /* UIM */
                || client.getVarbitValue(VarbitID.HANDSAND_QUEST) < SAND_QUEST_COMPLETE) {
            dailyStatuses.put(DailyTask.SAND, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.YANILLE_SAND_CLAIMED) == 0) {
            dailyStatuses.put(DailyTask.SAND, 0);
        } else {
            dailyStatuses.put(DailyTask.SAND, 1);
        }
    }

    private void checkFlax()
    {
        if (client.getVarbitValue(VarbitID.KANDARIN_DIARY_EASY_COMPLETE) != 1) {
            dailyStatuses.put(DailyTask.FLAX, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.SEERS_FREE_FLAX) == 0) {
            dailyStatuses.put(DailyTask.FLAX, 0);
        } else {
            dailyStatuses.put(DailyTask.FLAX, 1);
        }
    }

    private void checkArrows()
    {
        if (client.getVarbitValue(VarbitID.WESTERN_DIARY_EASY_COMPLETE) != 1) {
            dailyStatuses.put(DailyTask.ARROWS, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.WESTERN_RANTZ_ARROWS) == 0) {
            dailyStatuses.put(DailyTask.ARROWS, 0);
        } else {
            dailyStatuses.put(DailyTask.ARROWS, 1);
        }
    }

    private void checkDynamite()
    {
        if (client.getVarbitValue(VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE) == 1){
            dailyStatuses.put(DailyTask.DYNAMITE, -1);
            return;
        }

        if (client.getVarbitValue(VarbitID.KOUREND_FREE_DYNAMITE) == 0) {
            dailyStatuses.put(DailyTask.DYNAMITE, 0);
        } else {
            dailyStatuses.put(DailyTask.DYNAMITE, 1);
        }
    }

    private String generateDailyEntityId(String str){
        try {
            return String.format("sensor.runelite_%s_daily_%s", Utils.GetUserName(client), str.toLowerCase());
        }catch (NullPointerException e){
            log.error("Error generating entity id for daily {}: {}", str, e.getMessage());
            return null;
        }
    }
}