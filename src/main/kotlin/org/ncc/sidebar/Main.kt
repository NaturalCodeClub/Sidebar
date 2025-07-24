package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin


class Main : JavaPlugin(), Listener {
//    lateinit var sbLib: ScoreboardLibrary
//    lateinit var configManager: ConfigManager

    companion object{
        var instance: JavaPlugin? = null
        var configManager: ConfigManager? = null
        var sidebarManager : SidebarManager? = null
        var sbLib: ScoreboardLibrary? = null

//        fun getScoreboardLibrary(): ScoreboardLibrary{
//            return sbLib
//        }
//        fun getConfigManager(): ConfigManager{
//            return configManager
//        }
//        fun getInstance(): Main{
//            return instance
//        }
    }
//    lateinit var sidebar: Sidebar
    override fun onEnable() {
        instance = this
        sidebarManager = SidebarManager()
        try {
            sbLib = ScoreboardLibrary.loadScoreboardLibrary(this)
        } catch (e: NoPacketAdapterAvailableException) {
            sbLib = NoopScoreboardLibrary()
            logger.warning("No packet adapter available, falling back to noop library")
        }
        configManager = ConfigManager()
        configManager!!.initConfig()
        configManager!!.loadConfig()
        configManager!!.initData()
        configManager!!.loadData()
        configManager!!.getSidebar(config, logger)

//        sidebar = sbLib.createSidebar()

        Bukkit.getPluginManager().registerEvents(EventManager(), this)
        Bukkit.getPluginCommand("sidebar")?.setExecutor(Command())

    }

    override fun onDisable() {
//        sidebar.close()
        ConfigManager().closeSidebarResource()
        sbLib!!.close()
    }



}
