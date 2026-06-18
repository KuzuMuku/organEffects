# OEP Capability Tables

## Common config keys

| Key | Meaning |
|---|---|
| `mode` | Selects how to interpret the same condition or execution type |
| `op` | Comparison operator: `eq`, `ne`, `gt`, `gte`, `lt`, `lte` |
| `value` | Single threshold or target value |
| `min` / `max` | Inclusive range bounds |
| `slot` | Equipment slot, or organ slot context when the condition needs one |
| `state` | Enum-like state selector such as sprinting or underwater |
| `item` / `enchantment` / `effect` / `particle` / `sound` / `block` / `entity` | Concrete registry id |
| `item_tag` / `block_tag` / `entity_tag` | Tag-based filter |
| `radius` | Search radius in blocks |
| `amount` | Magnitude, damage, heal amount, or other numeric payload |
| `duration_ticks` | Lifetime or cooldown duration in ticks |
| `source` | Source layer tag; use `self` for per-organ-instance chains |
| `point_type` / `point_id` | Which point pool entry to read or consume |

## Conditions

| Type | Config | Purpose | Notes |
|---|---|---|---|
| `static` | none | Always true | Passive baseline |
| `health` | `mode`, `op`, `value`, `min`, `max` | Current, missing, percent, or max health | Use one type, vary by `config.mode` |
| `hunger` | `mode`, `op`, `value`, `min`, `max` | Food level or saturation | `mode=saturation` reads saturation |
| `air` | `mode`, `op`, `value` | Air supply or underwater check | `mode=underwater` is boolean |
| `xp` | `mode`, `op`, `value`, `min`, `max` | XP level or total XP | `mode=total` reads total XP |
| `status_effect` | `effect`, `amplifier`, `min_duration`, `max_duration` | Checks active mob effect | Good for potion-gated organs |
| `attribute` | `attribute`, `op`, `value`, `min`, `max` | Compares a living entity attribute | Useful for scaling organs |
| `movement_state` | `state` | Sprinting, sneaking, swimming, fall flying, on ground | Event-like state gate |
| `environment_state` | `state` | In water, underwater, on fire, riding, wet | General environment gate |
| `equipment` | `slot`, `item`, `item_tag`, `empty` | Equipped item checks | Supports mainhand, offhand, armor slots |
| `enchantment` | `slot`, `enchantment`, `op`, `value` | Enchantment level checks | Use on held or worn items |
| `nearby_entity` | `entity`, `entity_tag`, `radius`, `op`, `value`, `min`, `max` | Nearby entity count checks | Good for aura-style effects |
| `moon_phase` | `mode`, `op`, `value` | Moon phase gate | Use named phase modes when possible |
| `biome` | `biome`, `biome_tag` | Biome or biome tag checks | Existing core condition |
| `biome_category` | `value` | Biome category-like gate | Current implementation is compatibility-oriented |
| `dimension_type` | `value` | Dimension type or effects location gate | Use for dimension-locked organs |
| `dimid` | `value` | Dimension id check | Existing core condition |
| `weather` | `value` | Clear, rain, thunder | Existing core condition |
| `time` | `mode`, `op`, `value`, `min`, `max` | Day/night or time-of-day range | Supports wrapped ranges |
| `slot_index` | `op`, `value` | Slot index gate | Zero-based |
| `distance_to_edge` | `edge`, `op`, `value` | Slot-grid edge distance | `top`, `bottom`, `left`, `right` |
| `has_organ` | `scope`, `body_part`, `slot`, `organ` | Organ presence gate | Supports symmetric placement |
| `lightlevel` | `op`, `value` | Local brightness check | Good for light-sensitive organs |
| `stepon` | `block`, `block_tag` | Standing-on block gate | Recompute-based, not per-tick precise |

## Events

| Type | Common fields | Purpose | Notes |
|---|---|---|---|
| `move` | `distance`, `source`, `add_points`, `consume_points`, `actions` | Movement-driven point mutation | Supports distance batching |
| `attack` | `source`, `add_points`, `consume_points`, `actions` | Attacker-side combat event | Reads attacker context |
| `attacked` | `source`, `add_points`, `consume_points`, `actions` | Victim-side combat event | Triggered on being hurt |
| `health_loss` | `source`, `add_points`, `consume_points`, `actions` | Actual damage taken | Works for non-attack damage too |
| `kill` | `source`, `add_points`, `consume_points`, `actions` | Kill event | Triggered for the attacker |
| `biome_change` | `add_points`, `consume_points`, `actions` | Biome transition | Based on player tick state |
| `dimension_change` | `add_points`, `consume_points`, `actions` | Dimension transition | Useful for travel organs |
| `eat` | `food_only`, `item`, `item_tag`, `add_points`, `consume_points`, `actions` | Eating / consuming items | `food_only=true` filters edible items |
| `mine` | `block`, `block_tag`, `add_points`, `consume_points`, `actions` | Block break interaction | Tool or block filtered |
| `use_item` | `item`, `item_tag`, `add_points`, `consume_points`, `actions` | Right-click item use | Broad item trigger |
| `jump` | `add_points`, `consume_points`, `actions` | Jump event | Good for mobility organs |
| `land` | `add_points`, `consume_points`, `actions` | Landing after movement | Useful for impact or recovery organs |
| `sprint_start` / `sprint_stop` | `add_points`, `consume_points`, `actions` | Sprint state changes | Pairs with movement-state conditions |
| `sneak_start` / `sneak_stop` | `add_points`, `consume_points`, `actions` | Sneak state changes | Good for stealth organs |
| `swim_start` / `swim_stop` | `add_points`, `consume_points`, `actions` | Swim state changes | Water traversal organs |
| `enter_water` / `leave_water` | `add_points`, `consume_points`, `actions` | Water boundary changes | Environment gating |
| `take_damage` | `add_points`, `consume_points`, `actions` | Damaged entity | Damage source info is available in extra data |
| `deal_damage` | `add_points`, `consume_points`, `actions` | Successful damage dealt | Attacker-side mirror of take damage |
| `projectile_hit` | `add_points`, `consume_points`, `actions` | Projectile contact | Use for ranged organs |
| `block_place` | `add_points`, `consume_points`, `actions` | Block placement | Good for builder-style organs |
| `item_craft` | `add_points`, `consume_points`, `actions` | Craft completion | Recipe-driven effects |
| `item_smelt` | `add_points`, `consume_points`, `actions` | Smelting completion | Furnace-style rewards |
| `item_repair` | `add_points`, `consume_points`, `actions` | Repair action | Can drive maintenance organs |
| `item_enchant` | `add_points`, `consume_points`, `actions` | Enchant completion | Enchanter-style rewards |
| `fish_catch` | `add_points`, `consume_points`, `actions` | Fishing rewards | Fishing-specific organs |
| `sleep` | `add_points`, `consume_points`, `actions` | Sleep event | Rest or recovery organs |
| `respawn` | `add_points`, `consume_points`, `actions` | Player respawn | Death-recovery hooks |
| `consume_item` | `add_points`, `consume_points`, `actions` | Item consumed | Broader than `eat` |
| `equip_item` / `unequip_item` | `add_points`, `consume_points`, `actions` | Equipment change | Useful for gear-triggered organs |
| `critical_hit` | `add_points`, `consume_points`, `actions` | Critical strike | Good for burst builds |
| `shield_block` | `add_points`, `consume_points`, `actions` | Shield block event | Defensive playstyles |
| `parry` | `add_points`, `consume_points`, `actions` | Parry-like block event | Follows shield block path |

## Executions

| Type | Main fields | Purpose | Notes |
|---|---|---|---|
| `apply_mob_effect` | `point_type`, `point_id`, `effect`, `duration_ticks`, `amplifier`, `consume_points`, `max_consume` | Apply potion effect | Core buff/debuff output |
| `heal` | `point_type`, `point_id`, `amount`, `consume_points`, `max_consume` | Heal the player | Consumes points before healing |
| `grant_items` | `point_type`, `point_id`, `items`, `rolls`, `unique`, `drop_if_full`, `consume_points`, `max_consume` | Give item rewards | Weighted item rolls supported |
| `taunt` | `point_type`, `point_id`, `amount`, `target`, `consume_points`, `max_consume` | Pull hostile aggro | Radius-based mob targeting |
| `damage_self` | `amount`, `point_type`, `point_id`, `consume_points`, `max_consume` | Hurt the owner | Simple downside or tradeoff |
| `damage_target` | `amount`, `config.radius`, `point_type`, `point_id`, `consume_points`, `max_consume` | Hurt nearby target | Chooses nearest valid target |
| `knockback` | `amount`, `config.vertical`, `point_type`, `point_id`, `consume_points`, `max_consume` | Push the player back | Movement burst / escape tool |
| `launch` | `config.y`, `point_type`, `point_id`, `consume_points`, `max_consume` | Vertical launch | Jump burst or leap effect |
| `teleport` | `x/y/z` or `dx/dy/dz`, `point_type`, `point_id`, `consume_points`, `max_consume` | Teleport the player | Relative or absolute movement |
| `spawn_particle` | `config.particle`, `x/y/z`, `count`, `point_type`, `point_id` | Spawn particles | Mostly cosmetic |
| `play_sound` | `config.sound`, `volume`, `pitch`, `point_type`, `point_id` | Play a sound | Cosmetic or feedback |
| `remove_effect` | `effect`, `point_type`, `point_id` | Remove a specific effect | Useful for cleansing organs |
| `clear_negative_effects` | `point_type`, `point_id` | Remove harmful effects | Broad cleanse |
| `give_xp` | `amount`, `point_type`, `point_id` | Grant XP | Player progression output |
| `consume_hunger` | `amount`, `point_type`, `point_id` | Reduce hunger | Costs hunger as a payment |
| `restore_air` | `amount`, `point_type`, `point_id` | Restore air supply | Underwater support |
| `set_fire` | `amount`, `point_type`, `point_id` | Ignite the player | Risk / damage theme |
| `extinguish` | `point_type`, `point_id` | Clear fire | Fire counterplay |
| `summon_entity` | `config.entity`, `point_type`, `point_id` | Spawn an entity | Use carefully |
| `drop_items` | `items`, `point_type`, `point_id` | Drop item stacks | Safer than giving inventory items |
| `set_cooldown` | `duration_ticks` or `amount`, `point_type`, `point_id` | Apply item cooldown | Good for gating spam |
| `consume_item` | `amount`, `point_type`, `point_id` | Shrink held item stack | Resource sink |
| `repair_item` | `amount`, `point_type`, `point_id` | Repair held damageable item | Maintenance effect |
| `place_block` | `config.block`, `point_type`, `point_id` | Place a block above player | Requires empty space |
| `convert_block` | `config.from`, `config.to`, `point_type`, `point_id` | Replace nearby block | Simple block transformation |
| `force_target` | `amount`, `point_type`, `point_id` | Force nearby mobs to target player | More general than `taunt` |

## Authoring notes

- Prefer `runtime:*` for short-lived tokens and `counter:*` for accumulation.
- Prefer `source: "self"` when the point should stay bound to one organ instance.
- Keep events as ingress; let executions handle visible outcomes.
- If a behavior feels like it needs Java, first check whether a condition plus event plus execution chain can express it.
