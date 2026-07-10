---
name: ironspell-compat-development
description: Fast reference for writing OrganEffects organs against organIronSpell, including supported Iron's Spellbooks conditions/events/executions and the Iron attribute ids you can grant directly.
---

# organIronSpell compat development

Use this when you want to write or review organ JSON that integrates Organ Effect Processor with Iron's Spellbooks through the `organIronSpell` compat addon.

## Purpose

`organIronSpell` is a compat layer on top of OrganEffects.

It gives organ authors two integration styles:

1. **Direct Iron attribute grants** through normal OrganEffects `grants`
2. **Iron-aware conditions / runtime events / executions** through `organironspell:*` types

Preferred OrganEffects architecture still applies:

- `grants` = static/passive points
- `events` = runtime point mutations
- `executions` = consume/use points and produce visible effects

When possible:

- use Iron attributes for passive power scaling
- use `organironspell:*` events to create runtime tokens
- use `organironspell:*` executions or OrganEffects built-in executions to spend those tokens

## First checks

Make sure the compat addon is present in the runtime:

- `organEffects`
- `organIronSpell`
- `Iron's Spellbooks`

Compile flow:

```bash
cd ../organEffects && ./gradlew jar
cd ../organIronSpell && ./gradlew compileJava
```

## 1. Direct Iron's Spellbooks attribute grants

These can be granted through normal OrganEffects `grants` using `type: "attribute"`.

Example:

```json
{
  "type": "attribute",
  "attribute": "irons_spellbooks:max_mana",
  "amount": 40
}
```

### Global Iron attributes

- `irons_spellbooks:max_mana`
  - increases mana pool
- `irons_spellbooks:mana_regen`
  - improves mana regeneration
- `irons_spellbooks:cooldown_reduction`
  - reduces spell cooldowns
- `irons_spellbooks:spell_power`
  - generic spell power bonus
- `irons_spellbooks:spell_resist`
  - generic spell resistance bonus
- `irons_spellbooks:cast_time_reduction`
  - faster long/continuous casts
- `irons_spellbooks:summon_damage`
  - stronger summons
- `irons_spellbooks:casting_movespeed`
  - move speed while casting

### School-specific spell power attributes

- `irons_spellbooks:fire_spell_power`
- `irons_spellbooks:ice_spell_power`
- `irons_spellbooks:lightning_spell_power`
- `irons_spellbooks:holy_spell_power`
- `irons_spellbooks:ender_spell_power`
- `irons_spellbooks:blood_spell_power`
- `irons_spellbooks:evocation_spell_power`
- `irons_spellbooks:nature_spell_power`
- `irons_spellbooks:eldritch_spell_power`

### School-specific resistance attributes

- `irons_spellbooks:fire_magic_resist`
- `irons_spellbooks:ice_magic_resist`
- `irons_spellbooks:lightning_magic_resist`
- `irons_spellbooks:holy_magic_resist`
- `irons_spellbooks:ender_magic_resist`
- `irons_spellbooks:blood_magic_resist`
- `irons_spellbooks:evocation_magic_resist`
- `irons_spellbooks:nature_magic_resist`
- `irons_spellbooks:eldritch_magic_resist`

### When to prefer attribute grants

Use direct grants when you want passive effects like:

- more max mana
- more mana regen
- shorter cooldowns
- stronger fire magic
- more holy resistance

These are the simplest and most stable way to integrate with Iron's Spellbooks.

## 2. Supported `organironspell:*` conditions

These go under `effects[].conditions[]`.

### `organironspell:mana`
Current mana.

```json
{ "type": "organironspell:mana", "op": "gte", "value": 30 }
```

Or range:

```json
{ "type": "organironspell:mana", "min": 20, "max": 80 }
```

### `organironspell:max_mana`
Current max mana.

```json
{ "type": "organironspell:max_mana", "op": "gte", "value": 120 }
```

### `organironspell:missing_mana`
How much mana is missing.

```json
{ "type": "organironspell:missing_mana", "op": "gte", "value": 40 }
```

### `organironspell:mana_percent`
Mana percent as `0..100`.

```json
{ "type": "organironspell:mana_percent", "op": "lte", "value": 25 }
```

### `organironspell:knows_spell`
Checks whether the player has learned a spell.

Fields:

- `spell`
- `spell_id`

```json
{ "type": "organironspell:knows_spell", "spell": "irons_spellbooks:magic_missile" }
```

### `organironspell:spell_on_cooldown`
Checks whether a spell is cooling down.

```json
{ "type": "organironspell:spell_on_cooldown", "spell": "irons_spellbooks:fireball" }
```

### `organironspell:school`
Checks the **currently casting** spell school.

```json
{ "type": "organironspell:school", "school": "irons_spellbooks:fire" }
```

### `organironspell:casting`
Checks cast state.

Any cast:

```json
{ "type": "organironspell:casting" }
```

Specific spell:

```json
{ "type": "organironspell:casting", "spell": "irons_spellbooks:magic_missile" }
```

## 3. Supported `organironspell:*` runtime events

These go under `effects[].events[]`.

### Event types

- `organironspell:spell_pre_cast`
- `organironspell:spell_cast`
- `organironspell:spell_hit`
- `organironspell:spell_heal`
- `organironspell:mana_spent`
- `organironspell:mana_restored`
- `organironspell:cooldown_added`

### Available filter fields

These may be written directly on the event object.

- `spell`
- `school`
- `cast_source`
- `min_mana_cost`
- `max_mana_cost`
- `min_amount`
- `max_amount`

Example:

```json
{
  "type": "organironspell:spell_cast",
  "spell": "irons_spellbooks:magic_missile",
  "school": "irons_spellbooks:ender",
  "min_mana_cost": 10,
  "add_points": [
    {
      "type": "runtime",
      "id": "yourmod:arcane_trigger",
      "amount": 1,
      "duration_ticks": 2
    }
  ]
}
```

### `cast_source` values

Current Iron cast-source names exposed by compat:

- `spellbook`
- `scroll`
- `sword`
- `mob`
- `command`
- `none`

### Meaning of `amount` by event

- `spell_cast` -> mana cost
- `spell_hit` -> damage amount
- `spell_heal` -> heal amount
- `mana_spent` -> mana delta
- `mana_restored` -> mana delta
- `cooldown_added` -> cooldown ticks

## 4. Supported `organironspell:*` executions

These go under `effects[].executions[]`.

### `organironspell:restore_mana`
Consumes OrganEffects points and restores mana.

```json
{
  "type": "organironspell:restore_mana",
  "point_type": "runtime",
  "point_id": "yourmod:arcane_trigger",
  "consume_points": true,
  "max_consume": 1,
  "amount": 8
}
```

`amount` = mana restored per consumed point.

### `organironspell:consume_mana`
Consumes OrganEffects points and subtracts mana.

```json
{
  "type": "organironspell:consume_mana",
  "point_type": "runtime",
  "point_id": "yourmod:arcane_trigger",
  "consume_points": true,
  "max_consume": 1,
  "amount": 5
}
```

### `organironspell:cast_spell`
Consumes OrganEffects points and directly casts an Iron spell.

Fields:

- `spell` / `spell_id`
- `level`
- `cast_source`
- `trigger_cooldown`

```json
{
  "type": "organironspell:cast_spell",
  "point_type": "runtime",
  "point_id": "yourmod:echo_cast",
  "consume_points": true,
  "max_consume": 1,
  "spell": "irons_spellbooks:magic_missile",
  "level": 1,
  "cast_source": "spellbook",
  "trigger_cooldown": true
}
```

Current behavior:

- `level` is the base spell level
- if `amount` is present, it is treated as a per-point bonus-level multiplier

### `organironspell:clear_spell_cooldown`
Consumes OrganEffects points and removes a spell cooldown.

```json
{
  "type": "organironspell:clear_spell_cooldown",
  "point_type": "runtime",
  "point_id": "yourmod:reset_token",
  "consume_points": true,
  "max_consume": 1,
  "spell": "irons_spellbooks:fireball"
}
```

## 5. Recommended design patterns

### Passive caster organ
Use direct Iron attribute grants:

- `max_mana`
- `mana_regen`
- `cooldown_reduction`
- school-specific spell power

### Triggered mana refund organ
- condition: `organironspell:knows_spell`
- event: `organironspell:spell_cast`
- action path:
  - event adds `runtime:*`
  - execution uses `organironspell:restore_mana`

### Extra spell proc organ
- event: `organironspell:spell_cast`
- filter by `spell`
- execution: `organironspell:cast_spell`

### Cooldown reset organ
- condition: `organironspell:spell_on_cooldown`
- execution: `organironspell:clear_spell_cooldown`

## 6. Example organ snippets

### Passive max mana boost

```json
{
  "conditions": [{ "type": "static" }],
  "grants": [
    { "type": "attribute", "attribute": "irons_spellbooks:max_mana", "amount": 40 }
  ],
  "events": [],
  "executions": []
}
```

### Cast magic missile -> restore mana

```json
{
  "conditions": [
    { "type": "organironspell:knows_spell", "spell": "irons_spellbooks:magic_missile" }
  ],
  "grants": [],
  "events": [
    {
      "type": "organironspell:spell_cast",
      "spell": "irons_spellbooks:magic_missile",
      "add_points": [
        {
          "type": "runtime",
          "id": "yourmod:mana_refund_token",
          "amount": 1,
          "duration_ticks": 2
        }
      ]
    }
  ],
  "executions": [
    {
      "type": "organironspell:restore_mana",
      "point_type": "runtime",
      "point_id": "yourmod:mana_refund_token",
      "consume_points": true,
      "max_consume": 1,
      "amount": 8
    }
  ]
}
```

## 7. Gotchas

- `mana_percent` currently compares against `0..100`, not `0..1`
- `school` condition currently checks the **currently casting** spell, not all learned schools
- `spell_on_cooldown` and `casting` are dynamic checks; test them in real runtime, not only with the point viewer
- point viewer forces recompute and can hide stale runtime bugs
- if you want per-organ-instance runtime isolation, use `source: "self"`
- direct Iron attribute grants are usually better than inventing custom point bridges for passive spell stats

## 9. How to design OrganEffects compat extensions so other people can write organs quickly

If you are building an OrganEffects compat addon like `organIronSpell`, the most important goal is not just “make features work”, but “make organ authors need to remember as little as possible”.

### Core design principles

#### A. Prefer attribute grants for passive stats
If the target mod already exposes useful attributes, expose those first through normal OrganEffects `grants` instead of inventing a custom executor or point pipeline.

Why:

- organ authors already understand OrganEffects `grants`
- attribute effects are static and easy to debug
- they compose naturally with existing OrganEffects conditions
- they avoid unnecessary runtime/event complexity

For Iron's Spellbooks, this includes:

- max mana
- mana regen
- cooldown reduction
- generic spell power/resist
- school-specific spell power/resist

Rule of thumb:

- passive scaling -> attribute grant
- temporary proc / trigger / combo -> event + runtime point + execution

#### B. Reuse OrganEffects's standard three-layer model
Design extension features so they still fit:

- `conditions`
- `events`
- `executions`

This is easier for authors than inventing a parallel mini-language.

Good extension design:

- **condition** answers “when is this organ eligible?”
- **event** answers “what happened?”
- **execution** answers “what visible result should occur?”

Bad extension design:

- event type that also directly performs lots of hidden effects
- executor that secretly checks many unrelated conditions
- custom schema that bypasses OrganEffects's normal point flow without need

#### C. Keep runtime triggers decoupled through points whenever possible
When an external-mod event happens, prefer:

1. event fires
2. event adds `runtime:*` or `counter:*`
3. execution consumes or observes those points

Instead of:

- event fires
- Java code immediately does all final gameplay logic

Why this is better:

- easier to inspect in point viewer/debug traces
- organ authors can remix the same trigger into many outcomes
- multiple organs can share the same runtime trigger vocabulary
- keeps extension behavior aligned with the rest of OrganEffects

#### D. Make namespaced, reusable primitives instead of one-off features
A good compat addon should expose reusable building blocks, not content-specific magic.

Good examples:

- `organironspell:mana`
- `organironspell:knows_spell`
- `organironspell:spell_cast`
- `organironspell:restore_mana`
- `organironspell:cast_spell`

Bad examples:

- `organironspell:refund_magic_missile_only`
- `organironspell:fireball_crit_bonus`
- highly specific types that only serve one organ design

The more generic the primitive, the easier it is for other authors to combine it with their own JSON.

#### E. Match the target mod's mental model, not OrganEffects internals
The extension should speak in concepts the target mod's users already understand.

For Iron's Spellbooks that means:

- mana
- max mana
- spell
- school
- cast source
- cooldown
- spell power / resist

Do not force organ authors to think in internal helper structures if a target-mod concept already exists.

#### F. Put optional-mod knowledge in the compat addon, not OrganEffects core
OrganEffects core should stay generic.

Compat addon responsibilities:

- read external mod state
- hook external mod events
- translate those into OrganEffects-compatible condition/event/execution primitives
- provide docs/examples for the target mod domain

This keeps OrganEffects clean and makes the compat package the obvious place other developers look for answers.

### Author-experience rules

#### 1. Minimize mandatory fields
A good extension type should need as few fields as possible for the common case.

Example:

```json
{ "type": "organironspell:knows_spell", "spell": "irons_spellbooks:magic_missile" }
```

is much easier to remember than a deep nested object.

#### 2. Reuse OrganEffects field conventions whenever possible
Examples:

- numeric checks should use `op` + `value` or `min` + `max`
- event filters should be top-level event fields
- point consumers should still use `point_type`, `point_id`, `consume_points`, `max_consume`

This makes compat JSON feel like OrganEffects, not like a separate DSL.

#### 3. Accept common aliases when practical
If a field has an obvious alias, supporting both can reduce friction.

Examples:

- `spell`
- `spell_id`

This is especially useful for compat addons because users often guess field names from the target mod vocabulary.

#### 4. Provide readable tooltip/render output
If custom types appear in viewer/tooltip output as raw ids only, authors lose confidence quickly.

A good compat addon should register display renderers so authors can immediately see that their JSON means what they think it means.

#### 5. Make the simplest path also the recommended path
For example, if direct Iron attributes solve passive scaling, document those first before the more advanced event/execution path.

That helps authors avoid overengineering simple organs.

### Practical extension-authoring checklist

When adding a new OrganEffects compat feature for another mod, ask:

1. **Can this be a plain attribute grant?**
   - if yes, document the attribute id first
2. **If not, is it a condition, event, or execution?**
   - pick one clear role
3. **Can it reuse existing OrganEffects point flow?**
   - prefer yes
4. **Is the type generic enough to be reused across many organs?**
   - if no, redesign it
5. **Does the JSON follow normal OrganEffects conventions?**
   - `op/value`, `min/max`, `point_type/point_id`, etc.
6. **Will tooltip/viewer text be understandable?**
   - register display helpers if needed
7. **Can a new author discover it from one skill/doc page?**
   - if no, docs are still incomplete

### Recommended priority order for future compat work

When extending a new target mod, the most helpful order for other authors is usually:

1. **direct attribute ids**
2. **simple numeric/resource conditions**
3. **core runtime event vocabulary**
4. **high-value generic executions**
5. **examples and copy-paste snippets**

This order gives the fastest payoff to organ authors.

### Short version

If you want other people to write OrganEffects compat organs quickly, optimize for:

- passive effects via existing attributes
- generic namespaced condition/event/execution primitives
- OrganEffects-native JSON conventions
- point-bridge architecture for runtime behavior
- strong examples and readable tooltip output

That combination is much more valuable than implementing many clever but narrow features.

## 10. Where to look in code

OrganEffects side:

- `src/main/java/cn/kuzuanpa/organeffects/api/extension/OrganEffectsExtensionApi.java`
- `src/main/java/cn/kuzuanpa/organeffects/common/effect/RuntimeEffectService.java`
- `src/main/java/cn/kuzuanpa/organeffects/common/effect/RuntimePointExecutor.java`

compat side:

- `../organIronSpell/src/main/java/cn/kuzuanpa/organironspell/api/OrganIronSpellApi.java`
- `../organIronSpell/src/main/java/cn/kuzuanpa/organironspell/compat/OrganIronSpellRegistrations.java`
- `../organIronSpell/src/main/java/cn/kuzuanpa/organironspell/compat/IronSpellHelper.java`
- `../organIronSpell/src/main/java/cn/kuzuanpa/organironspell/compat/condition/`
- `../organIronSpell/src/main/java/cn/kuzuanpa/organironspell/compat/event/OrganIronSpellEventBridge.java`
- `../organIronSpell/src/main/java/cn/kuzuanpa/organironspell/compat/execution/`
