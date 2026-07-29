package org.ncc.sidebar.Tasks

import org.ncc.sidebar.Main
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SaveDataTaskManager {
    companion object{
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
        fun start(){
            scheduler.scheduleAtFixedRate({
                Main.configManager!!.saveData()
            },0L,Main.configManager!!.dataSaveIntervalMin.toLong(),TimeUnit.MINUTES)
        }
        fun close(){
            scheduler.shutdownNow()
        }
    }
}