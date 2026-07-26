package xyz.hengke.areaautominer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.render.RegionRenderer;

public class AreaAutoMinerClient implements ClientModInitializer {
    private BlockPos pos1 = null, pos2 = null;
    private boolean kPressedLastTick = false;
    private MiningController miningController;

    @Override
    public void onInitializeClient() {
        miningController = new MiningController(MinecraftClient.getInstance());
        miningController.setListener(new MiningListener() {
            @Override
            public void onMineComplete() {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§a挖掘完成！"), false);
                }
            }

            @Override
            public void onBlockSkipped(BlockPos pos) {
                // 已在 AreaMiner 中处理
            }

            @Override
            public void onBlockMined(BlockPos pos) {
                // 可选：添加挖掘完成的视觉反馈
            }

            @Override
            public void onStartMining(BlockPos pos1, BlockPos pos2) {
                // 可选：添加开始挖掘的视觉反馈
            }

            @Override
            public void onStopMining() {
                // 可选：添加停止挖掘的视觉反馈
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        UseItemCallback.EVENT.register(this::onSwordUse);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onRenderWorld);
    }

    private void onClientTick(MinecraftClient client) {
        boolean kPressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
        if (kPressed && !kPressedLastTick) {
            if (client.player != null) {
                if (!miningController.isMining()) {
                    miningController.startMining(pos1, pos2);
                } else {
                    miningController.stopMining();
                }
            }
        }
        kPressedLastTick = kPressed;

        miningController.tick();
    }

    private void onRenderWorld(WorldRenderContext context) {
        if (pos1 != null && pos2 != null) {
            RegionRenderer.renderRegion(context, pos1, pos2);
        }
    }

    private ActionResult onSwordUse(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

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
