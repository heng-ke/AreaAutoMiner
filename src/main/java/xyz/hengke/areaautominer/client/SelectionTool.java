package xyz.hengke.areaautominer.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;

public class SelectionTool {
    private final MiningConfig config;
    private final NotificationService notificationService;
    private BlockPos pos1 = null;
    private BlockPos pos2 = null;
    private boolean swordUsedThisTick = false;

    public SelectionTool(MiningConfig config, NotificationService notificationService) {
        this.config = config;
        this.notificationService = notificationService;
    }

    public ActionResult onSwordUse(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (swordUsedThisTick) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (!isSword(stack.getItem())) return ActionResult.PASS;

        BlockHitResult hit = (BlockHitResult) player.raycast(config.selectionMaxDistance, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return ActionResult.PASS;

        swordUsedThisTick = true;
        if (!player.isSneaking()) {
            pos1 = hit.getBlockPos();
            notificationService.sendMessage(Messages.POINT1_RECORDED + pos1);
        } else {
            pos2 = hit.getBlockPos();
            notificationService.sendMessage(Messages.POINT2_RECORDED + pos2);
        }
        return ActionResult.SUCCESS;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public void endTick() {
        swordUsedThisTick = false;
    }

    private boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
                || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
                || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }
}
