# Organ Effect JSON 开发文档

本文说明 `organEffects` 当前支持的器官效果 JSON 写法，包括静态条件、被动 grants、运行时 events、以及带来源分层的点数池模型。

## 数据位置

器官效果直接写在器官定义 JSON 内：

- `data/<namespace>/organapi/organs/*.json`

每个器官文件中的 `effects` 数组就是 organEffects 的读取入口。

---

## 1. effect 基本结构

当前推荐且实际使用的 schema：

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
          "type": "counter",
          "id": "food_charge",
          "amount": 1,
          "source": "self"
        }
      ]
    }
  ]
}
```

三部分职责：

- `conditions`：静态前置条件
- `grants`：条件满足时，写入 `organ` 来源层的被动点数
- `events`：运行时事件触发器，在玩家行为发生时增加/消费点数，或结算即时效果

说明：

- `conditions` 和 `grants` 用于**重算链路**
- `events` 用于**运行时事件链路**
- 一个 effect 可以同时拥有 `grants` 和 `events`

---

## 2. 复合条件规则

`conditions` 数组当前是 **AND 关系**。

也就是说：

- 数组内所有条件都满足，当前 effect 才生效。
- 目前 **不支持** `OR`、`NOT`、嵌套分组。

示例：

```json
{
  "conditions": [
    { "type": "weather", "value": "rain" },
    { "type": "time", "mode": "night" }
  ],
  "grants": [
    {
      "type": "attribute",
      "attribute": "attack_damage",
      "amount": 1
    }
  ],
  "events": []
}
```

含义：**只有在下雨且夜晚时才生效。**

---

## 3. 点数池与来源分层

点数池不再只是一个扁平总表，而是按 source 分层存储，然后再聚合成有效总量。

### 3.1 聚合 point key

点数 key 仍然是：

- `attribute:<id>`
- `skill:<id>`
- `counter:<id>`
- 以及其他未来自定义 type

例如：

- `attribute:minecraft:luck`
- `skill:organeffectprocessor:wonder_sight`
- `counter:organeffectprocessor:charge`

### 3.2 source layer

当前会区分不同来源层，例如：

- `organ`
- `runtime:charge`
- `runtime:item_use:modid/custom_item`
- 自动生成的器官实例来源 tag

最终玩家实际生效的总量 = 所有 source layer 的同名 pointKey 相加。

### 3.3 `source: "self"`

在 runtime `events` 里，推荐大量使用：

```json
"source": "self"
```

含义：

- 运行时自动生成“当前器官实例唯一 sourceTag”
- 同类器官的临时点数不会互相串池

自动 sourceTag 会基于以下信息生成：

- organ id
- body part id
- slot index
- effect / event 索引

如果希望多个器官共享同一个临时池，也可以自己写固定 source 字符串。

---

## 4. grants 写法

每个 effect 的 `grants` 是当前 effect 在静态条件满足时，写入 `organ` 层的点数条目。

通用字段：

- `type`: 点数类型
- `amount`: 数值

### 4.1 attribute

```json
{
  "type": "attribute",
  "attribute": "luck",
  "amount": 1
}
```

支持以下写法：

- `attribute: "luck"`
- `attribute: "attack_damage"`
- `attribute: "minecraft:luck"`
- `id: "minecraft:max_health"`

对于 `type: "attribute"`：

- 如果未写命名空间，则会自动补成 `minecraft:`
- 因此原版属性推荐直接写短名，例如：
  - `luck`
  - `attack_damage`
  - `max_health`
  - `movement_speed`

注意：

- 最终是否能生效，取决于该 attribute 是否已注册，并且玩家是否真的拥有这个 `AttributeInstance`
- 当前属性修正使用的是 **ADDITION** 叠加方式

### 4.2 skill

```json
{
  "type": "skill",
  "skill_name": "wonder_sight",
  "amount": 1
}
```

支持：

- `skill_name`
- `id`

对于非 attribute 类型，如果未写命名空间，则默认补当前数据包 namespace。

### 4.3 counter

```json
{
  "type": "counter",
  "id": "charge",
  "amount": 1
}
```

说明：

- `counter:*` 通常用于 runtime 累积值
- 它本身不会自动变成属性或技能
- 需要由 `events.actions` 在特定事件中消费或结算

---

## 5. 当前支持的条件类型

## 5.1 static

恒成立条件。

```json
{ "type": "static" }
```

## 5.2 slot_index

按当前器官在部位中的槽位索引判断。

```json
{
  "type": "slot_index",
  "op": "eq",
  "value": 2
}
```

支持操作符：

- `eq`
- `ne`
- `gt`
- `gte`
- `lt`
- `lte`

说明：

- 槽位索引从 `0` 开始

## 5.3 distance_to_edge

按当前器官距离所在部位布局边界的距离判断。

```json
{
  "type": "distance_to_edge",
  "edge": "top",
  "op": "lte",
  "value": 0
}
```

支持边：

- `top`
- `bottom`
- `left`
- `right`

支持操作符：

- `eq`
- `ne`
- `gt`
- `gte`
- `lt`
- `lte`

说明：

- 槽位网格根据部位容量和 `visualWidthRatio / visualHeightRatio` 自动推导
- 例如：
  - `top + lte 0` 表示最上排
  - `left + eq 0` 表示最左列

## 5.4 weather

按玩家当前世界天气判断。

```json
{ "type": "weather", "value": "clear" }
```

支持值：

- `clear`
- `rain`
- `thunder`

## 5.5 time

按世界时间判断。

### 语义模式

```json
{ "type": "time", "mode": "day" }
```

或

```json
{ "type": "time", "mode": "night" }
```

支持：

- `day`
- `night`

### 数值比较模式

```json
{
  "type": "time",
  "op": "gte",
  "value": 13000
}
```

### 区间模式

```json
{
  "type": "time",
  "min": 18000,
  "max": 2000
}
```

说明：

- 时间使用 `dayTime % 24000` 后的单日时间
- 当 `min > max` 时，表示**跨午夜区间**
- 例如上例表示：
  - `18000 ~ 23999`，或
  - `0 ~ 2000`

## 5.6 has_organ

按“是否存在某器官”进行联动判断。

### whole_body

```json
{
  "type": "has_organ",
  "scope": "whole_body",
  "organ": "organeffectprocessor:wonder_heart"
}
```

### body_part

```json
{
  "type": "has_organ",
  "scope": "body_part",
  "body_part": "organapi:chest",
  "organ": "organeffectprocessor:wonder_heart"
}
```

### exact_position

```json
{
  "type": "has_organ",
  "scope": "exact_position",
  "body_part": "organapi:left_leg",
  "slot": 0,
  "organ": "organeffectprocessor:wonder_leg_muscle"
}
```

### symmetric_position

```json
{
  "type": "has_organ",
  "scope": "symmetric_position",
  "organ": "organeffectprocessor:wonder_leg_muscle"
}
```

当前对称映射支持：

- `organapi:left_arm <-> organapi:right_arm`
- `organapi:left_leg <-> organapi:right_leg`

---

## 6. events 写法

`events` 用于响应运行时行为。

当前首批支持：

- `move`
- `attack`
- `eat`
- `mine`
- `use_item`

### 6.1 move

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
  "actions": []
}
```

含义：

- 每移动满 `distance` 指定的距离阈值，就执行一次 add_points
- 首版推荐把它理解为“每满 1 格 / 1 单位位移积累一次”

### 6.2 attack

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

含义：

- 读取某个 point（通常是 `counter:*`）
- 每层附加 `amount_per_point` 伤害
- 并在结算时自动消费这些点数

说明：

- 这类即时攻击加伤不是通过长期 attribute 修改实现的
- 而是在攻击事件里直接进行一次性结算

### 6.3 eat

```json
{
  "type": "eat",
  "food_only": true,
  "add_points": [
    {
      "type": "counter",
      "id": "food_charge",
      "amount": 1,
      "source": "self"
    }
  ]
}
```

说明：

- 只在真正完成食用时触发
- `food_only: true` 表示必须是食物

### 6.4 mine

```json
{
  "type": "mine",
  "add_points": [
    {
      "type": "counter",
      "id": "miner_instinct",
      "amount": 1,
      "source": "self"
    }
  ]
}
```

可用过滤字段：

- `block`
- `block_tag`

### 6.5 use_item

```json
{
  "type": "use_item",
  "item": "organeffectprocessor:effect_point_viewer",
  "add_points": [
    {
      "type": "counter",
      "id": "focus",
      "amount": 1,
      "source": "self"
    }
  ]
}
```

可用过滤字段：

- `item`
- `item_tag`

---

## 7. 典型样例：移动蓄力，攻击消费

```json
{
  "conditions": [
    { "type": "static" }
  ],
  "grants": [
    {
      "type": "attribute",
      "attribute": "jump_strength",
      "amount": 1
    }
  ],
  "events": [
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
      "actions": []
    },
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
  ]
}
```

流程含义：

1. 玩家移动时积攒 `counter:charge`
2. 下次攻击时读取并消费这些 charge
3. 每层 charge 额外增加 `0.5` 点伤害

---

## 8. 点数查看器行为

点数查看器现在建议理解为两层输出：

1. **总量视图**：所有来源聚合后的最终点数
2. **来源明细**：按 sourceTag 展示每层点数来源

这样更适合调试：

- 哪些点数来自器官被动层 `organ`
- 哪些点数来自 runtime 临时层
- 某个 `self` source 是否按预期累计 / 消费

---

## 9. 当前限制

当前版本仍有以下限制：

- `conditions` 只有 AND 关系
- 不支持 OR / NOT / 嵌套条件组
- `has_organ` 当前只判断“是否存在”，不支持数量比较
- `symmetric_position` 当前只支持左右臂、左右腿
- 属性修正当前统一使用 `ADDITION`
- runtime action 目前首批重点是 `bonus_damage_per_point`
- runtime point 数值当前以 `long` 计；需要小数语义时，建议通过 action 参数（例如 `amount_per_point: 0.5`）来表达，而不是把 point 本身改成浮点

后续推荐优先扩展：

- `all_of / any_of / not`
- 器官数量条件
- 更通用的数据驱动对称部位映射
- 更多 runtime action 类型
- 更细粒度的 viewer / HUD 展示
