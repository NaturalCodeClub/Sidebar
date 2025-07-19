package org.ncc.sidebar

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
        if(array[0] == "reload"){
            Main.configManager.reloadConfig()
            sender.sendMessage("重载 config.yml 成功")
        }
        if(array[0]== "toggle"){
            if(sender !is Player) {
                sender.sendMessage("你不是玩家")
                return true
            }
            Main.configManager.playerState[sender as Player] = !Main.configManager.playerState[sender]!!
            if(Main.configManager.playerState[sender]!!) {
                sender.sendMessage("Sidebar 调整为 开 ")
                return true
            }
            sender.sendMessage("Sidebar 调整为 关 ")
            return true
        }
        if(array[0] == "switch"){
            if(array.size < 2) {
                sender.sendMessage("格式错误")
                sender.sendMessage(helpList.joinToString("\n"))
                return true
            }
            //TODO ..
        }
        return true
    }

    override fun onTabComplete(p0: CommandSender, p1: Command, p2: String, array: Array<out String>): List<String?>? {
        if(array.size == 1) {
            return listOf("reload","help","toggle","switch")
        }
        if(array.size == 2&&array[0] == "switch") {
            val list = mutableListOf<String>()
            for(str in Main.configManager.sidebarMap.keys) {
                list.add(str)
            }
            return list
        }
        return null
    }


}