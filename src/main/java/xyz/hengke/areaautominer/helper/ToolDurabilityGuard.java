package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 工具耐久守卫（方案 5）：耐久检测 + 玩家通知。
 *
 * <p>从 BreakingHelper.breakBlockSurvival 内联逻辑提取，使"耐久策略变化
 * （自动换工具/仅警告）"不再影响挖掘执行逻辑。只做检测与通知，
 * 会话终止决策（teardown）由调用方（BreakingHelper）决定。</p>
 */
public class ToolDurabilityGuard {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final NotificationService notificationService;

    public ToolDurabilityGuard(MinecraftClient client, MiningConfig config, NotificationService notificationService) {
        this.client = client;
        this.config = config;
        this.notificationService = notificationService;
    }

    /**
     * 当前主手工具耐久是否低于阈值。
     *
     * @return true 表示耐久不足且已发送通知，调用方应暂停挖掘（可进一步终止会话）
     */
    public boolean shouldPause() {
        // 创造模式不消耗耐久，无需检查（isDamageable 已排除）
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
