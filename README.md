# Organ API

Organ API 是一个基于 Forge 1.20.1 的器官/部位容器基础模组，目标是提供一个尽量简单、原版风格、便于二次开发的 API，而不是内置大量固定器官内容。

## 当前实现

- 数据驱动的 body part 模板：定义部位默认容量、标签限制、UI 布局倾向与总览区域
- 数据驱动的 body plan：按实体类型决定实际拥有的部位集合、容量覆盖与渲染区域覆盖
- 实体级 organ holder：玩家与其他实体都可以持有器官状态
- target-aware 器官菜单：viewer 与 target 分离，菜单可编辑目标实体而非仅操作者自己
- 两套现有 UI 流程：
  - `organ_pouch` / `chest_opener` -> 部位选择 -> 便携器官编辑
  - `surgery_room` -> 总览式器官编辑
- 屠宰玩法入口：
  - `slaughter_room`：目标生物站在方块正上方且满足低血量条件时打开总览 UI
  - `slaughter_tool`：直接对低血量生物打开同一总览 UI
- 示例器官、扩容道具、样例数据与基础同步逻辑

## 数据目录

- `data/<namespace>/organapi/body_parts/*.json`
- `data/<namespace>/organapi/body_plans/*.json`
- `data/<namespace>/organapi/organs/*.json`

## API 设计目标

- 查询部位模板、实体 body plan 与器官定义
- 查询目标实体每个部位的容量 / 已安装器官
- 校验并安装、替换、移除器官
- 游戏内永久扩展目标部位容量
- 方便其他模组通过 JSON 或代码接入实体 anatomy 与器官逻辑

## Configuration

模组使用 Forge common config，当前屠宰相关参数位于 `slaughter` 分组下，可调整：

- `health_threshold_ratio`：允许开胸的血量比例阈值
- `restriction_duration_ticks`：开胸后限制效果持续时间（tick）
- `slowness_amplifier`：缓慢效果强度
- `weakness_amplifier`：虚弱效果强度

说明：Minecraft 原生效果 amplifier 语义为 `0 = I 级`，`1 = II 级`，依此类推。`slaughter_room` 与 `slaughter_tool` 共享同一组配置。

## 交互流概览

### 便携器官编辑

- `OrganPouchItem` / `chest_opener` 打开 `BodyPartSelectionMenu`
- 玩家在 body map 上选中部位后，通过 `OpenOrganMenuC2SPacket` 打开 `OrganMenu`
- `OrganMenu` 对当前 target 实体的对应部位进行编辑

### 手术室总览

- `SurgeryRoomBlock` 打开 `OrganOverviewMenu`
- 界面显示目标实体所有可见部位区域与编辑格子
- 点击区域切换当前编辑的 body part

### 屠宰入口

- `SlaughterRoomBlock`：查找方块正上方活着的 `LivingEntity`，若其当前血量比例 `<= slaughter.health_threshold_ratio`，则施加限制效果并打开 `OrganOverviewMenu`
- `SlaughterToolItem`：直接右键 `LivingEntity`，若满足同一低血量条件，则施加限制效果并打开 `OrganOverviewMenu`

## 开发者文档

- 器官效果 JSON：`docs/organ-effect-json-guide.md`

- 器官控制 API 示例：`docs/organ-control-api-guide.md`
- 数据格式说明：`docs/organ-data-format.md`

## 示例内容说明

仓库内自带的 `sample_*` 器官、`*_expansion_kit`、`surgery_room`、`slaughter_room`、`slaughter_tool` 都主要用于演示链路与默认玩法。其他模组可以只依赖 API、JSON loader 与菜单/同步逻辑，而不依赖这些样例内容。
