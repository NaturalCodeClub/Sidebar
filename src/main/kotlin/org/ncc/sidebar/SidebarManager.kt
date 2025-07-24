package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.entity.Player

class SidebarManager {

    val playerSidebarMap = mutableMapOf<Player, Sidebar>()

    fun getPlayerSidebar(player: String): Sidebar{
        return null!!
    }

    fun setPlayerSidebar(player: Player, sidebar: Sidebar){
        playerSidebarMap[player] = sidebar
    }

}