---
name: workspace-context
description: Quick map of the Organ Effects workspace, key entrypoints, docs, and build flow.
---

# Organ Effects workspace context

Use this when you need a fast mental map of the repository before editing, documenting, or building a compat submod.

## First commands

From the project root:

```bash
./gradlew compileJava
```

If OrganEffects fails because the local OrganAPI dependency is stale:

```bash
cd ../organAPI && ./gradlew compileJava jar
```

Then return and re-run OrganEffects compile.

If a sibling compat/addon module still sees an old OrganEffects or OrganAPI public signature after you changed source here, refresh the flat-dir dependency jar first:

```bash
./gradlew devJar
```

Then rebuild the dependent module so it picks up the refreshed `build/libs` artifact.

## Project purpose

OrganEffects sits on top of OrganAPI and interprets organ `effects[]` JSON into a player/entity point pool.

Core architecture:

- JSON `conditions` + `grants` -> static point recomputation
- JSON `events` -> runtime point mutations
- JSON `executions` -> consume/use points and produce visible effects
- other mods may extend production/execution through Java registration APIs instead of adding optional hard dependencies to OrganEffects itself

## Read these files first

- Bootstrap: `src/main/java/cn/kuzuanpa/organeffects/OrganEffectProcessorMod.java`
- JSON schema: `src/main/java/cn/kuzuanpa/organeffects/api/EffectDefinition.java`
- JSON loader: `src/main/java/cn/kuzuanpa/organeffects/common/data/OrganEffectData.java`
- Static recompute: `src/main/java/cn/kuzuanpa/organeffects/common/effect/EffectRecalculationService.java`
- Runtime events: `src/main/java/cn/kuzuanpa/organeffects/common/effect/RuntimeEffectService.java`
- Runtime executions: `src/main/java/cn/kuzuanpa/organeffects/common/effect/RuntimePointExecutor.java`
- Capability state: `src/main/java/cn/kuzuanpa/organeffects/common/capability/IEffectHolder.java`
- Extension API: `src/main/java/cn/kuzuanpa/organeffects/api/extension/`
- Skills: `src/main/java/cn/kuzuanpa/organeffects/common/skill/SkillManager.java`
- Organ samples: `src/main/resources/data/organeffects/organapi/organs/`
- Lang keys: `src/main/resources/assets/organeffects/lang/en_us.json`

## Important directories

- `src/main/java/.../api` - public data model and extension-facing interfaces
- `src/main/java/.../common/effect` - recompute, runtime event, and execution flow
- `src/main/java/.../common/skill` - skill metadata, selection, and cast behavior
- `src/main/resources/data/organeffects/organapi/organs` - organ JSON definitions
- `src/main/resources/data/organapi/tags/items/organs.json` - required placement tag for OrganEffects organ items
- `docs/organ-effect-json-guide.md` - canonical JSON authoring reference
- `.claude/skills/` - repo-local agent skills

## Relationship with OrganAPI

Stay inside this repo and the sibling `../organAPI` workspace.

Important integration points:

- OrganAPI owns anatomy, organ slots, menus, and organ storage
- OrganEffects reads organ definitions from `data/<namespace>/organapi/organs/*.json`
- OrganEffects recalculates after OrganAPI posts `OrganStateCommittedEvent`
- installed organs are queried through `OrganQueryService.getInstalledOrganPositions(entity)`

## Extension model for submods

Use Java extension APIs when JSON alone is not enough.

Typical reasons:

- reading state from another mod
- translating external energy/resources into OrganEffects points
- adding custom execution behavior for new `executions[].type` values
- registering new active skill behaviors without patching OrganEffects core

Recommended compat pattern:

- main OrganEffects stays dependency-light
- compat submod depends on OrganEffects + external mod
- compat submod registers point producers / condition handlers / point executors in common setup
- compat submod injects custom runtime events through `RuntimeEffectService.fireEvent(...)` when external hooks or mixins observe relevant behavior

## Gotchas

- if an organ item is missing `organapi:organs`, it will not be placeable
- the point viewer forces recompute, so it can hide stale-event bugs if you only test with it
- `source: "self"` resolves to an organ-instance-local source; executions read `runtime:*` first, then pooled source-backed points
- runtime events are point-ingress only; visible effects should usually be implemented through points + `RuntimePointExecutor`, not immediate event-side actions
- README and docs are intended for humans; the skills are the fast path for agents and repeat contributors
