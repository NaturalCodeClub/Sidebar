package org.ncc.sidebar

import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation
import org.bukkit.entity.Player

class SidebarManager {
    // 管理 Sidebar 实例与状态

    // 玩家独立 Sidebar
    val playerNormalSidebarMap = mutableMapOf<Player, Sidebar>()

    // 全局静态 Sidebar
    val simpleSidebarMap = mutableMapOf<String, Sidebar>()

    // 跟踪所有动画实例，插件关闭时统一停止，防止后台线程泄漏
    val animationNameMap = mutableMapOf<String, SidebarAnimation<Component>>()

    // 记录使用该动画的所有 Sidebar，以便动画帧更新时批量推送到对应玩家的显示
    val animationDataMap = mutableMapOf<SidebarAnimation<Component>, AnimationData>()

    private val ANIMATION_REGEX = Regex("\\{animation:.*?}")
    private val PAPI_REGEX = Regex("%.*?%")


    // 获取玩家的 Sidebar
    // 首次获取时通过工厂创建并缓存，后续直接复用
    fun getPlayerSidebar(player: Player): Sidebar {
        val sidebar = playerNormalSidebarMap[player]
            ?: Main.configManager!!.sidebarFactoryMap[Main.configManager!!.defaultSidebarSelection]!!.buildSidebar(
                player
            )
        playerNormalSidebarMap[player] = sidebar
        return sidebar
    }

    // 判断是否为无占位符的静态 Sidebar
    // 检测是否包含 PAPI 占位符（%变量%），不含则为静态 Sidebar
    fun isSimpleSidebar(originList: List<String>): Boolean {
        for (str in originList) {
            if (PAPI_REGEX.containsMatchIn(str)) {
                return false
            }
        }
        return true
    }

    // 检查是否包含动画行
    // 检测是否包含 {动画} 标记
    fun hasAnimatedLine(str: String): Boolean {
        return ANIMATION_REGEX.containsMatchIn(str)
    }

    fun hasPAPILine(str: String): Boolean {
        return PAPI_REGEX.containsMatchIn(str)
    }

    // 插件关闭时清理所有 Sidebar，避免玩家退出后残留引用导致内存泄漏
    fun onPluginClose() {
        playerNormalSidebarMap.clear()
        simpleSidebarMap.clear()
    }


    data class AnimationData(
        val includedSidebars: MutableMap<ComponentSidebarLayout, Sidebar>,
        val updateIntervalMs: Int
    )

}