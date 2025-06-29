package com.homeassistant.trackers.events;

import java.util.List;
import java.util.Map;

public abstract class UpdateEntitiesEvent
{
    public static class UpdateEntities extends UpdateEntitiesEvent
    {
        private final List<Map<String, Object>> entities;

        public UpdateEntities(List<Map<String, Object>> entities)
        {
            this.entities = entities;
        }

        public List<Map<String, Object>> onUpdate()
        {
            return entities;
        }
    }
}
