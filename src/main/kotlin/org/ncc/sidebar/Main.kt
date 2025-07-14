package org.ncc.sidebar

import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() , Listener{
    lateinit var sbLib: ScoreboardLibrary
    lateinit var sidebar: Sidebar
    override fun onEnable() {
        try {
            sbLib = ScoreboardLibrary.loadScoreboardLibrary(this)
        }
        catch (e: NoPacketAdapterAvailableException){
            sbLib = NoopScoreboardLibrary()
            logger.warning("No packet adapter available, falling back to noop library")
        }
        sidebar = sbLib.createSidebar()

        sidebar.title(MiniMessage.miniMessage().deserialize("<red><bold>TEST</bold></red>"))
        sidebar.line(0, MiniMessage.miniMessage().deserialize("<green>line 1"))
        Bukkit.getPluginManager().registerEvents(this, this)

    }

    override fun onDisable() {
        sidebar.close()
        sbLib.close()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent){
        sidebar.addPlayer(event.player)
    }
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent){
        sidebar.removePlayer(event.player)
    }
}
