package org.ncc.sidebar

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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
        var animationIntervalMap = mutableMapOf<SidebarAnimation<Component>, Int>()
        var hasAnimation = false
        for (str in lines) {
            if (Main.sidebarManager!!.hasAnimatedLine(str)) {
//                val animationFrame = ArrayList<Component>()
//                for (originLine in Main.configManager!!.getAnimationLines(str)){
//                    animationFrame.add(Main.miniMessage.deserialize(originLine))
//                }
//                // 创建独立的动画实例加入行构建器以驱动帧切换
//                sidebarComponentBuilder.addAnimatedLine(CollectionSidebarAnimation(animationFrame))
//                // 额外在全局集合中保留引用，确保插件关闭时可停止动画
//                Main.sidebarManager!!.animationSet.add(CollectionSidebarAnimation(animationFrame))
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
        if (hasAnimation) {
            for (animation in animationIntervalMap.keys) {
                if (Main.sidebarManager!!.animationDataMap.containsKey(animation))
                    Main.sidebarManager!!.animationDataMap[animation]!!.includedSidebars[sidebarLayout] = sb
                else {
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
        val animationListStr = ANIMATION_REGEX.findAll(str)
            .map { it.groupValues[1] }
            .toList()
        val parts = str.split(ANIMATION_REGEX)
        val animatedLine = SidebarComponent { drawable: LineDrawable? ->
            var line: Component = Component.text().build()
            var i = 0
            while (i < parts.size - 1) {
                line = line.append(Main.miniMessage.deserialize(parts[i]))
                if (i <= animationListStr.size - 1) {
                    var animationComponent: SidebarAnimation<Component>
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