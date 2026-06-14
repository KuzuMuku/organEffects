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
- you need a custom execution type

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

## Good sample files to copy from

- `wonder_heart.json` - point-bridge regen pulse + conditional grants
- `wonder_lung.json` - runtime mining momentum + execution buff
- `wonder_leg_muscle.json` - movement-generated counters/runtime pulses
- `wonder_eye_of_storm.json` - multi-stage conditional organ example

## Gotchas

- do not use the old `condition/limit/points` style for new content
- avoid player attributes that do not actually exist on players
- keep item id, JSON filename, and OepItems registration aligned
- if an effect feels inconsistent, test once with menu-close recompute and once with viewer-triggered recompute to separate event bugs from display bugs
