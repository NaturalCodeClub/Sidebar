package org.ncc.sidebar.Tasks

import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation
import org.bukkit.Bukkit
import org.ncc.sidebar.Main
import java.util.function.Consumer

class SidebarUpdateTasks {
    companion object {
        private val cooldownTimestampMap = mutableMapOf<SidebarAnimation<Component>, Long>()

        fun start() {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(Main.instance!!, Consumer { _ ->
                val sm = Main.sidebarManager!!
                val map = sm.animationDataMap
                if (map.isEmpty()) {
                    if (Main.configManager!!.isDebugMode) {
                        println("[DEBUG] task - map identity=${System.identityHashCode(map)}, sm identity=${System.identityHashCode(sm)}")
                    }
                    return@Consumer
                }
                for ((animation, data) in map) {
                    if (data.includedSidebars.isEmpty()) continue
                    if (cooldownTimestampMap.containsKey(animation)) {
                        if (cooldownTimestampMap[animation]!! > System.currentTimeMillis()) continue
                        animation.nextFrame()
                        for ((layout, sidebar) in data.includedSidebars) {
                            layout.apply(sidebar)
                        }
                        val addedMS = if (data.updateIntervalMs > 5) data.updateIntervalMs else 5
                        cooldownTimestampMap[animation] = System.currentTimeMillis() + addedMS
                    } else {
                        animation.nextFrame()
                        for ((layout, sidebar) in data.includedSidebars) {
                            layout.apply(sidebar)
                        }
                        val addedMS = if (data.updateIntervalMs > 5) data.updateIntervalMs else 5
                        cooldownTimestampMap[animation] = System.currentTimeMillis() + addedMS
                    }
                }
            }, 1L, 1L)
        }

        fun close() {
            Bukkit.getGlobalRegionScheduler().cancelTasks(Main.instance!!)
            cooldownTimestampMap.clear()
        }
    }
}