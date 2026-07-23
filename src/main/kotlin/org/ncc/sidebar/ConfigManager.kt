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
    // 管理配置与数据
    val gson = Gson()

    // 玩家数据模型
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

    // 数据自动保存间隔（分钟）
    var dataSaveIntervalMin = 10

    lateinit var sidebarSection: ConfigurationSection

    // 初始化配置文件
    fun initConfig() {
        var isConfigModified = false
        var isAnimationModified = false

        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.createNewFile()
            isConfigModified = true
        }
        val tempConf: FileConfiguration = YamlConfiguration.loadConfiguration(configFile)
        // 配置项不存在时填入默认值
        if (tempConf.get("data.save-interval") == null) {
            tempConf.set("data.save-interval", dataSaveIntervalMin)
            tempConf.setComments("data.save-interval", listOf("单位 分钟", "玩家数据的保存时间"))
            isConfigModified = true
        }
        if (tempConf.get("first-join.defaultsidebar") == null) {
            tempConf.set("first-join.defaultsidebar", defaultSidebarSelection)
            isConfigModified = true
        }
        // 写入默认 Sidebar 配置示例
        if (tempConf.get("sidebar") == null) {
            tempConf.set("sidebar.default.description", "<white>一个默认的Sidebar</white>")
            tempConf.set("sidebar.default.title", "<white>默认Sidebar</white>")
            tempConf.set("sidebar.default.lines", defaultSideBarLineConfig)
            isConfigModified = true
        }
        // 仅在有改动时保存，避免无意义的磁盘写入
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

    // 加载配置
    fun loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile)
        defaultSidebarSelection = config.getString("first-join.defaultsidebar",defaultSidebarSelection)!!
        animationConfig = YamlConfiguration.loadConfiguration(animationFile)
        // animation.yml 缺少 animation 段视为致命错误，直接关闭插件
        if (animationConfig.getConfigurationSection("animation") == null) {
            Main.instance!!.logger.warning("animation.yml error! please check it.")
            Bukkit.getPluginManager().disablePlugin(Main.instance!!)
            return
        }
        animationSection = animationConfig.getConfigurationSection("animation")!!
    }

    // 重载配置
    fun reloadConfig() {
        closeConfigResource()
        initConfig()
        loadConfig()
        getSidebar(config, Main.instance!!.logger)
    }

    // 解析 sidebar 配置段
    fun getSidebar(conf: FileConfiguration, log: Logger) {
        sidebarSection = conf.getConfigurationSection("sidebar")!!
        for (key: String in sidebarSection.getKeys(false)) {
            val subSection = sidebarSection.getConfigurationSection(key)!!
            // 跳过已加载的 Sidebar，避免重复创建
            if (sidebarFactoryMap.containsKey(key)) {
                continue
            }
            // 缺少标题、行或更新间隔则为无效配置
            if (subSection.get("title") == null || subSection.get("lines") == null || subSection.get("update-interval") == null) {
                log.warning("Sidebar $key is invalid")
                continue
            }
            val description = subSection.getString("description") ?: ""
            val title = subSection.getString("title")!!
            var line = subSection.getStringList("lines")
            // 超出15行上限时截断，避免性能问题
            if (line.size > 15) {
                log.warning("Sidebar $key line is too long, max length is 15, current length is ${line.size}")
                line = line.subList(0, 15)
            }

            val factory = SidebarFactory(title, line)
            sidebarFactoryMap[key] = factory
            // 静态 Sidebar 可预先构建全局实例，避免为每个玩家重复创建
            if (Main.sidebarManager!!.isSimpleSidebar(line)) Main.sidebarManager!!.simpleSidebarMap[key] =
                factory.buildSidebar()

            descriptionMap[key] = description
        }
    }

    fun getAnimationLines(section: String): List<String> {
//        val detailedAnimation = animationConfig.getConfigurationSection(section)!!
//        if(!detailedAnimation.getKeys(false).contains(section)) {
//            Main.instance?.logger?.warning("Animation section $section is invalid,please check config.yml and animation.yml .")
//            return listOf("<red><bold>Invalid.")
//        }
//        return detailedAnimation.getStringList("$section.lines")
        val detailedAnimation = animationConfig.getConfigurationSection("animation.$section")!!
        if (detailedAnimation.getConfigurationSection("lines") == null) {
            Main.instance!!.logger.warning("animation.$section is invalid,please check config.yml and animation.yml .")
            return listOf("<red><bold>Invalid.")
        }
        return detailedAnimation.getStringList("lines")
    }

    fun getAnimationUpdateIntervalMs(section: String): Int {
        return animationConfig.getInt("animation.$section.update-interval-ms", defaultAnimationUpdateIntervalMs)
    }

    // 释放配置资源
    fun closeConfigResource() {
        descriptionMap.clear()
        sidebarFactoryMap.clear()
    }

    // 释放数据资源
    fun closeDataResource() {
        playerNameSidebarNameMap.clear()
    }

    // 初始化数据文件
    fun initData() {
        if (!dataFile.exists()) {
            if (!dataFile.parentFile.exists()) dataFile.parentFile.mkdirs()
            dataFile.createNewFile()
        }
    }

    // 加载玩家数据
    fun loadData() {
        if (dataFile.length() == 0L) return
        val dataVar = gson.fromJson(dataFile.readText(Charsets.UTF_8), Data::class.java)
        playerNameSidebarNameMap = dataVar.playerNameSidebarNameMap
        // 将 JSON 中的 UUID 字符串转换为 OfflinePlayer 对象
        for ((uuid, state) in dataVar.playerUUIDState) {
            playerState[Bukkit.getOfflinePlayer(UUID.fromString(uuid))] = state
        }
    }

    // 保存玩家数据
    // 保存时将 OfflinePlayer 映射为 UUID 字符串以便 JSON 序列化
    fun saveData() {
        val playerUUIDState = mutableMapOf<String, Boolean>()
        for ((player, state) in playerState) {
            playerUUIDState[player.uniqueId.toString()] = state
        }
        val dataVar = Data(playerNameSidebarNameMap, playerUUIDState)
        val jsonStr = gson.toJson(dataVar)
        dataFile.outputStream().use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
    }

}