package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.entity.Player

class SidebarManager {

    val playerSidebarMap = mutableMapOf<Player, Sidebar>()

    fun getPlayerSidebar(player: Player): Sidebar{
        val sidebar = playerSidebarMap[player]?: Main.configManager!!.sidebarFactoryMap[Main.configManager!!.defaultSidebarSelection]!!.buildSidebar(player)
        playerSidebarMap.put(player, sidebar)
        return sidebar
    }

    fun update(sidebarFactory: SidebarFactory){
        for((player,sidebar) in playerSidebarMap){
            val sb = sidebarFactory.buildSidebar(player)
            sb.addPlayer(player)
            sidebar.removePlayer(player)
            playerSidebarMap[player] = sb
        }
    }


}