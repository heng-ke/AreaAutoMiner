package xyz.hengke.areaautominer.service;

import net.minecraft.text.Text;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;

public class NotificationService {
    private final MiningContext context;

    public NotificationService(MiningContext context) {
        this.context = context;
    }

    public void sendMessage(String text) {
        if (context.client.player != null) {
            context.client.player.sendMessage(Text.literal("§a[AreaAutoMiner]"+text), false);
        }
    }

    public void logDebug(String message) {
        if (MiningConfig.DEBUG) {
            if (context.client.player != null) {
                context.client.player.sendMessage(Text.literal("§7[DEBUG] " + message), false);
            }
        }
    }
}