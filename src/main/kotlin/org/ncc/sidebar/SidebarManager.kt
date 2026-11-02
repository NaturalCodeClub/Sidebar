package org.ncc.sidebar

import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation
import java.util.concurrent.ConcurrentHashMap

class SidebarManager {
    // 跟踪所有动画实例，插件关闭时统一停止，防止后台线程泄漏
    val animationNameMap = ConcurrentHashMap<String, SidebarAnimation<Component>>()

    // 记录使用该动画的所有 Sidebar，以便动画帧更新时批量推送到对应玩家的显示
    val animationDataMap = ConcurrentHashMap<SidebarAnimation<Component>, AnimationData>()

    // 插件关闭 / 重载时清理动画缓存与动画关联的 Sidebar
    fun cleanSidebarResources() {
        animationNameMap.clear()
        animationDataMap.clear()
    }

    data class AnimationData(
        val includedSidebars: ConcurrentHashMap<ComponentSidebarLayout, Sidebar>,
        val updateIntervalMs: Int
    )
}
