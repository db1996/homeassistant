package com.homeassistant.trackers.events;

import lombok.Getter;

public abstract class InternalEvents
{
    @Getter
    public static class VarbitUpdate extends InternalEvents
    {
        private final String trackerId;
        private final int varbitId;
        private final Integer previousValue;
        private final Integer newValue;

        public VarbitUpdate(String trackerId, int varbitId, Integer previousValue, Integer newValue)
        {
            this.trackerId = trackerId;
            this.varbitId = varbitId;
            this.previousValue = previousValue;
            this.newValue = newValue;
        }

    }
}
