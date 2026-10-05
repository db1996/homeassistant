package com.homeassistant.trackers;

import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ActivityTrackerTest {
    @Test
    public void miningAnimationsAreMining() {
        assertEquals("mining", ActivityTracker.skillFor(AnimationID.HUMAN_MINING_INFERNAL_PICKAXE));
        assertEquals("mining", ActivityTracker.skillFor(AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL));
    }

    @Test
    public void eachGatheringSkillHasItsOwnName() {
        assertEquals("woodcutting", ActivityTracker.skillFor(AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE));
        assertEquals("fishing", ActivityTracker.skillFor(AnimationID.HUMAN_HARPOON));
    }

    @Test
    public void lightingLogsIsFiremaking() {
        assertEquals("firemaking", ActivityTracker.skillFor(AnimationID.HUMAN_CREATEFIRE));
    }

    @Test
    public void pickpocketingIsThievingAndCraftingRunesIsRunecraft() {
        assertEquals("thieving", ActivityTracker.skillFor(AnimationID.HUMAN_PICKPOCKET));
        assertEquals("runecraft", ActivityTracker.skillFor(AnimationID.HUMAN_RUNECRAFT));
    }

    @Test
    public void animationsThatSayNothingAreNotMapped() {
        assertNull(ActivityTracker.skillFor(-1));
        assertNull(ActivityTracker.skillFor(AnimationID.HUMAN_DIG));
        assertNull(ActivityTracker.skillFor(AnimationID.HUMAN_PICKUPFLOOR));
    }
}
