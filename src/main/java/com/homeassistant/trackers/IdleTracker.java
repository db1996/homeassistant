package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class IdleTracker {
    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;

    private static final int IDLE_ANIMATION = -1;

    private int lastNonIdleTick = -1;
    private boolean sentEvent = false;

    @Inject
    public IdleTracker(EventBus eventBus, Client client, HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.config = config;
        reset();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!config.sendIdleEvents()) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        int animation = player.getAnimation();
        int pose = player.getPoseAnimation();
        int currentTick = client.getTickCount();

        // Consider player active if any of these are non-idle
        if (animation != IDLE_ANIMATION ||
                (pose != IDLE_ANIMATION && pose != player.getIdlePoseAnimation() )||
                player.getInteracting() != null) {
            lastNonIdleTick = currentTick;
            sentEvent = false; // allow idle event to fire again
            return;
        }

        // If enough time has passed, trigger idle
        if (isIdle() && !sentEvent) {
            log.debug("Player became idle, {}, {}", lastNonIdleTick, currentTick);
            sentEvent = true;

            eventBus.post(new HomeassistantEvents.SendEvent(new HashMap<>(), "trigger_idle_notify"));
        }
    }


    private boolean isIdle() {
        return client.getTickCount() >= (lastNonIdleTick + config.idleTickDelay()) && lastNonIdleTick != -1;
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned event) {
        if (event.getPlayer() == client.getLocalPlayer()) {
            reset();
        }
    }

    private void reset() {
        lastNonIdleTick = -1;
        sentEvent = false;
    }
}
