package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseTracker {
    protected final Client client;
    protected final EventBus eventBus;
    protected final HomeassistantConfig config;
    protected final VarbitTracker varbitTracker;

    @Inject
    public BaseTracker(Client client, EventBus eventBus, HomeassistantConfig config, VarbitTracker varbitTracker) {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;
        this.varbitTracker = varbitTracker;

        this.eventBus.register(this.varbitTracker);
    }

    // Optional: subclasses can override if they want to track varbits
    protected String getVarbitTrackerId() {
        return null;
    }

    protected List<Integer> getTrackedVarbits() {
        return List.of(); // empty list by default
    }

    public void setVarbitTracker(boolean active) {
        this.setVarbitTracker(active, null);
    }

    public void setVarbitTracker(boolean active, Integer overrideValue) {
        String id = getVarbitTrackerId();
        List<Integer> varbits = getTrackedVarbits();

        if (id == null || varbits == null || varbits.isEmpty()) {
            return; // this subclass doesn't use varbit tracking
        }

        if (active) {
            varbitTracker.setInternalWatch(id, varbits, overrideValue);
        } else {
            varbitTracker.setInternalWatch(id, new ArrayList<>(), overrideValue);
        }
    }
}
