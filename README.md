# Organ Effects

Organ Effects (OrganEffects) is a Forge 1.20.1 addon built on top of the sibling `../organAPI` workspace. OrganAPI owns anatomy, body parts, organ storage, and menus; OrganEffects interprets organ `effects[]` blocks and turns them into a layered point pool.

## Core model

OrganEffects keeps organ logic dependency-light by using points as the bridge between triggers and effects:

- `conditions` + `grants` produce static points during recomputation.
- `events` respond to runtime player actions and mutate runtime/source points.
- `executions` read or consume points and produce visible effects.
- Java extension APIs let compat submods add custom point producers/executors without adding optional dependencies to OrganEffects itself.

Example point keys:

- `attribute:minecraft:generic.luck`
- `skill:organeffects:wonder_sight`
- `counter:organeffects:charge`
- `runtime:organeffects:storm_insight_token`
- `shield:organeffects:quantum_barrier`

## Build

From this repository:

```bash
./gradlew compileJava
```

If the local OrganAPI dependency is stale, rebuild it first:

```bash
cd ../organAPI && ./gradlew compileJava jar
```

Then rebuild OrganEffects.

## Important directories

- Java source: `src/main/java/cn/kuzuanpa/organeffects/`
- Public API model: `src/main/java/cn/kuzuanpa/organeffects/api/`
- Java extension API: `src/main/java/cn/kuzuanpa/organeffects/api/extension/`
- Runtime effect flow: `src/main/java/cn/kuzuanpa/organeffects/common/effect/`
- Capability point storage: `src/main/java/cn/kuzuanpa/organeffects/common/capability/`
- Organ item registration: `src/main/java/cn/kuzuanpa/organeffects/common/registry/OrganEffectsItems.java`
- Organ JSON: `src/main/resources/data/organeffects/organapi/organs/`
- Point config JSON: `src/main/resources/data/<namespace>/point_config/*.json`
- Organ item placement tag: `src/main/resources/data/organapi/tags/items/organs.json`
- Localizations: `src/main/resources/assets/organeffects/lang/en_us.json`

## Runtime flow

1. OrganAPI commits an organ state change.
2. `ServerEventHandler` calls `EffectRecalculationService.recompute(...)`.
3. OrganEffects evaluates installed organ JSON effects and registered Java point producers.
4. Results are written into `IEffectHolder` source layers.
5. Attribute modifiers, skill availability, and runtime executors are updated.
6. `EffectPointViewerItem` can force recompute and print point groups for debugging.

Source model and execution order:

- `source: "self"` resolves to an organ-instance-local source tag, so event-earned non-runtime points stay associated with the specific installed organ instance.
- Executions check `runtime:*` points first, then fall back to pooled source-backed points with the same key.
- Static recompute-owned instance sources are rebuilt during recompute; event-earned `organ-instance:.../event/...` sources are expected to persist until consumed or explicitly cleared.
- `effect_point_viewer` is both a display tool and a recompute trigger, so it remains useful for debugging but can still hide stale recompute bugs if you only test through the item.
- `shield:*` points are consumed before health loss in `LivingHurtEvent`; scripts remain responsible for shield capacity and recharge.

## Java extension API

Use the extension API when JSON alone is not enough, especially for compat submods that depend on other mods.

Key classes:

- `OrganEffectsPointApi` - direct point read/write access for compat mods
- `OrganEffectsExtensionApi`
- `PointProducer`
- `ConditionHandler`
- `PointExecutor`
- `OrganEffectsRuntimeEvent`
- `SkillExecutor`

Typical compat pattern:

```java
OrganEffectsExtensionApi.registerPointProducer(new CreateStressProducer());
OrganEffectsExtensionApi.registerConditionHandler("compatmod:charged_dimension", new CreateChargedDimensionCondition());
OrganEffectsExtensionApi.registerPointExecutor(new CreateChargeBurstExecutor());
RuntimeEffectService.fireEvent(player, OrganEffectsRuntimeEvent.builder("compatmod:reactor_exploded", player).build());
SkillManager.registerSkill(...);
SkillManager.registerSkillExecutor("compatmod:rotational_overdrive", (player, level) -> {
    // external-mod-aware behavior lives in the compat submod
    return true;
});
```

The main OrganEffects mod should not import Create or other optional mod APIs. A compat submod should depend on OrganEffects plus the external mod, inspect external state, then contribute/consume OrganEffects points.

## Sample organs

Current sample organs live under `src/main/resources/data/organeffects/organapi/organs/`.

Representative focused samples now include:

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

## Developer docs

- Effect JSON guide: `docs/organ-effect-json-guide.md`
- Shield points: `docs/shield-point-system.md`
- Skills: `skills`

## Point Config

OrganEffects supports optional point-level config files under `data/<namespace>/point_config/*.json`.

Current supported fields:

- `point`: full point key such as `organ_stat:organeffects:muscular_strength`
- `display_name_key`: optional translation key used to override the default display-name lookup for this point
- `sync_to_client`: whether this point should be included in the lightweight client sync cache
- `priority`: optional shield priority for `shield:*` points, default `0`
- `damage_types`: optional shield damage type list
- `damage_type_whitelist`: when `true`, `damage_types` is treated as a whitelist; otherwise it is a blacklist
- `overflow_mode`: shield overflow mode, default `spill`, optional `block`
- `on_hit_runtime`: optional runtime point emitted when this shield absorbs damage
- `on_break_runtime`: optional runtime point emitted when this shield breaks

Shield-specific meaning:

- `shield:*` points use point config as shield consumption metadata.
- Point config does not define shield capacity, recharge rate, or recharge delay.
- Shield `amount` is always current value.
- `overflow_mode: "spill"` lets leftover damage pass through after the shield is reduced to zero.
- `overflow_mode: "block"` negates the whole hit if the shield is present, then breaks that shield layer.
- `on_hit_runtime` and `on_break_runtime` are the preferred bridge back into script-side behavior.

Example:

```json
{
  "point": "organ_stat:organeffects:muscular_strength",
  "display_name_key": "point.organeffects.organ_stat.organeffects.muscular_strength",
  "sync_to_client": true
}
```

If a point has no config file, OrganEffects keeps its previous default behavior. If `display_name_key` is omitted, OrganEffects keeps the existing default name resolution rules. Point config is intended as an extension point, so more fields can be added later without changing organ JSON structure.

## Quick organ checklist

1. Register an `OrganEffectsOrganItem` in `OrganEffectsItems`.
2. Add `data/organeffects/organapi/organs/<organ>.json`.
3. Add the item ID to `data/organapi/tags/items/organs.json`.
4. Add item/point/skill localization keys.
5. If granting a skill, register skill metadata and a skill executor.
6. Run `./gradlew compileJava`.
7. Install the organ and inspect points with `effect_point_viewer`.
