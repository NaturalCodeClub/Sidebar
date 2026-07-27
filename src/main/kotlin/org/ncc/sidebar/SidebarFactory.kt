package org.ncc.sidebar

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout
import net.megavex.scoreboardlibrary.api.sidebar.component.LineDrawable
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.CollectionSidebarAnimation
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation
import org.bukkit.entity.Player

class SidebarFactory(
    val title: String,
    val lines: List<String>
) {
    // 创建和管理 Sidebar 实例
    val playerSidebarMap = mutableMapOf<Player, Sidebar>()
    private val ANIMATION_REGEX = Regex("\\{animation:(.*?)}")

    override fun toString(): String {
        return "SidebarFactory(title=$title, lines=${lines.joinToString("\n")})"
    }

    // 为指定玩家构建 Sidebar（支持 PAPI）
    fun buildSidebar(player: Player): Sidebar {
        val sb = Main.sbLib!!.createSidebar()
        val sidebarComponentBuilder: SidebarComponent.Builder = SidebarComponent.builder()
        for (str in lines) {
            if (Main.sidebarManager!!.hasPAPILine(str)) sidebarComponentBuilder.addDynamicLine {
                Main.miniMessage.deserialize(
                    PlaceholderAPI.setPlaceholders(player, str)
                )
            }
            else sidebarComponentBuilder.addStaticLine(Main.miniMessage.deserialize(str))
        }
        val sidebarComponent = sidebarComponentBuilder.build()
        val sidebarLayout = ComponentSidebarLayout(
            SidebarComponent.staticLine(
                Main.miniMessage.deserialize(PlaceholderAPI.setPlaceholders(player, title))
            ),
            sidebarComponent
        )
        sidebarLayout.apply(sb)
        return sb
    }

    // 构建不含 PAPI 的静态 Sidebar
    fun buildSidebar(): Sidebar {
        val sb = Main.sbLib!!.createSidebar()
        val sidebarComponentBuilder: SidebarComponent.Builder = SidebarComponent.builder()
        // 收集本 Sidebar 中用到的动画实例及其刷新间隔，后续注册到全局 animationDataMap 统一驱动
        var animationIntervalMap = mutableMapOf<SidebarAnimation<Component>, Int>()
        var hasAnimation = false
        for (str in lines) {
            if (Main.sidebarManager!!.hasAnimatedLine(str)) {
                hasAnimation = true
                sidebarComponentBuilder.addComponent(getAnimatedComponent(str, animationIntervalMap))
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
                        mutableMapOf(),
                        animationIntervalMap[animation]!!
                    )
                    Main.sidebarManager!!.animationDataMap[animation]!!.includedSidebars[sidebarLayout] = sb
                }
            }
        }
        sidebarLayout.apply(sb)
        return sb
    }

    fun getAnimatedComponent(
        str: String,
        map: MutableMap<SidebarAnimation<Component>, Int>
    ): SidebarComponent {
        // 提取行中所有 {animation:xxx} 的动画名称
        val animationListStr = ANIMATION_REGEX.findAll(str)
            .map { it.groupValues[1] }
            .toList()
        // 按动画标记切分，parts 是静态文本段（数量 = 动画数 + 1，因首尾都可能存在静态文本）
        val parts = str.split(ANIMATION_REGEX)
        // 自定义绘制组件：每次重绘时拼接静态文本与各动画当前帧
        val animatedLine = SidebarComponent { drawable: LineDrawable? ->
            var line: Component = Component.text().build()
            var i = 0
            // parts 比 animationListStr 多一段，最后一次只拼接静态文本不拼接动画
            while (i < parts.size - 1) {
                line = line.append(Main.miniMessage.deserialize(parts[i]))
                if (i <= animationListStr.size - 1) {
                    var animationComponent: SidebarAnimation<Component>
                    // 缓存动画实例：同一动画名在多个 Sidebar 或多行中复用同一个实例
                    if (Main.sidebarManager!!.animationNameMap[animationListStr[i]] != null) {
                        animationComponent = Main.sidebarManager!!.animationNameMap[animationListStr[i]]!!
                    } else {
                        val animationComponentList: MutableList<Component> = ArrayList()
                        for (s in Main.configManager!!.getAnimationLines(animationListStr[i])) {
                            animationComponentList.add(Main.miniMessage.deserialize(s))
                        }
                        animationComponent = CollectionSidebarAnimation(animationComponentList)
                        Main.sidebarManager!!.animationNameMap[animationListStr[i]] = animationComponent
                    }
                    // 记录此动画实例在本 Sidebar 中使用的刷新间隔，用于调度
                    map[animationComponent] = Main.configManager!!.getAnimationUpdateIntervalMs(animationListStr[i])
                    line = line.append(animationComponent.currentFrame())
                }
                i++
            }
            drawable!!.drawLine(line)
        }
        return animatedLine
    }

    // 记录玩家 Sidebar
    fun addPlayerSidebar(player: Player, Sidebar: Sidebar) = playerSidebarMap.put(player, Sidebar)

    // 移除玩家 Sidebar
    fun removePlayerSidebar(player: Player, Sidebar: Sidebar) = playerSidebarMap.remove(player, Sidebar)
}