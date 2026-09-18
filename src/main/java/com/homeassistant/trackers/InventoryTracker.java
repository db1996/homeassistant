package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reports what the player is carrying and wearing.
 *
 * Two entities, each behind its own toggle: the inventory (28 slots, with
 * how many are free -- the number an "inventory full" automation wants) and
 * the worn equipment, keyed by slot name.
 *
 * Changes are collected per game tick rather than sent as they happen. A
 * bank withdrawal or a full drop fires one ItemContainerChanged per item, and
 * a tick later they all describe the same container anyway. Reading the
 * container on the tick also sidesteps the login race where the container
 * event arrives before the local player has a name.
 */
@Slf4j
@Singleton
public class InventoryTracker {
    private static final int INVENTORY_SLOTS = 28;

    // The slots the equipment tab shows. ARMS, HAIR and JAW are internal
    // slots the game uses for rendering; they never hold an item.
    private static final EquipmentInventorySlot[] WORN_SLOTS = {
            EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE,
            EquipmentInventorySlot.AMULET, EquipmentInventorySlot.WEAPON,
            EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD,
            EquipmentInventorySlot.LEGS, EquipmentInventorySlot.GLOVES,
            EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING,
            EquipmentInventorySlot.AMMO,
    };

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;
    private final ItemManager itemManager;

    private boolean inventoryDirty = false;
    private boolean equipmentDirty = false;
    private Map<String, Object> lastInventory = null;
    private Map<String, Object> lastEquipment = null;

    @Inject
    public InventoryTracker(EventBus eventBus, Client client, ItemManager itemManager,
                            HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        int id = event.getContainerId();
        if (id == InventoryID.INV) {
            inventoryDirty = true;
        } else if (id == InventoryID.WORN) {
            equipmentDirty = true;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // A fresh login has to re-send everything: the throttle in the plugin
        // merges by entity_id, so a value equal to the last one sent before
        // logging out would otherwise never reach Home Assistant again after
        // a restart of either end.
        if (event.getGameState() == GameState.LOGGED_IN) {
            lastInventory = null;
            lastEquipment = null;
            inventoryDirty = true;
            equipmentDirty = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!config.sendInventory() && !config.sendEquipment()) return;
        if (client.getGameState() != GameState.LOGGED_IN) return;

        String username = Utils.GetUserName(client);
        if (username == null) return; // stays dirty, tried again next tick

        List<Map<String, Object>> entities = new ArrayList<>();

        if (inventoryDirty && config.sendInventory()) {
            inventoryDirty = false;
            Map<String, Object> inventory = readInventory(username);
            if (!Objects.equals(inventory, lastInventory)) {
                lastInventory = inventory;
                entities.add(inventory);
            }
        }

        if (equipmentDirty && config.sendEquipment()) {
            equipmentDirty = false;
            Map<String, Object> equipment = readEquipment(username);
            if (!Objects.equals(equipment, lastEquipment)) {
                lastEquipment = equipment;
                entities.add(equipment);
            }
        }

        if (entities.isEmpty()) return;
        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    private Map<String, Object> readInventory(String username) {
        List<Map<String, Object>> items = new ArrayList<>();
        ItemContainer container = client.getItemContainer(InventoryID.INV);
        if (container != null) {
            Item[] slots = container.getItems();
            for (int slot = 0; slot < slots.length && slot < INVENTORY_SLOTS; slot++) {
                Item item = slots[slot];
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) continue;
                Map<String, Object> row = describe(item);
                row.put("slot", slot);
                items.add(row);
            }
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id", String.format("sensor.runelite_%s_inventory", username));
        attributes.put("items", items);
        attributes.put("used_slots", items.size());
        attributes.put("free_slots", INVENTORY_SLOTS - items.size());
        return attributes;
    }

    private Map<String, Object> readEquipment(String username) {
        // LinkedHashMap so the slots arrive in the order the equipment tab
        // shows them; a dashboard that lists them gets that for free.
        Map<String, Object> worn = new LinkedHashMap<>();
        ItemContainer container = client.getItemContainer(InventoryID.WORN);
        if (container != null) {
            for (EquipmentInventorySlot slot : WORN_SLOTS) {
                Item item = container.getItem(slot.getSlotIdx());
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) continue;
                worn.put(slot.name().toLowerCase(), describe(item));
            }
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id", String.format("sensor.runelite_%s_equipment", username));
        attributes.put("worn", worn);
        return attributes;
    }

    /**
     * One item as Home Assistant sees it. A noted item reports the name of
     * the thing it is a note for, with "noted" set, rather than a bare id.
     */
    private Map<String, Object> describe(Item item) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", item.getId());
        row.put("quantity", item.getQuantity());

        ItemComposition composition = itemManager.getItemComposition(item.getId());
        boolean noted = composition.getNote() != -1;
        if (noted) {
            composition = itemManager.getItemComposition(composition.getLinkedNoteId());
        }
        row.put("name", composition.getName());
        row.put("noted", noted);
        return row;
    }
}
