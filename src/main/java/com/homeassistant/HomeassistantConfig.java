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
			name = "Miscellaneous",
			description = "Some miscellaneous settings",
			position = 300
	)
	String miscellaneousSection = "Miscellaneous";

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
			keyName = "player_health",
			name = "Player health",
			description = "Update player health, this can result in a lot of calls",
			section = entitiesSection,
			position = 204
	)
	default boolean playerHealth() {
		return false;
	}

	@ConfigItem(
			keyName = "player_prayer",
			name = "Player prayer",
			description = "Update player prayer, this can result in a lot of calls",
			section = entitiesSection,
			position = 205
	)
	default boolean playerPrayer() {
		return false;
	}

	@ConfigItem(
			keyName = "player_special_attack",
			name = "Player special attack %",
			description = "Update player special attack",
			section = entitiesSection,
			position = 206
	)
	default boolean playerSpecialAttack() {
		return false;
	}

	@ConfigItem(
			keyName = "player_run_energy",
			name = "Player run energy %",
			description = "Update player run energy, this can result in a lot of calls",
			section = entitiesSection,
			position = 206
	)
	default boolean playerRunEnergy() {
		return false;
	}

	@ConfigItem(
			keyName = "player_status_effects",
			name = "Player status effects",
			description = "Will update for poison and venom (more soon)",
			section = entitiesSection,
			position = 206
	)
	default boolean playerStatusEffects() {
		return false;
	}

	@ConfigItem(
			keyName = "ignorefarmingguild",
			name = "Ignore Farming Guild",
			description = "Ignore patches in the farming guild when determining the next update.",
			section = miscellaneousSection,
			position = 301
	)
	default boolean ignoreFarmingGuild() {
		return true;
	}

	@ConfigItem(
			keyName = "farming_tick_offset",
			name = "Farming tick offset",
			description = "Tick offsets are account specific, this will automatically update it in homeassistant.",
			section = miscellaneousSection,
			position = 302
	)
	default boolean farmingTickOffset() {
		return true;
	}
}
