package xyz.hengke.areaautominer.lifecycle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.service.Messages;


/**
 * 玩家侧生命周期管理：玩家死亡自动停止挖掘；GUI 打开时暂停。
 * 与 {@link SessionLifecycle}（会话收尾）职责互补：本类关注"玩家状态变化 → 触发停止"，
 * SessionLifecycle 关注"停止后的统一收尾"；状态机独立于此，避免把生命周期逻辑混在 tick 回调里。
 *
 * <p>非干扰 GUI（背包/创意背包/聊天框）打开时挖掘继续，仅对其余 GUI（配置界面/箱子等）
 * 暂停——模拟按键与挖掘均走游戏原生 API，不受 GUI 焦点影响。</p>
 */
public class PlayerLifecycleManager {
    private enum LifeCycleState {
        ALIVE,
        DEAD,
        PAUSED
    }

    private final MiningController miningController;
    private LifeCycleState lifeCycleState = LifeCycleState.ALIVE;

    public PlayerLifecycleManager(MiningController miningController) {
        this.miningController = miningController;
    }

    /**
     * 非干扰 GUI 白名单：打开时挖掘不暂停。
     * 背包/创意背包/聊天框不遮挡玩家操作意图，且不影响模拟按键驱动。
     */
    private static boolean isNonIntrusiveScreen(Screen screen) {
        return screen instanceof ChatScreen
                || screen instanceof InventoryScreen
                || screen instanceof CreativeInventoryScreen;
    }

    /**
     * 每 tick 检查玩家状态；死亡或暂停时自动停止挖掘。
     * 返回 true 表示本轮应跳过后续处理（玩家死亡）。
     */
    public boolean handleTick(MinecraftClient client) {
        if (client.player == null) return true;

        // isDeadOrDying() 在 1.21.11 中不存在，使用 getHealth() <= 0 替代
        if (!client.player.isAlive() || client.player.getHealth() <= 0) {
            if (lifeCycleState != LifeCycleState.DEAD && miningController.isMining()) {
                // 原因文案经 stopMining(reason) 统一发送，不再单独发第二条消息
                miningController.stopMining(Messages.PLAYER_DEAD_STOP);
            }
            lifeCycleState = LifeCycleState.DEAD;
            return true;
        }

        // 仅非白名单 GUI 触发暂停（背包/聊天框等白名单内不暂停）
        boolean isPaused = client.currentScreen != null && !isNonIntrusiveScreen(client.currentScreen);
        if (isPaused && miningController.isMining()) {
            if (lifeCycleState != LifeCycleState.PAUSED) {
                // 原因文案经 stopMining(reason) 统一发送，不再单独发第二条消息
                miningController.stopMining(Messages.SCREEN_OPEN_STOP);
            }
            lifeCycleState = LifeCycleState.PAUSED;
        } else if (!isPaused) {
            lifeCycleState = LifeCycleState.ALIVE;
        }
        return false;
    }
}
