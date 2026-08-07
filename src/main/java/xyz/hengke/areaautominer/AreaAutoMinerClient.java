package xyz.hengke.areaautominer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
    private boolean swordUsedThisTick = false;
    private enum LifeCycleState {
        ALIVE,
        DEAD,
        PAUSED
    }
    private LifeCycleState lifeCycleState = LifeCycleState.ALIVE;

    @Override
    public void onInitializeClient() {
        miningController = new MiningController(MinecraftClient.getInstance());
        miningController.setListener(new MiningListener() {
            @Override
            public void onMineComplete() {
                // 通知已由 MiningCompletionService.forceCompleteMining() 统一发送，避免重复
            }

            @Override
            public void onBlockSkipped(BlockPos pos) {
            }

            @Override
            public void onBlockMined(BlockPos pos) {
            }

            @Override
            public void onStartMining(BlockPos pos1, BlockPos pos2) {
            }

            @Override
            public void onStopMining() {
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        UseItemCallback.EVENT.register(this::onSwordUse);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onRenderWorld);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (miningController.isMining()) {
                miningController.stopMining();
            }
        });
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        // isDeadOrDying() 在 1.21.11 中不存在，使用 getHealth() <= 0 替代
        if (!client.player.isAlive() || client.player.getHealth() <= 0) {
            if (lifeCycleState != LifeCycleState.DEAD && miningController.isMining()) {
                miningController.stopMining();
                client.player.sendMessage(Text.literal("§c玩家死亡，停止挖掘"), false);
            }
            lifeCycleState = LifeCycleState.DEAD;
            return;
        }

        // 任意 GUI 打开时暂停（包括聊天框、箱子、配置界面等）
        boolean isPaused = client.currentScreen != null;
        if (isPaused && miningController.isMining()) {
            if (lifeCycleState != LifeCycleState.PAUSED) {
                miningController.stopMining();
                client.player.sendMessage(Text.literal("§c界面打开，停止挖掘"), false);
            }
            lifeCycleState = LifeCycleState.PAUSED;
        } else if (!isPaused && client.player.isAlive()) {
            lifeCycleState = LifeCycleState.ALIVE;
        }

        boolean kPressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
        if (kPressed && !kPressedLastTick) {
            if (!miningController.isMining()) {
                miningController.startMining(pos1, pos2);
            } else {
                miningController.stopMining();
            }
        }
        kPressedLastTick = kPressed;
        miningController.tick();
        swordUsedThisTick = false;
    }

    private void onRenderWorld(WorldRenderContext context) {
        if (pos1 != null && pos2 != null) {
            RegionRenderer.renderRegion(context, pos1, pos2);
        }
        // 挖掘进行中时，用红色边框高亮当前目标方块
        RegionRenderer.renderTargetBlock(context, miningController.getCurrentTargetPos());
    }

    private ActionResult onSwordUse(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (swordUsedThisTick) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() != Items.WOODEN_SWORD &&
                stack.getItem() != Items.STONE_SWORD &&
                stack.getItem() != Items.IRON_SWORD &&
                stack.getItem() != Items.GOLDEN_SWORD &&
                stack.getItem() != Items.DIAMOND_SWORD &&
                stack.getItem() != Items.NETHERITE_SWORD) return ActionResult.PASS;

        BlockHitResult hit = (BlockHitResult) player.raycast(5.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return ActionResult.PASS;

        swordUsedThisTick = true;
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
