package com.homeassistant;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;

import com.homeassistant.enums.PatchStatus;
import com.homeassistant.runelite.farming.*;
import com.homeassistant.runelite.hunter.BirdHouseTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@PluginDescriptor(
	name = "Homeassistant"
)
public class HomeassistantPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private HomeassistantConfig config;

	@Inject
	private ConfigManager configManager;

	private BirdHouseTracker birdHouseTracker;
	private FarmingTracker farmingTracker;
	private FarmingContractManager farmingContractManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	@Inject
	private Notifier notifier;

	private final Map<Tab, Long> previousFarmingCompletionTimes = new EnumMap<>(Tab.class);
	private long previousBirdhouseCompletionTime = -2L;
	private long previousFarmingContractCompletionTime = -2L;
	private final HttpClient httpClient = HttpClient.newHttpClient(); // Instance variable initialization

	@Override
	protected void startUp() throws Exception
	{
		log.info("Homeassistant started!");
		for (Tab tab : Tab.values()) {
			previousFarmingCompletionTimes.put(tab, -2L);
		}
		previousBirdhouseCompletionTime = -2L;
		previousFarmingContractCompletionTime = -2L;
		initializeTrackers();
	}

	private boolean isRelevantFarmingTab(Tab tab) {
		return tab != Tab.OVERVIEW && tab != Tab.CLOCK && tab != Tab.TIME_OFFSET && tab != Tab.SPECIAL && tab != Tab.GRAPE; // Exclude overview, clock, offset, special (might need more specific handling), and grape
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Homeassistant stopped!");
	}


	@Provides
	HomeassistantConfig provideConfig(ConfigManager configManager)
	{
        return configManager.getConfig(HomeassistantConfig.class);
	}


	private void initializeTrackers() {
		TimeTrackingConfig timeTrackingConfig = configManager.getConfig(TimeTrackingConfig.class);
		FarmingWorld farmingWorld = new FarmingWorld();

		CompostTracker compostTracker = new CompostTracker(
				client,
				farmingWorld,
				configManager
		);

		PaymentTracker paymentTracker = new PaymentTracker(
				client,
				configManager,
				farmingWorld
		);

		birdHouseTracker = new BirdHouseTracker(
				client,
				itemManager,
				configManager,
				timeTrackingConfig,
				notifier
		);

		farmingTracker = new FarmingTracker(
				client,
				itemManager,
				configManager,
				timeTrackingConfig,
				farmingWorld,
				notifier,
				compostTracker,
				paymentTracker
		);
		farmingTracker.setIgnoreFarmingGuild(config.ignoreFarmingGuild());

		farmingContractManager = new FarmingContractManager(
				client,
				itemManager,
				configManager,
				timeTrackingConfig,
				farmingWorld,
				farmingTracker
		);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		birdHouseTracker.loadFromConfig();
		farmingTracker.loadCompletionTimes();
		farmingContractManager.loadContractFromConfig();
		updateAllEntities();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		String group = event.getGroup();
		if (!group.equals(TimeTrackingConfig.CONFIG_GROUP) && !group.equals(HomeassistantConfig.CONFIG_GROUP)) {
			return;
		}

		birdHouseTracker.loadFromConfig();
		farmingTracker.setIgnoreFarmingGuild(config.ignoreFarmingGuild());
		farmingTracker.loadCompletionTimes();
		farmingContractManager.loadContractFromConfig();
	}
	
	private void updateAllEntities() {
		for (Tab tab : Tab.values()) {
			if (getConfigFromTab(tab) && isRelevantFarmingTab(tab)) {
				updateFarmingPatchEntity(tab, farmingTracker.getCompletionTime(tab));
			}
		}

		if(config.birdHouses()) {
			updateBirdhouseEntity(birdHouseTracker.getCompletionTime());
		}

		if(config.farmingContract()){
			updateFarmingContractEntity(farmingContractManager.getCompletionTime(), farmingContractManager.getContractName());
		}
	}

	private boolean getConfigFromTab(Tab tab) {
		switch (tab) {
			case BUSH:
				return config.bushPatches();
			case HERB:
				return config.herbPatches();
			case ALLOTMENT:
				return config.allotmentPatches();
			case FLOWER:
				return config.flowerPatches();
			case TREE:
				return config.treePatches();
			case FRUIT_TREE:
				return config.fruitTreePatches();
			case HOPS:
				return config.hopsPatches();
			case SPECIAL:
			case MUSHROOM:
				return config.mushroomPatch();
			case BELLADONNA:
				return config.belladonnaPatch();
			case BIG_COMPOST:
				return config.giantCompostBin();
			case SEAWEED:
				return config.seaweedPatches();
			case CALQUAT:
				return config.calquatPatch();
			case CELASTRUS:
				return config.celastrusPatch();
			case HARDWOOD:
				return config.hardwoodPatches();
			case REDWOOD:
				return config.redwoodPatch();
			case CACTUS:
				return config.cactusPatches();
			case HESPORI:
				return config.hespori();
			case CRYSTAL:
				return config.crystalPatch();
			case ANIMA:
				return config.animaPatch();
			default:
				return false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick t) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		birdHouseTracker.updateCompletionTime();
		farmingTracker.updateCompletionTime();
		farmingContractManager.handleContractState();

		// Check farming patch completion times
		for (Tab tab : Tab.values()) {
			if (getConfigFromTab(tab)) {
				long currentCompletionTime = farmingTracker.getCompletionTime(tab);

				if (currentCompletionTime != previousFarmingCompletionTimes.get(tab)) {
					previousFarmingCompletionTimes.put(tab, currentCompletionTime);
					updateFarmingPatchEntity(tab, currentCompletionTime);
				}

			}
		}

		// Check birdhouse completion time
		if(config.birdHouses()) {
			long currentBirdhouseCompletionTime = birdHouseTracker.getCompletionTime();
			if (currentBirdhouseCompletionTime != previousBirdhouseCompletionTime) {
				previousBirdhouseCompletionTime = currentBirdhouseCompletionTime;
				updateBirdhouseEntity(currentBirdhouseCompletionTime);
			}
		}

		if(config.farmingContract()){
			long farmingContractCompletionTime = farmingContractManager.getCompletionTime();

			if (farmingContractCompletionTime != previousFarmingContractCompletionTime) {
				previousFarmingContractCompletionTime = farmingContractCompletionTime;
				updateFarmingContractEntity(farmingContractCompletionTime, farmingContractManager.getContractName());
			}

		}
	}

	private String generateFarmingPatchEntityId(Tab tab) {
		try {

			String username = Objects.requireNonNull(client.getLocalPlayer().getName()).toLowerCase();
			return String.format("sensor.runelite_%s_%s_patch", username, tab.name().toLowerCase());
		}catch (NullPointerException e){
			log.error("Error generating entity id for {}: {}", tab.name(), e.getMessage());
			return null;
		}
	}

	private String generateBirdhouseEntityId() {
		try{

			String username = Objects.requireNonNull(client.getLocalPlayer().getName()).toLowerCase();
			return String.format("sensor.runelite_%s_birdhouses", username);
		}catch (NullPointerException e){
			log.error("Error generating entity id for birdhouses: {}", e.getMessage());
			return null;
		}

	}

	private String generateFarmingContractEntityId() {
		try{

			String username = Objects.requireNonNull(client.getLocalPlayer().getName()).toLowerCase();
			return String.format("sensor.runelite_%s_farming_contract", username);
		}catch (NullPointerException e){
			log.error("Error generating entity id for farming contract: {}", e.getMessage());
			return null;
		}
	}

	private void updateFarmingPatchEntity(Tab tab, long completionTime) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		String entityId = generateFarmingPatchEntityId(tab);
		if (entityId == null) {
			previousFarmingCompletionTimes.put(tab, -2L);
			return;
		}

		Map<String, Object> attributes = new HashMap<>();

		PatchStatus patchStatus = PatchStatus.READY;
		if (completionTime > 0) {
			patchStatus = PatchStatus.IN_PROGRESS;
			attributes.put("completion_timestamp", Instant.ofEpochSecond(completionTime).toString());
		}else if(completionTime == -1){
			patchStatus = PatchStatus.NEVER_PLANTED;
		}
		attributes.put("Status", patchStatus.getName());
		attributes.put("friendly_name", tab.getName() + " " + patchStatus.getName());

		sendHomeAssistantUpdate(entityId, patchStatus.name().toLowerCase(), attributes);
	}

	private void updateBirdhouseEntity(long completionTime) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		String entityId = generateBirdhouseEntityId();

		if (entityId == null) {
			previousBirdhouseCompletionTime = -2;
			return;
		}

		Map<String, Object> attributes = new HashMap<>();

		PatchStatus patchStatus = PatchStatus.READY;
		if (completionTime > 0) {
			patchStatus = PatchStatus.IN_PROGRESS;
			attributes.put("completion_timestamp", Instant.ofEpochSecond(completionTime).toString());
		}else if(completionTime == -1){
			patchStatus = PatchStatus.NEVER_PLANTED;
		}
		attributes.put("Status", patchStatus.getName());
		attributes.put("friendly_name", "Bird houses " + patchStatus.getName());

		log.info("Sending birdhouse update for {} - Status: {}, time: {}", entityId, patchStatus.name().toLowerCase(), completionTime);

		sendHomeAssistantUpdate(entityId, patchStatus.name().toLowerCase(), attributes);
	}

	private void updateFarmingContractEntity(long completionTime, String contractName) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		String entityId = generateFarmingContractEntityId();

		if (entityId == null) {
			previousFarmingContractCompletionTime = -2;
			return;
		}

		Map<String, Object> attributes = new HashMap<>();

		PatchStatus patchStatus = PatchStatus.READY;
		if (completionTime > 0) {
			patchStatus = PatchStatus.IN_PROGRESS;
			if(completionTime == Long.MAX_VALUE){
				patchStatus = PatchStatus.OTHER;
				attributes.put("different_crop_planted", true);
			}else{
				log.info("Farming contract completion time: {}", Instant.ofEpochSecond(completionTime).toString());
				attributes.put("completion_timestamp", Instant.ofEpochSecond(completionTime).toString());
			}
		}else if(completionTime == -1){
			patchStatus = PatchStatus.NEVER_PLANTED;
		}
		attributes.put("Status", patchStatus.getName());
		attributes.put("friendly_name", "Farming contract " + patchStatus.getName());
		attributes.put("contract_name", contractName);

		log.info("Sending farming contract update for {} - Status: {}, time: {}, contract: {}", entityId, patchStatus.name().toLowerCase(), completionTime, contractName);

		sendHomeAssistantUpdate(entityId, patchStatus.name().toLowerCase(), attributes);
	}

	private void sendHomeAssistantUpdate(String entityId, String state, Map<String, Object> attributes) {
		String homeAssistantUrl = config.homeassistantUrl(); // Assuming you have this in your config
		String accessToken = config.homeassistantToken(); // Assuming you have this in your config

		if (homeAssistantUrl.isEmpty() || accessToken.isEmpty()) {
			log.warn("Home Assistant URL or Access Token not configured.");
			return;
		}

		String apiUrl = homeAssistantUrl + "/api/states/" + entityId;
		Map<String, Object> payload = new HashMap<>();
		payload.put("state", state);
		payload.put("attributes", attributes);

		Gson gson = this.gson.newBuilder().create();
		String jsonPayload = gson.toJson(payload);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(apiUrl))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
				.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					log.info("Home Assistant update for {} - Status: {}", entityId, response.statusCode());
					if (response.statusCode() >= 400) {
						log.error("Home Assistant update failed for {}: {}", entityId, response.body());
					}
				})
				.exceptionally(e -> {
					log.error("Error sending Home Assistant update for {}: {}", entityId, e.getMessage());
					return null;
				});
	}
}
