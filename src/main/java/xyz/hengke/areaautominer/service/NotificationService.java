package xyz.hengke.areaautominer.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import xyz.hengke.areaautominer.config.MiningConfig;

/**
 * 玩家可见消息与调试日志。仅依赖 client 与 config，与挖掘状态无关。
 */
public class NotificationService {
    private final MinecraftClient client;
    private final MiningConfig config;

    public NotificationService(MinecraftClient client, MiningConfig config) {
        this.client = client;
        this.config = config;
    }

    public void sendMessage(String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§a[AreaAutoMiner]" + text), false);
        }
    }

    public void logDebug(String message) {
        if (config.debug) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§7[DEBUG] " + message), false);
            }
        }
    }
}
