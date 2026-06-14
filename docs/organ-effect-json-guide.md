# Organ Effect JSON 开发文档

本文说明 `organEffects` 当前支持的器官效果 JSON 写法。核心设计是：**触发器和执行器通过玩家/实体身上的点数池解耦**。

- `conditions` + `grants`：静态重算时产生点数
- `events`：运行时事件触发器，只推荐增加/消费点数
- `executions`：读取或消费点数，并兑现为药水、回血、掉落等奖励
- Java 扩展 API：当需要读取其他模组状态或执行自定义逻辑时，通过代码注册 point producer / point executor

## 数据位置

器官效果直接写在器官定义 JSON 内：

- `data/<namespace>/organapi/organs/*.json`

每个器官文件中的 `effects` 数组就是 OEP 的读取入口。

---

## 1. effect 基本结构

当前推荐 schema：

```json
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
      "type": "eat",
      "food_only": true,
      "add_points": [
        {
          "type": "runtime",
          "id": "heart_regen_pulse",
          "amount": 1,
          "duration_ticks": 2
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
      "point_id": "heart_regen_pulse",
      "effect": "minecraft:regeneration",
      "duration_ticks": 100,
      "amplifier": 0,
      "consume_points": true,
      "max_consume": 1
    }
  ]
}
```

职责：

- `conditions`：当前 effect 是否启用
- `grants`：条件满足时写入 `organ` 来源层的被动点数
- `events`：玩家行为发生时增加/消费点数
- `executions`：从点数池读取/消费点数，并产生实际效果

`conditions` 数组是 **AND 关系**，目前不支持 OR / NOT / 嵌套分组。

---

## 2. 点数池与来源分层

点数 key 格式：

- `attribute:<id>`
- `skill:<id>`
- `counter:<id>`
- `runtime:<id>`
- 其他自定义 type

示例：

- `attribute:minecraft:luck`
- `skill:organeffectprocessor:wonder_sight`
- `counter:organeffectprocessor:charge`
- `runtime:organeffectprocessor:storm_insight_token`

点数按 source layer 存储，再聚合成最终总量。常见来源：

- `organ`：静态器官 grants
- 自动生成的器官实例 source：`source: "self"`
- Java 扩展 API 注册的 producer id
- `runtime`：带过期时间的运行时点数

### `source: "self"`

在 runtime events 中推荐使用：

```json
"source": "self"
```

含义：按当前器官实例自动生成唯一 source tag，避免多个同类器官互相串池。

---

### 2.5 `source: "self"`

在 runtime 事件里推荐使用：

```json
"source": "self"
```

含义：按**当前器官实例**生成唯一 source tag，而不是把所有同类器官写进一个共享 source。

这不仅影响 `add_points`，也影响后续 execution / event action 对 source-backed 点数的读取与消费。

当前规则可以概括为：

- `runtime:*` 点按全局 runtime key 读取
- 非 runtime source 点默认按同名 key 聚合为共享池读取/消费
- 如果显式写死自定义 `source`，则会限制到该 source
- `self` 用于“当前器官实例自己的 source”，最适合器官局部累积/局部消费的设计

---

## 3. grants 写法

### attribute

```json
{
  "type": "attribute",
  "attribute": "luck",
  "amount": 1
}
```

未写命名空间时，attribute 默认补 `minecraft:`。

常用可测属性：

- `luck`
- `attack_damage`
- `max_health`
- `movement_speed`

最终是否生效取决于该 attribute 是否已注册、玩家是否拥有对应 `AttributeInstance`。

### skill

```json
{
  "type": "skill",
  "skill_name": "wonder_sight",
  "amount": 1
}
```

未写命名空间时，默认补当前数据包 namespace。技能需要在 Java 中注册 metadata 和 executor。

### counter / runtime

```json
{
  "type": "counter",
  "id": "charge",
  "amount": 1
}
```

`counter:*` 常用于长期累积值；`runtime:*` 常用于带过期时间的短时 token。它们本身不会自动变成属性或技能，通常由 `executions` 消费或观察。

---

## 4. 条件类型

### static

```json
{ "type": "static" }
```

恒成立。

### slot_index

```json
{
  "type": "slot_index",
  "op": "eq",
  "value": 2
}
```

槽位索引从 0 开始。支持：`eq`、`ne`、`gt`、`gte`、`lt`、`lte`。

### distance_to_edge

```json
{
  "type": "distance_to_edge",
  "edge": "top",
  "op": "lte",
  "value": 0
}
```

支持边：`top`、`bottom`、`left`、`right`。

### weather

```json
{ "type": "weather", "value": "rain" }
```

支持：`clear`、`rain`、`thunder`。

### time

```json
{ "type": "time", "mode": "night" }
```

支持 `day` / `night`，也支持数值比较和区间：

```json
{ "type": "time", "min": 18000, "max": 2000 }
```

`min > max` 表示跨午夜区间。

### has_organ

```json
{
  "type": "has_organ",
  "scope": "symmetric_position",
  "organ": "organeffectprocessor:wonder_leg_muscle"
}
```

支持 scope：

- `whole_body`
- `body_part`
- `exact_position`
- `symmetric_position`

### biome

```json
{ "type": "biome", "value": "minecraft:plains" }
```

也支持 biome tag：

```json
{ "type": "biome", "biome_tag": "minecraft:is_overworld" }
```

为了兼容旧草案，也接受：

```json
{ "type": "biome", "tag": "minecraft:is_overworld" }
```

### dimid

```json
{ "type": "dimid", "value": "minecraft:the_nether" }
```

### lightlevel

```json
{ "type": "lightlevel", "op": "gte", "value": 12 }
```

与 `slot_index` 一样支持：`eq`、`ne`、`gt`、`gte`、`lt`、`lte`。

### stepon

```json
{ "type": "stepon", "block": "minecraft:moss_block" }
```

也支持 block tag：

```json
{ "type": "stepon", "block_tag": "minecraft:wool" }
```

`biome` / `stepon` 推荐优先使用 tag 方案来覆盖一组目标，避免开发者逐个枚举群系或方块。

`movement_speed` 这类速度属性当前按倍率处理，示例值应写成类似 `0.1`（+10%），不要继续按整数加法理解。

---

## 5. events 写法

当前支持事件：

- `move`
- `attack`
- `attacked`
- `health_loss`
- `kill`
- `biome_change`
- `dimension_change`
- `eat`
- `mine`
- `use_item`

推荐规则：events 只负责点数变化。伤害类 actions 是当前例外，因为它们需要当前攻击上下文。

### move

```json
{
  "type": "move",
  "distance": 1,
  "source": "self",
  "add_points": [
    {
      "type": "counter",
      "id": "charge",
      "amount": 1,
      "source": "self"
    }
  ],
  "consume_points": [],
  "actions": []
}
```

### attack

```json
{
  "type": "attack",
  "source": "self",
  "add_points": [],
  "consume_points": [],
  "actions": [
    {
      "type": "bonus_damage_per_point",
      "point_type": "counter",
      "point_id": "charge",
      "amount_per_point": 0.5,
      "source": "self",
      "max_consume": 999999
    }
  ]
}
```

`bonus_damage_per_point` 会读取/消费点数并在本次攻击中结算额外伤害。

### attacked / health_loss / kill

```json
{
  "type": "attacked",
  "add_points": [
    {
      "type": "runtime",
      "id": "guard_pulse",
      "amount": 1,
      "duration_ticks": 2
    }
  ]
}
```

- `attacked`：有攻击者时，受击者触发
- `health_loss`：生命值实际损失时触发，不要求攻击者存在（例如摔落、火焰）
- `kill`：击杀生物时由攻击者触发

兼容别名：

- `受到攻击时 -> attacked`
- `损失生命时 -> health_loss`
- `击杀生物时 -> kill`
- `on_biome_change -> biome_change`
- `on_dimension_change -> dimension_change`

### biome_change / dimension_change

```json
{
  "type": "biome_change",
  "add_points": [
    {
      "type": "runtime",
      "id": "storm_insight_token",
      "amount": 1,
      "duration_ticks": 40
    }
  ]
}
```

- `biome_change`：玩家进入新 biome 时触发
- `dimension_change`：玩家切换维度时触发

### eat / mine / use_item

这些事件通常只写 `add_points` / `consume_points`，再由 `executions` 兑现效果。

---

## 6. executions 写法

Executions 在玩家 tick / recompute / runtime event 后运行。它们读取或消费点数池。

点数解析顺序：

1. 先检查 `runtime:*`
2. 再检查 source-backed 同名点数池
3. `consume_points=false` 时只读取，不扣点
4. `consume_points=true` 时只扣这次真正使用到的量

关于 recompute 的保留/清理规则：

- 静态 grants 生成的 per-instance source 会在 recompute 时清空并重建
- event-earned `organ-instance:.../event/...` source 不会因为普通 recompute 被清掉
- `runtime:*` 点按自己的过期时间与消费逻辑处理，不归静态 recompute 清理

### apply_mob_effect

```json
{
  "type": "apply_mob_effect",
  "point_type": "runtime",
  "point_id": "storm_insight_token",
  "effect": "minecraft:night_vision",
  "duration_ticks": 200,
  "amplifier": 0,
  "consume_points": true,
  "max_consume": 1
}
```

### heal

```json
{
  "type": "heal",
  "point_type": "runtime",
  "point_id": "leg_recovery_pulse",
  "amount": 1.0,
  "consume_points": true,
  "max_consume": 1
}
```

### grant_items

```json
{
  "type": "grant_items",
  "point_type": "runtime",
  "point_id": "brain_reward_token",
  "consume_points": true,
  "max_consume": 1,
  "rolls": 2,
  "unique": true,
  "drop_if_full": true,
  "items": [
    { "item": "minecraft:redstone", "count": 3, "weight": 3 }
  ]
}
```

### taunt

```json
{
  "type": "taunt",
  "point_type": "runtime",
  "point_id": "brain_reward_token",
  "amount": 8.0,
  "target": "hostile",
  "consume_points": true,
  "max_consume": 1
}
```

目前 `target` 推荐使用 `hostile`，`amount` 表示半径（格）。

---

## 7. 单功能示例器官

现在的示例器官改成了“尽量一个器官一个功能”，便于单独安装测试：

- `wonder_brain`：最简单的静态 attribute grant
- `wonder_brain_v2`：`use_item -> grant_items`
- `wonder_heart`：`eat -> apply_mob_effect`
- `wonder_leg_muscle`：`move -> heal`
- `wonder_tendon`：`attack -> modify_damage`
- `wonder_lung`：`slot_index -> skill`
- `wonder_eye_of_storm`：`weather + time -> use_item -> night_vision`
- `wonder_biome_core`：`biome` / `biome_tag`
- `wonder_dimension_core`：`dimid`
- `wonder_light_core`：`lightlevel`
- `wonder_footing_core`：`stepon` / `block_tag`
- `wonder_guard_core`：`attacked` / `health_loss`
- `wonder_hunter_core`：`kill`
- `wonder_drifter_core`：`biome_change`
- `wonder_warp_core`：`dimension_change`
- `wonder_taunt_core`：`taunt`

优先按“单器官单功能”方式验证，避免多个 demo 逻辑混在一起时互相干扰。

---

## 8. Java 扩展 API

当 JSON 不足以表达外部模组状态时，用 Java 扩展 API。

入口：

- `cn.kuzuanpa.organeffectprocessor.api.extension.OepExtensionApi`
- `PointProducer`
- `PointExecutor`
- `SkillExecutor`

### 点数获取器

```java
OepExtensionApi.registerPointProducer(new PointProducer() {
    @Override
    public String id() {
        return "compat:create";
    }

    @Override
    public void producePoints(PointProductionContext context, MutablePointSink sink) {
        // compat 子模组在这里读取 Create 状态
        sink.add("counter", "compatmod:rotational_energy", 10);
    }
});
```

Producer 只写点，不直接施加效果。source layer 使用 `id()`。

### 点数执行器

```java
OepExtensionApi.registerPointExecutor(new PointExecutor() {
    @Override
    public String type() {
        return "compatmod:create_charge_burst";
    }

    @Override
    public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
        PointUsage usage = context.resolveUsage(action);
        if (usage.usedPoints() <= 0) return;
        // 消费点数后执行 compat 行为
    }
});
```

对应 JSON：

```json
{
  "type": "compatmod:create_charge_burst",
  "point_type": "counter",
  "point_id": "compatmod:rotational_energy",
  "consume_points": true,
  "max_consume": 5
}
```

主 OEP 不应该 import Create 等可选模组；这些依赖应放在 compat 子模组。

---

## 9. 调试与限制

使用 `effect_point_viewer` 查看当前点数池。它会强制 recompute，因此能验证点数计算，但可能隐藏 stale recompute 问题。

当前限制：

- `conditions` 只有 AND 关系
- 不支持 OR / NOT / 嵌套条件组
- `has_organ` 不支持数量比较
- `symmetric_position` 当前只支持左右臂、左右腿
- 属性修正统一使用 ADDITION
- `stepon` 属于静态 condition，是否生效依赖定期 recompute，不是逐 tick 精确切换
- `biome_change` 当前通过玩家 tick 里记录 biome key 变化检测
- `taunt` 是尽量把附近敌对生物目标切到玩家，部分 AI 可能会很快覆盖该目标
- 伤害修改 action 仍依赖 attack event 即时上下文
- runtime point 数值是 long；小数语义建议放在 action 参数中表达
