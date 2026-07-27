package org.ncc.sidebar

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class Command : TabExecutor {
    // /sidebar 命令处理器

    //TODO add configuration in lang.yml soon
    val helpList = listOf(
        "<color:#55cdfc>/sidebar reload</color> <gray>-</gray> <color:#f5abb9>重载配置文件</color>",
        "<color:#55cdfc>/sidebar help</color> <gray>-</gray> <color:#f5abb9>展示帮助</color>",
        "<color:#55cdfc>/sidebar toggle</color> <gray>-</gray> <color:#f5abb9>开启或关闭Sidebar</color>",
        "<color:#55cdfc>/sidebar switch</color> <color:#aaa9a1><sidebar></color> <gray>-</gray> <color:#f5abb9>切换Sidebar</color>"
    )

    // 处理 /sidebar 命令
    override fun onCommand(sender: CommandSender, p1: Command, str: String, array: Array<out String>): Boolean {
        if (array.isEmpty()) {
            sender.sendMessage(Main.miniMessage.deserialize(helpList.joinToString("\n")))
            return true
        }
        when (array[0]) {
            // 重载配置文件
            "reload" -> {
                if (sender.hasPermission("sidebar.reload")) {
                    Main.configManager!!.reloadConfig()
                    sender.sendMessage("重载 config.yml 成功")
                    return true
                }
                //TODO put it in lang.yml
                sender.sendMessage(Main.miniMessage.deserialize("<red>你没有权限"))
            }

            // 切换 Sidebar 开关
            "toggle" -> {
                if (sender !is Player) {
                    sender.sendMessage("你不是玩家")
                    return true
                }
                // 翻转当前玩家的 Sidebar 开关状态
                // playerState 同时以 Player 和 OfflinePlayer 为键，前者用于运行时查询，后者用于 JSON 持久化
                Main.configManager!!.playerState[Bukkit.getOfflinePlayer(sender.uniqueId)] =
                    !Main.configManager!!.playerState[sender]!!
                if (Main.configManager!!.playerState[Bukkit.getOfflinePlayer(sender.uniqueId)]!!) {
                    // 开启：根据 Sidebar 类型（静态/普通）添加玩家
                    if (Main.sidebarManager!!.simpleSidebarMap.containsKey(Main.configManager!!.playerNameSidebarNameMap[sender.name])) {
                        val sidebarName = Main.configManager!!.playerNameSidebarNameMap[sender.name]
                        Main.sidebarManager!!.simpleSidebarMap[sidebarName]?.addPlayer(sender)
                    } else {
                        val sidebar =
                            Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[sender.name]]!!.buildSidebar(
                                sender
                            )
                        sidebar.addPlayer(sender)
                        Main.sidebarManager!!.playerNormalSidebarMap.put(sender, sidebar)
                        Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[sender.name]]!!.addPlayerSidebar(
                            sender,
                            sidebar
                        )
                    }

                    sender.sendMessage("Sidebar 调整为 开 ")
                    return true
                } else {
                    // 关闭：从对应 Sidebar 中移除玩家
                    if (Main.sidebarManager!!.simpleSidebarMap.containsKey(Main.configManager!!.playerNameSidebarNameMap[sender.name])) {
                        val sidebarName = Main.configManager!!.playerNameSidebarNameMap[sender.name]
                        Main.sidebarManager!!.simpleSidebarMap[sidebarName]?.removePlayer(sender)
                    } else {
                        Main.sidebarManager!!.playerNormalSidebarMap[sender]?.removePlayer(sender)
                        Main.configManager!!.sidebarFactoryMap[Main.configManager!!.playerNameSidebarNameMap[sender.name]]!!.removePlayerSidebar(
                            sender,
                            Main.sidebarManager!!.playerNormalSidebarMap[sender]!!
                        )
                    }

                    sender.sendMessage("Sidebar 调整为 关 ")
                    return true
                }
            }

            // 切换 Sidebar
            "switch" -> {
                if (sender !is Player) {
                    sender.sendMessage("你不是玩家")
                    return true
                }
                if (array.size < 2) {
                    sender.sendMessage("格式错误")
                    sender.sendMessage(Main.miniMessage.deserialize(helpList.joinToString("\n")))
                    return true
                }
                if (array[1] !in Main.configManager!!.sidebarFactoryMap.keys) {
                    sender.sendMessage("你所指定的Sidebar不存在")
                    return true
                }
                // 玩家开启了 Sidebar 才需要切换显示
                if (Main.configManager!!.playerState[sender]!!) {
                    var sidebarName = Main.configManager!!.playerNameSidebarNameMap[sender.name]
                    // 先移除旧 Sidebar
                    if (Main.sidebarManager!!.simpleSidebarMap.containsKey(sidebarName)) {

                        Main.sidebarManager!!.simpleSidebarMap[sidebarName]?.removePlayer(sender)
                    } else {
                        Main.sidebarManager!!.playerNormalSidebarMap[sender]?.removePlayer(sender)
                        Main.configManager!!.sidebarFactoryMap[sidebarName]!!.removePlayerSidebar(
                            sender,
                            Main.sidebarManager!!.playerNormalSidebarMap[sender]!!
                        )
                    }
                    // 再添加新 Sidebar
                    Main.configManager!!.playerNameSidebarNameMap[sender.name] = array[1]
                    sidebarName = array[1]
                    if (Main.sidebarManager!!.simpleSidebarMap.containsKey(array[1])) {
                        Main.sidebarManager!!.simpleSidebarMap[array[1]]?.addPlayer(sender)
                    } else {
                        val sidebar = Main.configManager!!.sidebarFactoryMap[array[1]]!!.buildSidebar(sender)
                        sidebar.addPlayer(sender)
                        Main.sidebarManager!!.playerNormalSidebarMap[sender] = sidebar
                        Main.configManager!!.sidebarFactoryMap[sidebarName]!!.addPlayerSidebar(
                            sender,
                            Main.sidebarManager!!.playerNormalSidebarMap[sender]!!
                        )
                    }
                }
                sender.sendMessage(Main.miniMessage.deserialize("<green>更改成功"))
            }

            // 列出所有 Sidebar
            "list" -> {
                val list = mutableListOf<String>()
                for ((name, des) in Main.configManager!!.descriptionMap) {
                    list.add("<color:#55cdfc>$name</color> <gray>-</gray> <color:#f5abb9>$des</color>")
                }
                sender.sendMessage(Main.miniMessage.deserialize(list.joinToString("\n")))
                return true
            }

            else -> {
                sender.sendMessage(Main.miniMessage.deserialize(helpList.joinToString("\n")))
                return true
            }
        }
        return true
    }

    // Tab 补全
    override fun onTabComplete(p0: CommandSender, p1: Command, p2: String, array: Array<out String>): List<String?>? {
        if (array.size == 1) return listOf("reload", "help", "list", "toggle", "switch")
        if (array.size == 2 && array[0] == "switch") return Main.configManager!!.sidebarFactoryMap.keys.toList()
        return null
    }


}