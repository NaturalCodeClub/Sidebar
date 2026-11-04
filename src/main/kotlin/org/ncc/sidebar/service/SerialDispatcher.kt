package org.ncc.sidebar.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class SerialDispatcher(private val plugin: JavaPlugin) {

    fun execute(task: () -> Unit) {
        if (!isFolia) {
            // 已在主线程就直接执行，避免命令/事件回调白等一个 tick
            if (Bukkit.isPrimaryThread()) task() else Bukkit.getScheduler().runTask(plugin, Runnable { task() })
            return
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin) { task() }
    }

    fun executeForPlayer(player: Player, task: () -> Unit) {
        if (!isFolia) {
            execute(task)
            return
        }
        // Folia 下玩家实体带有 region 归属，必须交给实体调度器在自己线程执行
        player.scheduler.run(plugin, { _ -> task() }, null)
    }

    fun scheduleEachTick(task: () -> Unit): () -> Unit {
        if (!isFolia) {
            val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { task() }, 1L, 1L)
            return { bukkitTask.cancel() }
        }
        val scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { _ -> task() },
            1L,
            1L
        )
        return { scheduled.cancel() }
    }

    companion object {
        // Folia 上 Bukkit.getScheduler 会直接抛异常，必须按实际平台分流
        val isFolia: Boolean = try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
