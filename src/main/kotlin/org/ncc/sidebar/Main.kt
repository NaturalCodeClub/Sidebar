package org.ncc.sidebar

import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    lateinit var sbLib: ScoreboardLibrary
    override fun onEnable() {
        try {
            sbLib = ScoreboardLibrary.loadScoreboardLibrary(this)
        }
        catch (e: NoPacketAdapterAvailableException){
            sbLib = NoopScoreboardLibrary()
            logger.warning("No packet adapter available, falling back to noop library")
        }

    }

    override fun onDisable() {
        sbLib.close()
    }
}
