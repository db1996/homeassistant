package com.homeassistant.classes;

public abstract class AggressionEvent
{
    public static class AggroStarted extends AggressionEvent {}
    public static class AggroEnded extends AggressionEvent {}
    public static class AggroReset extends AggressionEvent {}
    public static class AggroTick extends AggressionEvent
    {
        private final int ticksLeft;

        public AggroTick(int ticksLeft)
        {
            this.ticksLeft = ticksLeft;
        }

        public int getTicksLeft()
        {
            return ticksLeft;
        }

        public int getSecondsLeft()
        {
            return (int)(ticksLeft * 0.6f);
        }
    }
}
