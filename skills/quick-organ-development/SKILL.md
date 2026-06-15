---
name: quick-organ-development
description: Fast checklist for adding or modifying an organ, from item registration to JSON effects and testing.
---

# Quick organ development

Use this when you want the shortest path to add or change an organ in OEP.

## 1. Register the item

Edit:

- `src/main/java/cn/kuzuanpa/organeffectprocessor/common/registry/OepItems.java`

Pattern:

- registry name must match the organ JSON filename
- OEP organ items should use `OepOrganItem`

## 2. Add the organ JSON

Create or edit:

- `src/main/resources/data/<namespace>/organapi/organs/<organ_name>.json`

Use the current schema:

- `conditions`
- `grants`
- `events`
- `executions`

Architecture rule:

- `grants` = static point sources
- `events` = runtime point mutations
- `executions` = consume/use points to produce visible effects

Prefer point-bridge design:

- event produces `runtime:*` or `counter:*`
- execution consumes or observes those points
- use `source: "self"` when the event-earned source should stay local to the current installed organ instance

## 3. Make it placeable

Update:

- `src/main/resources/data/organapi/tags/items/organs.json`

If the item is not in `organapi:organs`, OrganAPI slots will reject it even if the JSON exists.

## 4. Add localization

Update:

- `src/main/resources/assets/organeffectprocessor/lang/en_us.json`

Usually needed:

- `item.<namespace>.<organ_name>`
- any new point keys:
  - `point.organeffectprocessor.<type>.<namespace>.<path>`
  - `point.organeffectprocessor.<type>.<namespace>.<path>.desc`
- any new skills:
  - `point.organeffectprocessor.skill.<namespace>.<path>`
  - `point.organeffectprocessor.skill.<namespace>.<path>.desc`

## 5. If the organ grants a skill

Edit:

- `src/main/java/cn/kuzuanpa/organeffectprocessor/common/skill/SkillManager.java`

Checklist:

- register skill metadata
- register skill executor/cast behavior
- ensure the point grant uses `type: skill`

## 6. If the organ needs non-JSON behavior

Use the extension API instead of hard dependencies in OEP core:

- `src/main/java/cn/kuzuanpa/organeffectprocessor/api/extension/`

Use this when:

- another mod’s API/state must be queried in Java
- you need a custom point producer
- you need a custom condition type
- you need a custom execution type
- you need to inject a custom runtime event from Java compat or mixin code

Main extension entry points now include:

- `OepExtensionApi.registerPointProducer(...)`
- `OepExtensionApi.registerConditionHandler(...)`
- `OepExtensionApi.registerPointExecutor(...)`
- `RuntimeEffectService.fireEvent(...)` with `OepRuntimeEvent`

Compat rule:

- put external mod dependencies in a compat submod, not the main OEP mod

## 7. Test quickly

1. `./gradlew compileJava`
2. install the organ in a valid body part
3. close the OrganAPI menu to trigger recompute
4. use `effect_point_viewer`
5. verify:
   - static points appear
   - runtime points appear when events fire
   - executions consume/use the expected points
   - skills appear in wheel/cast flow if granted
   - tooltip / hover / potential-effect viewer output matches the organ design

## Good sample files to copy from

- `wonder_brain` - simplest static attribute grant
- `wonder_brain_v2` - `use_item -> grant_items`
- `wonder_heart` - `eat -> apply_mob_effect`
- `wonder_leg_muscle` - `move -> heal`
- `wonder_tendon` - `attack -> runtime charge`
- `wonder_lung` - `slot_index -> skill`
- `wonder_eye_of_storm` - `weather + time -> use_item -> night_vision`
- `wonder_biome_core` - `biome` / `biome_tag`
- `wonder_dimension_core` - `dimid`
- `wonder_light_core` - `lightlevel`
- `wonder_footing_core` - `stepon` / `block_tag`
- `wonder_guard_core` - `attacked` / `health_loss`
- `wonder_hunter_core` - `kill`
- `wonder_drifter_core` - `biome_change`
- `wonder_warp_core` - `dimension_change`
- `wonder_taunt_core` - `taunt`

## Gotchas

- do not use the old `condition/limit/points` style for new content
- avoid player attributes that do not actually exist on players
- keep item id, JSON filename, and OepItems registration aligned
- if an effect feels inconsistent, test once with menu-close recompute and once with viewer-triggered recompute to separate event bugs from display bugs
