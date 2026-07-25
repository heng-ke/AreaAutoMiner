package xyz.hengke.areaautominer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class AreaAutoMinerClient implements ClientModInitializer {
    private BlockPos pos1 = null, pos2 = null;

    @Override
    public void onInitializeClient() {
        UseItemCallback.EVENT.register(this::onWoodenSwordUse);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onRenderWorld);
    }

    private void onRenderWorld(WorldRenderContext context) {
        if (pos1 != null && pos2 != null) {
            RegionRenderer.renderRegion(context, pos1, pos2);
        }
    }

    private ActionResult onWoodenSwordUse(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() != Items.WOODEN_SWORD &&
                stack.getItem() != Items.STONE_SWORD &&
                stack.getItem() != Items.IRON_SWORD &&
                stack.getItem() != Items.GOLDEN_SWORD &&
                stack.getItem() != Items.DIAMOND_SWORD) return ActionResult.PASS;

        BlockHitResult hit = (BlockHitResult) player.raycast(5.0, 0.0f, false);
        if (hit == null) return ActionResult.PASS;

        if (!player.isSneaking()) {
            pos1 = hit.getBlockPos();
            player.sendMessage(Text.literal("§a点1已记录: " + pos1), false);
        } else {
            pos2 = hit.getBlockPos();
            player.sendMessage(Text.literal("§a点2已记录: " + pos2), false);
        }
        return ActionResult.SUCCESS;
    }
}
