package org.ncc.sidebar

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventManager: Listener {
    @EventHandler
    //Paper desparated it with some newer api but no javadoc on their website,wait to replace...
    fun onPlayerJoin(event: PlayerJoinEvent) {
//        sidebar.addPlayer(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
//        sidebar.removePlayer(event.player)
    }
}