---
name: kubejs-organ-development
description: Guide KubeJS developers to add OrganAPI/OEP-compatible organs using plain items plus organ JSON/effects.
---

# KubeJS organ development

Use this for adding OEP-compatible organs from KubeJS without writing a custom Java `OrganItem`.

## What this flow means

- KubeJS registers a plain item.
- OrganAPI resolves the organ from `data/<namespace>/organapi/organs/<id>.json`.
- OEP reads that organ's `effects[]`.
- The item works as an organ only if that `item` id maps to exactly one organ definition.

## Minimal recipe

1. Register the item in KubeJS and keep the final id stable, for example `kubejs:storm_organ`.
2. Create `data/<namespace>/organapi/organs/<organ_id>.json` with matching `"item": "<namespace>:<item_name>"`.
3. Add the item to `data/organapi/tags/items/organs.json` if you want OrganAPI slots to accept it.
4. Put all gameplay logic in `effects[]`:
   - `conditions` gate the effect
   - `grants` create passive points
   - `events` mutate runtime/counter points
   - `executions` consume points and produce visible effects
5. Add language entries for the item and any point/skill keys you introduce.

## Keep in mind

- Do not map two organ JSON files to the same `item` id.
- Matching is by item id, not NBT or metadata.
- Use `source: "self"` when event-earned points should stay local to one installed organ instance.
- If you need custom logic from another mod or non-JSON behavior, use the Java extension API instead of forcing it into KubeJS.

## Quick test loop

1. `cd ../organAPI && ./gradlew compileJava jar`
2. `./gradlew compileJava`
3. Install the organ in-game.
4. Close the organ menu to trigger recompute.
5. Check tooltip output and `effect_point_viewer`.
6. Confirm passive grants, runtime point changes, and execution results.

## Common failures

- Organ installs but acts like a normal item: wrong JSON path, wrong `item` id, or duplicate mapping.
- Item cannot be placed: not in `organapi:organs`, or `valid_parts` does not match the slot.
- Tooltip is missing OEP info: `effects[]` did not parse, or the point/skill localization keys are missing.

## See also

- `docs/organ-effect-json-guide.md`
- `skills/oep-organ-authoring/SKILL.md`
- `skills/quick-organ-development/SKILL.md`
- `src/main/resources/data/organeffectprocessor/organapi/organs/wonder_taunt_core.json`
- `../organAPI/docs/organ-data-format.md`
