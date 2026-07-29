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
            // 每 1 tick（50ms）轮询一次，各动画通过 cooldown 按各自 interval 错开推进
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
                        // 冷却未结束则跳过，等待下次轮询
                        if (cooldownTimestampMap[animation]!! > System.currentTimeMillis()) continue
                        animation.nextFrame()
                        // 将更新后的帧重新 apply 到所有引用此动画的 Sidebar
                        for ((layout, sidebar) in data.includedSidebars) {
                            layout.apply(sidebar)
                        }
                        // 设置下次推进的时间戳，下限 50ms 避免过于频繁的 apply 操作
                        val addedMS = if (data.updateIntervalMs > 50) data.updateIntervalMs else 50
                        cooldownTimestampMap[animation] = System.currentTimeMillis() + addedMS
                    } else {
                        animation.nextFrame()
                        for ((layout, sidebar) in data.includedSidebars) {
                            layout.apply(sidebar)
                        }
                        val addedMS = if (data.updateIntervalMs > 50) data.updateIntervalMs else 50
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