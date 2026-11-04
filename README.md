# Sidebar

一个面向 Paper / Folia 的 Minecraft 侧栏（计分板）插件，使用 Kotlin + scoreboard-library 实现。

支持多套侧栏配置、MiniMessage 文本、PlaceholderAPI、帧动画、玩家开关与切换，并提供扩展 API，让其他插件注册自己的侧栏并接入统一的 `/sb` 命令。内置侧栏与扩展侧栏共用同一套状态机。

- 服务端：Paper / Folia 1.21+（基于 1.21.4 开发）
- Java：21
- 构建：Gradle + Kotlin 2.2 + Shadow
- 依赖：[scoreboard-library](https://github.com/vytskalt/scoreboard-library) 2.8.3、PlaceholderAPI（可选）

---

## 功能

- 多侧栏：`config.yml` 里可配置任意套侧栏，玩家用 `/sb switch` 切换
- 静态侧栏共享一份实例；含 PAPI 占位符的侧栏自动按玩家独立渲染
- MiniMessage 文本格式
- PlaceholderAPI 占位符（需安装 PAPI）
- 帧动画：行内写 `{animation:动画名}`，帧序列在 `animation.yml` 配置
- 玩家开关：`/sb toggle`
- 侧栏列表：`/sb list`
- 配置重载：`/sb reload`
- 玩家选择与开关按 UUID 持久化到 `data.json`，按间隔自动保存
- Folia 支持：侧栏操作统一在 Paper 主线程 / Folia 全局区域线程串行调度
- 扩展 API：独立 `api` 模块，其他插件可注册 provider，统一进 `/sb list` / `/sb switch`

---

## 安装

1. 构建或获取 `Sidebar-1.0-SNAPSHOT-all.jar`
2. 放入服务端 `plugins/` 目录
3. 可选：安装 PlaceholderAPI（只有使用 `%placeholder%` 时才需要）
4. 启动服务端，首次启动会生成 `config.yml`、`animation.yml`、`data.json`

---

## 命令

主命令 `/sidebar`，别名 `/sb`。

| 命令 | 说明 | 执行者 |
|---|---|---|
| `/sb` | 显示帮助 | 任意 |
| `/sb help` | 显示帮助 | 任意 |
| `/sb list` | 列出可见侧栏 | 任意 |
| `/sb switch <侧栏>` | 切换到指定侧栏 | 玩家 |
| `/sb toggle` | 开 / 关自己的侧栏 | 玩家 |
| `/sb reload` | 重载配置并重建内置侧栏 | 需要权限 |

### 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `sidebar.reload` | op | 允许执行 `/sb reload` |
| `sidebar.usage` | not op | 已声明，当前命令未强制校验 |

---

## 配置文件

### config.yml

```yaml
# 是否输出调试日志
debug: false

data:
  # 玩家数据自动保存间隔，单位分钟，最小 1
  save-interval: 10

first-join:
  # 新玩家默认使用的侧栏 id
  defaultsidebar: default

sidebar:
  default:
    description: "<white>一个默认的Sidebar</white>"
    title: "<white>默认Sidebar</white>"
    lines:
      - "<green>最高支持</green><gray>15</gray><green>行</green>"
      - "<yellow>每行支持</yellow><gray>42</gray><yellow>个字符</yellow>"
      - "<blue>支持</blue><gray>PlaceHolderAPI</gray><blue>格式</blue>"
      - "<red>只支持</red><gray>MiniMessage</gray><red>格式</red>"

  # 再定义一个含动画和 PAPI 的侧栏
  stats:
    description: "<aqua>个人数据</aqua>"
    title: "<gradient:#0091FF:#F13ABC><bold>我的数据</bold></gradient>"
    lines:
      - "<white>玩家: </white><yellow>%player_name%</yellow>"
      - "<white>动画: {animation:default}</white>"
      - "<gray>在线人数: </gray><green>%server_online%</green>"
```

规则：

- `sidebar.<id>.title` 和 `sidebar.<id>.lines` 必填，`description` 可选
- 最多 15 行，超出会被截断并在控制台 warning
- 行内出现 `%...%` 时，这套侧栏按玩家独立渲染；没有占位符则所有玩家共享一份
- 动画标记写在行内：`{animation:动画名}`，一行可放多个

### animation.yml

```yaml
animation:
  default:
    # 帧刷新间隔，单位毫秒
    update-interval-ms: 200
    lines:
      - "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>"
      - "<gradient:#40AFFF:#FF80E0><bold>Default Animation</bold></gradient>"
      - "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>"
```

- 每个动画一组 `lines`，按顺序循环
- 同一个动画可以被多套侧栏引用，核心统一推进帧
- 修改后执行 `/sb reload` 生效

### data.json

```json
{
  "playerUUIDSidebarNameMap": {
    "eae1d848-54c7-4ae7-8bdf-c335ac651bd6": "default"
  },
  "playerUUIDState": {
    "eae1d848-54c7-4ae7-8bdf-c335ac651bd6": true
  }
}
```

- 两个 map 都以玩家 UUID 字符串为 key
- `playerUUIDSidebarNameMap`：玩家选择的侧栏 id
- `playerUUIDState`：侧栏开关
- 玩家改名不影响选择
- 旧格式（玩家名做 key）不迁移，按空数据处理

---

## 构建

```bash
# 编译 + 打 shadowJar
./gradlew build

# 产物
ls build/libs/Sidebar-1.0-SNAPSHOT-all.jar
```

Windows 用 `gradlew.bat`。

本地测试服：

```bash
./gradlew runServer
```

---

## 项目结构

```text
Sidebar/
├── api/                              # 独立 Java API 模块（扩展开发用）
│   └── src/main/java/org/ncc/sidebar/api/
├── src/main/kotlin/org/ncc/sidebar/
│   ├── Main.kt                       # 插件入口，装配各组件
│   ├── ConfigManager.kt              # config.yml / animation.yml / data.json
│   ├── SidebarFactory.kt             # 根据标题与行构建 Sidebar
│   ├── SidebarManager.kt             # 动画注册表
│   ├── Command.kt                    # /sb 命令
│   ├── EventManager.kt               # 加入 / 退出事件
│   ├── provider/                     # 内置 config 侧栏的 provider
│   ├── service/                      # 注册表、状态机、跨平台调度
│   └── Tasks/                        # 动画刷新、数据保存
├── docs/
│   ├── api-design.md                 # API 设计文档
│   └── api-usage.md                  # 扩展开发指南
└── build.gradle.kts
```

---

## 扩展开发

其他插件可以注册自己的侧栏，出现在 `/sb list` 与 `/sb switch` 中。侧栏内容与更新逻辑完全由扩展负责，核心只管理"谁在看哪个侧栏、什么时候切"。

### 发布 API 到本地

```bash
./gradlew :api:publishToMavenLocal
```

坐标：`org.ncc:sidebar-api:1.0-SNAPSHOT`

### 扩展侧最小依赖

```kotlin
repositories {
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.ncc:sidebar-api:1.0-SNAPSHOT")
}
```

扩展的 `plugin.yml` 必须写：

```yaml
depend: [Sidebar]
```

入口：

```java
SidebarApi api = Bukkit.getServicesManager().load(SidebarApi.class);
```

完整教程：**[docs/api-usage.md](docs/api-usage.md)**；设计细节：**[docs/api-design.md](docs/api-design.md)**。

### 注意

- 扩展不要 shade 或 relocate `org.ncc.sidebar.api` 与 `scoreboard-library`
- 必须使用核心提供的 `api.library()`，不要自己创建 `ScoreboardLibrary`
- `show` / `hide` 在核心串行线程回调，必须非阻塞
- 注册进 API 的 provider 只负责 sidebar 槽；belowName / playerList / teamSidebar 自管，不参与 `/sb switch`

---

## 已知限制

- 每个侧栏最多 15 行（原版客户端上限）
- 每行宽度受客户端限制，示例默认按约 42 字符说明，但代码不硬校验
- 核心只管理 sidebar 显示槽，其他显示槽由扩展自行负责
- 旧 `data.json` 不迁移；切换侧栏不会立即写盘，按 `data.save-interval` 定时保存
- 项目处于开发阶段，暂无自动化测试；扩展 API 仍可能调整

---

## 依赖与致谢

- [scoreboard-library](https://github.com/vytskalt/scoreboard-library)（megavex）提供侧栏、objective、team 的包级 API
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) 提供占位符
- Paper / Folia API
