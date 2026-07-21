package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.entity.Player

class SidebarManager {

    val playerSidebarMap = mutableMapOf<Player, Sidebar>()
    val simpleSidebarSet = mutableSetOf<Sidebar>()

    fun getPlayerSidebar(player: Player): Sidebar {
        val sidebar = playerSidebarMap[player]
            ?: Main.configManager!!.sidebarFactoryMap[Main.configManager!!.defaultSidebarSelection]!!.buildSidebar(
                player
            )
        playerSidebarMap[player] = sidebar
        return sidebar
    }

    fun isSimpleSidebar(originList: List<String>): Boolean {
        for (str in originList) {
            if (Regex("%.*?%").containsMatchIn(str)) {
                return false
            }
        }
        return true
    }

    fun hasAnimatedLine(str: String): Boolean {
        return Regex("\\{.*?}").containsMatchIn(str)
    }

}