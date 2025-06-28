package com.homeassistant.classes;

import com.homeassistant.enums.AggressionStatus;

public class AggressionData {
    public AggressionStatus status = AggressionStatus.UNKNOWN;
    public int seconds = 0;
    public int ticks = 0;

    public boolean isEquals(AggressionData other) {
        return status == other.status &&  seconds == other.seconds && ticks == other.ticks;
    }
}
