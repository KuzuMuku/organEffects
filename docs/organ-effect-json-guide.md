# Organ Effect JSON 开发文档

本文说明 OEP 当前支持的器官效果 JSON 写法。核心模型很简单：

- `conditions` 决定 effect 是否启用
- `grants` 在重算时写入被动点数
- `events` 在运行时把行为转成点数变化
- `executions` 消耗或读取点数，兑现成可见效果

器官效果写在器官定义文件里：

- `data/<namespace>/organapi/organs/*.json`

OEP 读取的入口是每个器官文件中的 `effects[]`。

## 最小结构

```json
{
  "conditions": [{ "type": "static" }],
  "grants": [
    { "type": "attribute", "attribute": "luck", "amount": 1 }
  ],
  "events": [],
  "executions": []
}
```

推荐写法：

- 条件只负责 gating
- `grants` 只放静态被动值
- `events` 尽量只做点数增减
- `executions` 再把点数兑现成药水、回血、掉落、嘲讽等实际效果
- 需要读取外部模组状态时，用 Java 扩展 API，不要硬塞进 JSON

## 点数模型

点数 key 统一写成 `type:id`，常见类型是：

- `attribute:<id>`
- `skill:<id>`
- `counter:<id>`
- `runtime:<id>`

约定：

- `runtime:*` 适合短时 token
- `counter:*` 适合长期累积
- `source: "self"` 表示当前器官实例自己的 source，不和同类器官串池
- 静态重算生成的 source 会在 recompute 时重建
- event 产生的 `organ-instance:.../event/...` source 不会被普通 recompute 清掉

## 适用范围

### 你该用 JSON 的时候

- 只是条件、事件、执行的组合
- 只依赖 OEP/vanilla 状态
- 不需要读别的模组 API

### 你该用 Java 扩展的时候

- 需要读外部模组状态
- 需要自定义条件
- 需要自定义 point executor
- 需要从代码注入 runtime event

## 事件与执行原则

- `events` 优先只写 `add_points` / `consume_points`
- `executions` 在后续阶段读取这些点数并产生结果
- 伤害类行为目前仍有少量事件侧例外，因为需要攻击上下文
- `effect_point_viewer` 会强制 recompute，适合看点数，但可能掩盖 stale recompute 问题

### 展示控制

`events[]` 和 `executions[]` 现在都支持下面两个可选字段，它们只影响 tooltip 和 `effect_point_viewer` 的展示，不影响实际触发与执行：

- `hidden: true`
- `custom_display_key: "your.translation.key"`

规则：

- `hidden: true` 会只隐藏当前这一条 event 或 execution 的展示
- `custom_display_key` 会用该语言 key 替换默认自动生成的描述
- 两者同时存在时，`hidden` 优先
- 这两个字段不会连带隐藏同一个 `effect` 里的其他 grants / events / executions

示例：

```json
{
  "type": "eat",
  "food_only": true,
  "hidden": true,
  "add_points": [
    { "type": "runtime", "id": "secret_charge", "amount": 1 }
  ]
}
```

```json
{
  "type": "apply_mob_effect",
  "point_type": "runtime",
  "point_id": "secret_charge",
  "effect": "minecraft:regeneration",
  "duration_ticks": 100,
  "custom_display_key": "tooltip.kubejs.secret_regen"
}
```

## 兼容提示

- 旧草案里混用过一些别名字段，当前以本文件的表格为准
- `conditions` 目前是 AND 关系，不支持 OR / NOT / 嵌套组
- `has_organ` 不做数量比较
- `movement_speed` 按倍率处理，不要当成整数加法

## 示例

```json
{
  "conditions": [
    { "type": "weather", "value": "rain" },
    { "type": "time", "mode": "night" }
  ],
  "grants": [
    {
      "type": "attribute",
      "attribute": "movement_speed",
      "amount": 0.1
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
          "duration_ticks": 20
        }
      ]
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

## Java 扩展 API

常用入口：

- `OepExtensionApi.registerPointProducer(...)`
- `OepExtensionApi.registerConditionHandler(...)`
- `OepExtensionApi.registerPointExecutor(...)`
- `RuntimeEffectService.fireEvent(...)`
- `SkillManager.registerSkill(...)`
- `SkillManager.registerSkillExecutor(...)`

原则：

- producer 只写点
- condition handler 只判断
- executor 只消费点并执行行为
- compat 逻辑尽量放在外部子模组里

## 调试与限制

测试顺序建议：

1. `cd ../organAPI && ./gradlew compileJava jar`
2. `./gradlew compileJava`
3. 安装器官
4. 关闭器官菜单触发重算
5. 用 `effect_point_viewer` 检查点数
6. 再看 tooltip、技能和执行效果

当前限制：

- `conditions` 只有 AND
- 不支持 OR / NOT / 嵌套条件组
- `has_organ` 不支持数量比较
- `symmetric_position` 目前只支持左右臂、左右腿
- `stepon` 依赖重算，不是逐 tick 精确切换
- `biome_change` 通过玩家 tick 记录 biome 变化
- `taunt` 只是尽量改目标，AI 可能很快覆盖
- runtime 点数是 `long`

## 内建能力总表

### 条件

| Type | 关键字段 | 用途 |
|---|---|---|
| `static` | 无 | 恒成立 |
| `slot_index` | `op`, `value` | 槽位索引比较 |
| `distance_to_edge` | `edge`, `op`, `value` | 到槽位边缘距离 |
| `weather` | `value` | `clear` / `rain` / `thunder` |
| `time` | `mode`, `op`, `value`, `min`, `max` | 昼夜或时间段 |
| `has_organ` | `scope`, `body_part`, `slot`, `organ` | 器官组合门槛 |
| `biome` | `biome`, `biome_tag` | 群系或群系列表 |
| `dimid` | `value` | 维度 id |
| `lightlevel` | `op`, `value` | 光照等级 |
| `stepon` | `block`, `block_tag` | 脚下方块 |
| `health` | `mode`, `op`, `value`, `min`, `max` | 生命值、缺失量、百分比 |
| `hunger` | `mode`, `op`, `value`, `min`, `max` | 饥饿值、饱和度、缺失量 |
| `air` | `mode`, `op`, `value` | 氧气或水下状态 |
| `xp` | `mode`, `op`, `value`, `min`, `max` | 经验等级或总经验 |
| `status_effect` | `effect`, `amplifier`, `min_duration`, `max_duration` | 药水效果 |
| `attribute` | `attribute`, `op`, `value`, `min`, `max` | 属性值比较 |
| `movement_state` | `state` | 跑动、潜行、游泳、飞行、落地 |
| `environment_state` | `state` | 在水中、着火、骑乘等 |
| `equipment` | `slot`, `item`, `item_tag`, `empty` | 装备检查 |
| `enchantment` | `slot`, `enchantment`, `op`, `value` | 附魔等级 |
| `nearby_entity` | `entity`, `entity_tag`, `radius`, `op`, `value`, `min`, `max` | 附近实体数量 |
| `moon_phase` | `mode`, `op`, `value` | 月相 |
| `biome_category` | `value` | 群系类别 |
| `dimension_type` | `value` | 维度类型 |

### 事件

| Type | 关键字段 | 用途 |
|---|---|---|
| `move` | `distance`, `source`, `add_points`, `consume_points`, `actions` | 移动驱动 |
| `tick` | `config.interval_ticks`, `add_points`, `consume_points`, `actions` | 周期驱动，默认每 tick 一次 |
| `attack` | `source`, `add_points`, `consume_points`, `actions` | 攻击者侧 |
| `attacked` | `source`, `add_points`, `consume_points`, `actions` | 被攻击者侧 |
| `health_loss` | `source`, `add_points`, `consume_points`, `actions` | 实际掉血 |
| `kill` | `source`, `add_points`, `consume_points`, `actions` | 击杀 |
| `biome_change` | `add_points`, `consume_points`, `actions` | 切换群系 |
| `dimension_change` | `add_points`, `consume_points`, `actions` | 切换维度 |
| `eat` | `food_only`, `item`, `item_tag`, `add_points`, `consume_points`, `actions` | 吃东西 |
| `mine` | `block`, `block_tag`, `add_points`, `consume_points`, `actions` | 挖方块 |
| `use_item` | `item`, `item_tag`, `add_points`, `consume_points`, `actions` | 使用物品 |
| `jump` | `add_points`, `consume_points`, `actions` | 跳跃 |
| `land` | `add_points`, `consume_points`, `actions` | 落地 |
| `sprint_start` / `sprint_stop` | `add_points`, `consume_points`, `actions` | 冲刺状态 |
| `sneak_start` / `sneak_stop` | `add_points`, `consume_points`, `actions` | 潜行状态 |
| `swim_start` / `swim_stop` | `add_points`, `consume_points`, `actions` | 游泳状态 |
| `enter_water` / `leave_water` | `add_points`, `consume_points`, `actions` | 进出水 |
| `take_damage` | `add_points`, `consume_points`, `actions` | 受伤 |
| `deal_damage` | `add_points`, `consume_points`, `actions` | 造成伤害 |
| `projectile_hit` | `add_points`, `consume_points`, `actions` | 投射物命中 |
| `block_place` | `add_points`, `consume_points`, `actions` | 放置方块 |
| `item_craft` | `add_points`, `consume_points`, `actions` | 合成 |
| `item_smelt` | `add_points`, `consume_points`, `actions` | 熔炼 |
| `item_repair` | `add_points`, `consume_points`, `actions` | 修理 |
| `item_enchant` | `add_points`, `consume_points`, `actions` | 附魔 |
| `fish_catch` | `add_points`, `consume_points`, `actions` | 钓鱼收获 |
| `sleep` | `add_points`, `consume_points`, `actions` | 睡觉 |
| `respawn` | `add_points`, `consume_points`, `actions` | 重生 |
| `consume_item` | `add_points`, `consume_points`, `actions` | 消耗物品 |
| `equip_item` / `unequip_item` | `add_points`, `consume_points`, `actions` | 穿脱装备 |
| `critical_hit` | `add_points`, `consume_points`, `actions` | 暴击 |
| `shield_block` | `add_points`, `consume_points`, `actions` | 格挡 |
| `parry` | `add_points`, `consume_points`, `actions` | 反击式格挡 |

### 执行

| Type | 关键字段 | 用途 |
|---|---|---|
| `apply_mob_effect` | `point_type`, `point_id`, `effect`, `duration_ticks`, `amplifier`, `consume_points`, `max_consume` | 药水效果 |
| `heal` | `point_type`, `point_id`, `amount`, `consume_points`, `max_consume` | 治疗 |
| `grant_items` | `point_type`, `point_id`, `items`, `rolls`, `unique`, `drop_if_full`, `consume_points`, `max_consume` | 发放物品 |
| `taunt` | `point_type`, `point_id`, `amount`, `target`, `consume_points`, `max_consume` | 嘲讽敌对生物 |
| `damage_self` | `amount`, `point_type`, `point_id` | 自伤 |
| `damage_target` | `amount`, `config.radius`, `point_type`, `point_id` | 伤害附近目标 |
| `knockback` | `amount`, `config.vertical`, `point_type`, `point_id` | 击退 |
| `launch` | `config.y`, `point_type`, `point_id` | 纵向弹起 |
| `teleport` | `x/y/z` 或 `dx/dy/dz`, `point_type`, `point_id` | 传送 |
| `spawn_particle` | `config.particle`, `x/y/z`, `count` | 粒子 |
| `play_sound` | `config.sound`, `volume`, `pitch` | 音效 |
| `remove_effect` | `effect`, `point_type`, `point_id` | 移除指定效果 |
| `clear_negative_effects` | `point_type`, `point_id` | 清除负面效果 |
| `give_xp` | `amount`, `point_type`, `point_id` | 经验 |
| `consume_hunger` | `amount`, `point_type`, `point_id` | 消耗饥饿 |
| `restore_air` | `amount`, `point_type`, `point_id` | 恢复氧气 |
| `set_fire` | `amount`, `point_type`, `point_id` | 点燃 |
| `extinguish` | `point_type`, `point_id` | 熄灭 |
| `summon_entity` | `config.entity`, `point_type`, `point_id` | 召唤实体 |
| `drop_items` | `items`, `point_type`, `point_id` | 掉落物品 |
| `set_cooldown` | `duration_ticks` 或 `amount`, `point_type`, `point_id` | 设置冷却 |
| `consume_item` | `amount`, `point_type`, `point_id` | 消耗物品栈 |
| `repair_item` | `amount`, `point_type`, `point_id` | 修理物品 |
| `place_block` | `config.block`, `point_type`, `point_id` | 放置方块 |
| `convert_block` | `config.from`, `config.to`, `point_type`, `point_id` | 方块转换 |
| `force_target` | `amount`, `point_type`, `point_id` | 强制目标转向玩家 |

## 单功能示例器官

示例器官尽量保持“一器官一功能”，便于单独测试：

- `wonder_brain`：静态 attribute grant
- `wonder_brain_v2`：`use_item -> grant_items`
- `wonder_heart`：`eat -> apply_mob_effect`
- `wonder_leg_muscle`：`move -> heal`
- `wonder_tendon`：`attack -> runtime charge`
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
