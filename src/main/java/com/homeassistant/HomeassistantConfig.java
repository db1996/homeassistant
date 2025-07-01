package com.homeassistant;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("homeassistant")
public interface HomeassistantConfig extends Config
{
	String CONFIG_GROUP = "homeassistant";


	@ConfigSection(
			name = "Homeassistant",
			description = "Homeassistant connection settings",
			position = 100
	)
	String homeassistantSection = "Homeassistant";

	@ConfigSection(
			name = "Entities",
			description = "Select which entites you want to update in homeassistant",
			position = 200
	)
	String entitiesSection = "Entities";


	@ConfigSection(
			name = "Events",
			description = "Select which events you want to send to homeassistant",
			position = 300
	)
	String eventsSection = "Events";

	@ConfigSection(
			name = "Miscellaneous",
			description = "Some miscellaneous settings",
			position = 400
	)
	String miscellaneousSection = "Miscellaneous";

	@ConfigSection(
			name = "Debug",
			description = "Debug settings",
			position = 500
	)
	String DebugSection = "Debug";

	/*
		Homeassistant section
	 */

	@ConfigItem(
			keyName = "homeassistant_url",
			name = "Homeassistant Base URL",
			description = "example: http://homeassistant.local:8123",
			section = homeassistantSection,
			position = 101
	)
	default String homeassistantUrl()
	{
		return "";
	}

	@ConfigItem(
			keyName = "homeassistant_token",
			name = "Homeassistant Access token",
			description = "Your home assistant access token",
			section = homeassistantSection,
			position = 102
	)
	default String homeassistantToken()
	{
		return "";
	}

	@ConfigItem(
			keyName = "validate_token",
			name = "Validate Home Assistant Token",
			description = "Turn on to validate your homeassistant setup, will provide details in game messages. ",
			section = homeassistantSection,
			position = 103
	)
	default boolean validateToken()
	{
		return false;
	}

	/*
		Entities section
	 */

	@ConfigItem(
			keyName = "farmingpatches",
			name = "Farming patches",
			description = "Update farming patches entities",
			section = entitiesSection,
			position = 201
	)
	default boolean farmingPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "farmingcontract",
			name = "Farming contract",
			description = "Update farming contract entity",
			section = entitiesSection,
			position = 202
	)
	default boolean farmingContract() {
		return true;
	}

	@ConfigItem(
			keyName = "birdhouses",
			name = "Bird houses",
			description = "Update birdhouse timer entities",
			section = entitiesSection,
			position = 203
	)
	default boolean birdHouses() {
		return true;
	}

	@ConfigItem(
			keyName = "dailies",
			name = "Dailies",
			description = "Update dailies entities (Battlestaves, sand etc)",
			section = entitiesSection,
			position = 203
	)
	default boolean dailies() {
		return true;
	}

	@ConfigItem(
			keyName = "player_online_status",
			name = "Player online status",
			description = "Update player online status, updates the status and world",
			section = entitiesSection,
			position = 204
	)
	default boolean playerOnlineStatus() {
		return false;
	}

	@ConfigItem(
			keyName = "player_health",
			name = "Player health",
			description = "Update player health, this can result in a lot of calls",
			section = entitiesSection,
			position = 205
	)
	default boolean playerHealth() {
		return false;
	}

	@ConfigItem(
			keyName = "player_prayer",
			name = "Player prayer",
			description = "Update player prayer, this can result in a lot of calls",
			section = entitiesSection,
			position = 206
	)
	default boolean playerPrayer() {
		return false;
	}

	@ConfigItem(
			keyName = "player_special_attack",
			name = "Player special attack %",
			description = "Update player special attack",
			section = entitiesSection,
			position = 207
	)
	default boolean playerSpecialAttack() {
		return false;
	}

	@ConfigItem(
			keyName = "player_run_energy",
			name = "Player run energy %",
			description = "Update player run energy, this can result in a lot of calls",
			section = entitiesSection,
			position = 208
	)
	default boolean playerRunEnergy() {
		return false;
	}

	@ConfigItem(
			keyName = "player_status_effects",
			name = "Player status effects",
			description = "Will update for poison and venom (more soon)",
			section = entitiesSection,
			position = 209
	)
	default boolean playerStatusEffects() {
		return false;
	}

	@ConfigItem(
			keyName = "aggression_timer",
			name = "Aggression timer",
			description = "Updates aggression timer updates to homeassistant, this includes status, if applicable time and ticks",
			section = entitiesSection,
			position = 210
	)
	default boolean aggressionTimer() {
		return false;
	}

	@ConfigItem(
			keyName = "skill_boosts",
			name = "Skill boosts",
			description = "Updates all skill boosts, this includes potions and debuffs",
			section = entitiesSection,
			position = 210
	)
	default boolean skillBoosts() {
		return false;
	}

	/*
		Events section
	 */

	@ConfigItem(
			keyName = "send_collection_log_events",
			name = "Collection log events",
			description = "Sends events when receiving a collection log",
			section = eventsSection,
			position = 301
	)
	default boolean sendCollectionLogEvents() {
		return true;
	}

	@ConfigItem(
			keyName = "send_combat_task_events",
			name = "Combat task events",
			description = "Sends events when receiving completing a combat task",
			section = eventsSection,
			position = 302
	)
	default boolean sendCombatTaskEvents() {
		return true;
	}

	@ConfigItem(
			keyName = "send_diary_events",
			name = "Achievement diary events",
			description = "Sends events when completing an achievement diary",
			section = eventsSection,
			position = 303
	)
	default boolean sendDiaryEvents() {
		return true;
	}

	@ConfigItem(
			keyName = "send_idle_events",
			name = "Idle events",
			description = "Sends events when you go idle",
			section = eventsSection,
			position = 304
	)
	default boolean sendIdleEvents() {
		return true;
	}
	@ConfigItem(
			keyName = "idle_tick_delay",
			name = "Idle delay (ticks)",
			description = "Updates once you've been idle for x ticks",
			section = eventsSection,
			position = 305
	)
	default int idleTickDelay() {
		return 50;
	}

	/*
		Miscellaneous section
	 */

	@ConfigItem(
			keyName = "ignorefarmingguild",
			name = "Ignore Farming Guild",
			description = "Ignore patches in the farming guild when determining the next update.",
			section = miscellaneousSection,
			position = 401
	)
	default boolean ignoreFarmingGuild() {
		return true;
	}

	@ConfigItem(
			keyName = "farming_tick_offset",
			name = "Farming tick offset",
			description = "Tick offsets are account specific, this will automatically update it in homeassistant.",
			section = miscellaneousSection,
			position = 402
	)
	default boolean farmingTickOffset() {
		return true;
	}

	@ConfigItem(
			keyName = "aggression_timer_delay",
			name = "Aggression timer delay (ticks)",
			description = "Only updates the aggression timer every x ticks.",
			section = miscellaneousSection,
			position = 403
	)
	default int aggressionTimerDelay() {
		return 50;
	}

	@ConfigItem(
			keyName = "global_update_throttle",
			name = "Global tick throttle",
			description = "Only updates homeassistant every x ticks. 0 for every tick",
			section = miscellaneousSection,
			position = 404
	)
	default int globalUpdateThrottle() {
		return 0;
	}

	/*
		Debug section
	 */

	@ConfigItem(
			keyName = "test_collection_log_event",
			name = "Test clog event",
			description = "Sends a test collection log request to your homeassistant",
			section = DebugSection,
			position = 501
	)
	default boolean testCollectionLogEvent() {
		return false;
	}

	@ConfigItem(
			keyName = "test_combat_task_event",
			name = "Test combat task event",
			description = "Sends a test combat task request to your homeassistant",
			section = DebugSection,
			position = 502
	)
	default boolean testCombatTaskEvent() {
		return false;
	}

	@ConfigItem(
			keyName = "test_diary_event",
			name = "Test achievement diary event",
			description = "Sends a test diary completion request to your homeassistant",
			section = DebugSection,
			position = 503
	)
	default boolean testDiaryEvent() {
		return false;
	}

}
