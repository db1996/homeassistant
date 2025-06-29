package com.homeassistant.trackers.events;

import lombok.Getter;

import java.util.List;
import java.util.Map;

public abstract class HomeassistantEvents
{
    @Getter
    public static class UpdateEntities extends HomeassistantEvents
    {
        private final List<Map<String, Object>> entities;

        public UpdateEntities(List<Map<String, Object>> entities)
        {
            this.entities = entities;
        }

    }

    @Getter
    public static class SendEvent extends HomeassistantEvents
    {
        private final Map<String, Object> eventObj;
        private final String service;

        public SendEvent(Map<String, Object> eventObj, String service)
        {
            this.eventObj = eventObj;
            this.service = service;
        }

    }
}
