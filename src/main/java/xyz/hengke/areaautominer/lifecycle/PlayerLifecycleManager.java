package xyz.hengke.areaautominer.lifecycle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.service.Messages;


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

    private static boolean isNonIntrusiveScreen(Screen screen) {
        return screen instanceof ChatScreen
                || screen instanceof InventoryScreen
                || screen instanceof CreativeInventoryScreen;
    }

    public boolean handleTick(MinecraftClient client) {
        if (client.player == null) return true;

        if (!client.player.isAlive() || client.player.getHealth() <= 0) {
            if (lifeCycleState != LifeCycleState.DEAD && miningController.isMining()) {
                miningController.stopMining(Messages.PLAYER_DEAD_STOP);
            }
            lifeCycleState = LifeCycleState.DEAD;
            return true;
        }

        boolean isPaused = client.currentScreen != null && !isNonIntrusiveScreen(client.currentScreen);
        if (isPaused && miningController.isMining()) {
            if (lifeCycleState != LifeCycleState.PAUSED) {
                miningController.stopMining(Messages.SCREEN_OPEN_STOP);
            }
            lifeCycleState = LifeCycleState.PAUSED;
        } else if (!isPaused) {
            lifeCycleState = LifeCycleState.ALIVE;
        }
        return false;
    }
}
