package org.ncc.sidebar

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class ConfigManager {
    // 管理配置与数据
    val gson = Gson()

    // 玩家数据模型，JSON 字段名与字段名保持一致
    data class Data(
        var playerUUIDSidebarNameMap: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
        var playerUUIDState: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    )


    lateinit var config: FileConfiguration
    val configFile: File = File(Main.instance.dataFolder, "config.yml")
    val defaultSideBarLineConfig = listOf(
        "<green>最高支持</green><gray>15</gray><green>行</green>",
        "<yellow>每行支持</yellow><gray>42</gray><yellow>个字符</yellow>",
        "<blue>支持</blue><gray>PlaceHolderAPI</gray><blue>格式</blue>",
        "<red>只支持</red><gray>MiniMessage</gray><red>格式</red>"
    )
    var defaultSidebarSelection: String = "default"

    val animationFile: File = File(Main.instance.dataFolder, "animation.yml")
    lateinit var animationConfig: FileConfiguration
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

    val dataFile: File = File(Main.instance.dataFolder, "data.json")
    val sidebarFactoryMap = mutableMapOf<String, SidebarFactory>()
    val descriptionMap = mutableMapOf<String, String>()

    val playerUUIDSidebarNameMap = ConcurrentHashMap<String, String>()
    val playerState = ConcurrentHashMap<String, Boolean>()

    // 数据自动保存间隔（分钟）
    var dataSaveIntervalMin = 10
    var isDebugMode = false

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
        if (tempConf.get("debug") == null) {
            tempConf.set("debug", isDebugMode)
            isConfigModified = true
        }
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
            tempAniConf.setComments("animation.default.update-interval-ms", listOf("单位 毫秒", "不可小于5ms."))
            isAnimationModified = true
        }
        if (isAnimationModified) {
            tempAniConf.save(animationFile)
        }

    }

    // 加载配置
    fun loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile)
        isDebugMode = config.getBoolean("debug", false)
        defaultSidebarSelection = config.getString("first-join.defaultsidebar", defaultSidebarSelection)!!
        // 保存间隔最小为 1 分钟，防止配置误填 0 或负数导致过于频繁的磁盘写入
        dataSaveIntervalMin = if (config.getInt(
                "data.save-interval",
                dataSaveIntervalMin
            ) <= 0
        ) 1 else config.getInt("data.save-interval", dataSaveIntervalMin)
        animationConfig = YamlConfiguration.loadConfiguration(animationFile)
        // animation.yml 缺少 animation 段视为致命错误，直接关闭插件
        if (animationConfig.getConfigurationSection("animation") == null) {
            Main.instance.logger.warning("animation.yml error! please check it.")
            Bukkit.getPluginManager().disablePlugin(Main.instance)
            return
        }
    }

    // 重载配置
    // 只重新解析配置；在线玩家的显示由 SidebarService.reload 负责重建
    fun reloadConfig() {
        cleanConfigResource()
        initConfig()
        loadConfig()
        getSidebar(config, Main.instance.logger)
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
            if (subSection.get("title") == null || subSection.get("lines") == null) {
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
            if (isDebugMode) {
                println(
                    "[DEBUG] getSidebar key=" + key + " isSimple=" + factory.isSimple +
                        " hasAnimation=" + factory.hasAnimation + " lines=" + line
                )
            }

            descriptionMap[key] = description
        }
    }

    fun getAnimationLines(section: String): List<String> {
        // animation.yml 路径结构: animation.<动画名>.lines，需带上 animation. 前缀定位
        val detailedAnimation = animationConfig.getConfigurationSection("animation.$section")!!
        if (detailedAnimation.getStringList("lines").isEmpty()) {
            Main.instance.logger.warning("animation.$section is invalid,please check config.yml and animation.yml .")
            return listOf("<red><bold>Invalid.")
        }
        return detailedAnimation.getStringList("lines")
    }

    fun getAnimationUpdateIntervalMs(section: String): Int {
        return animationConfig.getInt("animation.$section.update-interval-ms", defaultAnimationUpdateIntervalMs)
    }

    // 释放配置资源
    fun cleanConfigResource() {
        descriptionMap.clear()
        sidebarFactoryMap.clear()
    }

    // 初始化数据文件
    fun initData() {
        if (!dataFile.exists()) {
            if (!dataFile.parentFile.exists()) dataFile.parentFile.mkdirs()
            dataFile.createNewFile()
        }
    }

    // 加载玩家数据，JSON 的 key 是 UUID 字符串
    fun loadData() {
        if (dataFile.length() == 0L) return
        // 解析失败按空数据处理，避免损坏的 data.json 卡死插件启动
        val dataVar = try {
            gson.fromJson(dataFile.readText(Charsets.UTF_8), Data::class.java)
        } catch (e: Exception) {
            Main.instance.logger.warning("Failed to load data.json: " + e.message)
            return
        }
        if (dataVar == null) return
        playerUUIDSidebarNameMap.putAll(dataVar.playerUUIDSidebarNameMap)
        playerState.putAll(dataVar.playerUUIDState)
    }

    // 保存玩家数据，两个 map 的 key 都是 UUID 字符串
    fun saveData() {
        // 拷贝一份再序列化，防止定时保存线程与串行线程同时写 map 造成错乱
        val dataVar = Data(ConcurrentHashMap(playerUUIDSidebarNameMap), ConcurrentHashMap(playerState))
        val jsonStr = gson.toJson(dataVar)
        dataFile.outputStream().use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
    }

}