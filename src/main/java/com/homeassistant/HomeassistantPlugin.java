package com.homeassistant;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;

import com.homeassistant.classes.StatusEffect;
import com.homeassistant.enums.PatchStatus;
import com.homeassistant.enums.DailyTask;
import com.homeassistant.runelite.farming.*;
import com.homeassistant.runelite.hunter.BirdHouseTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.util.Text;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Homeassistant"
)
public class HomeassistantPlugin extends Plugin
{
	private static final int HERB_BOX_MAX = 15;
	private static final int HERB_BOX_COST = 9500;
	private static final int SAND_QUEST_COMPLETE = 160;
	private static final int ONE_DAY = 86400000;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private HomeassistantConfig config;
	@Inject
	private ConfigManager configManager;


	@Inject
	private ItemManager itemManager;
	@Inject
	private Gson gson;
	@Inject
	private Notifier notifier;
	@Inject
	private OkHttpClient okHttpClient;

	private BirdHouseTracker birdHouseTracker;
	private FarmingTracker farmingTracker;
	private FarmingContractManager farmingContractManager;

	private int farmingTickOffset = 0;
	private final Map<Tab, Long> previousFarmingCompletionTimes = new EnumMap<>(Tab.class);
	private long previousBirdhouseCompletionTime = -2L;
	private long previousFarmingContractCompletionTime = -2L;


	private final Map<Tab, Long> nextFarmingCompletionTimes = new EnumMap<>(Tab.class);
	private long nextBirdhouseCompletionTime = -2L;
	private long nextFarmingContractCompletionTime = -2L;

	private final Map<DailyTask, Integer> dailyStatuses = new EnumMap<>(DailyTask.class);
	private final Map<DailyTask, Integer> previousDailyStatuses = new EnumMap<>(DailyTask.class);

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

	private boolean isLoggingIn = false;

	private boolean waitingForBattlestavesPurchase = false;
	private long battlestaffWatchStart = 0;
	private static final int BATTLESTAFF_WATCH_DURATION_MS = 120_000;
	private static final int BATTLESTAFF_NOTED_ID = 1392;

	private boolean previousIsOnline = false;
	private int previousOnlineWorld = -1;
	private boolean isOnline = false;
	private int onlineWorld = -1;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Homeassistant started!");
		resetDailies();
		resetCompletionTimes();
		initializeTrackers();

		isLoggingIn = true;
	}

	private void resetDailies() {
		dailyStatuses.clear();

		for (DailyTask tab : DailyTask.values()) {
			dailyStatuses.put(tab, -1);
		}

		resetPreviousDailies();
	}

	private void resetPreviousDailies(){
		previousDailyStatuses.clear();
		for (DailyTask tab : DailyTask.values()) {
			previousDailyStatuses.put(tab, dailyStatuses.get(tab));
		}
	}

	private boolean isRelevantFarmingTab(Tab tab) {
		return tab != Tab.OVERVIEW && tab != Tab.CLOCK && tab != Tab.TIME_OFFSET && tab != Tab.SPECIAL && tab != Tab.GRAPE; // Exclude overview, clock, offset, special (might need more specific handling), and grape
	}

	@Override
	protected void shutDown() throws Exception
	{
		resetDailies();
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

	private void resetCompletionTimes(){
		for (Tab tab : Tab.values()) {
			previousFarmingCompletionTimes.put(tab, -2L);
		}
		previousBirdhouseCompletionTime = -2L;
		previousFarmingContractCompletionTime = -2L;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		GameState gameState = gameStateChanged.getGameState();
		if(gameState == GameState.CONNECTION_LOST || gameState == GameState.LOGIN_SCREEN){
			isOnline = false;
			onlineWorld = -1;
			updateAllEntities();
		}

		if (gameState == GameState.LOGGING_IN){
			isLoggingIn = true;
		}

		if (gameState != GameState.LOGGED_IN) {
			return;
		}

		isOnline = true;
		onlineWorld = client.getWorld();
		resetCompletionTimes();
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

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.MESBOX &&
				event.getMessage().equals("Bert delivers the sand to your bank."))
		{
			dailyStatuses.put(DailyTask.SAND, 1);
			updateAllEntities();
		}

//		Check if the player attempts to buy battlestaves from Zaff, if so check for 30 seconds if the player gains them
		String message = Text.removeTags(event.getMessage());
		if (message.contains("discounted battlestaves"))
		{
			waitingForBattlestavesPurchase = true;
			battlestaffWatchStart = System.currentTimeMillis();
			log.info("Detected battlestaff purchase prompt, starting 30s watch window.");
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		log.debug("itemcontainer changed {}", event);
//		Make sure this doesn't constantly run, only run when the chatbox is detected
		if (!waitingForBattlestavesPurchase)
		{
			return;
		}

		// Check timeout
		if (System.currentTimeMillis() - battlestaffWatchStart > BATTLESTAFF_WATCH_DURATION_MS)
		{
			waitingForBattlestavesPurchase = false;
			log.info("Battlestaff watch window expired.");
			return;
		}

		if (event.getContainerId() == InventoryID.INV)
		{
			ItemContainer container = event.getItemContainer();
			if (container == null) return;

			Item[] items = container.getItems();
			boolean purchased = false;

			for (Item item : items)
			{
				if (item.getId() == BATTLESTAFF_NOTED_ID)
				{
					if (item.getQuantity() > 15){
						purchased = true;
					}
				}
			}

			log.info("Battlestaff watch window purchased., {}", purchased);
			if (purchased)
			{
				dailyStatuses.put(DailyTask.STAVES, 1);
				waitingForBattlestavesPurchase = false;
				updateAllEntities();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (isLoggingIn && config.dailies()){
			isLoggingIn = false;
			checkAllDailies();
		}

		if (!config.playerRunEnergy() && !config.playerHealth() && !config.playerPrayer() && !config.playerSpecialAttack()){
			return;
		}

		final Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null){
			return;
		}

		checkCurrentStats();
		updateAllEntities();
	}

	private void updateAllEntities() {
		String username = getUsername();
		if(username == null){
			return;
		}

		List<Map<String, Object>> entities = new ArrayList<>();

		if (config.birdHouses()) {
			nextBirdhouseCompletionTime = birdHouseTracker.getCompletionTime();
		}

		if (config.farmingContract()) {
			nextFarmingContractCompletionTime = farmingContractManager.getCompletionTime();
		}

		if(config.farmingPatches()) {
			for (Tab tab : Tab.values()) {
				nextFarmingCompletionTimes.put(tab, farmingTracker.getCompletionTime(tab));
			}
		}
		try {
			// Farming Patches
			if(config.farmingPatches()) {
				for (Tab tab : Tab.values()) {
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

			// Farming Contract
			if(config.farmingContract()) {
				if (previousFarmingContractCompletionTime != nextFarmingContractCompletionTime) {
					String entityId = generateFarmingContractEntityId();
					if (entityId != null) {

						Map<String, Object> attributes = new HashMap<>();
						attributes.put("entity_id", entityId);
						attributes.put("status", PatchStatus.IN_PROGRESS.getName());
						String contractName = farmingContractManager.getContractName();
						Tab tab = farmingContractManager.getContractTab();
						if (tab != null){
							try {
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
								} else {
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
				}
			}
		} catch (Exception e){
			log.error(e.getMessage());
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

//		Dailies (sand, staves etc)
		if(config.dailies()) {
			try {
				dailyStatuses.forEach((name, status) -> {
					if (!previousDailyStatuses.get(name).equals(status)) {
						String entityId = generateDailyEntityId(name.getId());
						if (entityId == null) {
							return;
						}

						Map<String, Object> attributes = new HashMap<>();
						attributes.put("entity_id", entityId);
						attributes.put("state", status);
						entities.add(attributes);
					}
				});
				resetPreviousDailies();
			} catch (Exception e) {
				log.error(e.getMessage());
			}
		}

//		Player stats
		if(isOnline != previousIsOnline || onlineWorld != previousOnlineWorld) {
			String entityId = String.format("sensor.runelite_%s_player_status", getUsername());
			Map<String, Object> attributes = new HashMap<>();
			attributes.put("entity_id", entityId);
			attributes.put("is_online", isOnline);
			attributes.put("world", onlineWorld);
			entities.add(attributes);
			previousIsOnline = isOnline;
			previousOnlineWorld = onlineWorld;
		}
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


		if(config.farmingTickOffset()) {
			int offset = configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, TimeTrackingConfig.FARM_TICK_OFFSET, int.class);
			offset = offset * -1;

			if (offset != farmingTickOffset) {
				farmingTickOffset = offset;
				Map<String, Object> farmingTickAttributes = new HashMap<>();
				farmingTickAttributes.put("username", username);
				farmingTickAttributes.put("farming_tick_offset", offset);
				sendPayloadToHomeAssistant(gson.toJson(farmingTickAttributes), "/services/runelite/set_farming_tick_offset");
			}
		}
	}

	private void sendPayloadToHomeAssistant(String jsonPayload) {
		sendPayloadToHomeAssistant(jsonPayload, "/services/runelite/set_multi_entity_data");
	}

	private void sendPayloadToHomeAssistant(String jsonPayload, String url) {
		String homeAssistantUrl = config.homeassistantUrl(); // Assuming you have this in your config
		String accessToken = config.homeassistantToken(); // Assuming you have this in your config

		if (homeAssistantUrl.isEmpty() || accessToken.isEmpty()) {
			log.warn("Home Assistant URL or Access Token not configured.");
			return;
		}

		String apiUrl = homeAssistantUrl + "/api" + url;
		RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonPayload);
		log.debug("Sending payload to home assistant, {}: {}", apiUrl, jsonPayload);
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
				} else {
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
							} else {
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
									} else {
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

	private String generateFarmingPatchEntityId(Tab tab) {
		try {
			if(tab == Tab.BIG_COMPOST){
				return String.format("sensor.runelite_%s_%s", getUsername(), tab.name().toLowerCase());
			}
			return String.format("sensor.runelite_%s_%s_patch", getUsername(), tab.name().toLowerCase());
		}catch (NullPointerException e){
			log.error("Error generating entity id for {}: {}", tab.name(), e.getMessage());
			return null;
		}
	}

	private String generateDailyEntityId(String str){
		try {
			return String.format("sensor.runelite_%s_daily_%s", getUsername(), str.toLowerCase());
		}catch (NullPointerException e){
			log.error("Error generating entity id for daily {}: {}", str, e.getMessage());
			return null;
		}
	}

	private String generateBirdhouseEntityId() {
		try{
			return String.format("sensor.runelite_%s_birdhouses", getUsername());
		}catch (NullPointerException e){
			log.error("Error generating entity id for birdhouses: {}", e.getMessage());
			return null;
		}

	}

	private String generateFarmingContractEntityId() {
		try{
			return String.format("sensor.runelite_%s_farming_contract", getUsername());
		}catch (NullPointerException e){
			log.error("Error generating entity id for farming contract: {}", e.getMessage());
			return null;
		}
	}

	private String generateFarmingTickEntityId() {
		try{
			return String.format("runelite_%s_farming_tick_offset", getUsername());
		}catch (NullPointerException e){
			log.error("Error generating entity id for farming contract: {}", e.getMessage());
			return null;
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

	private void checkAllDailies() {
		resetPreviousDailies();
		checkHerbBoxes();
		checkStaves();
		checkEssence();
		checkRunes();
		checkSand();
		checkFlax();
		checkArrows();
		checkDynamite();
	}

	private void checkHerbBoxes()
	{
		if(client.getVarbitValue(VarbitID.IRONMAN) != 0 || client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS) < HERB_BOX_COST){
			dailyStatuses.put(DailyTask.HERB_BOXES, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.NZONE_HERBBOXES_PURCHASED) < HERB_BOX_MAX)
		{
			dailyStatuses.put(DailyTask.HERB_BOXES, 0);
		} else {
			dailyStatuses.put(DailyTask.HERB_BOXES, 1);
		}
	}

	private void checkStaves()
	{
		if (client.getVarbitValue(VarbitID.VARROCK_DIARY_EASY_COMPLETE) != 1) {
			dailyStatuses.put(DailyTask.STAVES, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.ZAFF_LAST_CLAIMED) == 0) {
			dailyStatuses.put(DailyTask.STAVES, 0);
		} else {
			dailyStatuses.put(DailyTask.STAVES, 1);
		}
	}

	private void checkEssence()
	{
		if (client.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) != 1) {
			dailyStatuses.put(DailyTask.ESSENCE, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.ARDOUGNE_FREE_ESSENCE) == 0) {
			dailyStatuses.put(DailyTask.ESSENCE, 0);

		} else {
			dailyStatuses.put(DailyTask.ESSENCE, 1);
		}
	}

	private void checkRunes()
	{
		if(client.getVarbitValue(VarbitID.WILDERNESS_DIARY_EASY_COMPLETE) == 1){
			dailyStatuses.put(DailyTask.RUNES, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.LUNDAIL_LAST_CLAIMED) == 0) {
			dailyStatuses.put(DailyTask.RUNES, 0);
		} else {
			dailyStatuses.put(DailyTask.RUNES, 1);
		}
	}

	private void checkSand()
	{
		if (client.getVarbitValue(VarbitID.IRONMAN) == 2 /* UIM */
			|| client.getVarbitValue(VarbitID.HANDSAND_QUEST) < SAND_QUEST_COMPLETE) {
			dailyStatuses.put(DailyTask.SAND, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.YANILLE_SAND_CLAIMED) == 0) {
			dailyStatuses.put(DailyTask.SAND, 0);
		} else {
			dailyStatuses.put(DailyTask.SAND, 1);
		}
	}

	private void checkFlax()
	{
		if (client.getVarbitValue(VarbitID.KANDARIN_DIARY_EASY_COMPLETE) != 1) {
			dailyStatuses.put(DailyTask.FLAX, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.SEERS_FREE_FLAX) == 0) {
			dailyStatuses.put(DailyTask.FLAX, 0);
		} else {
			dailyStatuses.put(DailyTask.FLAX, 1);
		}
	}

	private void checkArrows()
	{
		if (client.getVarbitValue(VarbitID.WESTERN_DIARY_EASY_COMPLETE) != 1) {
			dailyStatuses.put(DailyTask.ARROWS, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.WESTERN_RANTZ_ARROWS) == 0) {
			dailyStatuses.put(DailyTask.ARROWS, 0);
		} else {
			dailyStatuses.put(DailyTask.ARROWS, 1);
		}
	}

	private void checkDynamite()
	{
		if (client.getVarbitValue(VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE) == 1){
			dailyStatuses.put(DailyTask.DYNAMITE, -1);
			return;
		}

		if (client.getVarbitValue(VarbitID.KOUREND_FREE_DYNAMITE) == 0) {
			dailyStatuses.put(DailyTask.DYNAMITE, 0);
		} else {
			dailyStatuses.put(DailyTask.DYNAMITE, 1);
		}
	}

	private void checkCurrentStats(){
		previousHealth = currentHealth;
		previousPrayer = currentPrayer;
		previousRunEnergy = currentRunEnergy;
		previousSpecialAttack = currentSpecialAttack;
		previousStatusEffects = new ArrayList<>();
		for (StatusEffect value : currentStatusEffects ){
			previousStatusEffects.add(value.copy());
		}

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
	}
}
