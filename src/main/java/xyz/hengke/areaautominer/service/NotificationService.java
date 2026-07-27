package xyz.hengke.areaautominer.service;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;

public class NotificationService {
    private final MiningContext context;

    public NotificationService(MiningContext context) {
        this.context = context;
    }

    public void sendMessage(Text text) {
        if (context.client.player != null) {
            context.client.player.sendMessage(text, false);
        }
    }

    public void logDebug(String message) {
        if (MiningConfig.DEBUG) {
            if (context.client.player != null) {
                context.client.player.sendMessage(Text.literal("§7[DEBUG] " + message), false);
            }
        }
    }

    public void onMineComplete() {
        context.isMining = false;
        if (context.client.player != null) {
            context.client.player.sendMessage(Text.literal("§a挖掘完成！"), false);
        }
        if (context.listener != null) {
            context.listener.onMineComplete();
        }
    }

    public void onBlockMined(BlockPos pos) {
        context.lastMinedPos = new BlockPos(pos);
        if (context.listener != null) {
            context.listener.onBlockMined(pos);
        }
    }

    public void onBlockSkipped(BlockPos pos) {
        if (context.listener != null) {
            context.listener.onBlockSkipped(pos);
        }
        if (context.client.player != null) {
            context.client.player.sendMessage(Text.literal("§e跳过方块: " + pos.getX() + "," + pos.getY() + "," + pos.getZ()), false);
        }
    }
}