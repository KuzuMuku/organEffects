---
name: oep-organ-authoring
description: Author Organ Effect Processor organs in JSON using the built-in condition, event, and execution catalog, with config-driven variants and point-bridge patterns.
---

# OrganEffects Organ Authoring

Use this when adding or editing OrganEffects organ JSON and you want to stay in data only, without Java.

## Core rules

- Keep `type` stable and put variants in `config`.
- `conditions` gate the effect.
- `grants` create passive points.
- `events` add or consume points only.
- `executions` spend points to produce visible effects.
- Prefer `source: "self"` for per-organ-instance runtime chains.
- Do not introduce `grant_ability` or `unlock_skill`.

## Workflow

1. Pick the smallest built-in condition/event/execution that fits.
2. Put all mode-like differences into `config`.
3. Use point bridges instead of immediate side effects when possible.
4. Add localization entries for any new type or config label used in tooltips.
5. Test with `./gradlew compileJava`, install the organ, then verify with `effect_point_viewer`.

## When to use which pattern

- Passive bonuses: use `static` conditions plus `grants`.
- Triggered bursts: use runtime `events` that create `runtime:*` points, then `executions` that consume them.
- Stateful organs: use `counter:*` for accumulation and `runtime:*` for short-lived tokens.
- Environment-sensitive organs: use conditions like health, hunger, air, biome, weather, time, movement, equipment, and nearby entities.

## Pitfalls

- Do not hardcode gameplay in `events` if an execution can do it later.
- Do not split one concept into many `type` values when `config` is enough.
- Avoid shared source tags unless the effect should be global across matching organs.
- Remember that `effect_point_viewer` forces recompute, so it can hide stale runtime bugs.

## Reference

See [capabilities.md](references/capabilities.md) for the current built-in condition, event, and execution tables.
