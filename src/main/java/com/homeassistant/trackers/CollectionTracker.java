package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class CollectionTracker {
    private final HomeassistantConfig config;
    private final EventBus eventBus;

    private boolean currentSendTestRequest = false;

    @Inject
    public CollectionTracker(EventBus eventBus, HomeassistantConfig config)
    {
        this.eventBus = eventBus;
        this.config = config;

        this.currentSendTestRequest = config.testCollectionLogEvent();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if(!config.sendCollectionLogEvents()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = Text.removeTags(event.getMessage());
        String prefix = "New item added to your collection log:";
        if (msg.startsWith(prefix)) {
            String item = msg.substring(prefix.length()).trim();
            log.debug("Detected collection log unlock: {}", item);

            Map<String, Object> thisEvent = new HashMap<>();
            thisEvent.put("item_name", item);
            eventBus.post(new HomeassistantEvents.SendEvent(thisEvent, "trigger_collection_log_notify"));
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        String group = event.getGroup();
        if (!group.equals(HomeassistantConfig.CONFIG_GROUP)) {
            return;
        }

        if(!currentSendTestRequest && config.testCollectionLogEvent()) {
            currentSendTestRequest = true;

            Map<String, Object> thisEvent = new HashMap<>();
            thisEvent.put("item_name", "Test Item");
            eventBus.post(new HomeassistantEvents.SendEvent(thisEvent, "trigger_collection_log_notify"));
        }else{
            currentSendTestRequest = config.testCollectionLogEvent();
        }
    }
}
