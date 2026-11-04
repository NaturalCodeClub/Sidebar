package org.ncc.sidebar

import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import org.ncc.sidebar.Tasks.SaveDataTaskManager
import org.ncc.sidebar.Tasks.SidebarUpdateTasks
import org.ncc.sidebar.api.SidebarApi
import org.ncc.sidebar.service.SerialDispatcher
import org.ncc.sidebar.service.SidebarService

class Main : JavaPlugin() {
    // 插件主类

    // 单例与核心实例
    companion object {
        lateinit var instance: JavaPlugin
        lateinit var configManager: ConfigManager
        lateinit var sidebarManager: SidebarManager
        lateinit var sbLib: ScoreboardLibrary
        lateinit var dispatcher: SerialDispatcher
        lateinit var sidebarService: SidebarService
        var isNativeLibrary: Boolean = false
        val miniMessage: MiniMessage = MiniMessage.miniMessage()
    }

    // 插件启用
    override fun onEnable() {
        instance = this
        dispatcher = SerialDispatcher(this)
        sidebarManager = SidebarManager()
        isNativeLibrary = try {
            sbLib = ScoreboardLibrary.loadScoreboardLibrary(this)
            true
        } catch (e: NoPacketAdapterAvailableException) {
            // 没有数据包适配器时退化为空操作库，防止插件崩溃
            // 使用 NoopScoreboardLibrary 避免直接抛异常导致插件加载失败
            sbLib = NoopScoreboardLibrary()
            logger.warning(
                "No packet adapter available, falling back to noop library.\n" +
                    "Check whether game version is too old or too new."
            )
            false
        }

        configManager = ConfigManager()
        configManager.initConfig()
        configManager.loadConfig()
        configManager.initData()
        configManager.loadData()

        sidebarService = SidebarService(this, configManager, sbLib, dispatcher, isNativeLibrary)
        configManager.getSidebar(config, logger)
        sidebarService.registerBuiltIns()

        // 先注册内置 provider 再暴露服务，扩展在 onEnable 拿到服务时内置侧栏已可用
        Bukkit.getServicesManager().register(SidebarApi::class.java, sidebarService, this, ServicePriority.Normal)
        Bukkit.getPluginManager().registerEvents(EventManager(), this)
        Bukkit.getPluginCommand("sidebar")?.setExecutor(Command())

        SaveDataTaskManager.start()
        SidebarUpdateTasks.start()

        // 兼容热加载场景：启用时已经在线的玩家立即绑定侧栏
        for (player in Bukkit.getOnlinePlayers()) sidebarService.onJoin(player)
    }

    // 插件禁用
    override fun onDisable() {
        SaveDataTaskManager.close()
        SidebarUpdateTasks.close()
        sidebarService.shutdown()
        Bukkit.getServicesManager().unregisterAll(this)
        configManager.cleanConfigResource()
        sbLib.close()
    }
}
