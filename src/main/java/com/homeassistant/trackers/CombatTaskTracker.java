package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CombatTaskTracker {

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final ClientThread  clientThread;
    private final Client client;

    private boolean currentSendTestRequest;

    private static final Pattern COMBAT_TASK_PATTERN = Pattern.compile(
            "Congratulations, you've completed an? (?<tier>easy|medium|hard|elite|master|grandmaster) combat task:(?<task>.+?)",
            Pattern.CASE_INSENSITIVE
    );

    @Inject
    public CombatTaskTracker(EventBus eventBus, HomeassistantConfig config, Client client, ClientThread clientThread) {
        this.eventBus = eventBus;
        this.config = config;
        this.client = client;
        this.clientThread = clientThread;

        this.currentSendTestRequest = config.testCombatTaskEvent();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!config.sendCombatTaskEvents()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher matcher = COMBAT_TASK_PATTERN.matcher(msg);

        if (matcher.matches()) {
            String task = matcher.group("task");
            String tier = matcher.group("tier");

            log.debug("Detected combat task completion: {} (tier: {})", task, tier);

            Map<String, Object> thisEvent = new HashMap<>();
            thisEvent.put("task_name", task);
            thisEvent.put("tier", tier);
            eventBus.post(new HomeassistantEvents.SendEvent(thisEvent, "trigger_combat_task_notify"));
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(HomeassistantConfig.CONFIG_GROUP)) return;

        if (!currentSendTestRequest && config.testCombatTaskEvent()) {
            currentSendTestRequest = true;

            clientThread.invoke(() -> client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "Congratulations, you've completed an easy combat task: <col=06600c>A Slow Death</col> (1 point).",
                    null
            ));

        } else {
            currentSendTestRequest = config.testCombatTaskEvent();
        }
    }
}
