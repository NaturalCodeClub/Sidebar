package org.ncc.sidebar.Tasks

import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation
import org.ncc.sidebar.Main

class SidebarUpdateTasks {
    companion object {
        private val cooldownTimestampMap = mutableMapOf<SidebarAnimation<Component>, Long>()
        private var cancelSchedule: (() -> Unit)? = null

        fun start() {
            // 每 1 tick（50ms）轮询一次，各动画通过 cooldown 按各自 interval 错开推进
            cancelSchedule = Main.dispatcher.scheduleEachTick {
                val map = Main.sidebarManager.animationDataMap
                if (map.isEmpty()) {
                    if (Main.configManager.isDebugMode) {
                        println(
                            "[DEBUG] task - map identity=" + System.identityHashCode(map) +
                                ", sm identity=" + System.identityHashCode(Main.sidebarManager)
                        )
                    }
                    return@scheduleEachTick
                }
                for ((animation, data) in map) {
                    if (data.includedSidebars.isEmpty()) continue
                    val nextAllowed = cooldownTimestampMap[animation]
                    if (nextAllowed != null && nextAllowed > System.currentTimeMillis()) continue
                    animation.nextFrame()
                    // 将更新后的帧重新 apply 到所有引用此动画的 Sidebar
                    for ((layout, sidebar) in data.includedSidebars) {
                        layout.apply(sidebar)
                    }
                    // 设置下次推进的时间戳，下限 50ms 避免过于频繁的 apply 操作
                    val addedMS = if (data.updateIntervalMs > 50) data.updateIntervalMs else 50
                    cooldownTimestampMap[animation] = System.currentTimeMillis() + addedMS
                }
            }
        }

        fun close() {
            cancelSchedule?.invoke()
            cancelSchedule = null
            cooldownTimestampMap.clear()
        }
    }
}
