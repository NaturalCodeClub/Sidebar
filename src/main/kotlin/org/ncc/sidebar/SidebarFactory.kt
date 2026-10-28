package org.ncc.sidebar

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.CollectionSidebarAnimation
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class SidebarFactory(
    val title: String,
    val lines: List<String>
) {
    // 创建和管理 Sidebar 实例
    val playerSidebarMap = mutableMapOf<Player, Sidebar>()

    // 带捕获组的版本：groupValues[1] 可提取动画名，用于按名查找配置
    private val ANIMATION_REGEX = Regex("\\{animation:(.*?)}")

    override fun toString(): String {
        return "SidebarFactory(title=$title, lines=${lines.joinToString("\n")})"
    }

    // 为指定玩家构建 Sidebar（支持 PAPI + 动画）
    fun buildSidebar(player: Player): Sidebar {
        val sb = Main.sbLib!!.createSidebar()
        val sidebarComponentBuilder: SidebarComponent.Builder = SidebarComponent.builder()
        // 收集此 Sidebar 中用到的动画实例及刷新间隔，后续注册到全局 animationDataMap 统一驱动
        val animationIntervalMap = mutableMapOf<SidebarAnimation<Component>, Int>()
        var hasAnimation = false
        for (str in lines) {
            val hasPAPI = Main.sidebarManager!!.hasPAPILine(str)
            if (Main.sidebarManager!!.hasAnimatedLine(str)) {
                hasAnimation = true
                sidebarComponentBuilder.addDynamicLine(getAnimatedComponent(str, hasPAPI, player, animationIntervalMap))
                continue
            }
            if (hasPAPI) {
                sidebarComponentBuilder.addDynamicLine {
                    Main.miniMessage.deserialize(PlaceholderAPI.setPlaceholders(player, str))
                }
                continue
            }
            sidebarComponentBuilder.addStaticLine(
                Main.miniMessage.deserialize(str)
            )
        }
        val sidebarLayout = ComponentSidebarLayout(
            SidebarComponent.staticLine(
                Main.miniMessage.deserialize(PlaceholderAPI.setPlaceholders(player, title))
            ),
            sidebarComponentBuilder.build()
        )
        if (hasAnimation) {
            for (animation in animationIntervalMap.keys) {
                // 同一动画可能被多个 Sidebar 引用，新增到此动画的关联列表即可
                if (Main.sidebarManager!!.animationDataMap.containsKey(animation))
                    Main.sidebarManager!!.animationDataMap[animation]!!.includedSidebars[sidebarLayout] = sb
                else {
                    // 首次出现的动画需要创建 AnimationData 并记录其刷新间隔
                    Main.sidebarManager!!.animationDataMap[animation] = SidebarManager.AnimationData(
                        ConcurrentHashMap(),
                        animationIntervalMap[animation]!!
                    )
                    Main.sidebarManager!!.animationDataMap[animation]!!.includedSidebars[sidebarLayout] = sb
                }
            }
            if (Main.configManager!!.isDebugMode) {
                println(
                    "[DEBUG] buildSidebar - map identity=${System.identityHashCode(Main.sidebarManager!!.animationDataMap)}, " +
                            "size=${Main.sidebarManager!!.animationDataMap.size}"
                )
            }
        }
        sidebarLayout.apply(sb)
        return sb
    }

    // 构建不含 PAPI 的静态 Sidebar
    fun buildSidebar(): Sidebar {
        val sb = Main.sbLib!!.createSidebar()
        val sidebarComponentBuilder: SidebarComponent.Builder = SidebarComponent.builder()
        // 收集本 Sidebar 中用到的动画实例及其刷新间隔，后续注册到全局 animationDataMap 统一驱动
        val animationIntervalMap = mutableMapOf<SidebarAnimation<Component>, Int>()
        var hasAnimation = false
        for (str in lines) {
            if (Main.sidebarManager!!.hasAnimatedLine(str)) {
                hasAnimation = true
                sidebarComponentBuilder.addDynamicLine(getAnimatedComponent(str, false, null, animationIntervalMap))
                continue
            }
            sidebarComponentBuilder.addStaticLine(
                Main.miniMessage.deserialize(str)
            )
        }
        val sidebarLayout = ComponentSidebarLayout(
            SidebarComponent.staticLine(Main.miniMessage.deserialize(title)),
            sidebarComponentBuilder.build()
        )
        // 将动画及其所属 Sidebar 注册到全局映射，动画调度任务据此遍历并刷新
        if (hasAnimation) {
            for (animation in animationIntervalMap.keys) {
                // 同一动画可能被多个 Sidebar 引用，新增到此动画的关联列表即可
                if (Main.sidebarManager!!.animationDataMap.containsKey(animation))
                    Main.sidebarManager!!.animationDataMap[animation]!!.includedSidebars[sidebarLayout] = sb
                else {
                    // 首次出现的动画需要创建 AnimationData 并记录其刷新间隔
                    Main.sidebarManager!!.animationDataMap[animation] = SidebarManager.AnimationData(
                        ConcurrentHashMap(),
                        animationIntervalMap[animation]!!
                    )
                    Main.sidebarManager!!.animationDataMap[animation]!!.includedSidebars[sidebarLayout] = sb
                }
            }
            if (Main.configManager!!.isDebugMode) {
                println(
                    "[DEBUG] buildSidebar - map identity=${System.identityHashCode(Main.sidebarManager!!.animationDataMap)}, " +
                            "size=${Main.sidebarManager!!.animationDataMap.size}"
                )
            }
        }
        sidebarLayout.apply(sb)
        return sb
    }

    fun getAnimatedComponent(
        str: String,
        hasPAPI: Boolean,
        player: Player?,
        map: MutableMap<SidebarAnimation<Component>, Int>
    ): () -> Component {
        // 返回一个 lambda 作为动态行供给器，每次重绘时重新拼接静态文本与动画当前帧
        // 提取行中所有 {animation:xxx} 的动画名称
        val animationListStr = ANIMATION_REGEX.findAll(str)
            .map { it.groupValues[1] }
            .toList()
        // 按动画标记切分，parts 是静态文本段（数量 = 动画数 + 1，因首尾都可能存在静态文本）
        val parts = str.split(ANIMATION_REGEX)
        // 预先从缓存或配置文件加载所有动画实例，避免每次重绘时重复创建
        var animations: Array<SidebarAnimation<Component>> = emptyArray()
        for (animation in animationListStr) {
            var animationComponent: SidebarAnimation<Component>
            if (Main.sidebarManager!!.animationNameMap[animation] != null) {
                animationComponent = Main.sidebarManager!!.animationNameMap[animation]!!
                animations = animations.plus(animationComponent)
            } else {
                val animationComponentList: MutableList<Component> = ArrayList()
                for (s in Main.configManager!!.getAnimationLines(animation)) {
                    animationComponentList.add(Main.miniMessage.deserialize(s))
                }
                animationComponent = CollectionSidebarAnimation(animationComponentList)
                Main.sidebarManager!!.animationNameMap[animation] = animationComponent
                animations = animations.plus(animationComponent)
            }
            map[animationComponent] = Main.configManager!!.getAnimationUpdateIntervalMs(animation)
        }
        // 自定义绘制组件：每次重绘时拼接静态文本与各动画当前帧
        // 重绘时调用此 lambda，按顺序交替拼接静态文本段与对应动画的当前帧
        val animatedLines = {
            var line: Component = Component.text().build()
            var i = 0
            // parts 比 animationListStr 多一段，最后一次只拼接静态文本不拼接动画
            while (i <= parts.size - 1) {
                line = if (hasPAPI && player != null) line.append(
                    Main.miniMessage.deserialize(
                        PlaceholderAPI.setPlaceholders(
                            player,
                            parts[i]
                        )
                    )
                )
                else line.append(Main.miniMessage.deserialize(parts[i]))
                if (i <= animations.size - 1) {
                    line = line.append(animations[i].currentFrame())
                }
                i++
            }
            line
        }
        return animatedLines
    }

    // 记录玩家 Sidebar
    fun addPlayerSidebar(player: Player, Sidebar: Sidebar) = playerSidebarMap.put(player, Sidebar)

    // 移除玩家 Sidebar
    fun removePlayerSidebar(player: Player, Sidebar: Sidebar) = playerSidebarMap.remove(player, Sidebar)
}