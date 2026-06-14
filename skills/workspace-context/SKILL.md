---
name: workspace-context
description: Quick map of the Organ Effect Processor workspace, key entrypoints, docs, and build flow.
---

# Organ Effect Processor workspace context

Use this when you need a fast mental map of the repository before editing, documenting, or building a compat submod.

## First commands

From the project root:

```bash
./gradlew compileJava
```

If OEP fails because the local OrganAPI dependency is stale:

```bash
cd ../organAPI && ./gradlew compileJava jar
```

Then return and re-run OEP compile.

## Project purpose

OEP sits on top of OrganAPI and interprets organ `effects[]` JSON into a player/entity point pool.

Core architecture:

- JSON `conditions` + `grants` -> static point recomputation
- JSON `events` -> runtime point mutations
- JSON `executions` -> consume/use points and produce visible effects
- other mods may extend production/execution through Java registration APIs instead of adding optional hard dependencies to OEP itself

## Read these files first

- Bootstrap: `src/main/java/cn/kuzuanpa/organeffectprocessor/OrganEffectProcessorMod.java`
- JSON schema: `src/main/java/cn/kuzuanpa/organeffectprocessor/api/EffectDefinition.java`
- JSON loader: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/data/OrganEffectData.java`
- Static recompute: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/effect/EffectRecalculationService.java`
- Runtime events: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/effect/RuntimeEffectService.java`
- Runtime executions: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/effect/RuntimePointExecutor.java`
- Capability state: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/capability/IEffectHolder.java`
- Extension API: `src/main/java/cn/kuzuanpa/organeffectprocessor/api/extension/`
- Skills: `src/main/java/cn/kuzuanpa/organeffectprocessor/common/skill/SkillManager.java`
- Organ samples: `src/main/resources/data/organeffectprocessor/organapi/organs/`
- Lang keys: `src/main/resources/assets/organeffectprocessor/lang/en_us.json`

## Important directories

- `src/main/java/.../api` - public data model and extension-facing interfaces
- `src/main/java/.../common/effect` - recompute, runtime event, and execution flow
- `src/main/java/.../common/skill` - skill metadata, selection, and cast behavior
- `src/main/resources/data/organeffectprocessor/organapi/organs` - organ JSON definitions
- `src/main/resources/data/organapi/tags/items/organs.json` - required placement tag for OEP organ items
- `docs/organ-effect-json-guide.md` - canonical JSON authoring reference
- `.claude/skills/` - repo-local agent skills

## Relationship with OrganAPI

Stay inside this repo and the sibling `../organAPI` workspace.

Important integration points:

- OrganAPI owns anatomy, organ slots, menus, and organ storage
- OEP reads organ definitions from `data/<namespace>/organapi/organs/*.json`
- OEP recalculates after OrganAPI posts `OrganStateCommittedEvent`
- installed organs are queried through `OrganQueryService.getInstalledOrganPositions(entity)`

## Extension model for submods

Use Java extension APIs when JSON alone is not enough.

Typical reasons:

- reading state from another mod
- translating external energy/resources into OEP points
- adding custom execution behavior for new `executions[].type` values
- registering new active skill behaviors without patching OEP core

Recommended compat pattern:

- main OEP stays dependency-light
- compat submod depends on OEP + external mod
- compat submod registers point producers / point executors / skill executors in common setup

## Gotchas

- if an organ item is missing `organapi:organs`, it will not be placeable
- the point viewer forces recompute, so it can hide stale-event bugs if you only test with it
- current damage-modifying event actions still run in event context; non-damage effects should go through point-pool bridging
- README and docs are intended for humans; the skills are the fast path for agents and repeat contributors
