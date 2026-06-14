---
name: kubejs-organ-development
description: Guide KubeJS developers to add OrganAPI/OEP-compatible organs using plain items plus organ JSON/effects.
---

# KubeJS organ development

Use this when a KubeJS developer wants to add organs without writing a custom Java `OrganItem` subclass.

This repo now supports a **plain item + organ JSON** workflow:

- KubeJS (or another mod) registers the item
- OrganAPI resolves the organ definition by the organ JSON `item` field
- OEP reads `effects[]` from the same organ JSON
- OEP tooltips/effect viewer work as long as the item resolves to a unique organ definition

## Core rule

A plain item becomes an organ when all of these are true:

1. the item exists
2. a JSON exists at `data/<namespace>/organapi/organs/<organ_id>.json`
3. that JSON has `"item": "<namespace>:<item_name>"`
4. no second organ definition points at the same item id

If multiple organ definitions use the same `item`, plain-item fallback is ignored for that item.

## Recommended workflow

### 1. Register the item in KubeJS

Example shape:

```js
StartupEvents.registry('item', event => {
  event.create('storm_organ')
    .displayName('Storm Organ')
})
})
```

The exact KubeJS syntax can vary by version; the important part is the final item id, e.g. `kubejs:storm_organ`.

### 2. Make sure the item is accepted by organ slots

Update the organ item tag so body parts that accept organs also accept this item:

- `data/organapi/tags/items/organs.json`

Example:

```json
{
  "replace": false,
  "values": [
    "kubejs:storm_organ"
  ]
}
```

Important nuance:

- ordinary items may still be placeable in some slot setups
- but if you want this item treated as an actual organ, put it in `organapi:organs`
- body-part restrictions are only meaningful for items recognized as organs

### 3. Create the organ definition JSON

Path:

- `data/<namespace>/organapi/organs/<organ_id>.json`

Example:

```json
{
  "item": "kubejs:storm_organ",
  "valid_parts": ["organapi:head"],
  "size": 1,
  "tooltips": ["A storm-tuned synthetic organ."],
  "tags": ["kubejs", "storm"],
  "effects": [
    {
      "conditions": [
        { "type": "static" }
      ],
      "grants": [
        {
          "type": "attribute",
          "attribute": "luck",
          "amount": 1
        }
      ],
      "events": [
        {
          "type": "use_item",
          "item": "organeffectprocessor:effect_point_viewer",
          "add_points": [
            {
              "type": "runtime",
              "id": "storm_insight_token",
              "amount": 1,
              "duration_ticks": 20
            }
          ],
          "consume_points": [],
          "actions": []
        }
      ],
      "executions": [
        {
          "type": "apply_mob_effect",
          "point_type": "runtime",
          "point_id": "storm_insight_token",
          "effect": "minecraft:night_vision",
          "duration_ticks": 100,
          "amplifier": 0,
          "consume_points": true,
          "max_consume": 1
        }
      ]
    }
  ]
}
```

## What belongs to OrganAPI vs OEP

### OrganAPI fields

These are OrganAPI-owned:

- `item`
- `valid_parts`
- `size`
- `tooltips`
- `tags`

### OEP fields

This is OEP-owned:

- `effects`
  - `conditions`
  - `grants`
  - `events`
  - `executions`

## Effect authoring rules

Preferred architecture:

- `grants` = static/passive points
- `events` = runtime point mutations
- `executions` = consume/use points to produce visible effects

Prefer point-bridge design:

- event produces `counter:*` or `runtime:*`
- execution reads or consumes those points

## What works automatically for KubeJS organs

Once the item resolves to a unique organ definition:

- OrganAPI install/query logic
- OEP recomputation
- OEP runtime events/executions
- effect point viewer
- organ effect tooltip injection

## What does **not** work

### Duplicate item mapping

Do **not** do this:

- `data/a/organapi/organs/foo.json` -> `"item": "kubejs:storm_organ"`
- `data/b/organapi/organs/bar.json` -> `"item": "kubejs:storm_organ"`

Result:

- plain-item fallback becomes ambiguous
- the organ definition will not resolve by item id
- OEP effects/tooltips will not attach reliably

### Stack-specific matching

Current fallback is by **item id only**.

Not supported in this flow:

- NBT-based organ identity
- component-based organ identity
- one item id representing several organ definitions by metadata

If you need that, you need a Java-side extension/change.

## Localization checklist

Update:

- `src/main/resources/assets/organeffectprocessor/lang/en_us.json`

Usually needed:

- `item.kubejs.storm_organ`
- any new point keys:
  - `point.organeffectprocessor.<type>.<namespace>.<path>`
  - `point.organeffectprocessor.<type>.<namespace>.<path>.desc`
- any new skill keys if you grant skills

## Testing checklist

1. `cd ../organAPI && ./gradlew compileJava jar`
2. `./gradlew compileJava`
3. start game / load data
4. confirm the item can be placed in the intended body part
5. close the organ menu to trigger recompute
6. hover the item and confirm OEP effect tooltip appears
7. use `effect_point_viewer`
8. verify:
   - passive grants appear
   - runtime points appear when events fire
   - executions consume/use expected points

## Debugging guide

### Item installs but behaves like a normal item

Check:

- does organ JSON exist at `data/<namespace>/organapi/organs/<id>.json`
- does `item` exactly match the KubeJS item id
- is there another organ JSON using the same item id

### Item cannot be placed where expected

Check:

- is it in `organapi:organs`
- does `valid_parts` include the target body part
- does the body part accept organ-tagged items of that kind

### Tooltip does not show OEP effects

Check:

- does the organ resolve uniquely by item id
- does the organ JSON actually include `effects`
- do the effect point names/descriptions have lang keys if needed

## Representative files

- `docs/organ-effect-json-guide.md`
- `src/main/resources/data/organeffectprocessor/organapi/organs/wonder_heart.json`
- `src/main/resources/data/organeffectprocessor/organapi/organs/wonder_eye_of_storm.json`
- `../organAPI/docs/organ-data-format.md`
