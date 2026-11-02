package org.ncc.sidebar.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 玩家侧栏即将切换时触发，仅在核心串行线程上触发。
 * COMMAND 与 API 两种来源的取消会中止切换；其余来源只通知。
 */
public class SidebarSwitchEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String fromId;
    private final String toId;
    private final Cause cause;
    private boolean cancelled;

    public SidebarSwitchEvent(Player player, String fromId, String toId, Cause cause) {
        this.player = player;
        this.fromId = fromId;
        this.toId = toId;
        this.cause = cause;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 切换前的侧栏 id，可能为 null。
     */
    public String getFromId() {
        return fromId;
    }

    public String getToId() {
        return toId;
    }

    public Cause getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Cause {
        /** 玩家执行 /sb switch */
        COMMAND,
        /** 扩展调用 SidebarApi.select */
        API,
        /** 选中的 provider 不存在，回退默认 */
        FALLBACK,
        /** 核心重载后重新绑定 */
        RELOAD,
        /** provider 被注销触发迁移 */
        UNREGISTER
    }
}
