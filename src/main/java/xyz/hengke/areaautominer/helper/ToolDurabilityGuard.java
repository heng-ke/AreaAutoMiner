package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;

public class ToolDurabilityGuard {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final NotificationService notificationService;

    public ToolDurabilityGuard(MinecraftClient client, MiningConfig config, NotificationService notificationService) {
        this.client = client;
        this.config = config;
        this.notificationService = notificationService;
    }

    public boolean shouldPause() {
        ItemStack toolStack = client.player.getStackInHand(Hand.MAIN_HAND);
        if (toolStack.isDamageable() && toolStack.getMaxDamage() > 0) {
            int currentDurability = toolStack.getMaxDamage() - toolStack.getDamage();
            if (currentDurability < config.minToolDurability) {
                notificationService.sendMessage(String.format(Messages.TOOL_LOW_DURABILITY, currentDurability));
                return true;
            }
        }
        return false;
    }
}
