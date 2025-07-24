package org.ncc.sidebar

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class Command : TabExecutor {

    //TODO add configuration in lang.yml soon
    val helpList = listOf(
        "<color:#55cdfc>/sidebar reload</color> <gray>-</gray> <color:#f5abb9>重载配置文件</color>",
        "<color:#55cdfc>/sidebar help</color> <gray>-</gray> <color:#f5abb9>展示帮助</color>",
        "<color:#55cdfc>/sidebar toggle</color> <gray>-</gray> <color:#f5abb9>开启或关闭Sidebar</color>",
        "<color:#55cdfc>/sidebar switch</color> <color:#aaa9a1><sidebar></color> <gray>-</gray> <color:#f5abb9>切换Sidebar</color>"
    )

    override fun onCommand(sender: CommandSender, p1: Command, str: String, array: Array<out String>): Boolean {
        if (array.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(helpList.joinToString("\n")))
            return true
        }
        when (array[0]) {
            "reload" -> {
                if(sender.hasPermission("sidebar.reload")){
                    Main.configManager!!.reloadConfig()
                    sender.sendMessage("重载 config.yml 成功")
                    return true
                }
                //TODO put it in lang.yml
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>你没有权限"))
            }

            "toggle" -> {
                if (sender !is Player) {
                    sender.sendMessage("你不是玩家")
                    return true
                }
                Main.configManager!!.playerState[Bukkit.getOfflinePlayer(sender.uniqueId)] = !Main.configManager!!.playerState[sender]!!
                if (Main.configManager!!.playerState[Bukkit.getOfflinePlayer(sender.uniqueId)]!!) {
                    Main.configManager!!.getPlayerSidebar(sender).addPlayer(sender)
                    sender.sendMessage("Sidebar 调整为 开 ")
                    return true
                } else {
                    Main.configManager!!.getPlayerSidebar(sender).removePlayer(sender)
                    sender.sendMessage("Sidebar 调整为 关 ")
                    return true
                }
            }

            "switch" -> {
                if (sender !is Player) {
                    sender.sendMessage("你不是玩家")
                    return true
                }
                if (array.size < 2) {
                    sender.sendMessage("格式错误")
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(helpList.joinToString("\n")))
                    return true
                }
                if (array[1] !in Main.configManager!!.sidebarMap.keys) {
                    sender.sendMessage("你所指定的Sidebar不存在")
                    return true
                }
                Main.configManager!!.getPlayerSidebar(sender).removePlayer(sender)
                Main.configManager!!.playerNameSidebarNameMap[sender.name] = array[1]
                Main.configManager!!.sidebarMap[array[1]]!!.addPlayer(sender)
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>更改成功"))
            }

            "list" ->{

            }

            else -> {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(helpList.joinToString("\n")))
                return true
            }
        }
        return true
    }

    override fun onTabComplete(p0: CommandSender, p1: Command, p2: String, array: Array<out String>): List<String?>? {
        if (array.size == 1) {
            return listOf("reload", "help", "list", "toggle", "switch")
        }
        if (array.size == 2 && array[0] == "switch") {
            val list = mutableListOf<String>()
            for (str in Main.configManager!!.sidebarMap.keys) {
                list.add(str)
            }
            return list
        }
        return null
    }


}