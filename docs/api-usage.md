# Sidebar 扩展开发指南

> 面向插件作者：写一个扩展插件，把自定义侧栏接进 Sidebar，并出现在 `/sb list` / `/sb switch` 里。
> 设计细节见 [api-design.md](api-design.md)。API 坐标：`org.ncc:sidebar-api:1.0-SNAPSHOT`。

---

## 0. 前置条件

- 服务端：Paper 1.21 或 Folia 1.21（Sidebar 本体标了 `folia-supported: true`）
- Java 21
- Sidebar 插件已安装并正常启用
- 扩展插件用 Java 或 Kotlin 都可以；API 本身是纯 Java 接口

---

## 1. 添加依赖

### 1.1 让本地 Maven 有 API 包

在 Sidebar 仓库里执行一次：

```text
./gradlew :api:publishToMavenLocal
```

产物安装到 `~/.m2/repository/org/ncc/sidebar-api/1.0-SNAPSHOT/`。

### 1.2 扩展的 build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.ncc:sidebar-api:1.0-SNAPSHOT")
}
```

关键点：

- 用 `compileOnly`，不要 `implementation`。API 类由 Sidebar 本体在运行时提供
- **不要**把 `org.ncc.sidebar.api` 或 `scoreboard-library` shade 进自己的 jar
- 不要自己加 `net.megavex:scoreboard-library-*` 依赖

### 1.3 plugin.yml

```yaml
name: MyArena
version: '1.0'
main: org.example.arena.ArenaPlugin
api-version: '1.21'
depend: [Sidebar]
```

`depend` 保证 Sidebar 先加载，扩展 `onEnable` 里才拿得到服务。写成 `softdepend` 会拿到 `null`。

---

## 2. 拿到 SidebarApi

```java
SidebarApi api = Bukkit.getServicesManager().load(SidebarApi.class);
if (api == null) {
    getLogger().severe("Sidebar 插件未加载，侧栏功能不可用");
    return;
}
```

后面所有操作都通过这个 `api` 对象。

---

## 3. 第一个侧栏（所有玩家共享一份）

适合内容固定、不含 `%placeholder%` 的侧栏。

### 3.1 Provider

示例省略 import 与包名；`Sidebar` 来自 `net.megavex.scoreboardlibrary.api.sidebar.Sidebar`，编译期通过 API 的传递依赖带入。

```java
public final class ArenaSidebar implements SidebarProvider {

    private final SidebarApi api;
    private final Sidebar sidebar;

    public ArenaSidebar(SidebarApi api) {
        this.api = api;
        this.sidebar = api.library().createSidebar();
        this.sidebar.title(api.miniMessage().deserialize("<gold>竞技场"));
        this.sidebar.line(1, Component.text("击杀: 0"));
    }

    @Override
    public String id() {
        return "arena";
    }

    @Override
    public String description() {
        return "竞技场计分板";
    }

    @Override
    public String permission() {
        return "myarena.sidebar";
    }

    @Override
    public void show(Player player) {
        sidebar.addPlayer(player);
    }

    @Override
    public void hide(Player player) {
        sidebar.removePlayer(player);
    }

    public void close() {
        sidebar.close();
    }
}
```

说明：

- `id()` 返回裸 id。注册后展示为 `myarena:arena`（插件名小写 + 冒号 + id）
- `permission()` 可选，返回 `null` 表示不限制
- `isAvailable(Player)` 可选，用来做"当前世界才能看"这类判断
- `show` / `hide` 必须幂等，可能被重复调用

### 3.2 注册与清理

```java
public final class ArenaPlugin extends JavaPlugin {

    private SidebarApi api;
    private ArenaSidebar arenaSidebar;

    @Override
    public void onEnable() {
        api = Bukkit.getServicesManager().load(SidebarApi.class);
        if (api == null) {
            getLogger().severe("Sidebar 插件未加载");
            return;
        }
        arenaSidebar = new ArenaSidebar(api);
        api.register(this, arenaSidebar);
    }

    @Override
    public void onDisable() {
        if (api != null && arenaSidebar != null) {
            api.unregister("arena");
            arenaSidebar.close();
        }
    }
}
```

**顺序要注意**：先 `unregister`，再 `close`。注销时核心会把仍显示该侧栏的玩家迁移到默认侧栏，此时 provider 还需要能正常 `hide`。Folia 上这次迁移是异步排队的；如果扩展紧接着就关闭了资源，导致 `hide` 失败，核心只会记日志并继续完成迁移。

---

## 4. 每个玩家显示不同内容

适合含 `%player_name%` 之类占位符、或内容随玩家变化的侧栏。

### 4.1 每位玩家一份 Sidebar

```java
public final class StatsSidebar implements SidebarProvider {

    private final SidebarApi api;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();

    public StatsSidebar(SidebarApi api) {
        this.api = api;
    }

    @Override
    public String id() {
        return "stats";
    }

    @Override
    public String description() {
        return "个人数据";
    }

    @Override
    public void show(Player player) {
        Sidebar sidebar = api.library().createSidebar();
        sidebar.title(api.miniMessage().deserialize("<aqua>我的数据"));
        sidebar.line(1, Component.text("玩家: " + player.getName()));
        sidebar.line(2, Component.text("击杀: 0"));
        sidebar.addPlayer(player);
        sidebars.put(player.getUniqueId(), sidebar);
    }

    @Override
    public void hide(Player player) {
        Sidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) {
            sidebar.removePlayer(player);
            sidebar.close();
        }
    }

    public Sidebar sidebarOf(Player player) {
        return sidebars.get(player.getUniqueId());
    }

    public void closeAll() {
        for (Sidebar sidebar : sidebars.values()) {
            sidebar.close();
        }
        sidebars.clear();
    }
}
```

- 每次 `show` 重建实例，`hide` 负责关闭，避免泄漏
- 不要在 `show` 里读玩家坐标、背包、世界；见第 7 章线程契约

### 4.2 更新内容

下面的代码是片段，`statsSidebar` 指你插件里持有的 provider 实例，`player` 指目标玩家。

```java
// 自己开的异步定时器不能直接改 Sidebar，必须回到串行线程
Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
    int kills = fetchKills();
    api.runSerialized(() -> {
        Sidebar sidebar = statsSidebar.sidebarOf(player);
        if (sidebar != null) {
            sidebar.line(2, Component.text("击杀: " + kills));
        }
    });
}, 0L, 40L);
```

Folia 下不能用 `Bukkit.getScheduler()`，改用 Folia 的调度器；取到数据后同样通过 `runSerialized` 回串行线程。

需要玩家实时数据（位置、背包等）：

```java
api.runForPlayer(player, () -> {
    int health = (int) player.getHealth();
    api.runSerialized(() -> {
        Sidebar sidebar = statsSidebar.sidebarOf(player);
        if (sidebar != null) {
            sidebar.line(3, Component.text("生命: " + health));
        }
    });
});
```

`runForPlayer` 里只读玩家数据，**不要**操作 Sidebar / ObjectiveManager。

---

## 5. 监听侧栏切换

```java
public final class SwitchListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onSwitch(SidebarSwitchEvent event) {
        if ("myarena:arena".equals(event.getToId())) {
            // 玩家切到竞技场侧栏
        }
        if ("myarena:arena".equals(event.getFromId())) {
            // 玩家从竞技场侧栏切走
        }
    }
}
```

注册监听和普通 Bukkit 事件一样：

```java
Bukkit.getPluginManager().registerEvents(new SwitchListener(), this);
```

事件字段：

| 方法 | 含义 |
|---|---|
| `getPlayer()` | 玩家 |
| `getFromId()` | 切换前 id，可能为 `null` |
| `getToId()` | 切换后 id |
| `getCause()` | `COMMAND` / `API` / `FALLBACK` / `RELOAD` / `UNREGISTER` |
| `isCancelled()` | 是否被取消 |

取消规则：只有 `COMMAND` 和 `API` 两种来源的 `setCancelled(true)` 会真正中止切换；其余来源只通知，不可阻断。

事件在 Sidebar 的串行线程触发，监听器里不要依赖玩家所在区域线程。

---

## 6. 命令、权限与展示

注册成功后：

- `/sb list` 显示所有 provider
- `/sb switch myarena:arena` 切换
- Tab 补全会列出玩家有权限且当前可用的 provider

规则：

- 展示 id：扩展为 `插件名小写:id`；内置侧栏是裸 id（如 `default`）
- `/sb switch` 接受全名；如果裸 id 只匹配到一个 provider，也接受裸 id
- 有 `permission()` 的 provider：无权限玩家在 list / tab 中看不到，直接 switch 会提示无权限
- `isAvailable` 返回 `false` 时同样不可选

编程方式切换：

```java
api.select(player, "myarena:arena");
```

异步执行，和 `/sb switch` 走同一条路径（含权限、可用性、回滚）。

---

## 7. 线程契约（必须遵守）

Sidebar 会把所有 provider 回调放到**同一条串行线程**上：

- Paper：主线程
- Folia：global region 线程

因此：

1. `show` / `hide` 必须**非阻塞**，不要读方块、改背包、传送、等 IO
2. 回调里禁止访问玩家世界状态；只允许 UUID、名字、权限、库对象操作
3. 需要世界状态 → `api.runForPlayer(player, ...)` 读取，再回 `api.runSerialized(...)` 操作侧栏
4. 自己的定时任务改 Sidebar / ObjectiveManager 前，必须 `api.runSerialized(...)`
5. **不要**在回调里调用 `library().close()`

Folia 上违反线程归属会直接抛区域线程异常；Paper 上不会立刻报错，但会卡主线程。

---

## 8. 生命周期

| 时机 | 扩展该做什么 |
|---|---|
| `onEnable` | 取 `SidebarApi`；创建 provider；`api.register` |
| 玩家进服 | 核心决定是否调用你的 `show`，扩展不用监听 Join |
| 玩家退服 | 核心调用你的 `hide`，扩展清理该玩家状态 |
| 玩家 `/sb switch` | 核心先调用旧 provider 的 `hide`，再调用新 provider 的 `show` |
| `/sb reload` | 只重建核心内置 provider；扩展的 provider 和已显示玩家不受影响 |
| 扩展 `onDisable` | `api.unregister(id)`，然后关闭自己的 Sidebar 对象 |
| 插件被卸载 / 重载 | 核心把受影响的玩家迁移到默认 provider |

---

## 9. 数据与持久化

- Sidebar 只持久化"玩家选了哪个 provider id"，按 UUID 存在 `data.json`，扩展不需要关心
- 扩展 id 必须稳定：玩家重启后按同一个 id 恢复
- 扩展自己的业务数据（击杀数、任务进度等）自己存，不要写 `data.json`
- 核心不保证 provider 一定存在：玩家选了某个扩展侧栏，但扩展没装时，核心静默回退默认侧栏并保留选择

---

## 10. 限制

- 注册进 API 的 provider **只能操作 sidebar 显示槽**
- `objectiveManager` 的 `belowName` / `playerList` / `teamSidebar` 扩展可以自己用，但不要注册成 provider，也不归 `/sb switch` 管
- 一个玩家同一时刻只有一个 active provider，由核心保证
- 不要自己创建 `ScoreboardLibrary` 实例，必须用 `api.library()`
- 不要 `close` 共享库；只有 Sidebar 本体能关闭
- 核心加载失败退化为 Noop 库时，侧栏不会显示；用 `api.isNativeLibrary()` 判断

---

## 11. 排错清单

**`/sb list` 看不到我的侧栏**

1. 扩展 `plugin.yml` 写了 `depend: [Sidebar]` 吗
2. `register` 在 `onEnable` 里调了吗
3. 玩家有 `permission()` 返回的权限吗
4. `isAvailable` 是不是返回了 `false`

**`/sb switch` 提示不存在**

- 用全名 `插件名小写:id` 试一次
- id 是否和 `provider.id()` 完全一致

**切过去但屏幕没变化**

- `api.isNativeLibrary()` 是否为 `true`
- `show` 里有没有调用 `sidebar.addPlayer(player)`
- 是否超过 15 行（原版客户端上限）
- 用 `/sb reload` 后重新切一次

**日志出现 ClassCastException / NoSuchMethodError**

- 多半是自己 shade 了 API 或 scoreboard-library，去掉依赖重新编译
- 或扩展编译的 API 版本高于本体运行版本

**事件收不到**

- `SidebarSwitchEvent` 被 shade 进扩展 jar 了，导致类不是同一个；不要 shade API

**注销后玩家显示变回默认**

- 这是预期行为，核心会把玩家迁移到默认 provider

---

## 12. 最小可用模板

一个完整的扩展骨架：

```text
src/main/java/org/example/myarena/
├── ArenaPlugin.java        # onEnable 取 API、注册 provider
├── ArenaSidebar.java       # 实现 SidebarProvider
└── SwitchListener.java     # 可选，监听切换
```

```java
public final class ArenaPlugin extends JavaPlugin {

    private SidebarApi api;

    @Override
    public void onEnable() {
        api = Bukkit.getServicesManager().load(SidebarApi.class);
        if (api == null) {
            getLogger().severe("Sidebar 插件未加载");
            return;
        }
        api.register(this, new ArenaSidebar(api));
        Bukkit.getPluginManager().registerEvents(new SwitchListener(), this);
    }

    @Override
    public void onDisable() {
        if (api != null) {
            api.unregister("arena");
        }
    }
}
```

---

## 13. 版本兼容

- API 坐标与 Sidebar 本体版本同步，当前都是 `1.0-SNAPSHOT`
- 次版本只加方法，扩展不用改；主版本可能改行为，需要重编
- 不要实现 API 模块以外的内部类（`org.ncc.sidebar.service` 等），那些不保证稳定
