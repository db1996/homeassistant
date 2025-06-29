package com.homeassistant.trackers;

import java.util.*;
import java.util.stream.Collectors;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.StatusEffect;
import com.homeassistant.trackers.events.HomeassistantEvents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class PlayerDataTracker {
    private final EventBus eventBus;
    private final Client client;
    private final HomeassistantConfig config;


    private int currentHealth = 0;
    private int currentPrayer = 0;
    private int currentSpecialAttack = 0;
    private int currentRunEnergy = 0;
    private List<StatusEffect> currentStatusEffects = new ArrayList<>();
    private int previousHealth = 0;
    private int previousPrayer = 0;
    private int previousSpecialAttack = 0;
    private int previousRunEnergy = 0;
    private List<StatusEffect> previousStatusEffects = new ArrayList<>();
    private final Map<Skill, Integer> boostedSkills = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> previousBoostedSkills = new EnumMap<>(Skill.class);

    private boolean previousIsOnline = false;
    private int previousOnlineWorld = -1;
    private boolean isOnline = false;
    private int onlineWorld = -1;

    @Inject
    public PlayerDataTracker(Client client, EventBus eventBus, HomeassistantConfig config)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;

        for (Skill skill : Skill.values()) {
            boostedSkills.put(skill, 0);
        }

        resetPrevious();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();

        if(gameState == GameState.CONNECTION_LOST || gameState == GameState.LOGIN_SCREEN){
            isOnline = false;
            onlineWorld = -1;
            checkAllEntities();
        }

        if (gameState != GameState.LOGGED_IN) {
            return;
        }

        isOnline = true;
        onlineWorld = client.getWorld();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!config.playerRunEnergy() && !config.playerHealth() && !config.playerPrayer() && !config.playerSpecialAttack() && !config.playerOnlineStatus() && !config.skillBoosts()){
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        checkCurrentStats();
        checkAllEntities();
    }

    private void checkAllEntities() {
        List<Map<String, Object>> entities = new ArrayList<>();
        if(currentHealth != previousHealth && config.playerHealth()){
            String entityId = String.format("sensor.runelite_%s_health", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);
            attributes.put("current_health", currentHealth);
            entities.add(attributes);
        }
        if (currentPrayer != previousPrayer && config.playerPrayer())
        {
            String entityId = String.format("sensor.runelite_%s_prayer", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);
            attributes.put("current_prayer", currentPrayer);
            entities.add(attributes);
        }
        if (currentSpecialAttack != previousSpecialAttack && config.playerSpecialAttack())
        {
            String entityId = String.format("sensor.runelite_%s_special_attack", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);
            attributes.put("current_special_attack", currentSpecialAttack);
            entities.add(attributes);
        }
        if (currentRunEnergy != previousRunEnergy && config.playerRunEnergy())
        {
            String entityId = String.format("sensor.runelite_%s_run_energy", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);
            attributes.put("current_run_energy", currentRunEnergy);
            entities.add(attributes);
        }
        if (!currentStatusEffects.equals(previousStatusEffects) && config.playerStatusEffects())
        {
            String entityId = String.format("sensor.runelite_%s_status_effects", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);

            // Convert status effects to a serializable list of maps or strings
            List<Map<String, Object>> effectList = currentStatusEffects.stream()
                    .map(effect -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", effect.name);
                        map.put("number", effect.number);
                        map.put("time", effect.time);
                        return map;
                    })
                    .collect(Collectors.toList());

            attributes.put("current_status_effects", effectList);
            entities.add(attributes);
        }

        if(config.skillBoosts()){
            for (Skill skill : Skill.values()) {
                if (!Objects.equals(previousBoostedSkills.get(skill), boostedSkills.get(skill))) {
                    String entityId = String.format("sensor.runelite_%s_skill_%s", getUsername(), skill.getName().toLowerCase().replaceAll(" ", "_"));
                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("entity_id", entityId);
                    attributes.put("virtual_level", boostedSkills.get(skill));
                    entities.add(attributes);
                }
            }
        }


//		Player stats
        if((isOnline != previousIsOnline || onlineWorld != previousOnlineWorld) && config.playerOnlineStatus()) {
            String entityId = String.format("sensor.runelite_%s_player_status", getUsername());
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("entity_id", entityId);
            attributes.put("is_online", isOnline);
            attributes.put("world", onlineWorld);
            entities.add(attributes);
        }

        resetPrevious();

        if(entities.isEmpty()){
            return;
        }

        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    private void checkCurrentStats(){
        if(config.playerStatusEffects()){
            currentStatusEffects = new ArrayList<>();

            int poison = client.getVarpValue(102);
            boolean isPoisoned = false;
            boolean isVenomed = false;
            int damage = 0;

            if (poison > 0 && poison <= 100)
            {
                damage = (int) Math.ceil(poison / 5.0f);
                isPoisoned = true;

            }
            else if (poison >= 1_000_000)
            {
                damage = Math.min(20, (poison - 999_997) * 2);
                isVenomed = true;
            }

            if(isPoisoned || isVenomed){
                StatusEffect statusEffect = new StatusEffect();
                statusEffect.number = damage;
                statusEffect.name = isPoisoned ? "Poison" : "Venom";
                currentStatusEffects.add(statusEffect);
            }
        }

        if(config.playerRunEnergy())
            currentRunEnergy = client.getEnergy() / 100;

        if(config.playerSpecialAttack())
            currentSpecialAttack = client.getVarpValue(300) / 10;

        if(config.playerHealth())
            currentHealth = client.getBoostedSkillLevel(Skill.HITPOINTS);

        if(config.playerPrayer())
            currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);

        if(config.skillBoosts()){
            for (Skill skill : Skill.values()) {
                boostedSkills.put(skill, client.getBoostedSkillLevel(skill));
            }
        }
    }

    private void resetPrevious(){
        previousHealth = currentHealth;
        previousPrayer = currentPrayer;
        previousRunEnergy = currentRunEnergy;
        previousSpecialAttack = currentSpecialAttack;
        previousStatusEffects = new ArrayList<>();
        for (StatusEffect value : currentStatusEffects ){
            previousStatusEffects.add(value.copy());
        }
        previousIsOnline = isOnline;
        previousOnlineWorld = onlineWorld;

        for (Skill skill : Skill.values()) {
            previousBoostedSkills.put(skill, boostedSkills.get(skill));
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
