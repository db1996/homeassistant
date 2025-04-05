package com.homeassistant;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("homeassistant")
public interface HomeassistantConfig extends Config
{
	String CONFIG_GROUP = "homeassistant";

	@ConfigItem(
			keyName = "homeassistant_url",
			name = "Homeassistant Base URL",
			description = "example: http://homeassistant.local:8123",
			position = 1
	)
	default String homeassistantUrl()
	{
		return "";
	}

	@ConfigItem(
			keyName = "homeassistant_token",
			name = "Homeassistant Access token",
			description = "Your home assistant access token",
			position = 2
	)
	default String homeassistantToken()
	{
		return "";
	}

	@ConfigItem(
			keyName = "ignorefarmingguild",
			name = "Ignore Farming Guild",
			description = "Ignore patches in the farming guild when determining the next update.",
			position = 3
	)
	default boolean ignoreFarmingGuild() {
		return true;
	}


	@ConfigSection(
			name = "Miscellaneous",
			description = "Settings for miscellaneous infoboxes",
			position = 100
	)
	String miscellaneousSection = "Miscellaneous";

	@ConfigSection(
			name = "Farming patches",
			description = "Settings for farming patch infoboxes",
			position = 200
	)
	String farmingPatchesSection = "Farming patches";

	// -- Miscellaneous infoboxes ---

	@ConfigItem(
			keyName = "birdhouses",
			name = "Bird houses",
			description = "Create an entity for when your bird houses are ready.",
			section = miscellaneousSection,
			position = 101
	)
	default boolean birdHouses() {
		return true;
	}

	@ConfigItem(
			keyName = "farmingcontract",
			name = "Farming contract",
			description = "Create an entity for when your farming contract is ready.",
			section = miscellaneousSection,
			position = 102
	)
	default boolean farmingContract() {
		return true;
	}

	@ConfigItem(
			keyName = "hespori",
			name = "Hespori",
			description = "Create an entity for when your Hespori patch is ready.",
			section = miscellaneousSection,
			position = 103
	)
	default boolean hespori() {
		return true;
	}

	@ConfigItem(
			keyName = "giantcompostbin",
			name = "Giant compost bin",
			description = "Create an entity for when your giant compost bin is ready.",
			section = miscellaneousSection,
			position = 104
	)
	default boolean giantCompostBin() {
		return true;
	}

	// -- Farming patch infoboxes ---

	@ConfigItem(
			keyName = "herbpatches",
			name = "Herb patches",
			description = "Create an entity for when your herb patches are ready.",
			section = farmingPatchesSection,
			position = 201
	)
	default boolean herbPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "treepatches",
			name = "Tree patches",
			description = "Create an entity for when your tree patches are ready.",
			section = farmingPatchesSection,
			position = 202
	)
	default boolean treePatches() {
		return true;
	}

	@ConfigItem(
			keyName = "fruittreepatches",
			name = "Fruit tree patches",
			description = "Create an entity for when your fruit tree patches are ready.",
			section = farmingPatchesSection,
			position = 203
	)
	default boolean fruitTreePatches() {
		return true;
	}

	@ConfigItem(
			keyName = "celastrusPatch",
			name = "Celastrus patch",
			description = "Create an entity for when your celastrus patch is ready.",
			section = farmingPatchesSection,
			position = 204
	)
	default boolean celastrusPatch() {
		return true;
	}

	@ConfigItem(
			keyName = "hardwoodpatches",
			name = "Hardwood patches",
			description = "Create an entity for when your hardwood patches are ready.",
			section = farmingPatchesSection,
			position = 204
	)
	default boolean hardwoodPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "calquatpatch",
			name = "Calquat patch",
			description = "Create an entity for when your calquat patch is ready.",
			section = farmingPatchesSection,
			position = 205
	)
	default boolean calquatPatch() {
		return true;
	}

	@ConfigItem(
			keyName = "redwoodpatch",
			name = "Redwood patch",
			description = "Create an entity for when your redwood patch is ready.",
			section = farmingPatchesSection,
			position = 206
	)
	default boolean redwoodPatch() {
		return true;
	}

	@ConfigItem(
			keyName = "seaweedpatches",
			name = "Seaweed patches",
			description = "Create an entity for when your seaweed patches are ready.",
			section = farmingPatchesSection,
			position = 207
	)
	default boolean seaweedPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "hopspatches",
			name = "Hops patches",
			description = "Create an entity for when your hops patches are ready.",
			section = farmingPatchesSection,
			position = 208
	)
	default boolean hopsPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "bushpatches",
			name = "Bush patches",
			description = "Create an entity for when your bush patches are ready.",
			section = farmingPatchesSection,
			position = 209
	)
	default boolean bushPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "cactuspatches",
			name = "Cactus patches",
			description = "Create an entity for when your cactus patches are ready.",
			section = farmingPatchesSection,
			position = 210
	)
	default boolean cactusPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "mushroompatch",
			name = "Mushroom patch",
			description = "Create an entity for when your mushroom patch is ready.",
			section = farmingPatchesSection,
			position = 211
	)
	default boolean mushroomPatch() {
		return true;
	}

	@ConfigItem(
			keyName = "belladonnapatch",
			name = "Belladonna patch",
			description = "Create an entity for when your belladonna patch is ready.",
			section = farmingPatchesSection,
			position = 212
	)
	default boolean belladonnaPatch() {
		return true;
	}

	@ConfigItem(
			keyName = "crystalpatch",
			name = "Crystal patch",
			description = "Create an entity for when your crystal patch is ready.",
			section = farmingPatchesSection,
			position = 213
	)
	default boolean crystalPatch() {
		return true;
	}

	@ConfigItem(
			keyName = "allotmentpatches",
			name = "Allotment patches",
			description = "Create an entity for when your allotment patches are ready.",
			section = farmingPatchesSection,
			position = 214
	)
	default boolean allotmentPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "flowerpatches",
			name = "Flower patches",
			description = "Create an entity for when your flower patches are ready.",
			section = farmingPatchesSection,
			position = 215
	)
	default boolean flowerPatches() {
		return true;
	}

	@ConfigItem(
			keyName = "animapatch",
			name = "Anima patch",
			description = "Create an entity for when your anima patch is ready to be replaced.",
			section = farmingPatchesSection,
			position = 216
	)
	default boolean animaPatch() {
		return true;
	}
}
