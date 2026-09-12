package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.runelite.quest.QuestVarPlayer;
import com.homeassistant.runelite.quest.QuestVarbits;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reports the quest being worked on, from the varbit or varplayer that holds
 * its progress stage.
 */
@Slf4j
@Singleton
public class QuestTracker {
    private static final String PREFIX = "QUEST_";
    private static final Map<String, String> ALIASES = Map.of(
            "DESERTTREASURE", "DESERTTREASUREI",
            "DESERTTREASUREII", "DESERTTREASUREIIFALLENEMPIRE",
            "RECIPEFORDISASTERDWARF", "RECIPEFORDISASTERMOUNTAINDWARF",
            "RECIPEFORDISASTERMONKEYAMBASSADOR", "RECIPEFORDISASTERKINGAWOWOGEI",
            "MAGEARENA", "MAGEARENAI"
    );

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;

    private final Map<Integer, String> byVarbit = new HashMap<>();
    private final Map<Integer, String> byVarp = new HashMap<>();
    private final Map<String, Quest> quests = new HashMap<>();
    private final Map<String, Integer> lastValues = new HashMap<>();
    private final Map<String, String> lastStates = new HashMap<>();

    private Map<String, Object> lastSent = null;
    private String currentQuest = null;
    private int currentStage = 0;
    private boolean sendOnTick = false;
    private boolean testRequested;

    @Inject
    public QuestTracker(EventBus eventBus, Client client, HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.config = config;
        this.testRequested = config.testQuestCompleteEvent();

        Map<String, Quest> byKey = new HashMap<>();
        for (Quest quest : Quest.values()) {
            byKey.put(key(quest.name()), quest);
        }
        for (QuestVarbits varbit : QuestVarbits.values()) {
            String name = questName(varbit.name(), byKey);
            if (name != null) byVarbit.put(varbit.getId(), name);
        }
        for (QuestVarPlayer varp : QuestVarPlayer.values()) {
            String name = questName(varp.name(), byKey);
            if (name != null) byVarp.put(varp.getId(), name);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState state = event.getGameState();
        if (state != GameState.LOGGING_IN && state != GameState.HOPPING) return;

        lastValues.clear();
        lastStates.clear();
        currentQuest = null;
        currentStage = 0;
        lastSent = null;
        sendOnTick = true;
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!sendOnTick) return;

        sendOnTick = false;
        send();

        if (currentQuest == null) return;

        String state = stateOf(currentQuest);
        String was = lastStates.put(currentQuest, state);
        if (config.sendQuestCompleteEvents() && "FINISHED".equals(state) && !"FINISHED".equals(was)) {
            sendComplete(currentQuest, client.getVarpValue(VarPlayerID.QP));
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!HomeassistantConfig.CONFIG_GROUP.equals(event.getGroup())) return;

        if (!testRequested && config.testQuestCompleteEvent()) {
            sendComplete("Test Quest", 0);
        }
        testRequested = config.testQuestCompleteEvent();
    }

    private void sendComplete(String quest, int questPoints) {
        Map<String, Object> completed = new HashMap<>();
        completed.put("quest", quest);
        completed.put("quest_points", questPoints);
        eventBus.post(new HomeassistantEvents.SendEvent(completed, "trigger_quest_complete_notify"));
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (!config.sendQuestProgress()) return;

        String quest;
        String key;
        if (event.getVarbitId() != -1) {
            quest = byVarbit.get(event.getVarbitId());
            key = "b" + event.getVarbitId();
        } else {
            quest = byVarp.get(event.getVarpId());
            key = "p" + event.getVarpId();
        }
        if (quest == null) return;

        Integer previous = lastValues.put(key, event.getValue());
        if (previous == null || previous == event.getValue()) return;

        currentQuest = quest;
        currentStage = event.getValue();
        sendOnTick = true;
    }

    private String questName(String constant, Map<String, Quest> byKey) {
        if (!constant.startsWith(PREFIX) || constant.equals("QUEST_TAB")) return null;

        String name = constant.substring(PREFIX.length()).replaceAll("_STATE_\\d+$", "");
        String key = key(name);
        Quest quest = byKey.get(ALIASES.getOrDefault(key, key));
        if (quest == null) return readable(name);

        quests.put(quest.getName(), quest);
        return quest.getName();
    }

    private static String key(String constant) {
        StringBuilder out = new StringBuilder();
        for (String word : constant.split("_")) {
            if (!word.equals("THE") && !word.equals("AND")) out.append(word);
        }
        return out.toString();
    }

    private String readable(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.toLowerCase().split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            if (word.matches("[ivx]+")) {
                out.append(word.toUpperCase());
            } else {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private String stateOf(String quest) {
        Quest match = quests.get(quest);
        if (match == null) return "UNKNOWN";

        QuestState state = match.getState(client);
        return state == null ? "UNKNOWN" : state.name();
    }

    private void send() {
        if (!config.sendQuestProgress()) return;

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id",
                String.format("sensor.runelite_%s_last_quest", Utils.GetUserName(client)));
        attributes.put("quest", currentQuest == null ? "None" : currentQuest);
        attributes.put("state", currentQuest == null ? "NONE" : stateOf(currentQuest));
        attributes.put("stage", currentStage);
        attributes.put("quest_points", client.getVarpValue(VarPlayerID.QP));
        attributes.put("completed", client.getVarbitValue(VarbitID.QUESTS_COMPLETED_COUNT));
        attributes.put("total", client.getVarbitValue(VarbitID.QUESTS_TOTAL_COUNT));

        if (Objects.equals(lastSent, attributes)) return;
        lastSent = attributes;

        log.debug("Quest: {} (stage {}, {})", attributes.get("quest"),
                attributes.get("stage"), attributes.get("state"));

        List<Map<String, Object>> entities = new ArrayList<>();
        entities.add(attributes);
        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }
}
