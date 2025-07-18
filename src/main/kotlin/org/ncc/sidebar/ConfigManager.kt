package org.ncc.sidebar

import com.google.gson.Gson
import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

class ConfigManager {
    val gson = Gson()
    lateinit var config: FileConfiguration
    val configFile: File = File(Main().getInstance().dataFolder, "config.yml")
    val defaultSideBarLineConfig = listOf(
        "<green>最高支持</green><gray>15</gray><green>行</green>",
        "<yellow>每行支持</yellow><gray>42</gray><yellow>个字符</yellow>",
        "<blue>支持</blue><gray>PlaceHolderAPI</gray><blue>格式</blue>",
        "<red>只支持</red><gray>MiniMessage</gray><red>格式</red>"
    )
    val defaultSideBarUpdateInterval = 10

    val dataFile: File = File(Main().getInstance().dataFolder, "data.json")

    val sidebarMap = mutableMapOf<String, Sidebar>()
    val descriptionMap = mutableMapOf<String, String>()
    //TODO complete it
    val playerSidebarMap = mutableMapOf<String, Sidebar>()
    var playerSidebarNameMap = mutableMapOf<String, String>()

    var dataSaveInterval = 10

    lateinit var sidebarSection: ConfigurationSection
    fun initConfig() {
        if (!configFile.exists()) {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdirs()
            }
            configFile.createNewFile()
        }
        val tempConf: FileConfiguration = YamlConfiguration.loadConfiguration(configFile)
        if(tempConf.get("data.save-interval") == null){
            tempConf.set("data.save-interval", dataSaveInterval)
        }
        if (tempConf.get("sidebar") == null) {
            tempConf.set("sidebar.default.description", "<white>一个默认的Sidebar</white>")
            tempConf.set("sidebar.default.title", "<white>默认Sidebar</white>")
            tempConf.set("sidebar.default.line", defaultSideBarLineConfig)
            tempConf.set("sidebar.default.update-interval", defaultSideBarUpdateInterval)
            tempConf.setComments("sidebar.default.update-interval", listOf("单位 毫秒"))
        }
        tempConf.save(configFile)
    }

    fun loadConfig(conf: FileConfiguration) {
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    fun getSidebar(conf: FileConfiguration, log: Logger) {
        sidebarSection = conf.getConfigurationSection("sidebar")!!
        for (key: String in sidebarSection.getKeys(false)) {
            val subSection = sidebarSection.getConfigurationSection(key)!!
            if (subSection.get("title") == null || subSection.get("line") == null || subSection.get("update-interval") == null) {
                log.warning("Sidebar $key is invalid")
                continue
            }
            val description = subSection.getString("description") ?: ""
            val title = subSection.getString("title")!!
            val line = subSection.getStringList("line")
            val updateInterval = subSection.getInt("update-interval")
            if (line.size > 15) {
                log.warning("Sidebar $key line is too long, max length is 15, current length is ${line.size}")
            }
            var i = 0
            val tempSidebar = Main().sbLib.createSidebar()
            for (str in line) {
                if (i > 14) break
                tempSidebar.line(i, MiniMessage.miniMessage().deserialize(str))
                i++
            }
            sidebarMap.put(key, tempSidebar)
            descriptionMap.put(key, description)
        }
    }

    fun closeSidebar(){
        for((str,sidebar) in sidebarMap){
            sidebar.close()
        }
    }

    fun initData(){
        if(!dataFile.exists()){
            if(!dataFile.parentFile.exists()){
                dataFile.parentFile.mkdirs()
            }
            dataFile.createNewFile()
        }
    }
    fun loadData(){
        if(dataFile.inputStream().available() == 0){
            return
        }
        playerSidebarNameMap = gson.fromJson(dataFile.readText(Charsets.UTF_8), mutableMapOf<String, String>().javaClass)
    }

}