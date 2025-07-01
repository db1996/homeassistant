package com.homeassistant.classes;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import java.util.Objects;

@Slf4j
public  class Utils {
    public static String GetUserName(Client client){

        try {
            String slug = Objects.requireNonNull(client.getLocalPlayer().getName()).toLowerCase();

            // Replace all non a-z, 0-9, or _ with _
            slug = slug.replaceAll("[^a-z0-9_]", "_");

            // Collapse multiple underscores into one
            slug = slug.replaceAll("_+", "_");

            // Remove leading/trailing underscores
            slug = slug.replaceAll("^_+|_+$", "");
            return slug;
        } catch (NullPointerException e) {
            log.error("Error fetching username: {}", e.getMessage());
            return null;
        }
    }
}
