package org.ncc.sidebar.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class SerialDispatcher(private val plugin: JavaPlugin) {

    fun execute(task: () -> Unit) {
        if (!isFolia) {
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
