package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventManager : Listener {
    @EventHandler
    //Paper desparated it with some newer api but no javadoc on their website,wait to replace...
    fun onPlayerJoin(event: PlayerJoinEvent) {
//        sidebar.addPlayer(event.player)

        val player = event.player
        val sb: Sidebar =
            Main.configManager?.getPlayerSidebar(player)?: Main.configManager!!.sidebarMap[Main.configManager!!.defaultSidebarSelection]!!
        if(Main.configManager!!.playerState[player] == null) {
            Main.configManager!!.playerState[player] = true
        }
        if(Main.configManager!!.playerNameSidebarNameMap[player.name] == null) {
            Main.configManager!!.playerNameSidebarNameMap[player.name] = Main.configManager!!.defaultSidebarSelection
        }
        if(!Main.configManager!!.playerState[player]!!) return
        sb.addPlayer(player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
//        sidebar.removePlayer(event.player)
        val player = event.player
        val sb: Sidebar = Main.configManager!!.getPlayerSidebar(player)
        sb.removePlayer(player)
    }
}