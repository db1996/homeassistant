package com.homeassistant.classes;

import java.util.Objects;

public class StatusEffect {
    public String name;
    public int number = 0;
    public String time = "";

    public StatusEffect copy(){
        StatusEffect copy = new StatusEffect();
        copy.name = name;
        copy.number = number;
        copy.time = time;
        return copy;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StatusEffect that = (StatusEffect) o;
        return number == that.number &&
                Objects.equals(name, that.name) &&
                Objects.equals(time, that.time);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, number, time);
    }
}
