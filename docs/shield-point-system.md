# Shield Point System

This document defines the minimum shield-point contract for OrganEffects.

## Goals

- Keep shield logic separate from normal points.
- Let Java handle only damage consumption.
- Let scripts own capacity, recharge, and shield identity.
- Support multiple shield sources without cross-system bleeding.

## Point model

- Shield points use the `shield:<id>` key namespace.
- `amount` is the current shield value.
- Maximum capacity is script-owned and must not be stored in Java config.
- Shield points may be written and read like other point pools.

Example:

```json
{
  "type": "shield",
  "id": "organeffects:quantum_barrier",
  "amount": 20,
  "config": {
    "priority": 0,
    "damage_types": [],
    "damage_type_whitelist": false,
    "overflow_mode": "spill",
    "on_hit_runtime": "",
    "on_break_runtime": ""
  }
}
```

## Java responsibilities

- Collect all active `shield:*` pools before health damage is applied.
- Sort shield pools by `priority` descending.
- Randomly pick one pool among equal priority shields.
- Apply shield damage consumption only.
- Write remaining damage back to the hurt event.
- Emit runtime points when configured.

## Script responsibilities

- Maintain shield capacity.
- Recharge shield values.
- Decide when shield pools are created or removed.
- Attach source-specific behavior and styling.

## Config

- `priority`
  - Default: `0`
  - Higher values are consumed first.
- `damage_types`
  - Default: `[]`
  - Empty means unrestricted.
- `damage_type_whitelist`
  - Default: `false`
  - `false` means blacklist, `true` means whitelist.
- `overflow_mode`
  - Default: `spill`
  - `spill`: leftover damage passes through.
  - `block`: if the shield is present, the hit is fully negated and the shield breaks.
- `on_hit_runtime`
  - Default: empty
  - No runtime point is emitted if empty.
- `on_break_runtime`
  - Default: empty
  - No runtime point is emitted if empty.

## Recommended runtime events

- `shield_damage`
  - Includes `shield_id`, `absorbed_amount`, `remaining_after`
- `shield_break`
  - Includes `shield_id`, `overflow_damage`

