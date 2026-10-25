package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends events for the Gemstone Crab. Its health bar is a timer rather than
 * damage taken, so the HP threshold is a warning that it is about to burrow.
 */
@Slf4j
@Singleton
public class GemCrabTracker {
    // The game only sends this to players who did enough damage to mine the shell.
    private static final String CAN_MINE_MESSAGE = "You gained enough understanding of the crab to mine from its remains.";

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;

    private NPC crab = null;
    private boolean hpEventSent = false;
    private boolean testHpRequested;
    private boolean testCanMineRequested;

    @Inject
    public GemCrabTracker(EventBus eventBus, Client client, HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.config = config;
        this.testHpRequested = config.testGemCrabHpThresholdEvent();
        this.testCanMineRequested = config.testGemCrabCanMineEvent();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (event.getNpc().getId() != NpcID.GEMSTONE_CRAB) return;

        crab = event.getNpc();
        hpEventSent = false;
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc() == crab) {
            crab = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!config.sendGemCrabHpThresholdEvents() || crab == null || hpEventSent) return;

        int ratio = crab.getHealthRatio();
        int scale = crab.getHealthScale();
        // The health bar is only sent while you are fighting the crab.
        if (ratio < 0 || scale <= 0) return;

        int hpPercent = (int) Math.ceil(ratio * 100.0 / scale);
        if (hpPercent > config.gemCrabHpThreshold()) return;

        log.debug("Gem crab at {}% HP", hpPercent);
        hpEventSent = true;
        sendHp(hpPercent);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!config.sendGemCrabCanMineEvents()) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        if (!event.getMessage().contains(CAN_MINE_MESSAGE)) return;

        log.debug("Gem crab shell can be mined");
        sendCanMine();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!HomeassistantConfig.CONFIG_GROUP.equals(event.getGroup())) return;

        if (!testHpRequested && config.testGemCrabHpThresholdEvent()) {
            sendHp(config.gemCrabHpThreshold());
        }
        testHpRequested = config.testGemCrabHpThresholdEvent();

        if (!testCanMineRequested && config.testGemCrabCanMineEvent()) {
            sendCanMine();
        }
        testCanMineRequested = config.testGemCrabCanMineEvent();
    }

    private void sendHp(int hpPercent) {
        Map<String, Object> data = new HashMap<>();
        data.put("hp_percent", hpPercent);
        data.put("threshold", config.gemCrabHpThreshold());
        data.put("world", client.getWorld());
        eventBus.post(new HomeassistantEvents.SendEvent(data, "trigger_gem_crab_hp_notify"));
    }

    private void sendCanMine() {
        Map<String, Object> data = new HashMap<>();
        data.put("world", client.getWorld());
        eventBus.post(new HomeassistantEvents.SendEvent(data, "trigger_gem_crab_can_mine_notify"));
    }
}
