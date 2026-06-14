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

---

## 5. events 写法

当前支持事件：

- `move`
- `attack`
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

### eat / mine / use_item

这些事件通常只写 `add_points` / `consume_points`，再由 `executions` 兑现效果。

---

## 6. executions 写法

Executions 在玩家 tick / recompute / runtime event 后运行。它们读取或消费点数池。

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

---

## 7. 递进式器官示例

`wonder_eye_of_storm` 展示推荐写法：

1. 静态基础层：`luck +1`
2. 雨夜进阶层：`movement_speed +1`
3. 雨夜行为层：使用 point viewer 产 `storm_insight_token`
4. execution 层：消费 token 给夜视

查看：

- `src/main/resources/data/organeffectprocessor/organapi/organs/wonder_eye_of_storm.json`

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
- 伤害修改 action 仍依赖 attack event 即时上下文
- runtime point 数值是 long；小数语义建议放在 action 参数中表达
