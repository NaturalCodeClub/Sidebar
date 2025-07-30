package org.ncc.sidebar

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import org.bukkit.block.sign.Side
import org.bukkit.entity.Player

class SidebarFactory(
    val title: String,
    val lines: List<String>,
    val updateInterval: Int
) {
    val playerSidebarMap = mutableMapOf<Player, Sidebar>()

    override fun toString(): String {
        return "SidebarFactory(title=$title, lines=$lines, updateInterval=$updateInterval)"
    }

    fun buildSidebar(player: Player): Sidebar {
        val sb = Main.sbLib!!.createSidebar()
        sb.title(MiniMessage.miniMessage().deserialize(PlaceholderAPI.setPlaceholders(player, title)))
        val sidebarComponentBuilder: SidebarComponent.Builder = SidebarComponent.builder()
        for(str in lines){
            sidebarComponentBuilder.addStaticLine(MiniMessage.miniMessage().deserialize(PlaceholderAPI.setPlaceholders(player,str)))
        }
        val sidebarComponent = sidebarComponentBuilder.build()
        val sidebarLayout = ComponentSidebarLayout(SidebarComponent.staticLine(MiniMessage.miniMessage().deserialize(PlaceholderAPI.setPlaceholders(player,title))),
            sidebarComponent)
        sidebarLayout.apply(sb)
        return sb
    }

    fun addPlayerSidebar(player: Player, Sidebar: Sidebar) = playerSidebarMap.put(player, Sidebar)
    fun removePlayerSidebar(player: Player, Sidebar: Sidebar) = playerSidebarMap.remove(player, Sidebar)
}