package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
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
 * Reports the current slayer task.
 *
 * The task is read from RuneLite's own Slayer plugin rather than from varbits.
 * That plugin already maintains the task name, the counts and the location, and
 * writes them to the "slayer" config group -- which is the same source the
 * !task chat command reads. Reusing it means no creature-id lookup table to
 * keep in sync with the game, and it picks up every correction the Slayer
 * plugin makes (task assignment, partial completion, task cancellation).
 *
 * The trade-off is a hard requirement: RuneLite's Slayer plugin has to be
 * enabled, otherwise nothing writes those keys and this stays empty.
 */
@Slf4j
@Singleton
public class SlayerTracker {
    private static final String SLAYER_GROUP = "slayer";
    private static final String TASK_NAME_KEY = "taskName";
    private static final String AMOUNT_KEY = "amount";
    private static final String INIT_AMOUNT_KEY = "initialAmount";
    private static final String TASK_LOC_KEY = "taskLocation";
    private static final String STREAK_KEY = "streak";
    private static final String POINTS_KEY = "points";

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;
    private final ConfigManager configManager;

    private Map<String, Object> lastSent = null;

    @Inject
    public SlayerTracker(EventBus eventBus, Client client, ConfigManager configManager,
                         HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.configManager = configManager;
        this.config = config;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!SLAYER_GROUP.equals(event.getGroup())) return;

        // The Slayer plugin writes several keys per change, so this fires a few
        // times in a row for one task update. sendTask() compares against what
        // was last sent, so the duplicates collapse.
        sendTask();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // On login the config is already populated but nothing has changed, so
        // without this the task would only appear after the first kill.
        if (event.getGameState() == GameState.LOGGED_IN) {
            lastSent = null;
            sendTask();
        }
    }

    private String readString(String key) {
        String value = configManager.getRSProfileConfiguration(SLAYER_GROUP, key);
        return value == null || value.isEmpty() ? null : value;
    }

    private int readInt(String key) {
        String value = readString(key);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sendTask() {
        if (!config.sendSlayerTask()) return;

        String task = readString(TASK_NAME_KEY);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id",
                String.format("sensor.runelite_%s_slayer_task", Utils.GetUserName(client)));
        // No task is a state worth reporting, not a reason to send nothing --
        // otherwise a finished task would leave the last one on screen forever.
        attributes.put("task", task == null ? "None" : task);
        attributes.put("remaining_amount", task == null ? 0 : readInt(AMOUNT_KEY));
        attributes.put("initial_amount", task == null ? 0 : readInt(INIT_AMOUNT_KEY));
        attributes.put("task_location", task == null ? "" : String.valueOf(readString(TASK_LOC_KEY)));
        attributes.put("streak", readInt(STREAK_KEY));
        attributes.put("points", readInt(POINTS_KEY));

        if (Objects.equals(lastSent, attributes)) return;
        lastSent = attributes;

        log.debug("Slayer task: {} ({} left)", attributes.get("task"),
                attributes.get("remaining_amount"));

        List<Map<String, Object>> entities = new ArrayList<>();
        entities.add(attributes);
        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }
}
