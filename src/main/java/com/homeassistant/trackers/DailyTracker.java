package com.homeassistant.trackers;

import java.util.*;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.enums.DailyTask;

import com.homeassistant.trackers.events.UpdateEntitiesEvent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class DailyTracker {
    private final EventBus eventBus;
    private final Client client;
    private final HomeassistantConfig config;

    private static final int HERB_BOX_MAX = 15;
    private static final int HERB_BOX_COST = 9500;
    private static final int SAND_QUEST_COMPLETE = 160;


    private final Map<DailyTask, Integer> dailyStatuses = new EnumMap<>(DailyTask.class);
    private final Map<DailyTask, Integer> previousDailyStatuses = new EnumMap<>(DailyTask.class);

    private boolean waitingForBattlestavesPurchase = false;
    private long battlestaffWatchStart = 0;
    private static final int BATTLESTAFF_WATCH_DURATION_MS = 120_000;
    private static final int BATTLESTAFF_NOTED_ID = 1392;
    private boolean isLoggingIn = false;

    @Inject
    public DailyTracker(Client client, EventBus eventBus, HomeassistantConfig config)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;

        reset();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if(!config.dailies()){
            return;
        }

        if (event.getType() == ChatMessageType.MESBOX &&
                event.getMessage().equals("Bert delivers the sand to your bank.")) {
            log.debug("Daily tracker: Detected bert delivery");
            dailyStatuses.put(DailyTask.SAND, 1);
        }

        String message = Text.removeTags(event.getMessage());
        if (message.contains("discounted battlestaves"))
        {
            waitingForBattlestavesPurchase = true;
            battlestaffWatchStart = System.currentTimeMillis();
            log.info("daily tracker: Detected battlestaff purchase prompt, starting {}s watch window.", (BATTLESTAFF_WATCH_DURATION_MS / 1000));
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if(!config.dailies()){
            return;
        }
        // Make sure this doesn't constantly run, only run when the chatbox is detected
        if (!waitingForBattlestavesPurchase)
        {
            return;
        }
        log.debug("daily tracker: itemcontainer changed {}", event);

        // Check timeout
        if (System.currentTimeMillis() - battlestaffWatchStart > BATTLESTAFF_WATCH_DURATION_MS)
        {
            waitingForBattlestavesPurchase = false;
            log.info("daily tracker: Battlestaff watch window expired.");
            return;
        }

        if (event.getContainerId() == InventoryID.INV)
        {
            ItemContainer container = event.getItemContainer();
            if (container == null) return;

            Item[] items = container.getItems();
            boolean purchased = false;

            for (Item item : items)
            {
                if (item.getId() == BATTLESTAFF_NOTED_ID)
                {
                    if (item.getQuantity() > 15){
                        purchased = true;
                    }
                }
            }

            log.info("daily tracker: Battlestaff watch window purchased., {}", purchased);
            if (purchased)
            {
                dailyStatuses.put(DailyTask.STAVES, 1);
                waitingForBattlestavesPurchase = false;
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if(!config.dailies()){
            return;
        }

        GameState gameState = gameStateChanged.getGameState();
        if (gameState == GameState.LOGGING_IN) {
            isLoggingIn = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event){
        if (isLoggingIn && config.dailies()){
            isLoggingIn = false;
            log.debug("daily tracker: checking all dailies");
            checkAll();
        }
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

        eventBus.post(new UpdateEntitiesEvent.UpdateEntities(entities));
    }

    private void reset(){
        dailyStatuses.clear();

        for (DailyTask tab : DailyTask.values()) {
            dailyStatuses.put(tab, -1);
        }

        resetPrevious();
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
            return String.format("sensor.runelite_%s_daily_%s", getUsername(), str.toLowerCase());
        }catch (NullPointerException e){
            log.error("Error generating entity id for daily {}: {}", str, e.getMessage());
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