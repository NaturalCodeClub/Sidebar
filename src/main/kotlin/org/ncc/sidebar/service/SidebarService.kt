package org.ncc.sidebar.service

import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.ncc.sidebar.ConfigManager
import org.ncc.sidebar.Main
import org.ncc.sidebar.api.SidebarApi
import org.ncc.sidebar.api.SidebarProvider
import org.ncc.sidebar.api.event.SidebarSwitchEvent
import org.ncc.sidebar.provider.ConfigSidebarProvider
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SidebarService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val sbLib: ScoreboardLibrary,
    private val dispatcher: SerialDispatcher,
    private val nativeLibrary: Boolean
) : SidebarApi {

    data class Entry(
        val displayId: String,
        val bareId: String,
        val provider: SidebarProvider,
        val onRemove: (() -> Unit)?
    )

    data class ProviderView(val id: String, val provider: SidebarProvider)

    enum class SelectOutcome { SUCCESS, DISABLED_SAVED, SAME, NOT_FOUND, NO_PERMISSION, UNAVAILABLE, CANCELLED, FAILED }

    private val registry = ConcurrentHashMap<String, Entry>()

    // 运行时正在显示的 provider，id 不持久化
    private val activeByUuid = ConcurrentHashMap<UUID, String>()

    override fun library(): ScoreboardLibrary = sbLib

    override fun isNativeLibrary(): Boolean = nativeLibrary

    override fun miniMessage(): MiniMessage = Main.miniMessage

    override fun register(owner: Plugin, provider: SidebarProvider) {
        val bareId = provider.id()
        // 扩展 id 统一加插件名前缀，避免不同插件注册同名侧栏互相覆盖
        val displayId = owner.name.lowercase() + ":" + bareId
        val previous = registry.putIfAbsent(displayId, Entry(displayId, bareId, provider, null))
        if (previous != null) {
            plugin.logger.warning("Sidebar provider " + displayId + " already registered, ignored.")
        }
    }

    override fun unregister(id: String): Boolean {
        val entry = resolve(id) ?: return false
        if (!registry.remove(entry.displayId, entry)) return false

        // 迁移回串行线程：旧 provider 在扩展 onDisable 后可能已失效，hide 失败只记日志
        dispatcher.execute {
            val fallback = resolve(configManager.defaultSidebarSelection)
            if (fallback != null) {
                for (player in Bukkit.getOnlinePlayers()) {
                    if (activeByUuid[player.uniqueId] != entry.displayId) continue
                    fireSwitch(player, entry.displayId, fallback.displayId, SidebarSwitchEvent.Cause.UNREGISTER)
                    hideEntry(player, entry)
                    activeByUuid.remove(player.uniqueId)
                    configManager.playerUUIDSidebarNameMap[player.uniqueId.toString()] = fallback.displayId
                    activate(player, fallback)
                }
            } else {
                plugin.logger.severe("Default sidebar provider is missing while unregistering " + entry.displayId)
                for (player in Bukkit.getOnlinePlayers()) {
                    if (activeByUuid[player.uniqueId] != entry.displayId) continue
                    hideEntry(player, entry)
                    activeByUuid.remove(player.uniqueId)
                }
            }
            entry.onRemove?.invoke()
        }
        return true
    }

    override fun select(player: Player, id: String) {
        select(player, id, SidebarSwitchEvent.Cause.API, null)
    }

    override fun selection(player: Player): Optional<String> =
        Optional.ofNullable(configManager.playerUUIDSidebarNameMap[player.uniqueId.toString()])

    override fun isEnabled(player: Player): Boolean =
        configManager.playerState[player.uniqueId.toString()] ?: true

    override fun runSerialized(task: Runnable) {
        dispatcher.execute { task.run() }
    }

    override fun runForPlayer(player: Player, task: Runnable) {
        dispatcher.executeForPlayer(player) { task.run() }
    }

    fun select(
        player: Player,
        id: String,
        cause: SidebarSwitchEvent.Cause,
        notify: ((SelectOutcome) -> Unit)? = null
    ) {
        // 命令与 API 可能来自任意线程，统一投递到串行线程再碰库对象
        dispatcher.execute { selectInternal(player, id, cause, notify) }
    }

    fun toggle(player: Player, notify: ((Boolean) -> Unit)? = null) {
        dispatcher.execute {
            if (!player.isOnline) return@execute
            val uuidKey = player.uniqueId.toString()
            val newState = !(configManager.playerState[uuidKey] ?: true)
            configManager.playerState[uuidKey] = newState
            if (newState) {
                val selectedId =
                    configManager.playerUUIDSidebarNameMap[uuidKey] ?: configManager.defaultSidebarSelection
                val entry = resolve(selectedId) ?: resolve(configManager.defaultSidebarSelection)
                if (entry == null) {
                    plugin.logger.severe("Sidebar provider not found: " + selectedId)
                } else {
                    activate(player, entry)
                }
            } else {
                deactivate(player)
            }
            notify?.invoke(newState)
        }
    }

    fun onJoin(player: Player) {
        dispatcher.execute {
            if (!player.isOnline) return@execute
            val uuidKey = player.uniqueId.toString()
            // 新玩家缺省开启；已有数据保持原值
            configManager.playerState.putIfAbsent(uuidKey, true)
            configManager.playerUUIDSidebarNameMap.putIfAbsent(uuidKey, configManager.defaultSidebarSelection)
            if (configManager.playerState[uuidKey] != true) return@execute
            val selectedId =
                configManager.playerUUIDSidebarNameMap[uuidKey] ?: configManager.defaultSidebarSelection
            val entry = resolve(selectedId) ?: resolve(configManager.defaultSidebarSelection)
            if (entry == null) {
                plugin.logger.severe("Sidebar provider not found: " + selectedId)
                return@execute
            }
            activate(player, entry)
        }
    }

    fun onQuit(player: Player) {
        dispatcher.execute { deactivate(player) }
    }

    fun reload(notify: ((Boolean) -> Unit)? = null) {
        dispatcher.execute {
            try {
                // 先撤下并关闭旧的内置 provider，再重建，避免旧 Sidebar 残留动画缓存
                for (player in Bukkit.getOnlinePlayers()) {
                    val activeId = activeByUuid[player.uniqueId] ?: continue
                    val entry = registry[activeId] ?: continue
                    if (entry.onRemove == null) continue
                    deactivate(player)
                }
                for (entry in registry.values.filter { it.onRemove != null }) {
                    registry.remove(entry.displayId, entry)
                    entry.onRemove?.invoke()
                }
                configManager.reloadConfig()
                Main.sidebarManager.cleanSidebarResources()
                registerBuiltIns()
                for (player in Bukkit.getOnlinePlayers()) {
                    val uuidKey = player.uniqueId.toString()
                    if (configManager.playerState[uuidKey] != true) continue
                    val selectedId =
                        configManager.playerUUIDSidebarNameMap[uuidKey] ?: configManager.defaultSidebarSelection
                    val selected = resolve(selectedId)
                    val entry = selected ?: resolve(configManager.defaultSidebarSelection) ?: continue
                    // 选中项已被删除时按回退通知，否则按重载通知，方便扩展同步自己的界面
                    fireSwitch(
                        player,
                        null,
                        entry.displayId,
                        if (selected == null) SidebarSwitchEvent.Cause.FALLBACK else SidebarSwitchEvent.Cause.RELOAD
                    )
                    activate(player, entry)
                }
                notify?.invoke(true)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to reload sidebars: " + e.message)
                notify?.invoke(false)
            }
        }
    }

    fun registerBuiltIns() {
        for ((id, factory) in configManager.sidebarFactoryMap) {
            val provider = ConfigSidebarProvider(id, factory, configManager.descriptionMap[id] ?: "")
            registry[id] = Entry(id, id, provider, { provider.close() })
        }
    }

    fun visibleProviders(sender: CommandSender): List<ProviderView> =
        registry.values
            .filter { canSelect(sender, it.provider) }
            .sortedBy { it.displayId }
            .map { ProviderView(it.displayId, it.provider) }

    fun shutdown() {
        // 先撤显示再关闭 provider，避免关闭瞬间仍有侧栏指向已释放的实例
        for (player in Bukkit.getOnlinePlayers()) {
            val activeId = activeByUuid[player.uniqueId] ?: continue
            val entry = registry[activeId] ?: continue
            hideEntry(player, entry)
        }
        activeByUuid.clear()
        for (entry in registry.values) {
            try {
                entry.onRemove?.invoke()
            } catch (e: Exception) {
                plugin.logger.warning("Failed to close sidebar provider " + entry.displayId + ": " + e.message)
            }
        }
        registry.clear()
    }

    private fun selectInternal(
        player: Player,
        id: String,
        cause: SidebarSwitchEvent.Cause,
        notify: ((SelectOutcome) -> Unit)?
    ) {
        if (!player.isOnline) return
        // 先校验存在性与可用性，再处理关闭状态，避免把无效 id 写进玩家数据
        val entry = resolve(id)
        if (entry == null) {
            notify?.invoke(SelectOutcome.NOT_FOUND)
            return
        }
        if (!canSelect(player, entry.provider)) {
            val permission = entry.provider.permission()
            notify?.invoke(
                if (permission != null && !player.hasPermission(permission)) SelectOutcome.NO_PERMISSION
                else SelectOutcome.UNAVAILABLE
            )
            return
        }
        val uuid = player.uniqueId
        val uuidKey = uuid.toString()
        // 关闭状态只保存选择，不显示
        if (configManager.playerState[uuidKey] != true) {
            configManager.playerUUIDSidebarNameMap[uuidKey] = entry.displayId
            notify?.invoke(SelectOutcome.DISABLED_SAVED)
            return
        }
        if (activeByUuid[uuid] == entry.displayId) {
            notify?.invoke(SelectOutcome.SAME)
            return
        }
        if (!fireSwitch(player, activeByUuid[uuid], entry.displayId, cause)) {
            notify?.invoke(SelectOutcome.CANCELLED)
            return
        }
        if (!activate(player, entry)) {
            notify?.invoke(SelectOutcome.FAILED)
            return
        }
        configManager.playerUUIDSidebarNameMap[uuidKey] = entry.displayId
        notify?.invoke(SelectOutcome.SUCCESS)
    }

    private fun activate(player: Player, entry: Entry): Boolean {
        if (!player.isOnline) return false
        val uuid = player.uniqueId
        val oldId = activeByUuid[uuid]
        if (oldId == entry.displayId) return true
        val old = oldId?.let { registry[it] }
        // 必须先撤下旧显示再挂新显示，否则 packet 侧栏与 objective 侧栏会抢同一个 sidebar 槽
        if (old != null) {
            hideEntry(player, old)
            activeByUuid.remove(uuid)
        }
        return try {
            entry.provider.show(player)
            activeByUuid[uuid] = entry.displayId
            true
        } catch (e: Exception) {
            plugin.logger.warning(
                "Sidebar provider " + entry.displayId + " failed to show for " + player.name + ": " + e.message
            )
            // 新 provider 显示失败时回滚旧 provider，保持玩家原有显示
            if (old != null) {
                try {
                    old.provider.show(player)
                    activeByUuid[uuid] = old.displayId
                } catch (e2: Exception) {
                    activeByUuid.remove(uuid)
                    plugin.logger.warning("Rollback to " + old.displayId + " failed: " + e2.message)
                }
            }
            false
        }
    }

    private fun deactivate(player: Player) {
        val uuid = player.uniqueId
        val oldId = activeByUuid.remove(uuid) ?: return
        val entry = registry[oldId] ?: return
        hideEntry(player, entry)
    }

    private fun hideEntry(player: Player, entry: Entry) {
        try {
            entry.provider.hide(player)
        } catch (e: Exception) {
            plugin.logger.warning(
                "Sidebar provider " + entry.displayId + " failed to hide for " + player.name + ": " + e.message
            )
        }
    }

    private fun fireSwitch(
        player: Player,
        fromId: String?,
        toId: String,
        cause: SidebarSwitchEvent.Cause
    ): Boolean {
        val event = SidebarSwitchEvent(player, fromId, toId, cause)
        Bukkit.getPluginManager().callEvent(event)
        // 只有命令与 API 允许监听器取消；回退/重载/注销若可取消会把玩家留在不一致状态
        return !(cause == SidebarSwitchEvent.Cause.COMMAND || cause == SidebarSwitchEvent.Cause.API) || !event.isCancelled
    }

    private fun resolve(id: String): Entry? {
        registry[id]?.let { return it }
        // 内置侧栏用裸 id；扩展只接受全名，或唯一匹配的裸 id
        if (':' in id) return null
        val matches = registry.values.filter { it.bareId == id }
        return if (matches.size == 1) matches[0] else null
    }

    private fun canSelect(sender: CommandSender, provider: SidebarProvider): Boolean {
        val permission = provider.permission()
        if (permission != null && !sender.hasPermission(permission)) return false
        // 控制台没有"当前世界/状态"概念，可用性判断只对玩家生效
        return sender !is Player || provider.isAvailable(sender)
    }
}
