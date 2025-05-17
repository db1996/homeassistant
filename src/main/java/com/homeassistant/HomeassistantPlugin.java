package com.homeassistant;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;

import com.homeassistant.enums.PatchStatus;
import com.homeassistant.runelite.farming.*;
import com.homeassistant.runelite.hunter.BirdHouseTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@PluginDescriptor(
	name = "Homeassistant"
)
public class HomeassistantPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

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

	@Inject
	private OkHttpClient okHttpClient;

	private final Map<Tab, Long> previousFarmingCompletionTimes = new EnumMap<>(Tab.class);
	private long previousBirdhouseCompletionTime = -2L;
	private long previousFarmingContractCompletionTime = -2L;

	private final Map<Tab, Long> nextFarmingCompletionTimes = new EnumMap<>(Tab.class);
	private long nextBirdhouseCompletionTime = -2L;
	private long nextFarmingContractCompletionTime = -2L;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Homeassistant started!");
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

		if (event.getKey().equals("validate_token") && config.validateToken())
		{
			testHomeAssistant();
        }
		birdHouseTracker.loadFromConfig();
		farmingTracker.setIgnoreFarmingGuild(config.ignoreFarmingGuild());
		farmingTracker.loadCompletionTimes();
		farmingContractManager.loadContractFromConfig();

		updateAllEntities();
	}

	private void updateAllEntities() {
		List<Map<String, Object>> entities = new ArrayList<>();

		for (Tab tab : Tab.values()) {
			if (getConfigFromTab(tab)) {
				nextFarmingCompletionTimes.put(tab, farmingTracker.getCompletionTime(tab));
			}
		}

		if (config.birdHouses()) {
			nextBirdhouseCompletionTime = birdHouseTracker.getCompletionTime();
		}

		if (config.farmingContract()) {
			nextFarmingContractCompletionTime = farmingContractManager.getCompletionTime();
		}

		// Farming Patches
		for (Tab tab : Tab.values()) {
			if (getConfigFromTab(tab)) {
				if (!Objects.equals(previousFarmingCompletionTimes.get(tab), nextFarmingCompletionTimes.get(tab))) {
					String entityId = generateFarmingPatchEntityId(tab);
					if (entityId == null) continue;

					Map<String, Object> attributes = new HashMap<>();
					attributes.put("entity_id", entityId);

					long completionTime = nextFarmingCompletionTimes.get(tab);
					PatchStatus patchStatus = PatchStatus.READY;

					if (completionTime > 0) {
						patchStatus = PatchStatus.IN_PROGRESS;
						attributes.put("completion_time", Instant.ofEpochSecond(completionTime).toString());
					} else if (completionTime == -1) {
						patchStatus = PatchStatus.NEVER_PLANTED;
					}

					attributes.put("status", patchStatus.getName());

					entities.add(attributes); // ✅ add to array
					previousFarmingCompletionTimes.put(tab, nextFarmingCompletionTimes.get(tab));
				}
			}
		}

		// Birdhouse
		if (previousBirdhouseCompletionTime != nextBirdhouseCompletionTime) {
			String entityId = generateBirdhouseEntityId();
			if (entityId != null) {
				Map<String, Object> attributes = new HashMap<>();
				attributes.put("entity_id", entityId);
				attributes.put("status", PatchStatus.IN_PROGRESS.getName());
				attributes.put("completion_time", Instant.ofEpochSecond(nextBirdhouseCompletionTime).toString());

				entities.add(attributes); // ✅ add to array
				previousBirdhouseCompletionTime = nextBirdhouseCompletionTime;
			}
		}

		// Farming Contract
		if (previousFarmingContractCompletionTime != nextFarmingContractCompletionTime) {
			String entityId = generateFarmingContractEntityId();
			if (entityId != null) {

				Map<String, Object> attributes = new HashMap<>();
				attributes.put("entity_id", entityId);
				attributes.put("status", PatchStatus.IN_PROGRESS.getName());
				String contractName = farmingContractManager.getContractName();
				Tab tab = farmingContractManager.getContractTab();
				try {
                    assert tab != null;
                    attributes.put("patch_type", tab.name().toLowerCase());
					attributes.put("crop_type", contractName);
				}catch (NullPointerException e){
					log.debug("Error getting contract name or tab: {}", e.getMessage());
				}

				log.info("Farming contract completion time: {}", nextFarmingContractCompletionTime);
				PatchStatus patchStatus = PatchStatus.READY;
				if (nextFarmingContractCompletionTime > 0) {
					patchStatus = PatchStatus.IN_PROGRESS;
					if(nextFarmingContractCompletionTime == Long.MAX_VALUE){
						patchStatus = PatchStatus.OTHER;
					}else{
//				log.info("Farming contract completion time: {}", Instant.ofEpochSecond(completionTime).toString());
						attributes.put("completion_time", Instant.ofEpochSecond(nextFarmingContractCompletionTime).toString());
					}
				}else if(nextFarmingContractCompletionTime == -1){
					patchStatus = PatchStatus.NEVER_PLANTED;
				}
				attributes.put("status", patchStatus.getName());

				entities.add(attributes); // ✅ add to array
				previousFarmingContractCompletionTime = nextFarmingContractCompletionTime;
			}
		}

		if(entities.isEmpty()){
			return;
		}
		// ✅ Wrap it into the final payload and serialize
		Map<String, Object> payload = new HashMap<>();
		payload.put("entities", entities);


		Gson gson = this.gson.newBuilder().create();
		String jsonPayload = gson.toJson(payload);

		// ✅ Send it to HA using your existing HTTP call method
		sendPayloadToHomeAssistant(jsonPayload);
	}

	private void sendPayloadToHomeAssistant(String jsonPayload) {
		String homeAssistantUrl = config.homeassistantUrl(); // Assuming you have this in your config
		String accessToken = config.homeassistantToken(); // Assuming you have this in your config

		if (homeAssistantUrl.isEmpty() || accessToken.isEmpty()) {
			log.warn("Home Assistant URL or Access Token not configured.");
			return;
		}

		String apiUrl = homeAssistantUrl + "/api/services/runelite/set_multi_entity_data";
		RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonPayload);

		Request request = new Request.Builder()
				.url(Objects.requireNonNull(HttpUrl.parse(apiUrl)))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "application/json")
				.post(requestBody)
				.build();

		okHttpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("Error submitting the entity to homeassistant ", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				log.info("Successfully created/updated entity {}.", jsonPayload);
				response.close();
			}
		});
	}

	private void testHomeAssistant()
	{
		String homeAssistantUrl = config.homeassistantUrl();
		String accessToken = config.homeassistantToken();


		if (homeAssistantUrl.isEmpty() || accessToken.isEmpty())
		{
			log.warn("Home Assistant URL or Access Token not configured.");
			return;
		}

		String apiUrl = homeAssistantUrl + "/api/";

		Request request = new Request.Builder()
				.url(Objects.requireNonNull(HttpUrl.parse(apiUrl)))
				.header("Authorization", "Bearer " + accessToken)
				.get()
				.build();

		log.info("Testing Home Assistant connection with URL: {}", apiUrl);

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("Error connecting to the Home Assistant API", e);
				clientThread.invoke(() -> client.addChatMessage(
					ChatMessageType.GAMEMESSAGE,
					"",
					"Invalid Home Assistant token or URL.",
					null
				));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				int code  = response.code();
				String message = response.message();
				if(code != 200){
					clientThread.invoke(() -> client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
						"",
							String.format("Invalid Home Assistant token or URL. Code: %s, message: %s", code, message),
						null
					));
				}else{
					clientThread.invoke(() -> client.addChatMessage(
						ChatMessageType.GAMEMESSAGE,
						"",
						"Home Assistant token and URL are valid.",
						null
					));

					String apiUrl = homeAssistantUrl + "/api/services";

					Request request = new Request.Builder()
							.url(Objects.requireNonNull(HttpUrl.parse(apiUrl)))
							.header("Authorization", "Bearer " + accessToken)
							.get()
							.build();

					log.info("Listing home assistant services: {}", apiUrl);
					okHttpClient.newCall(request).enqueue(new Callback()
					{
						@Override
						public void onFailure(Call call, IOException e)
						{
							log.error("Error listing home assistant services", e);
							clientThread.invoke(() -> client.addChatMessage(
									ChatMessageType.GAMEMESSAGE,
									"",
									"Could not list home assistant services",
									null
							));
						}

						@Override
						public void onResponse(Call call, Response response)
						{
							int code  = response.code();
							String message = response.message();
							if(code != 200){
								clientThread.invoke(() -> client.addChatMessage(
										ChatMessageType.GAMEMESSAGE,
										"",
										String.format("Could not list home assistant services. Code: %s, message: %s", code, message),
										null
								));
							}else{
								log.info("Successfully listed home assistant services.");
								try {
									String responseBody = response.body().string();
									List<Map<String, Object>> services = gson.fromJson(responseBody, List.class);
									Set<String> domains = new HashSet<>();
									for (Map<String, Object> service : services) {
										domains.add((String) service.get("domain"));
									}
									log.info("Available service domains: {}", domains);
									if (!domains.contains("runelite")) {
										clientThread.invoke(() -> client.addChatMessage(
											ChatMessageType.GAMEMESSAGE,
											"",
											"Warning: 'runelite' service domain not found in Home Assistant",
											null
										));

										clientThread.invoke(() -> client.addChatMessage(
											ChatMessageType.GAMEMESSAGE,
											"",
											"To make this plugin work, please add the 'runelite' integration to Home Assistant, more information in this plugin's GitHub repository.",
											null
										));
									}else{
										clientThread.invoke(() -> client.addChatMessage(
											ChatMessageType.GAMEMESSAGE,
											"",
											"Successfully found the runelite plugin",
											null
										));

									}
								} catch (Exception e) {
									log.error("Error parsing Home Assistant services response", e);
								}
							}
							response.close();
						}
					});
				}
				response.close();
			}
		});
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
}
