package com.homeassistant;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;

import com.homeassistant.trackers.*;
import com.homeassistant.trackers.FarmingTracker;
import com.homeassistant.trackers.events.UpdateEntitiesEvent;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

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
	private Gson gson;
	@Inject
	private OkHttpClient okHttpClient;
	@Inject
	private EventBus eventBus;
	@Inject
	private AggressionTracker aggressionTracker;
	@Inject
	private PlayerDataTracker playerDataTracker;
	@Inject
	private DailyTracker dailyTracker;
	@Inject
	private FarmingTracker farmingTracker;
	@Inject
	private BirdhouseTracker  birdhouseTracker;

	private final Map<String,Map<String, Object>> updateSortedEntities = new HashMap<>();

	private int currentDelayCount = -5;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Homeassistant started!");
		currentDelayCount = -5;
		updateSortedEntities.clear();
		registerTrackers();
	}

	private void registerTrackers(){
		eventBus.register(playerDataTracker);
		eventBus.register(aggressionTracker);
		eventBus.register(dailyTracker);
		eventBus.register(farmingTracker);
		eventBus.register(birdhouseTracker);
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(playerDataTracker);
		eventBus.unregister(aggressionTracker);
		eventBus.unregister(dailyTracker);
		eventBus.unregister(farmingTracker);
		eventBus.unregister(birdhouseTracker);

		log.info("Homeassistant stopped!");
	}


	@Provides
	HomeassistantConfig provideConfig(ConfigManager configManager)
	{
        return configManager.getConfig(HomeassistantConfig.class);
	}

/*
	This event can be called from any tracker. It will overwrite any previous object with the same entity_id. So if a throttle is active it will always update the latest
	onUpdate() returns List<Map<String, Object>>
 */
	@Subscribe
	public void onUpdateEntities(UpdateEntitiesEvent.UpdateEntities event){
		for(Map<String, Object> map : event.onUpdate()){
			if(map.containsKey("entity_id")){
				String entityId = (String) map.get("entity_id");
				updateSortedEntities.put(entityId, map);
			}
		}
		log.info("update entities received, total: {}", updateSortedEntities);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		String group = event.getGroup();
		if (!group.equals(HomeassistantConfig.CONFIG_GROUP)) {
			return;
		}
		if (event.getKey().equals("validate_token") && config.validateToken())
		{
			testHomeAssistant();
        }
	}

	@Subscribe
	public void onGameTick(GameTick event){
		// If the user has a global throttled setup, it will only update once every x ticks
		currentDelayCount++;
		log.debug("tick received: {}", currentDelayCount);
		if(currentDelayCount >= config.globalUpdateThrottle()){
			currentDelayCount = 0;
			if(getUsername() != null && !updateSortedEntities.isEmpty()){
				List<Map<String, Object>> entities = new ArrayList<>();
				for(Map.Entry<String, Map<String, Object>> entry : updateSortedEntities.entrySet()){
					entities.add(entry.getValue());
				}

				Map<String, Object> payload = new HashMap<>();
				payload.put("entities", entities);

				Gson gson = this.gson.newBuilder().create();
				String jsonPayload = gson.toJson(payload);

				updateSortedEntities.clear();

				sendPayloadToHomeAssistant(jsonPayload);
			}
		}
	}

	private void sendPayloadToHomeAssistant(String jsonPayload) {
		sendPayloadToHomeAssistant(jsonPayload, "/services/runelite/set_multi_entity_data");
	}

	private void sendPayloadToHomeAssistant(String jsonPayload, String url) {
		String homeAssistantUrl = config.homeassistantUrl();
		String accessToken = config.homeassistantToken();

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
