# Home assistant integration
This plugin creates entities in your home assistant instance with information about all farming patch timers.
This lets you create automations to send notifications when you're not playing.

## Setup
1. Install the plugin through the plugin hub
2. In homeassistant, go to your profile (bottom left) -> Security and create a Long-Lived access token
3. In the plugin settings in runelite 
   - Enter your home assistant domain
   - Enter your long-lived access token
   - Choose which entities you want to update, each one of these requires a call to home assistant so limiting it may improve performance

Once you login for the first time with this plugin active, all entities will be created/updated when logging in and whenever something changes with your farming patches

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
Because of how these entities are made in home assistant (without a custom HACS integration), the entities will not persist through homeassistant reboots. Any automation based on the above will just never trigger when this happens. Once you login again after that, the entities will be made again automatically.

You could send your own API call to homeassistant to create/update the entities yourself if you want to, for example when you're playing on mobile. But for it is not possible to automate with this plugin until a companion plugin is made in home assistant itself.