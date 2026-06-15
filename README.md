# Organ Effect Processor

Organ Effect Processor (OEP) is a Forge 1.20.1 addon built on top of the sibling `../organAPI` workspace. OrganAPI owns anatomy, body parts, organ storage, and menus; OEP interprets organ `effects[]` blocks and turns them into a layered point pool.

## Core model

OEP keeps organ logic dependency-light by using points as the bridge between triggers and effects:

- `conditions` + `grants` produce static points during recomputation.
- `events` respond to runtime player actions and mutate runtime/source points.
- `executions` read or consume points and produce visible effects.
- Java extension APIs let compat submods add custom point producers/executors without adding optional dependencies to OEP itself.

Example point keys:

- `attribute:minecraft:luck`
- `skill:organeffectprocessor:wonder_sight`
- `counter:organeffectprocessor:charge`
- `runtime:organeffectprocessor:storm_insight_token`

## Build

From this repository:

```bash
./gradlew compileJava
```

If the local OrganAPI dependency is stale, rebuild it first:

```bash
cd ../organAPI && ./gradlew compileJava jar
```

Then rebuild OEP.

## Important directories

- Java source: `src/main/java/cn/kuzuanpa/organeffectprocessor/`
- Public API model: `src/main/java/cn/kuzuanpa/organeffectprocessor/api/`
- Java extension API: `src/main/java/cn/kuzuanpa/organeffectprocessor/api/extension/`
- Runtime effect flow: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/effect/`
- Capability point storage: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/capability/`
- Organ item registration: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/registry/OepItems.java`
- Organ JSON: `src/main/resources/data/organeffectprocessor/organapi/organs/`
- Organ item placement tag: `src/main/resources/data/organapi/tags/items/organs.json`
- Localizations: `src/main/resources/assets/organeffectprocessor/lang/en_us.json`

## Runtime flow

1. OrganAPI commits an organ state change.
2. `ServerEventHandler` calls `EffectRecalculationService.recompute(...)`.
3. OEP evaluates installed organ JSON effects and registered Java point producers.
4. Results are written into `IEffectHolder` source layers.
5. Attribute modifiers, skill availability, and runtime executors are updated.
6. `EffectPointViewerItem` can force recompute and print point groups for debugging.

Source model and execution order:

- `source: "self"` resolves to an organ-instance-local source tag, so event-earned non-runtime points stay associated with the specific installed organ instance.
- Executions and point-based event actions check `runtime:*` points first, then fall back to pooled source-backed points with the same key.
- Static recompute-owned instance sources are rebuilt during recompute; event-earned `organ-instance:.../event/...` sources are expected to persist until consumed or explicitly cleared.
- `effect_point_viewer` is both a display tool and a recompute trigger, so it remains useful for debugging but can still hide stale recompute bugs if you only test through the item.

## Java extension API

Use the extension API when JSON alone is not enough, especially for compat submods that depend on other mods.

Key classes:

- `OepExtensionApi`
- `PointProducer`
- `PointExecutor`
- `SkillExecutor`

Typical compat pattern:

```java
OepExtensionApi.registerPointProducer(new CreateStressProducer());
OepExtensionApi.registerPointExecutor(new CreateChargeBurstExecutor());
SkillManager.registerSkill(...);
SkillManager.registerSkillExecutor("compatmod:rotational_overdrive", (player, level) -> {
    // external-mod-aware behavior lives in the compat submod
    return true;
});
```

The main OEP mod should not import Create or other optional mod APIs. A compat submod should depend on OEP plus the external mod, inspect external state, then contribute/consume OEP points.

## Sample organs

Current sample organs live under `src/main/resources/data/organeffectprocessor/organapi/organs/`.

Representative focused samples now include:

- `wonder_brain` - simplest static attribute grant
- `wonder_brain_v2` - `use_item -> grant_items`
- `wonder_heart` - `eat -> apply_mob_effect`
- `wonder_leg_muscle` - `move -> heal`
- `wonder_tendon` - `attack -> modify_damage`
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
- Skills: `skills`

## Quick organ checklist

1. Register an `OepOrganItem` in `OepItems`.
2. Add `data/organeffectprocessor/organapi/organs/<organ>.json`.
3. Add the item ID to `data/organapi/tags/items/organs.json`.
4. Add item/point/skill localization keys.
5. If granting a skill, register skill metadata and a skill executor.
6. Run `./gradlew compileJava`.
7. Install the organ and inspect points with `effect_point_viewer`.
