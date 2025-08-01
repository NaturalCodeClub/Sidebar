package org.ncc.sidebar

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import java.util.logging.Logger

class ConfigManager {
    val gson = Gson()

    data class Data(
        var playerNameSidebarNameMap: MutableMap<String, String>,
        var playerUUIDState: MutableMap<String, Boolean>
    )


    lateinit var config: FileConfiguration
    val configFile: File = File(Main.instance!!.dataFolder, "config.yml")
    val defaultSideBarLineConfig = listOf(
        "<green>最高支持</green><gray>15</gray><green>行</green>",
        "<yellow>每行支持</yellow><gray>42</gray><yellow>个字符</yellow>",
        "<blue>支持</blue><gray>PlaceHolderAPI</gray><blue>格式</blue>",
        "<red>只支持</red><gray>MiniMessage</gray><red>格式</red>"
    )
    val defaultSideBarUpdateInterval = 10
    var defaultSidebarSelection: String = "default"

    val animationFile: File = File(Main.instance!!.dataFolder, "animation.yml")
    lateinit var animationConfig: FileConfiguration
    lateinit var animationSection: ConfigurationSection
    val defaultAnimationLines = listOf(
        "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>",
        "<gradient:#40AFFF:#FF80E0><bold>Default Animation</bold></gradient>",
        "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>",
        "<gradient:#40AFFF:#FF80E0><bold>Default Animation</bold></gradient>",
        "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>",
        "<gradient:#40AFFF:#FF80E0><bold>Default Animation</bold></gradient>",
        "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>",
        "<gradient:#40AFFF:#FF80E0><bold>Default Animation</bold></gradient>",
        "<gradient:#0091FF:#F13ABC><bold>Default Animation</bold></gradient>"
    )
    val defaultAnimationUpdateIntervalMs = 200

    val dataFile: File = File(Main.instance!!.dataFolder, "data.json")
    val sidebarFactoryMap = mutableMapOf<String, SidebarFactory>()
    val descriptionMap = mutableMapOf<String, String>()

    //TODO complete it
    var playerNameSidebarNameMap = mutableMapOf<String, String>()
    val playerState = mutableMapOf<OfflinePlayer, Boolean>()

    var dataSaveInterval = 10

    lateinit var sidebarSection: ConfigurationSection
    fun initConfig() {
        var isConfigModified = false
        var isAnimationModified = false

        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.createNewFile()
            isConfigModified = true
        }
        val tempConf: FileConfiguration = YamlConfiguration.loadConfiguration(configFile)
        if (tempConf.get("data.save-interval") == null) {
            tempConf.set("data.save-interval", dataSaveInterval)
            isConfigModified = true
        }
        if (tempConf.get("first-join.defaultsidebar") == null) {
            tempConf.set("first-join.defaultsidebar", defaultSidebarSelection)
            isConfigModified = true
        }
        if (tempConf.get("sidebar") == null) {
            tempConf.set("sidebar.default.description", "<white>一个默认的Sidebar</white>")
            tempConf.set("sidebar.default.title", "<white>默认Sidebar</white>")
            tempConf.set("sidebar.default.lines", defaultSideBarLineConfig)
            tempConf.set("sidebar.default.update-interval", defaultSideBarUpdateInterval)
            tempConf.setComments(
                "sidebar.default.update-interval", listOf(
                    "单位 毫秒",
                    "animation.yml中动画的更新间隔为动画的更新间隔",
                    "记分板的更新间隔最终由此项决定"
                )
            )
            isConfigModified = true
        }
        if (isConfigModified) {
            tempConf.save(configFile)
        }

        if (!animationFile.exists()) {
            animationFile.parentFile.mkdirs()
            animationFile.createNewFile()
            isAnimationModified = true
        }
        val tempAniConf: FileConfiguration = YamlConfiguration.loadConfiguration(animationFile)
        if (tempAniConf.get("animation") == null) {
            tempAniConf.set("animation.default.lines", defaultAnimationLines)
            tempAniConf.set("animation.default.update-interval-ms", defaultAnimationUpdateIntervalMs)
            isAnimationModified = true
        }
        if (isAnimationModified) {
            tempAniConf.save(animationFile)
        }

    }

    fun loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile)
        defaultSidebarSelection = config.getString("first-join.defaultsidebar") ?: defaultSidebarSelection
    }

    fun reloadConfig() {
        closeConfigResource()
        initConfig()
        loadConfig()
        getSidebar(config, Main.instance!!.logger)
    }

    fun getSidebar(conf: FileConfiguration, log: Logger) {
        sidebarSection = conf.getConfigurationSection("sidebar")!!
        for (key: String in sidebarSection.getKeys(false)) {
            val subSection = sidebarSection.getConfigurationSection(key)!!
            if (subSection.get("title") == null || subSection.get("lines") == null || subSection.get("update-interval") == null) {
                log.warning("Sidebar $key is invalid")
                continue
            }
            val description = subSection.getString("description") ?: ""
            val title = subSection.getString("title")!!
            var line = subSection.getStringList("lines")
            val updateInterval = subSection.getInt("update-interval")
            if (line.size > 15) {
                log.warning("Sidebar $key line is too long, max length is 15, current length is ${line.size}")
                line = line.subList(0, 14)
            }
            if (sidebarFactoryMap.containsKey(key)) {
                continue
            }
            sidebarFactoryMap.put(key, SidebarFactory(title, line, updateInterval))
//            val tempSidebar = Main.sbLib!!.createSidebar()
//            tempSidebar.title(MiniMessage.miniMessage().deserialize(title))
//            for (str in line) {
//                if (i > 14) break
//                tempSidebar.line(i, MiniMessage.miniMessage().deserialize(str))
//                i++
//            }
//            sidebarMap.put(key, tempSidebar)
            descriptionMap.put(key, description)
        }
    }

//    fun getPlayerSidebar(player: Player): Sidebar {
//        if (!playerNameSidebarNameMap.containsKey(player.name)) return sidebarMap[defaultSidebarSelection]!!
//        return sidebarMap[playerNameSidebarNameMap[player.name]]!!
//    }

    fun closeConfigResource() {
        descriptionMap.clear()
        for ((player, sidebarName) in playerNameSidebarNameMap) {
//            getPlayerSidebar(Bukkit.getPlayer(player)!!).removePlayer(Bukkit.getPlayer(player)!!)
        }
        sidebarFactoryMap.clear()
    }
    fun closeDataResource() {
        playerNameSidebarNameMap.clear()
    }

    fun initData() {
        if (!dataFile.exists()) {
            if (!dataFile.parentFile.exists()) {
                dataFile.parentFile.mkdirs()
            }
            dataFile.createNewFile()
        }
    }

    fun loadData() {
        if (dataFile.inputStream().available() == 0) {
            return
        }
        val dataVar = gson.fromJson(dataFile.readText(Charsets.UTF_8), Data::class.java)
        playerNameSidebarNameMap = dataVar.playerNameSidebarNameMap
        for ((uuid, state) in dataVar.playerUUIDState) {
            playerState[Bukkit.getOfflinePlayer(UUID.fromString(uuid))] = state
        }
    }

    fun saveData() {
        val playerUUIDState = mutableMapOf<String, Boolean>()
        for ((player, state) in playerState) {
            playerUUIDState.put(player.uniqueId.toString(), state)
        }
        val dataVar = Data(playerNameSidebarNameMap, playerUUIDState)
        val jsonStr = gson.toJson(dataVar)
        dataFile.outputStream().use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
    }

}