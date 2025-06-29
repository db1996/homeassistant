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
public class AchievementDiaryTracker {

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;
    private final ClientThread clientThread;

    private boolean currentSendTestRequest;

    private static final Pattern DIARY_PATTERN = Pattern.compile(
            "Well done! You have completed an? (?<tier>easy|medium|hard|elite) task in the (?<region>.+?) area\\. Your Achievement Diary has been updated\\.",
            Pattern.CASE_INSENSITIVE
    );


    @Inject
    public AchievementDiaryTracker(EventBus eventBus, HomeassistantConfig config, Client client, ClientThread clientThread) {
        this.eventBus = eventBus;
        this.config = config;
        this.currentSendTestRequest = config.testDiaryEvent();
        this.client = client;
        this.clientThread = clientThread;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!config.sendDiaryEvents()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        Matcher matcher = DIARY_PATTERN.matcher(msg);

        if (matcher.matches()) {
            String region = matcher.group("region");
            String tier = matcher.group("tier");

            log.debug("Detected diary completion: {} (tier: {})", region, tier);

            Map<String, Object> thisEvent = new HashMap<>();
            thisEvent.put("task_name", region);
            thisEvent.put("tier", tier);
            eventBus.post(new HomeassistantEvents.SendEvent(thisEvent, "trigger_achievement_diary_notify"));
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(HomeassistantConfig.CONFIG_GROUP)) return;

        if (!currentSendTestRequest && config.testDiaryEvent()) {
            currentSendTestRequest = true;

            clientThread.invoke(() -> client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Well done! You have completed an easy task in the Western Provinces area. Your Achievement Diary has been updated.",
                null
            ));
        } else {
            currentSendTestRequest = config.testDiaryEvent();
        }
    }
}
