package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin(), Listener {
    lateinit var sbLib: ScoreboardLibrary
    lateinit var sidebar: Sidebar
    override fun onEnable() {
        try {
            sbLib = ScoreboardLibrary.loadScoreboardLibrary(this)
        } catch (e: NoPacketAdapterAvailableException) {
            sbLib = NoopScoreboardLibrary()
            logger.warning("No packet adapter available, falling back to noop library")
        }
        ConfigManager().initConfig()
        ConfigManager().loadConfig(config)
        sidebar = sbLib.createSidebar()

        Bukkit.getPluginManager().registerEvents(this, this)

    }

    override fun onDisable() {
        sidebar.close()
        sbLib.close()
    }

    fun getInstance(): JavaPlugin {
        return this
    }


}
