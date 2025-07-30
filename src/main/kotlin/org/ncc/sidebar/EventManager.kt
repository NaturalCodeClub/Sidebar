package org.ncc.sidebar

import org.bukkit.Bukkit
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
        val offlinePlayer = Bukkit.getOfflinePlayer(player.uniqueId)
//        val originSb: Sidebar =
//            Main.configManager?.getPlayerSidebar(player)?: Main.configManager!!.sidebarMap[Main.configManager!!.defaultSidebarSelection]!!

        if(Main.configManager!!.playerState[offlinePlayer] == null) {
            Main.configManager!!.playerState[offlinePlayer] = true
        }
        if(Main.configManager!!.playerNameSidebarNameMap[player.name] == null) {
            Main.configManager!!.playerNameSidebarNameMap[player.name] = Main.configManager!!.defaultSidebarSelection
        }
        if(!Main.configManager!!.playerState[offlinePlayer]!!) return
        val sidebarFactory = Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[player.name]]
        val sidebar = sidebarFactory!!.buildSidebar(player)
        sidebar.addPlayer(player)
        Main.sidebarManager!!.playerSidebarMap.put(player, sidebar)
        Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[player.name]]!!.addPlayerSidebar(player,sidebar)
//        originSb.addPlayer(player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
//        sidebar.removePlayer(event.player)
        val player = event.player
        if(!Main.configManager!!.playerState[Bukkit.getOfflinePlayer(player.uniqueId)]!!) return
        val sidebar = Main.sidebarManager!!.playerSidebarMap[player]!!
        Main.sidebarManager!!.playerSidebarMap.remove(player)
        sidebar.removePlayer(player)
        Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[player.name]]!!.removePlayerSidebar(player,sidebar)
//        val sb: Sidebar = Main.configManager!!.getPlayerSidebar(player)
//        sb.removePlayer(player)
    }
}