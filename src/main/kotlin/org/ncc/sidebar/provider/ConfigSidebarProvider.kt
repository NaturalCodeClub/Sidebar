package org.ncc.sidebar.provider

import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.entity.Player
import org.ncc.sidebar.SidebarFactory
import org.ncc.sidebar.api.SidebarProvider
import java.util.concurrent.ConcurrentHashMap

class ConfigSidebarProvider(
    private val id: String,
    private val factory: SidebarFactory,
    private val description: String
) : SidebarProvider {

    // 静态侧栏所有玩家共用一份实例
    private val sharedSidebar: Sidebar? = if (factory.isSimple) factory.buildSidebar() else null

    // 含 PAPI 的侧栏每位玩家渲染结果不同，各自一份实例
    private val playerSidebars = ConcurrentHashMap<Player, Sidebar>()

    override fun id(): String = id

    override fun description(): String = description

    override fun show(player: Player) {
        if (sharedSidebar != null) {
            sharedSidebar.addPlayer(player)
            return
        }
        val sidebar = factory.buildSidebar(player)
        sidebar.addPlayer(player)
        playerSidebars[player] = sidebar
    }

    override fun hide(player: Player) {
        if (sharedSidebar != null) {
            sharedSidebar.removePlayer(player)
            return
        }
        playerSidebars.remove(player)?.removePlayer(player)
    }

    fun close() {
        sharedSidebar?.close()
        for (sidebar in playerSidebars.values) sidebar.close()
        playerSidebars.clear()
    }
}
