package xyz.hengke.areaautominer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.service.Messages;

/**
 * 客户端生命周期管理：玩家死亡 / GUI 打开时自动停止挖掘。
 * 状态机独立于此，避免把生命周期逻辑混在 tick 回调里。
 */
public class LifecycleManager {
    private enum LifeCycleState {
        ALIVE,
        DEAD,
        PAUSED
    }

    private final MiningController miningController;
    private LifeCycleState lifeCycleState = LifeCycleState.ALIVE;

    public LifecycleManager(MiningController miningController) {
        this.miningController = miningController;
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
                miningController.stopMining();
                client.player.sendMessage(Text.literal(Messages.PLAYER_DEAD_STOP), false);
            }
            lifeCycleState = LifeCycleState.DEAD;
            return true;
        }

        // 任意 GUI 打开时暂停（包括聊天框、箱子、配置界面等）
        boolean isPaused = client.currentScreen != null;
        if (isPaused && miningController.isMining()) {
            if (lifeCycleState != LifeCycleState.PAUSED) {
                miningController.stopMining();
                client.player.sendMessage(Text.literal(Messages.SCREEN_OPEN_STOP), false);
            }
            lifeCycleState = LifeCycleState.PAUSED;
        } else if (!isPaused) {
            lifeCycleState = LifeCycleState.ALIVE;
        }
        return false;
    }
}
