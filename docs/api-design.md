# Sidebar 插件 API 设计文档

> 状态：v2 已实现（2026-11-02）。`api` 模块与本体 Provider 化已落地，接口签名与最终代码一致。
> 本文面向"没写过插件 API"的读者，第 0 章先把概念讲清楚，第 1 章开始才是设计。
> 数据格式有变更：侧栏选择按 UUID 存储，旧格式不迁移，见第 9 章。

---

## 0. 先讲人话：插件 API 到底是什么

### 0.1 一句话

API 就是**一份只有"插座"、没有"电器"的 jar 包**。核心插件负责造电器，扩展插件照着插座标准写插头，双方在运行时对接。

具体到本项目，会多出两个东西：

| 角色 | 是什么 | 谁维护 |
|---|---|---|
| 核心插件 `Sidebar` | 真正干活的 Bukkit 插件，管状态、管命令、管显示切换 | 你 |
| API 包 `sidebar-api` | 纯 Java 接口，没有任何实现逻辑 | 你，跟核心一起发版 |
| 扩展插件 | 别人写的插件，`depend: [Sidebar]`，注册自己的侧栏 | 第三方 |

扩展插件**不包含**核心插件的代码，它只在编译时看到接口，运行时从核心插件那里拿到实现。

### 0.2 为什么要有 API

现在侧栏内容写死在 `config.yml` 里，只能出"标题 + 若干行文本"。想做排行榜、任务进度、小游戏计分，就得改核心代码。有了 API，这些逻辑可以由独立插件实现，核心不用动。

### 0.3 五个必须懂的概念

**compileOnly（编译期依赖）**

Gradle 里写 `compileOnly("org.ncc:sidebar-api:...")` 表示：编译时我能看到这些接口，但打包时不要把 API 的类塞进我的 jar。因为运行时核心插件已经提供了一份，塞进来就重复了。

**shade / relocate（打包 / 改包名）**

- shade：把依赖库的 class 文件解压、塞进自己的 jar
- relocate：塞进去的同时把包名改掉（比如 `net.megavex` 改成 `myplugin.libs.megavex`），防止和别的插件撞名

本项目当前：**核心插件会把 scoreboard-library shade 进自己的 jar，并且不 relocate**。这个选择直接决定了 API 的玩法，见第 8 章。

**classloader（类加载器）**

Bukkit 给每个插件一个独立的类加载器。**两个插件里各有一份同名类，在 JVM 眼里就是两个不同的类**，互相不能赋值，传对象会抛 `ClassCastException`。

Bukkit 有一个全局类表作为兜底：一个插件找不到某个类时，会先查别的插件有没有已经加载过同名类。所以"先加载的赢"。这就是为什么文档要强制"扩展不要 shade 这个库"——一旦各自塞一份，谁先加载谁赢，行为不可预测。

**ServicesManager（服务登记处）**

Bukkit 自带的一个注册表。核心插件启动时把自己的实现登记进去：

```java
Bukkit.getServicesManager().register(
    SidebarApi.class,
    implementation,
    plugin,
    ServicePriority.Normal
);
```

扩展插件按类型取出来：

```java
SidebarApi api = Bukkit.getServicesManager().load(SidebarApi.class);
```

比自定义静态单例好在：加载顺序、重载、清理都由 Bukkit 管；也不会出现两套入口被人混用。

**Bukkit 事件**

扩展注册一个 `Listener`，核心在切换侧栏时 fire 一个事件对象，扩展就能收到。第 6 章细讲。

### 0.4 几个 Minecraft 术语

- **sidebar 槽**：游戏右侧那块计分板的位置。原版叫 display slot 0
- **objective**：原版计分板对象，一份标题 + 若干"条目 + 分数"
- **Folia**：Paper 的多线程分支。没有"主线程"，每个区域有自己的线程；另有一条 global region 线程，不拥有任何实体和区块
- **PAPI**：PlaceholderAPI，往文本里塞 `%player_name%` 这类变量

---

## 1. 目标与非目标

### 目标

- 扩展可以注册自己的侧栏，出现在 `/sb switch` 里，和内置侧栏统一管理
- 扩展自己决定侧栏长什么样、怎么更新、什么时候刷新
- 核心保证：一个玩家同一时刻只显示一个侧栏；切换有回滚；玩家退出、重载、扩展卸载都能正确收尾
- 开放完整的 scoreboard-library，扩展可以用 `Sidebar`，也可以用 `ObjectiveManager`
- 侧栏选择按 UUID 持久化（旧格式不迁移）

### 非目标

- 核心不渲染扩展的内容（不解析扩展的文本，不管扩展的刷新频率）
- 核心不做 GUI 选择界面
- 核心不管 sidebar 槽以外的显示（belowName、playerList、teamSidebar 归扩展自管，不进注册表）
- 核心不保证扩展代码不崩。扩展异常会被捕获 + 记日志 + 回滚，但不会替扩展保证正确性

---

## 2. 术语表

| 术语 | 含义 |
|---|---|
| provider | 扩展注册的一个侧栏实现，核心只认它的 `show` / `hide` |
| registry | 核心里的注册表，存所有 provider，按 id 查 |
| selection | 玩家"选了哪个侧栏"，持久化到 `data.json` |
| active | 玩家"当前真正显示着哪个侧栏"，运行时状态，可能为空 |
| enabled | 玩家的侧栏总开关，`/sb toggle` 控制 |
| 串行线程 | 所有 provider 回调统一执行的那一条线程（Paper 主线程 / Folia global region） |
| 独占 | 一个玩家同一时刻只有一个 active provider |

---

## 3. 模块划分

### 3.1 目录

```text
Sidebar/
├── api/                          # 纯 Java，无实现
│   └── src/main/java/org/ncc/sidebar/api/
│       ├── SidebarApi.java
│       ├── SidebarProvider.java
│       └── event/SidebarSwitchEvent.java
├── src/main/kotlin/org/ncc/sidebar/   # 核心插件，Kotlin
│   ├── Main.kt
│   ├── api/                      # 核心对 SidebarApi 的 Kotlin 实现
│   ├── provider/                 # 内置 provider（原 config.yml 那套）
│   └── ...
└── settings.gradle.kts
```

### 3.2 依赖关系

- `api` 模块：`compileOnly` paper-api、`api` scoreboard-library-api、`api` adventure-api
- 核心模块：`implementation` 依赖 `:api`，shadowJar 打包时把 api 的类一起塞进去
- 扩展插件：`compileOnly("org.ncc:sidebar-api:<版本>")`，插件 jar 里**不含** api

### 3.3 坐标与版本

- 坐标：`org.ncc:sidebar-api`
- 版本：跟插件版本走（当前 `1.0-SNAPSHOT`）
- 版本策略：主版本变化 = API break，扩展需要重新编译；次版本只加不删

### 3.4 plugin.yml 约定

扩展必须写：

```yaml
depend: [Sidebar]
```

`depend` 保证核心先加载，扩展 `onEnable` 里才拿得到 `SidebarApi`。写成 `softdepend` 会出问题：核心可能还没加载。

---

## 4. 核心模型

### 4.1 SidebarProvider

```java
package org.ncc.sidebar.api;

import org.bukkit.entity.Player;

public interface SidebarProvider {

    /** 侧栏 id。裸 id，核心自动加"插件名:"前缀，避免撞名。 */
    String id();

    /** /sb list 里显示的一行说明。 */
    String description();

    /** 可选权限。返回 null 表示不限制。无权限的玩家看不到、也不能切到这个侧栏。 */
    default String permission() { return null; }

    /** 当前条件下该侧栏是否可以显示。返回 false 时 /sb switch 拒绝，列表里过滤掉。 */
    default boolean isAvailable(Player player) { return true; }

    /** 开始显示。核心保证在串行线程调用，且调用前上一个 provider 已经 hide。 */
    void show(Player player);

    /** 停止显示。必须清理干净自己挂上去的东西。 */
    void hide(Player player);
}
```

逐条解释：

- `id()`：注册后对外全名是 `插件名:id`。比如插件 `MyArena` 注册 id `arena`，玩家看到的是 `MyArena:arena`。内置 config 侧栏保持裸 id（`default`），兼容旧数据
- `description()`：随便写，用于 `/sb list`
- `permission()`：只控制"能不能选"，不控制"能不能显示"。显示条件用 `isAvailable`
- `isAvailable()`：轻量判断，比如"玩家在当前世界"、"玩家在游戏中"。**核心把它当作准入门槛**，返回 false 就不让切
- `show()`：**必须幂等**。核心可能在玩家重连、reload 后重复调用
- `hide()`：**必须幂等**。可能对没有显示过的玩家调用

### 4.2 SidebarApi

```java
package org.ncc.sidebar.api;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public interface SidebarApi {

    /** 核心共享的 scoreboard-library 实例。可能是不工作的 Noop 实现，用 isNativeLibrary() 判断。 */
    ScoreboardLibrary library();

    /** 核心是否成功加载了真实的包适配器。false 表示当前服务端版本不支持，显示不会生效。 */
    boolean isNativeLibrary();

    /** 核心用的 MiniMessage 实例，扩展可以直接拿去解析文本。 */
    MiniMessage miniMessage();

    /** 注册一个 provider。owner 用于拼 id 前缀和日志。 */
    void register(Plugin owner, SidebarProvider provider);

    /** 注销。id 用注册时的裸 id 或全名都行。返回是否真的删掉了。 */
    boolean unregister(String id);

    /** 以编程方式切换某个玩家的侧栏，和 /sb switch 走同一条路径（含权限、可用性、回滚）。 */
    void select(Player player, String id);

    /** 玩家当前选中的 id。 */
    Optional<String> selection(Player player);

    /** 玩家的侧栏总开关状态。 */
    boolean isEnabled(Player player);

    /** 回到串行线程执行。扩展自己的 timer 要改库对象时必须用它。 */
    void runSerialized(Runnable task);

    /** 到该玩家的线程执行。只用来读玩家状态，里面禁止碰库对象。 */
    void runForPlayer(Player player, Runnable task);
}
```

### 4.3 独占规则

核心保证：**每个玩家同一时刻最多一个 active provider**。

流程固定为：先 `hide(旧)`，再 `show(新)`。

为什么核心必须管这件事：scoreboard-library 内部只保证**同类型内部**互斥。它给每个玩家开三条独立队列——sidebar、objectiveManager、teamManager。Sidebar 和"显示在 sidebar 槽的 ObjectiveManager"属于不同类型，库不会阻止它们同时显示，客户端最后收到的包赢，表现为闪烁或错乱。所以独占由核心负责。

### 4.4 作用域限制

注册进本 API 的 provider **只能操作 sidebar 显示槽**。

- 允许：`createSidebar()`；`createObjectiveManager()` 后 `display(ObjectiveDisplaySlot.sidebar(), ...)`
- 不允许：`ObjectiveDisplaySlot.belowName()`、`playerList()`、`teamSidebar()`

belowName 等其他槽的显示，扩展可以自己用 `library().createObjectiveManager()` 做，但**不要注册成 provider，也不参与 `/sb switch`**。核心不管，也不会帮你 hide。

原因：`/sb switch` 的语义是"换侧栏"。如果一个 provider 实际改的是玩家头顶数字，切换时右侧侧栏没变、头顶变了，玩家会懵。

---

## 5. 生命周期与状态机

### 5.1 三份状态

每个玩家有三份状态，**必须分开存**：

| 状态 | 含义 | 是否持久化 |
|---|---|---|
| `enabled` | 侧栏总开关 | 是 |
| `selectedId` | 玩家选的哪个侧栏 | 是 |
| `activeId` | 当前真正显示的哪个 | 否 |

为什么要分开：玩家可能关掉侧栏（`enabled=false`）但保留选择（`selectedId`）；也可能选了某个侧栏但该扩展没装（`selectedId` 有值、`activeId` 为空，回退显示默认）。

现有代码只有一个 `playerState`，且 switch 在关闭状态下直接不处理，属于已知缺陷。

### 5.2 动作表

| 触发 | 行为 |
|---|---|
| 玩家加入 | 校正 `selectedId`（不存在则静默回退默认，**保留**原选择）；`enabled` 默认 true；activate |
| 玩家退出 | `hide`，清 `activeId` |
| `/sb toggle` 关闭 | `hide`，`enabled=false`，保留 `selectedId` |
| `/sb toggle` 开启 | `enabled=true`，activate(`selectedId`) |
| `/sb switch <id>` | 见 5.3 |
| provider 被注销 | 受影响玩家：try `hide` 旧（异常只记日志）→ show 默认 → `selectedId` 改默认 |
| `/sb reload` | 只重建核心内置 provider；在线玩家按 `selectedId` 重新绑定；扩展侧栏不重建 |

"activate(id)" = 查 provider → 判权限 / 可用性 → `show` → 成功才写 `activeId`。

### 5.3 switch 的完整流程

```text
switch(player, target):

  if !enabled:
      selectedId = target
      保存
      提示"已保存，开启后生效"
      return

  if target == activeId:
      return                      # 幂等，不重建，避免动画相位重置

  provider = registry.get(target)
  if provider == null:
      提示"侧栏不存在"
      return

  if !checkPermission(player, provider): 提示无权限; return
  if !provider.isAvailable(player):     提示当前不可用; return

  old = registry.get(activeId)

  if old != null:
      try old.hide(player)
      catch e: 记日志，继续        # 旧的不干净也要继续切

  try:
      provider.show(player)
  catch e:
      记日志
      if old != null:
          try old.show(player)     # 回滚
          catch: activeId = null
          提示"切换失败"
      return

  activeId = target
  selectedId = target
  保存
  提示"更改成功"
```

要点：

- **隐藏旧的在显示新的之前**。反过来在 packet 侧栏和 objective 侧栏之间切换时会出现双显示
- **写入状态只发生在 show 成功之后**。先写状态再 show，失败就得回滚两份数据
- **同一个 id 直接返回**。否则每次切同一个侧栏都会重建实例、重启动画
- **关闭状态下也更新选择**。现在的代码是直接忽略，属于 bug

### 5.4 状态图

```text
             /sb switch X
   (none) ----------------> (X active)
      ^                        |
      |  hide 失败/扩展卸载     | /sb switch Y
      |                        v
   (fallback) <---------- (Y active)
        \                    /
         +---- toggle off ---+
```

（示意，实际以 5.2 / 5.3 的文字规则为准）

---

## 6. 事件

### 6.1 SidebarSwitchEvent

```java
public class SidebarSwitchEvent extends Event implements Cancellable {

    private final Player player;
    private final String fromId;     // 可能为 null
    private final String toId;
    private final Cause cause;
    private boolean cancelled;

    public enum Cause {
        COMMAND,      // 玩家执行 /sb switch
        API,          // 扩展调用 SidebarApi.select()
        FALLBACK,     // 选中的 provider 不存在，回退默认
        RELOAD,       // 核心重载后重新绑定
        UNREGISTER    // provider 被注销触发迁移
    }

    // getter / setCancelled ...
}
```

- 在**任何实际状态变更之前**触发
- `cancel()` 后整个切换中止，状态不动
- FALLBACK / UNREGISTER 也触发，让扩展知道自己的显示被拿走了
- toggle 不触发（不是"切换侧栏"，是开关）

### 6.2 监听示例

```java
public class SwitchListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onSwitch(SidebarSwitchEvent event) {
        if ("MyArena:arena".equals(event.getToId())) {
            startArenaUi(event.getPlayer());
        }
        if ("MyArena:arena".equals(event.getFromId())) {
            stopArenaUi(event.getPlayer());
        }
    }
}
```

注意：事件类在 `sidebar-api` 里，扩展**不要**把它 shade 进自己的 jar，否则会拿到两个不同的 `SidebarSwitchEvent` 类，监听永远收不到。

---

## 7. 线程模型（A1）

### 7.1 结论

所有 provider 回调、以及核心对库对象的操作，统一在**一条串行线程**执行：

- Paper：主线程
- Folia：global region 线程

玩家加入 / 退出事件在玩家所在区域线程触发，核心会 hop 到串行线程再处理。执行前必须判 `player.isOnline()`。

### 7.2 为什么不让扩展在玩家线程跑

因为强制共享同一个 `ScoreboardLibrary` 实例。库的 `Sidebar`、`ObjectiveManager` 文档明确写"非线程安全，同一时间只能一个线程使用"，实现里 `players` 是 `HashSet`。如果按玩家线程回调，不同玩家的区域线程会同时改同一批对象，轻则状态错乱、行文本不可见，重则 `IllegalStateException: displayable already registered`。

串行线程把并发关在核心，扩展不用加锁。代价是回调里碰不到世界状态。

### 7.3 扩展必须遵守

- `show` / `hide` 里**必须非阻塞**，禁止读方块、改背包、传送、调度同步任务
- 只允许纯数据操作：UUID、名字、权限判断、库对象操作
- 需要世界状态 → `api.runForPlayer(player, ...)` 去玩家线程读，**里面禁止碰库对象**
- 自己的 timer 要改 `Sidebar` / `ObjectiveManager` → 用 `api.runSerialized(...)` 回来
- **不要**自己 `loadScoreboardLibrary`，不要 shade 库

### 7.4 两段示例

```java
// 错误：自建异步 timer 直接改共享对象
Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
    sidebar.line(0, Component.text("tick"));   // 线程不安全
}, 0, 20);

// 正确
Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
    api.runSerialized(() -> sidebar.line(0, Component.text("tick")));
}, 0, 20);
```

```java
// 需要玩家位置：先去玩家线程取，再回串行线程用
api.runForPlayer(player, () -> {
    Location loc = player.getLocation();       // 只在玩家线程安全
    api.runSerialized(() -> {
        sidebar.line(1, Component.text(loc.getBlockX()));
    });
});
```

### 7.5 常见误解

- **Folia 的 `AsyncScheduler` 不是串行线程**。它是线程池，同一个任务的不同次执行可能落在不同线程
- Paper 的"主线程"在 Folia 上不存在，对应物是"玩家所属 region 线程"和"global region 线程"
- 库内部自己的刷新任务是异步的（Paper 用 `runTaskTimerAsynchronously`，Folia 用 `AsyncScheduler`）。这一点不需要扩展操心，但也不代表扩展可以随便多线程调用库

---

## 8. 与 scoreboard-library 的约束

以下每条都有具体原因，破坏任意一条都会得到难查的偶发 bug。

### 8.1 强制共享核心实例

- 扩展通过 `api.library()` 拿核心那份
- 扩展 **禁止** shade 或 relocate `scoreboard-library`
- 扩展 `compileOnly` 依赖它，运行时由核心的 classloader 提供
- 核心 **禁止** 在 shadowJar 里 relocate 这个库

原因：

1. **类加载器**：各自 shade 一份 → 两个同名类 → 核心传出的对象扩展接不住（`ClassCastException`），或者谁先加载谁赢
2. **队列不共享**：库的"同一玩家只显示一个 Sidebar"是 per 实例的队列。两个实例互不知道，两个包都会发出去
3. **内存与生命周期**：共享一份，核心 `onDisable` 统一 `close()`

### 8.2 版本

- 钉在 `2.8.x`，当前 `2.8.3`
- 次版本升级（2.8.4）安全；升到 2.9 / 3.0 视为 break，需要通知扩展作者重编
- 扩展编译的 API 版本必须小于等于核心运行版本

### 8.3 库能力速查

| 类 | 用途 |
|---|---|
| `library().createSidebar()` | 高层侧栏：组件行、动画、ScoreFormat，只能放 sidebar 槽 |
| `library().createObjectiveManager()` | 低层：多 objective、任意 display slot、条目 + 分数 |
| `library().createTeamManager()` | name tag 前后缀，跟侧栏无关 |

注意：`Sidebar` 底层就是一个 objective + sidebar 显示槽。所以 `Sidebar` 和"显示在 sidebar 槽的 `ObjectiveManager`"会抢同一个位置，核心独占规则必须覆盖二者。

### 8.4 数字隐藏

原版侧栏默认在每行右侧显示分数数字。要隐藏：

- `Sidebar`：`line(index, component, ScoreFormat.blank())`，或建布局时统一处理
- `ObjectiveManager`：`objective.defaultScoreFormat(ScoreFormat.blank())` 或每个 score 单独指定

`ScoreFormat` 只有 1.20.3+ 支持，低版本客户端该字段无效。

### 8.5 关闭

- `library().close()` **只有核心能调**
- 扩展在 `onDisable` 里只做自己的清理：`unregister(id)`、`sidebar.removePlayer(...)`、`sidebar.close()`
- Bukkit 先禁用依赖者，扩展的 `onDisable` 一定早于核心，顺序安全

---

## 9. 数据

### 9.1 数据结构

`data.json`：

```json
{
  "playerUUIDSidebarNameMap": { "eae1d848-54c7-4ae7-8bdf-c335ac651bd6": "default" },
  "playerUUIDState": { "eae1d848-54c7-4ae7-8bdf-c335ac651bd6": true }
}
```

两个 map 都以 UUID 字符串为 key，字段名与 JSON 字段一致。

### 9.2 规则

- 内置 provider 用裸 id（`default` 等），扩展 provider 存全名 `插件名:id`
- 玩家改名不影响选择（UUID 键）
- 读到未知 id：不崩、不删，静默回退默认显示，保留原选择
- 旧格式（玩家名键的 `playerNameSidebarNameMap`）不迁移，按空数据处理
- 数据由 `ConfigManager` 持有，`loadData()` / `saveData()` 仍由现有定时任务驱动，不在切换时额外写盘

### 9.3 已处理的旧问题

- 两个数据 map 改为 `ConcurrentHashMap`
- `saveData()` 序列化前做 map 拷贝，避免边写边读
- `/sb reload` 由 `SidebarService.reload()` 重建在线玩家显示
- 消除了 provider 缺失、quit 状态缺失处的 `!!`
- `/sb switch` 在关闭状态下会保存选择

---

## 10. 从零写一个扩展

### 10.1 build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal()   // 本地发布的 api 包
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.ncc:sidebar-api:1.0-SNAPSHOT")
}
```

不要加 `implementation("net.megavex:...")`，运行时有核心那份。

### 10.2 plugin.yml

```yaml
name: MyArena
version: '1.0'
main: org.example.arena.ArenaPlugin
api-version: '1.21'
depend: [Sidebar]
```

### 10.3 注册一个静态共享侧栏

```java
public final class ArenaSidebar implements SidebarProvider {

    private final SidebarApi api;
    private final Sidebar sidebar;   // 所有玩家共享一份

    public ArenaSidebar(SidebarApi api) {
        this.api = api;
        this.sidebar = api.library().createSidebar(3);
        sidebar.title(api.miniMessage().deserialize("<gold>竞技场"));
        sidebar.line(0, Component.text("击杀: 0"));
    }

    @Override public String id() { return "arena"; }
    @Override public String description() { return "竞技场计分板"; }
    @Override public String permission() { return "myarena.sidebar"; }

    @Override
    public void show(Player player) {
        sidebar.addPlayer(player);        // 核心保证串行线程，直接调
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

```java
@Override
public void onEnable() {
    SidebarApi api = Bukkit.getServicesManager().load(SidebarApi.class);
    if (api == null) {
        getLogger().severe("Sidebar 插件未加载");
        return;
    }
    arenaSidebar = new ArenaSidebar(api);
    api.register(this, arenaSidebar);
}

@Override
public void onDisable() {
    api.unregister("arena");
    arenaSidebar.close();
}
```

### 10.4 更新内容

```java
// 定时刷新（异步 timer 里必须回串行线程）
Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
    int kills = fetchKills();
    api.runSerialized(() ->
        arenaSidebar.sidebar.line(0, Component.text("击杀: " + kills))
    );
}, 0, 40);
```

### 10.5 监听切换

见 6.2。

---

## 11. 落地顺序

按这个顺序改，每步都能单独验证，不要求一次做完。

1. **抽接口 + 注册表，内置侧栏包成 provider**
   - 纯重构，行为不变
   - 验收：`/sb list`、`/sb switch`、进服退服和现在一致
2. **三态状态机 + switch 回滚 + provider 缺失回退**
   - 验收：关闭状态切换能保存选择；切到不存在的 id 有提示；provider show 抛异常能回滚
3. **并发容器 + 串行调度 + `runSerialized` / `runForPlayer`**
   - 验收：Folia 上多玩家并发进出服不报并发异常
4. **独立 api 模块 + ServicesManager 注册 + `SidebarSwitchEvent`**
   - 验收：示例扩展能注册、能切、能收事件
5. **文档与示例扩展**

---

## 12. 已知风险与待验证

| 项 | 说明 |
|---|---|
| Folia join/quit 事件线程 | 需实测事件在哪个线程触发，hop 到 global 的时机与玩家是否还在线 |
| 事件类跨 classloader | 扩展必须不 shade api，否则监听失效；文档已写，但无法强制 |
| provider 卸载时扩展已失效 | 核心对旧 provider 的 `hide` 可能抛异常，只能 try/catch + 记日志 |
| 强制共享库的限制 | 扩展想用自己 relocate 的库就不行，这是明确取舍 |
| reload 时在线玩家 | 旧 provider 实例被替换，玩家显示要重建，顺序不能错 |
| 扩展 id 与内置 id 撞名 | 内置是裸 id，扩展带命名空间前缀，理论上不撞；仍建议注册时校验 |

---

## 13. FAQ

**Q：为什么不能扩展自己 `loadScoreboardLibrary`？**

A：技术上能，但两个实例互不协调：同一玩家可能同时被两份库挂侧栏，包互相覆盖；而且类加载器不同时，核心传出的对象扩展接不住。强制共享是为了行为可预测。

**Q：API 为什么用 Java 写？**

A：扩展作者未必用 Kotlin；Java 接口对双方都最稳定。Kotlin 的 `MutableMap`、默认参数、data class 在 Java 侧很难用。核心用 Kotlin 实现 Java 接口完全没问题。

**Q：扩展能只用 Kotlin 写吗？**

A：能，API 是 Java 接口，Kotlin 实现无障碍。

**Q：为什么 `/sb list` 看不到我的侧栏？**

A：按顺序查——`depend: [Sidebar]` 写了吗；`register` 在 `onEnable` 里调了吗；玩家有 `permission()` 吗；`isAvailable` 是不是返回了 false。

**Q：我和别人注册同名 id 会怎样？**

A：id 会自动加插件名前缀，不会撞。落地时注册表仍应校验重复并拒绝。

**Q：扩展卸载时玩家还开着我的侧栏怎么办？**

A：在 `onDisable` 里先 `unregister`，核心会把受影响玩家迁回默认侧栏；你自己再清理实例。

**Q：能不能做一个"选择侧栏"的 GUI 菜单？**

A：可以，属于扩展范畴。用 `api.select(player, id)` 走和 `/sb switch` 一样的路径。

**Q：provider 的 `show` 里能发消息吗？**

A：不建议。串行线程不保证是玩家线程；`show` / `hide` 里只做显示相关的事。

---

## 附录 A：关键决策记录

| 决策 | 结论 | 理由 |
|---|---|---|
| 核心是否渲染扩展内容 | 否，只开放库 | 扩展要完整能力 |
| 是否开放完整 scoreboard-library | 是，强制共享实例 | 队列协调 + 类加载器 |
| 作用域 | 仅 sidebar 槽 | `/sb switch` 语义干净 |
| 线程模型 | A1，Paper 主线程 / Folia global region | 库非线程安全，核心串行化 |
| API 语言 | Java | 扩展作者友好 |
| API 形态 | 独立模块，版本跟插件 | 边界清晰 |
| 事件 | 提供 `SidebarSwitchEvent`，可取消 | 扩展需要同步 UI |
| 数据 | 侧栏选择按 UUID 存储，不迁移旧格式 | 开发阶段，简化实现 |
