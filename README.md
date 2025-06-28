# Home assistant integration
This plugin updates entities in your home assistant instance with information for all kinds of useful tracking and automations.

Everything it can update in homeassistant:

- Birdhouses
- Farming patches
- Farming contracts
- Dailies
- Player online status + world
- Player data (health, prayer, spec, run enegry)
- Player status effects (poison and venom for now)
- Aggression timer
- Farming tick offset

## Setup
1. Install the plugin through the plugin hub
2. In homeassistant, go to your profile (bottom left) -> Security and create a Long-Lived access token
3. In the plugin settings in runelite 
   - Enter your home assistant domain
   - Enter your long-lived access token

## Setup home assistant custom integration

I have created an accompanying custom integration that makes the sensors long-lived. And has easy to use actions to do things manually.

**This is required**.<br> 
The services created by the custom integration are used in this plugin, which drastically reduces the amount of calls needed.

Check here for instructions and complete docs: https://github.com/db1996/homeassistant_runelite