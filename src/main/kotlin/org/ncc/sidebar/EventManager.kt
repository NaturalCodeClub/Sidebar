package org.ncc.sidebar

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventManager : Listener {
    // 事件监听器

    @EventHandler
    //Paper desparated it with some newer api but no Javadoc on their website,wait to replace...
    // 玩家加入时初始化 Sidebar
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // 首次加入时初始化 Sidebar 状态为开启
        if (Main.configManager!!.playerState[player] == null) {
            Main.configManager!!.playerState[player] = true
        }
        // 首次加入时分配默认 Sidebar
        if (Main.configManager!!.playerNameSidebarNameMap[player.name] == null) {
            Main.configManager!!.playerNameSidebarNameMap[player.name] = Main.configManager!!.defaultSidebarSelection
        }
        // 玩家已关闭 Sidebar 则不显示
        if (!Main.configManager!!.playerState[player]!!) return

        // 静态 Sidebar（无 PAPI 占位符）可共用全局实例，只需将玩家添加到该实例的观察列表
        if (Main.sidebarManager!!.simpleSidebarMap.containsKey(Main.configManager!!.playerNameSidebarNameMap[player.name])) {
            val sidebarName = Main.configManager!!.playerNameSidebarNameMap[player.name]
            Main.sidebarManager!!.simpleSidebarMap[sidebarName]!!.addPlayer(player)
            return
        }

        // 普通 Sidebar（含 PAPI 占位符）每位玩家的渲染结果不同，需为每位玩家构建独立实例
        val sidebarFactory =
            Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[player.name]]
        val sidebar = sidebarFactory!!.buildSidebar(player)
        sidebar.addPlayer(player)
        Main.sidebarManager!!.playerNormalSidebarMap[player] = sidebar
//        Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[player.name]]!!.addPlayerSidebar(
//            player,
//            sidebar
//        )
    }

    @EventHandler
    // 玩家退出时清理 Sidebar
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (!Main.configManager!!.playerState[player]!!) return

        // 静态 Sidebar 只需从全局实例移除玩家
        if (Main.sidebarManager!!.simpleSidebarMap.containsKey(Main.configManager!!.playerNameSidebarNameMap[player.name])) {
            val sidebarName = Main.configManager!!.playerNameSidebarNameMap[player.name]
            Main.sidebarManager!!.simpleSidebarMap[sidebarName]!!.removePlayer(player)
            return
        }

        // 普通 Sidebar 需清理玩家专属实例
        val sidebar = Main.sidebarManager!!.playerNormalSidebarMap[player]!!
        Main.sidebarManager!!.playerNormalSidebarMap.remove(player)
        sidebar.removePlayer(player)
//        Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[player.name]]!!.removePlayerSidebar(
//            player,
//            sidebar
//        )
    }
}