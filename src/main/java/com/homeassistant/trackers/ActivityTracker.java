package com.homeassistant.trackers;

import com.homeassistant.HomeassistantConfig;
import com.homeassistant.classes.Utils;
import com.homeassistant.trackers.events.HomeassistantEvents;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class ActivityTracker {
    public static final String NONE = "none";

    private static final int HOLD_TICKS = 5;

    private static final int COMBAT_HOLD_TICKS = 10;

    public static final String COMBAT = "combat";

    private static final Map<Integer, String> ANIMATIONS = buildAnimations();

    private final HomeassistantConfig config;
    private final EventBus eventBus;
    private final Client client;

    private String lastSkill = null;
    private int lastSeenTick = -1;
    private String lastSent = null;

    @Inject
    public ActivityTracker(EventBus eventBus, Client client, HomeassistantConfig config) {
        this.eventBus = eventBus;
        this.client = client;
        this.config = config;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            lastSent = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!config.sendActivity()) return;
        if (client.getGameState() != GameState.LOGGED_IN) return;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        String username = Utils.GetUserName(client);
        if (username == null) return;

        int currentTick = client.getTickCount();
        String skill = ANIMATIONS.get(player.getAnimation());
        if (skill == null && isFighting(player)) {
            skill = COMBAT;
        }
        if (skill != null) {
            lastSkill = skill;
            lastSeenTick = currentTick;
        }

        int hold = COMBAT.equals(lastSkill) ? COMBAT_HOLD_TICKS : HOLD_TICKS;
        String activity = lastSkill != null && currentTick - lastSeenTick <= hold
                ? lastSkill
                : NONE;
        if (activity.equals(lastSent)) return;
        lastSent = activity;
        log.debug("Activity is now {}", activity);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("entity_id", String.format("sensor.runelite_%s_activity", username));
        attributes.put("activity", activity);

        List<Map<String, Object>> entities = Collections.singletonList(attributes);
        eventBus.post(new HomeassistantEvents.UpdateEntities(entities));
    }

    private static boolean isFighting(Player player) {
        Actor target = player.getInteracting();
        if (!(target instanceof NPC)) return false;
        NPCComposition composition = ((NPC) target).getTransformedComposition();
        if (composition == null) return false;
        String[] actions = composition.getActions();
        if (actions == null) return false;
        for (String action : actions) {
            if ("Attack".equalsIgnoreCase(action)) return true;
        }
        return false;
    }

    public static String skillFor(int animation) {
        return ANIMATIONS.get(animation);
    }

    private static void put(Map<Integer, String> map, String skill, int... animations) {
        for (int animation : animations) {
            map.put(animation, skill);
        }
    }

    private static Map<Integer, String> buildAnimations() {
        Map<Integer, String> map = new HashMap<>();
        put(map, "woodcutting",
            AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE,
            AnimationID.HUMAN_WOODCUTTING_IRON_AXE,
            AnimationID.HUMAN_WOODCUTTING_STEEL_AXE,
            AnimationID.HUMAN_WOODCUTTING_BLACK_AXE,
            AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE,
            AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE,
            AnimationID.HUMAN_WOODCUTTING_RUNE_AXE,
            AnimationID.HUMAN_WOODCUTTING_GILDED_AXE,
            AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE,
            AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL,
            AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE,
            AnimationID.HUMAN_WOODCUTTING_3A_AXE,
            AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE,
            AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL,
            AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE,
            AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE,
            AnimationID.FORESTRY_2H_AXE_CHOPPING_3A,
            AnimationID.HUMAN_CANOEING_CARVE_BRONZE_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_IRON_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_STEEL_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_BLACK_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_MITHRIL_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_ADAMANT_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_RUNE_AXE,
            AnimationID.BRUT_HUMAN_CANOEING_CARVE_GILDED_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_DRAGON_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_TRAILBLAZER_AXE_NO_INFERNAL,
            AnimationID.HUMAN_CANOEING_CARVE_INFERNAL_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_LEAGUE_TRAILBLAZER_AXE,
            AnimationID.BRUT_HUMAN_CANOEING_CARVE_3A_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_CRYSTAL_AXE,
            AnimationID.BRUT_HUMAN_CANOEING_CARVE_CRYSTAL_AXE,
            AnimationID.BRUT_HUMAN_CANOEING_CARVE_LEAGUE_TRAILBLAZER_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_BRONZE_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_IRON_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_STEEL_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_BLACK_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_MITHRIL_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_ADAMANT_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_RUNE_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_DRAGON_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_CRYSTAL_2H_AXE,
            AnimationID.HUMAN_CANOEING_CARVE_CRYSTAL_2H_AXE_INACTIVE,
            AnimationID.HUMAN_CANOEING_CARVE_3A_2H_AXE);
        put(map, "firemaking",
            AnimationID.FORESTRY_CAMPFIRE_BURNING_ARCTIC_PINE_LOG,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_BLISTERWOOD_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_MAGIC_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_MAHOGANY_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_MAPLE_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_OAK_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_REDWOOD_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_TEAK_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_WILLOW_LOGS,
            AnimationID.FORESTRY_CAMPFIRE_BURNING_YEW_LOGS,
            AnimationID.HUMAN_CREATEFIRE,
            AnimationID.HUMAN_CREATEFIRE_SINGLE,
            AnimationID.BRUT_PLAYER_FIREMAKING_AIDE_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_OAK_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_WILLOW_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_MAPLE_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_YEW_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_MAGIC_SHORTBOW,
            AnimationID.BRUT_PLAYER_FIREMAKING_DAGANOTH_BOW);
        put(map, "thieving",
            AnimationID.HUMAN_PICKPOCKET);
        put(map, "runecraft",
            AnimationID.HUMAN_RUNECRAFT,
            AnimationID.HUMAN_RUNECRAFT_WALKMERGE);
        put(map, "cooking",
            AnimationID.HUMAN_FIRECOOKING,
            AnimationID.HUMAN_COOKING,
            AnimationID.HUMAN_MAKE_WINE,
            AnimationID.HUMAN_CUT_FOOD);
        put(map, "crafting",
            AnimationID.HUMAN_OPALCUTTING,
            AnimationID.HUMAN_JADECUTTING,
            AnimationID.HUMAN_REDTOPAZCUTTING,
            AnimationID.HUMAN_SAPPHIRECUTTING,
            AnimationID.HUMAN_EMERALDCUTTING,
            AnimationID.HUMAN_RUBYCUTTING,
            AnimationID.HUMAN_DIAMONDCUTTING,
            AnimationID.HUMAN_DRAGONSTONECUTTING,
            AnimationID.HUMAN_ONYXCUTTING,
            AnimationID.HUMAN_AMETHYSTCUTTING,
            AnimationID.HUMAN_GLASSBLOWING,
            AnimationID.HUMAN_SPINNINGWHEEL_60,
            AnimationID.HUMAN_SPINNINGWHEEL_90,
            AnimationID.FARMING_USELOOM,
            AnimationID.HUMAN_BATTLESTAFF_CRAFTING,
            AnimationID.HUMAN_LEATHER_CRAFTING,
            AnimationID.HUMAN_POTTERYWHEEL,
            AnimationID.HUMAN_CUTTING_RESTART,
            AnimationID.HUMAN_GOLEM_CHISEL_END);
        put(map, "fletching",
            AnimationID.HUMAN_FLETCHING,
            AnimationID.XBOWS_FLETCHING_WOOD_BRONZE,
            AnimationID.XBOWS_FLETCHING_OAK_BLURITE,
            AnimationID.XBOWS_FLETCHING_WILLOW_IRON,
            AnimationID.XBOWS_FLETCHING_TEAK_STEEL,
            AnimationID.XBOWS_FLETCHING_MAPLE_MITHRIL,
            AnimationID.XBOWS_FLETCHING_MAHOGANY_ADAMANTITE,
            AnimationID.XBOWS_FLETCHING_YEW_RUNITE,
            AnimationID.XBOWS_FLETCHING_YEW_DRAGON,
            AnimationID.STRINGING_SHORTBOW,
            AnimationID.STRINGING_OAK_SHORTBOW,
            AnimationID.STRINGING_WILLOW_SHORTBOW,
            AnimationID.STRINGING_MAPLE_SHORTBOW,
            AnimationID.STRINGING_YEW_SHORTBOW,
            AnimationID.STRINGING_MAGIC_SHORTBOW,
            AnimationID.STRINGING_LONGBOW,
            AnimationID.STRINGING_OAK_LONGBOW,
            AnimationID.STRINGING_WILLOW_LONGBOW,
            AnimationID.STRINGING_MAPLE_LONGBOW,
            AnimationID.STRINGING_YEW_LONGBOW,
            AnimationID.STRINGING_MAGIC_LONGBOW,
            AnimationID.HUMAN_FLETCHING_ADD_FEATHER,
            AnimationID.HUMAN_FLETCHING_ADD_ARROW_TIPS,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_BRONZE,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_IRON,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_BLURITE,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_STEEL,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_MITHRIL,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_ADAMANT,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_RUNE,
            AnimationID.HUMAN_FLETCHING_ADD_BOLT_TIPS_DRAGON,
            AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS);
        put(map, "smithing",
            AnimationID.HUMAN_SMITHING,
            AnimationID.HUMAN_SMITHING_IMCANDO_HAMMER,
            AnimationID.HUMAN_FURNACE);
        put(map, "fishing",
            AnimationID.INFERNALEEL_BREAK,
            AnimationID.INFERNALEEL_BREAK_IMCANDO,
            AnimationID.SNAKEBOSS_SLICEEEL,
            AnimationID.HUMAN_LARGENET,
            AnimationID.HUMAN_SMALLNET,
            AnimationID.HUMAN_FISH_ONSPOT,
            AnimationID.HUMAN_LOBSTER,
            AnimationID.HUMAN_HARPOON,
            AnimationID.HUMAN_HARPOON_BARBED,
            AnimationID.HUMAN_HARPOON_DRAGON,
            AnimationID.HUMAN_HARPOON_TRAILBLAZER_NO_INFERNAL,
            AnimationID.HUMAN_HARPOON_INFERNAL,
            AnimationID.HUMAN_HARPOON_CRYSTAL,
            AnimationID.HUMAN_HARPOON_LEAGUE_TRAILBLAZER,
            AnimationID.HUMAN_HARPOON_TRAILBLAZER_RELOADED_NO_INFERNAL,
            AnimationID.HUMAN_HARPOON_TRAILBLAZER,
            AnimationID.HUMAN_HARPOON_TRAILBLAZER_RELOADED,
            AnimationID.HUMAN_FISHING_CASTING,
            AnimationID.HUMAN_OCTOPUS_POT,
            AnimationID.BRUT_PLAYER_HAND_FISHING_END_BLANK,
            AnimationID.HUMAN_FISHING_CASTING_PEARL,
            AnimationID.HUMAN_FISHING_CASTING_PEARL_FLY,
            AnimationID.HUMAN_FISHING_CASTING_PEARL_BRUT,
            AnimationID.HUMAN_FISH_ONSPOT_PEARL,
            AnimationID.HUMAN_FISH_ONSPOT_PEARL_FLY,
            AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT,
            AnimationID.HUMAN_FISHING_CASTING_PEARL_OILY,
            AnimationID.HUMAN_FISHING_ONSPOT_BRUT);
        put(map, "mining",
            AnimationID.HUMAN_MINING_BRONZE_PICKAXE,
            AnimationID.HUMAN_MINING_IRON_PICKAXE,
            AnimationID.HUMAN_MINING_STEEL_PICKAXE,
            AnimationID.HUMAN_MINING_BLACK_PICKAXE,
            AnimationID.HUMAN_MINING_MITHRIL_PICKAXE,
            AnimationID.HUMAN_MINING_ADAMANT_PICKAXE,
            AnimationID.HUMAN_MINING_RUNE_PICKAXE,
            AnimationID.HUMAN_MINING_GILDED_PICKAXE,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY,
            AnimationID.HUMAN_MINING_ZALCANO_PICKAXE,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL,
            AnimationID.HUMAN_MINING_INFERNAL_PICKAXE,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE,
            AnimationID.HUMAN_MINING_3A_PICKAXE,
            AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE,
            AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE,
            AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_ZALCANO_LEAGUE_TRAILBLAZER_PICKAXE,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL,
            AnimationID.PICKAXE_POWER_SWING,
            AnimationID.PICKAXE_POWER_SWING_BRONZE,
            AnimationID.PICKAXE_POWER_SWING_IRON,
            AnimationID.PICKAXE_POWER_SWING_STEEL,
            AnimationID.PICKAXE_POWER_SWING_BLACK,
            AnimationID.PICKAXE_POWER_SWING_MITHRIL,
            AnimationID.PICKAXE_POWER_SWING_ADAMANT,
            AnimationID.PICKAXE_POWER_SWING_RUNE,
            AnimationID.PICKAXE_POWER_SWING_GILDED,
            AnimationID.PICKAXE_POWER_SWING_DRAGON,
            AnimationID.PICKAXE_POWER_SWING_PRETTY,
            AnimationID.PICKAXE_POWER_SWING_ZALCANO,
            AnimationID.PICKAXE_POWER_SWING_INFERNAL,
            AnimationID.PICKAXE_POWER_SWING_3A,
            AnimationID.PICKAXE_POWER_SWING_CRYSTAL,
            AnimationID.PICKAXE_POWER_SWING_TRAILBLAZER,
            AnimationID.PICKAXE_POWER_SWING_LEAGUE_TRAILBLAZER,
            AnimationID.PICKAXE_POWER_SWING_TRAILBLAZER_NO_INFERNAL,
            AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL,
            AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL,
            AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_3A_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL,
            AnimationID.HUMAN_MINING_BRONZE_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_IRON_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_STEEL_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_BLACK_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_RUNE_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_GILDED_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_3A_PICKAXE_NOREACHFORWARD,
            AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_NOREACHFORWARD);
        put(map, "herblore",
            AnimationID.HUMAN_HERBING_GRIND,
            AnimationID.HUMAN_HERBING_VIAL,
            AnimationID.HUMAN_SALAMANDER_TAR_GRIND,
            AnimationID.HUMAN_MACHINERY_ALCHEMY01_RETORT01_INTERACT01,
            AnimationID.HUMAN_MACHINERY_ALCHEMY01_ALEMBIC01_INTERACT01,
            AnimationID.HUMAN_MACHINERY_ALCHEMY01_AGITATOR01_INTERACT01,
            AnimationID.HUMAN_ALCHEMY01_MILL01_INTERACT01,
            AnimationID.HUMAN_HERBING_VIAL_RESTART);
        put(map, "magic",
            AnimationID.HUMAN_CASTCHARGEORB,
            AnimationID.DREAM_PLAYER_MAKE_PLANK_SPELL,
            AnimationID.LUNAR_HUMAN_MAGIC_SUMMON1,
            AnimationID.POH_CREATE_MAGIC_TABLET_WITHSTAFF,
            AnimationID.HUMAN_CAST_ENCHANTRING,
            AnimationID.HUMAN_ENCHANTAMULETLVL1,
            AnimationID.HUMAN_ENCHANTAMULETLVL2,
            AnimationID.HUMAN_ENCHANTAMULETLVL3,
            AnimationID.HUMAN_XBOW_ENCHANT_ARROWTIP);
        put(map, "prayer",
            AnimationID.HUMAN_BONE_SACRIFICE,
            AnimationID.QUEST_AHOY_HUMAN_FILLING_BUCKET,
            AnimationID.AHOY_BONE_DUMP,
            AnimationID.AHOY_BONE_GRIND,
            AnimationID.AHOY_FILLBUCKET_BONEDUST);
        put(map, "farming",
            AnimationID.ULTRACOMPOST_MAKE,
            AnimationID.PICKING_MID,
            AnimationID.PICKING_LOW,
            AnimationID.PICKING_HIGH,
            AnimationID.FARMING_PICK_MUSHROOM);
        put(map, "sailing",
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_IDLE01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_2X5_IDLE01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_3X8_IDLE01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_INTERACT01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_1X3_RESET01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_2X5_RESET01,
            AnimationID.SAILING_HUMAN_SALVAGE_HOOK_KANDARIN_3X8_RESET01);
        return Collections.unmodifiableMap(map);
    }
}
