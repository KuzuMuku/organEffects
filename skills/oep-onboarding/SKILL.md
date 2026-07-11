---
name: oep-onboarding
description: Quickly recover the Organ Effects workspace context: organ JSON effect parsing, point recomputation, capability/state, point viewer, active skill flow, networking, and build commands.
---

# Organ Effects workspace onboarding

Use this when a new agent needs to quickly understand this Forge 1.20.1 addon mod before editing. Stay inside this project directory and the `../organ*` directories; other directories are unrelated.

## First commands

From the project root:

```bash
./gradlew compileJava
```

Optional source map:

```bash
find src/main/java/cn/kuzuanpa/organeffects -type f | sort
```

## Project goal

organEffectProcessor (OrganEffects) is a bridge mod on top of OrganAPI. It reads embedded `effects[]` blocks from organ definition JSON, evaluates installed organs on entities, aggregates the results into a generic `Map<String, Long>`, and converts those points into runtime effects such as:

- vanilla attribute modifiers
- available active skills
- player-facing debug output (point viewer)
- client skill input + radial wheel selection

Core idea:

- OrganAPI owns anatomy, organ slots, menus, and organ storage.
- OrganEffects owns interpretation of organ `effects[]` payloads.
- Runtime truth is an effect-point map like:
  - `attribute:minecraft:luck -> 1`
  - `skill:organeffects:wonder_sight -> 2`

## Layout to read first

- Mod bootstrap: `src/main/java/cn/kuzuanpa/organeffects/OrganEffectProcessorMod.java`
- Embedded effect schema:
  - `api/EffectDefinition.java`
  - `common/data/OrganEffectData.java`
- Runtime recomputation:
  - `common/effect/EffectRecalculationService.java`
  - `common/event/ServerEventHandler.java`
- Capability/state:
  - `common/capability/IEffectHolder.java`
  - `common/capability/EffectHolderProvider.java`
  - `common/capability/EffectPointMap.java`
- Attribute and point presentation:
  - `common/sync/AttributeSyncer.java`
  - `common/point/EffectPointTextHelper.java`
  - `common/item/EffectPointViewerItem.java`
- Active skill flow:
  - `common/skill/SkillDefinition.java`
  - `common/skill/SkillManager.java`
  - `common/network/OrganEffectsNetwork.java`
  - `common/network/CastSkillC2SPacket.java`
  - `common/network/CastSelectedSkillC2SPacket.java`
  - `common/network/SyncSkillsS2CPacket.java`
  - `client/input/OrganEffectsKeyMappings.java`
  - `client/input/SkillKeyHandler.java`
  - `client/screen/SkillWheelScreen.java`
- Item registration:
  - `common/item/OrganEffectsOrganItem.java`
  - `common/registry/OrganEffectsItems.java`
- Resources:
  - `src/main/resources/data/organeffects/organapi/organs/*.json`
  - `src/main/resources/data/organapi/tags/items/organs.json`
  - `src/main/resources/assets/organeffects/lang/en_us.json`

## Relationship with OrganAPI

OrganEffects depends on OrganAPI’s anatomy and menu lifecycle.

Critical OrganAPI integration points:

- organ definitions are still loaded from `data/<namespace>/organapi/organs/*.json`
- organ items should extend `cn.kuzuanpa.organapi.common.item.OrganItem`
- OrganEffects recalculates after OrganAPI posts `OrganStateCommittedEvent` when organ menus close
- OrganEffects queries installed organs via `OrganQueryService.getInstalledOrganPositions(entity)`

## Effect JSON model

OrganEffects reads `effects[]` blocks embedded inside organ JSON files under `organapi/organs`.

Current internal model:

- `conditions`: AND-list of condition objects such as `static`, `slot_index`, `distance_to_edge`, `weather`, `time`, `has_organ`, `biome`, `dimid`, `lightlevel`, `stepon`
- `grants`: static point grants produced during recomputation
- `events`: runtime triggers that mutate source/runtime points, including `move`, `attack`, `attacked`, `health_loss`, `kill`, `biome_change`, `dimension_change`, `eat`, `mine`, `use_item`
- `executions`: point-driven effects that read or consume points, including built-ins like `apply_mob_effect`, `heal`, `grant_items`, and `taunt`

Events and executions should stay decoupled through the point pool whenever possible. Treat events as point mutation ingress; visible behavior should generally happen in `executions` or Java extension code that writes points.

Typical example:

```json
"effects": [
  {
    "conditions": [{ "type": "static" }],
    "grants": [
      { "type": "attribute", "attribute": "luck", "amount": 1 }
    ],
    "events": [],
    "executions": []
  },
  {
    "conditions": [
      { "type": "weather", "value": "rain" },
      { "type": "time", "mode": "night" }
    ],
    "grants": [
      { "type": "attribute", "attribute": "movement_speed", "amount": 1 }
    ],
    "events": [],
    "executions": []
  }
]
```

Supported condition semantics today:

- `static`
- `slot_index`
- `distance_to_edge`
- `weather`
- `time`
- `has_organ`

Position checks use the 0-based organ slot index returned by OrganAPI.

## Runtime data flow

1. Organ menus close in OrganAPI.
2. OrganAPI posts `OrganStateCommittedEvent`.
3. `ServerEventHandler` calls `EffectRecalculationService.recompute(target)`.
4. OrganEffects walks installed organs via `OrganQueryService.getInstalledOrganPositions(entity)`.
5. Matching grants are accumulated into an `EffectPointMap`.
6. The final `Map<String, Long>` is saved into `IEffectHolder`.
7. For players:
   - `AttributeSyncer` applies attribute modifiers
   - `SkillManager` updates skill levels and selected skill
   - `OrganEffectsNetwork.syncSkills(...)` mirrors current skill state to client

The point viewer also forces a recompute on use so chat output reflects current installed organs immediately.

Important source-model note:

- `source: "self"` resolves to an organ-instance-local source tag
- event writes and executions check `runtime:*` points first, then pooled source-backed points
- static recompute-owned instance sources are rebuilt during recompute, but event-earned `organ-instance:.../event/...` sources should persist until consumed/cleared

## Capability/state

`IEffectHolder` stores two categories of state:

- current effect points: `Map<String, Long>`
- currently selected active skill id

`EffectHolderProvider` persists both into entity persistent data.

Current attachment rule:

- attached to `LivingEntity`

## Attribute application rules

`AttributeSyncer` is responsible only for OrganEffects-managed modifiers.

Important rule:

- OrganEffects should not mutate vanilla/base attribute values directly
- it uses stable UUID-backed `AttributeModifier`s keyed by `attribute:<id>` instead

This avoids clobbering other mods’ values or the player’s natural base stats.

## Active skill system

Current user flow:

- skill key: `R`
- short press (< about 0.5s / 10 ticks): cast currently selected skill
- long press: open `SkillWheelScreen`
- release after wheel selection: send chosen skill to server

Current networking:

- `CastSelectedSkillC2SPacket` = cast current selected skill
- `CastSkillC2SPacket` = cast a specific chosen skill
- `SyncSkillsS2CPacket` = push available skill levels + selected skill to client

Current default sample skills registered in `SkillManager.registerDefaults()`:

- `organeffects:wonder_sight`
- `organeffects:water_breathing`
- `organeffects:double_jump`

Current sample effects are still basic/placeholder-friendly:

- wonder sight -> night vision
- water breathing -> water breathing effect
- double jump -> currently represented by jump boost, not a real second-jump movement mechanic yet

## Point viewer / localization

`EffectPointViewerItem` is the main debug tool for inspecting the current aggregated map.

Behavior:

- right click on server
- force recompute
- print grouped chat output
- groups currently by point type, e.g. `attribute` and `skill`
- point names are localized through `EffectPointTextHelper`
- hover text shows descriptions from language keys

Translation key conventions:

- point name: `point.organeffects.<type>.<namespace>.<path>`
- point description: `point.organeffects.<type>.<namespace>.<path>.desc`
- point group header: `message.organeffects.points.group.<type>`

If a translation is missing, helper code falls back to raw key text rather than failing.

## Items and organ tagging

OrganEffects-owned organ items are registered in `OrganEffectsItems` and implemented through `OrganEffectsOrganItem`.

Important placement requirement:

- items must be listed in `src/main/resources/data/organapi/tags/items/organs.json`

Without that tag membership, OrganAPI body parts that accept `organapi:organs` will reject the item.

## Build and run notes

Verified compile command:

```bash
./gradlew compileJava
```

This project depends on the local OrganAPI jar from `../organAPI/build/libs`, so if the dependency gets stale, rebuild OrganAPI first:

```bash
cd ../organAPI && ./gradlew compileJava jar
```

Then re-run OrganEffects compile.

If you changed an OrganEffects public Java type or method signature and a sibling compat module like `organKubejs` still compiles against the old API, refresh OrganEffects's local flat-dir artifact first:

```bash
./gradlew devJar
```

Then rebuild the dependent module. This is the fix when the source here is correct but the other project still resolves an older class shape from `../organEffects/build/libs`.

## Common edit patterns

### Adding or editing organ effects

1. Edit organ JSON under `src/main/resources/data/<namespace>/organapi/organs/`.
2. Keep item id, valid parts, and organ tag membership aligned.
3. Use `effects[]` with `conditions / grants / events / executions`.
4. Rebuild and use the effect point viewer to confirm the new map output.

### Adding a new point type

1. Ensure the effect parser emits `type:id` keys.
2. Extend any runtime consumers that care about the new type.
3. Add translation keys for name/description/group heading.
4. Confirm the point viewer still renders cleanly.

### Adding a new active skill

1. Add a grant in organ JSON with `type: skill`.
2. Register the skill in `SkillManager.registerDefaults()` or a future registry.
3. Add localized name/description keys.
4. Implement the server-side effect in `SkillManager.applySkillEffect(...)` or extracted execution helpers.
5. Verify the skill appears in the radial wheel and can be cast from client input.

### Adjusting recomputation behavior

- Prefer changing `EffectRecalculationService`; it is the shared recompute entrypoint.
- Keep `ServerEventHandler` lightweight and event-oriented.
- If chat output seems stale, inspect whether recompute ran and whether OrganAPI actually emitted `OrganStateCommittedEvent`.

## Gotchas

- OrganEffects is tightly coupled to the current OrganAPI workspace; if OrganAPI changes its JSON schema or organ/menu lifecycle, re-check integration points.
- The point viewer is not just a display tool; it currently triggers recomputation, so it can hide stale-cache bugs if you only test through the item.
- `double_jump` is not a real movement-based double jump yet; treat it as an incomplete sample skill.
- The skill wheel and networking exist now, but client art/resources are still minimal.
- If an organ grants no visible points, verify all of:
  - organ item is tagged in `organapi:organs`
  - organ JSON path is correct
  - `effects[]` parsed successfully
  - trigger conditions actually match slot position
  - recomputation ran after install/remove/menu close
