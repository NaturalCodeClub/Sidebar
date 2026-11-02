package org.ncc.sidebar

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.ncc.sidebar.api.event.SidebarSwitchEvent
import org.ncc.sidebar.service.SidebarService

class Command : TabExecutor {
    // /sidebar 命令处理器

    //TODO add configuration in lang.yml soon - priority extremely low.
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
                if (!sender.hasPermission("sidebar.reload")) {
                    sender.sendMessage(Main.miniMessage.deserialize("<red>你没有权限"))
                    return true
                }
                Main.sidebarService.reload { success ->
                    sender.sendMessage(if (success) "重载 config.yml 成功" else "重载 config.yml 失败")
                }
                return true
            }

            // 切换 Sidebar 开关
            "toggle" -> {
                if (sender !is Player) {
                    sender.sendMessage("你不是玩家")
                    return true
                }
                val player = sender
                Main.sidebarService.toggle(player, { state ->
                    player.sendMessage(if (state) "Sidebar 调整为 开 " else "Sidebar 调整为 关 ")
                })
                return true
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
                val player = sender
                Main.sidebarService.select(player, array[1], SidebarSwitchEvent.Cause.COMMAND, { outcome ->
                    val message = when (outcome) {
                        SidebarService.SelectOutcome.SUCCESS -> "<green>更改成功"
                        SidebarService.SelectOutcome.SAME -> "<green>更改成功"
                        SidebarService.SelectOutcome.DISABLED_SAVED -> "<green>已保存，开启后生效"
                        SidebarService.SelectOutcome.NOT_FOUND -> "你所指定的Sidebar不存在"
                        SidebarService.SelectOutcome.NO_PERMISSION -> "<red>你没有权限"
                        SidebarService.SelectOutcome.UNAVAILABLE -> "<red>该Sidebar当前不可用"
                        SidebarService.SelectOutcome.CANCELLED -> "<red>切换已被取消"
                        SidebarService.SelectOutcome.FAILED -> "<red>切换失败"
                    }
                    player.sendMessage(Main.miniMessage.deserialize(message))
                })
                return true
            }

            // 列出所有 Sidebar
            "list" -> {
                val list = Main.sidebarService.visibleProviders(sender).map {
                    "<color:#55cdfc>" + it.id + "</color> <gray>-</gray> <color:#f5abb9>" +
                        it.provider.description() + "</color>"
                }
                sender.sendMessage(Main.miniMessage.deserialize(list.joinToString("\n")))
                return true
            }

            else -> {
                sender.sendMessage(Main.miniMessage.deserialize(helpList.joinToString("\n")))
                return true
            }
        }
    }

    // Tab 补全
    override fun onTabComplete(p0: CommandSender, p1: Command, p2: String, array: Array<out String>): List<String>? {
        if (array.size == 1) return listOf("reload", "help", "list", "toggle", "switch")
        if (array.size == 2 && array[0] == "switch") {
            return Main.sidebarService.visibleProviders(p0).map { it.id }
        }
        return null
    }
}
