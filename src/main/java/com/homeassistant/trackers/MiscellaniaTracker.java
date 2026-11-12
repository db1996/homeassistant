package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the Kingdom of Miscellania coffer and approval rating.
 *
 * The varbits are only reliable while you are in the kingdom, so the last values
 * seen there are stored per account and the rest is projected from them. The
 * game takes its cut at midnight UTC: 10% of the coffer, capped at 50k (75k
 * after Royal Trouble), and 2.5% approval (1% after Royal Trouble).
 */
@Slf4j
@Singleton
public class MiscellaniaTracker {
    // Stored apart from the plugin's own group so writing it doesn't wake the
    // trackers that listen for setting changes.
    private static final String STORAGE_GROUP = "homeassistant_miscellania";
    private static final String COFFER_KEY = "coffer";
    private static final String APPROVAL_KEY = "approval";
    private static final String LAST_UPDATED_KEY = "lastUpdated";
    private static final String LAST_VISIT_KEY = "lastVisit";

    private static final Set<Integer> KINGDOM_REGIONS = Set.of(10044, 10300);
    private static final int MAX_WITHDRAWAL_BASE = 50_000;
    private static final int MAX_WITHDRAWAL_ROYAL_TROUBLE = 75_000;
    private static final double APPROVAL_DECAY_BASE = 2.5;
    private static final double APPROVAL_DECAY_ROYAL_TROUBLE = 1.0;
    private static final int MIN_APPROVAL_PERCENT = 25;
    private static final int MAX_APPROVAL = 127;
    // 10% of anything below this rounds down to nothing, so the coffer stops draining there.
    private static final int EMPTY_COFFER = 10;
    // The kingdom stops spending coins when you haven't visited for this long.
    private static final Duration SPENDING_PAUSE = Duration.ofDays(30);
    // A full coffer runs dry in about 200 days.
    private static final int MAX_PROJECTION_DAYS = 1000;

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;
    private final ConfigManager configManager;

    private boolean wasInKingdom = false;
    // Quest state can only be read on the client thread, so sends wait for a game tick.
    private boolean pendingSend = false;
    private List<Map<String, Object>> lastSent = null;

    @Inject
    public MiscellaniaTracker(EventBus eventBus, Client client, ConfigManager configManager,
                              HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.configManager = configManager;
        this.config = config;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            wasInKingdom = false;
            lastSent = null;
            pendingSend = true;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!HomeassistantConfig.CONFIG_GROUP.equals(event.getGroup())) return;
        pendingSend = true;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.miscellania()) return;

        boolean inKingdom = isInKingdom();
        if (inKingdom) {
            recordKingdom(!wasInKingdom);
        }
        wasInKingdom = inKingdom;

        if (pendingSend && Utils.GetUserName(client) != null) {
            pendingSend = false;
            send();
        }
    }

    private boolean isInKingdom() {
        Player player = client.getLocalPlayer();
        return player != null && KINGDOM_REGIONS.contains(player.getWorldLocation().getRegionID());
    }

    private void recordKingdom(boolean arrived) {
        // Throne of Miscellania not started, so there is no kingdom yet.
        if (client.getVarpValue(VarPlayerID.MISC_QUEST) == 0) return;

        int coffer = client.getVarbitValue(VarbitID.MISC_COFFERS);
        int approval = client.getVarbitValue(VarbitID.MISC_APPROVAL);
        // Approval never drops below 25%, so 0 means the varbits haven't loaded yet.
        if (approval == 0) return;

        Instant now = Instant.now();
        if (arrived) {
            setInstant(LAST_VISIT_KEY, now);
        }

        if (!arrived
                && Objects.equals(readInt(COFFER_KEY), coffer)
                && Objects.equals(readInt(APPROVAL_KEY), approval)) {
            return;
        }

        log.debug("Miscellania coffer {} approval {}", coffer, approval);
        configManager.setRSProfileConfiguration(STORAGE_GROUP, COFFER_KEY, coffer);
        configManager.setRSProfileConfiguration(STORAGE_GROUP, APPROVAL_KEY, approval);
        setInstant(LAST_UPDATED_KEY, now);
        pendingSend = true;
    }

    private void send() {
        if (!config.miscellania()) return;

        String username = Utils.GetUserName(client);
        Integer coffer = readInt(COFFER_KEY);
        Integer approval = readInt(APPROVAL_KEY);
        Instant lastUpdated = readInstant(LAST_UPDATED_KEY);
        if (username == null || coffer == null || approval == null || lastUpdated == null) return;

        Instant lastVisit = readInstant(LAST_VISIT_KEY);
        if (lastVisit == null) lastVisit = lastUpdated;

        boolean royalTrouble = Quest.ROYAL_TROUBLE.getState(client) == QuestState.FINISHED;
        int maxWithdrawal = royalTrouble ? MAX_WITHDRAWAL_ROYAL_TROUBLE : MAX_WITHDRAWAL_BASE;
        double approvalDecay = royalTrouble ? APPROVAL_DECAY_ROYAL_TROUBLE : APPROVAL_DECAY_BASE;
        // Below ten days' maximum withdrawal the 10% cut is smaller than the cap, and so is the yield.
        int cofferThreshold = config.miscellaniaCofferThreshold() > 0
                ? config.miscellaniaCofferThreshold()
                : maxWithdrawal * 10;
        int approvalThreshold = config.miscellaniaApprovalThreshold();
        double approvalPercent = approval * 100.0 / MAX_APPROVAL;

        Map<String, Object> cofferAttributes = new HashMap<>();
        cofferAttributes.put("entity_id", String.format("sensor.runelite_%s_miscellania_coffer", username));
        cofferAttributes.put("coffer", coffer);
        cofferAttributes.put("royal_trouble", royalTrouble);
        cofferAttributes.put("last_updated", lastUpdated.toString());
        cofferAttributes.put("last_visit", lastVisit.toString());
        cofferAttributes.put("spending_paused_time", lastVisit.plus(SPENDING_PAUSE).toString());
        cofferAttributes.put("threshold", cofferThreshold);
        putInstant(cofferAttributes, "threshold_time",
                cofferBelowTime(coffer, maxWithdrawal, cofferThreshold, lastUpdated));
        putInstant(cofferAttributes, "empty_time",
                cofferBelowTime(coffer, maxWithdrawal, EMPTY_COFFER, lastUpdated));

        Map<String, Object> approvalAttributes = new HashMap<>();
        approvalAttributes.put("entity_id", String.format("sensor.runelite_%s_miscellania_approval", username));
        approvalAttributes.put("approval", (int) approvalPercent);
        approvalAttributes.put("royal_trouble", royalTrouble);
        approvalAttributes.put("last_updated", lastUpdated.toString());
        approvalAttributes.put("threshold", approvalThreshold);
        putInstant(approvalAttributes, "threshold_time",
                approvalBelowTime(approvalPercent, approvalDecay, approvalThreshold, lastUpdated));

        List<Map<String, Object>> entities = new ArrayList<>();
        entities.add(cofferAttributes);
        entities.add(approvalAttributes);

        if (Objects.equals(lastSent, entities)) return;
        lastSent = entities;

        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    /**
     * First midnight UTC at which the coffer is below the target, or null if it never gets there.
     * Returns the starting time when it is already below.
     */
    static Instant cofferBelowTime(int coffer, int maxWithdrawal, int target, Instant from) {
        if (coffer < target) return from;

        Instant day = nextMidnight(from);
        for (int i = 0; i < MAX_PROJECTION_DAYS; i++) {
            int withdrawal = Math.min(coffer / 10, maxWithdrawal);
            if (withdrawal == 0) return null;

            coffer -= withdrawal;
            if (coffer < target) return day;
            day = day.plus(Duration.ofDays(1));
        }
        return null;
    }

    /**
     * First midnight UTC at which approval is below the threshold, or null if it never gets there.
     * Returns the starting time when it is already below.
     */
    static Instant approvalBelowTime(double approvalPercent, double decayPerDay, int threshold, Instant from) {
        if (approvalPercent < threshold) return from;
        if (threshold <= MIN_APPROVAL_PERCENT) return null;

        long days = (long) Math.floor((approvalPercent - threshold) / decayPerDay) + 1;
        return nextMidnight(from).plus(Duration.ofDays(days - 1));
    }

    private static Instant nextMidnight(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // Always send the key so Home Assistant clears a time that no longer applies.
    private static void putInstant(Map<String, Object> attributes, String key, Instant instant) {
        attributes.put(key, instant == null ? "" : instant.toString());
    }

    private Integer readInt(String key) {
        String value = configManager.getRSProfileConfiguration(STORAGE_GROUP, key);
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant readInstant(String key) {
        String value = configManager.getRSProfileConfiguration(STORAGE_GROUP, key);
        if (value == null || value.isEmpty()) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setInstant(String key, Instant instant) {
        configManager.setRSProfileConfiguration(STORAGE_GROUP, key, instant.getEpochSecond());
    }
}
