package org.ncc.sidebar.api;

import org.bukkit.entity.Player;

/**
 * 一个由插件或扩展提供的侧栏实现。
 * 所有回调都由 Sidebar 插件在串行线程上调用，实现必须非阻塞、禁止访问世界状态。
 */
public interface SidebarProvider {

    /**
     * 侧栏 id。扩展注册时会自动拼接为 "插件名:id"。
     */
    String id();

    /**
     * /sb list 中展示的一行说明。
     */
    String description();

    /**
     * 可选权限。返回 null 表示不限制；无权限的玩家看不到也不能切换。
     */
    default String permission() {
        return null;
    }

    /**
     * 当前条件下是否可以显示。返回 false 时 /sb switch 拒绝，列表与补全中过滤。
     */
    default boolean isAvailable(Player player) {
        return true;
    }

    /**
     * 开始显示，必须幂等。
     */
    void show(Player player);

    /**
     * 停止显示，必须幂等且清理干净自己挂上去的资源。
     */
    void hide(Player player);
}
