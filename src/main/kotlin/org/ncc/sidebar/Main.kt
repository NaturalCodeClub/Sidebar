package org.ncc.sidebar

import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin


class Main : JavaPlugin(), Listener {
    // 插件主类

    // 单例与核心实例
    companion object {
        var instance: JavaPlugin? = null
        var configManager: ConfigManager? = null
        var sidebarManager: SidebarManager? = null
        var sbLib: ScoreboardLibrary? = null
        val miniMessage = MiniMessage.miniMessage()
    }

    // 插件启用
    override fun onEnable() {
        instance = this
        sidebarManager = SidebarManager()
        try {
            sbLib = ScoreboardLibrary.loadScoreboardLibrary(this)
        } catch (e: NoPacketAdapterAvailableException) {
            // 没有数据包适配器时退化为空操作库，防止插件崩溃
            // 使用 NoopScoreboardLibrary 避免直接抛异常导致插件加载失败
            sbLib = NoopScoreboardLibrary()
            logger.warning("No packet adapter available, falling back to noop library.\n" + e.message)
        }
        configManager = ConfigManager()
        configManager!!.initConfig()
        configManager!!.loadConfig()
        configManager!!.initData()
        configManager!!.loadData()
        configManager!!.getSidebar(config, logger)

        Bukkit.getPluginManager().registerEvents(EventManager(), this)
        Bukkit.getPluginCommand("sidebar")?.setExecutor(Command())

    }

    // 插件禁用
    override fun onDisable() {
        sidebarManager!!.onPluginClose()
        configManager!!.closeConfigResource()
        configManager!!.closeDataResource()
        sbLib!!.close()
    }


}
