package org.ncc.sidebar.api;

import java.util.Optional;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Sidebar 插件对外暴露的服务，通过 Bukkit ServicesManager 获取。
 * 所有与库对象相关的调用都应依赖本插件保证的串行线程；扩展不要自己加载 scoreboard-library。
 */
public interface SidebarApi {

    /**
     * 核心共享的 scoreboard-library 实例。服务端版本不支持时可能是 Noop 实现。
     */
    ScoreboardLibrary library();

    /**
     * 核心是否成功加载了真实的包适配器。
     */
    boolean isNativeLibrary();

    /**
     * 核心使用的 MiniMessage 实例。
     */
    MiniMessage miniMessage();

    /**
     * 注册一个 provider。id 会加上插件名前缀；重复注册会被拒绝。
     */
    void register(Plugin owner, SidebarProvider provider);

    /**
     * 注销 provider。返回是否真的移除了。
     */
    boolean unregister(String id);

    /**
     * 以编程方式切换玩家侧栏，与 /sb switch 走同一条路径。
     */
    void select(Player player, String id);

    /**
     * 玩家当前选中的侧栏 id。
     */
    Optional<String> selection(Player player);

    /**
     * 玩家的侧栏总开关状态。
     */
    boolean isEnabled(Player player);

    /**
     * 回到串行线程执行。扩展自己的定时任务要操作库对象时必须使用。
     */
    void runSerialized(Runnable task);

    /**
     * 到该玩家所在线程执行，只用来读玩家状态，里面禁止操作库对象。
     */
    void runForPlayer(Player player, Runnable task);
}
