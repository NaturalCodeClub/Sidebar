package org.ncc.sidebar

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventManager : Listener {
    // 事件监听器

    @EventHandler
    // 玩家加入时初始化 Sidebar
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Main.sidebarService.onJoin(event.player)
    }

    @EventHandler
    // 玩家退出时清理 Sidebar
    fun onPlayerQuit(event: PlayerQuitEvent) {
        Main.sidebarService.onQuit(event.player)
    }
}
