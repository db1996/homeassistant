# Home assistant integration
This plugin creates entities in your home assistant instance with information about all farming patch timers.
This lets you create automations to send notifications when you're not playing.

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

Check here for instructions: https://github.com/db1996/homeassistant_runelite

## Setup automation in home assistant
If you want to react to when a farming patch is ready, and you're not online currently, create a new automation in the web interface and edit as YAML

Below is an example for the farming contract notification
```yaml
alias: Runelite farming contract
description: Notify when RuneLite Farming Contract is done
triggers:
  - trigger: template
    value_template: |-
      {% set sen = 'sensor.farming_contract_id' %}
      {% if is_state(sen, 'in_progress') %}
        {% if state_attr(sen, 'completion_timestamp') %}
          {% set completion_timestamp_str = state_attr(sen, 'completion_timestamp') %}
          {% set completion_timestamp = strptime(completion_timestamp_str, '%Y-%m-%dT%H:%M:%S%z') %}
          {{ completion_timestamp < now() }}
        {% else %}
          false
        {% endif %}
      {% else %}
        false
      {% endif %}
conditions: []
actions: []
mode: single
```
Change the sensor id 'sensor.farming_contract_id' to the correct one created by the plugin. The ID will be runelite_yourusername_farming_contract

This trigger will fire when the contract completion time attribute is in the past, and the status is still in_progress. If you are currently playing runelite, the status will be changed to ready so this automation will never trigger if you're currently playing

### Fire even when you are currently playing
You can just create an automation with a trigger that checks when the state of the sensor changes to 'ready' (optionally, from 'in_progress'). 

## Different statuses
- 'ready'
  - Patch or contract is now ready
  - Does not have the completion_time attribute anymore
- 'in_progress'
  - Patch or contract is currently growing.
  - contains an attribute completion_time
    - Formatted like this: 2025-04-05T18:00:00Z
- 'other'
  - This happens if a patch has either never been planted, or if the patch is dead/deceased. Or if the farming contract patch is used by another seed

    
## Limitations
There are currently no sensors for each seperate patch, but I opted to create sensors for each farming patch type, farming contract and birdhouses.

This Plugin updates the patch type sensors completion time and status for the latest crop planted. So if you do a herb run, the sensor will reflect the last herb planted in your herb run. 
