package xyz.hengke.areaautominer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import xyz.hengke.areaautominer.client.SelectionTool;
import xyz.hengke.areaautominer.config.MiningConfigHolder;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.di.MinerComponents;
import xyz.hengke.areaautominer.lifecycle.PlayerLifecycleManager;
import xyz.hengke.areaautominer.model.PathMode;
import xyz.hengke.areaautominer.render.RegionRenderer;

import java.util.List;


public class AreaAutoMinerClient implements ClientModInitializer {
    private static final KeyBinding TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.areaautominer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyBinding.Category.GAMEPLAY));

    private SelectionTool selectionTool;
    private PlayerLifecycleManager lifecycleManager;
    private MiningController miningController;

    @Override
    public void onInitializeClient() {
        MinerComponents components = new MinerComponents(MinecraftClient.getInstance());
        miningController = components.controller();
        selectionTool = new SelectionTool(MiningConfigHolder.get(), components.notificationService());
        lifecycleManager = new PlayerLifecycleManager(miningController);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        UseItemCallback.EVENT.register(selectionTool::onSwordUse);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onRenderWorld);
        WorldRenderEvents.START_MAIN.register(ctx -> miningController.onRenderFrame());
        HudElementRegistry.addLast(AreaAutoMiner.id("path_mode_hud"), (drawContext, tickCounter) -> {
            PathMode mode = miningController.getDebugPathMode();
            if (mode != null) {
                int color = switch (mode) {
                    case GREEDY -> 0xFF55FF55;   // 绿色
                    case A_STAR -> 0xFF55FFFF;   // 青色
                    case NONE -> 0xFFAAAAAA;     // 灰色
                };
                MinecraftClient client = MinecraftClient.getInstance();
                drawContext.drawText(client.textRenderer, "寻路模式: " + mode.getDisplayName(), 4, 4, color, true);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (miningController.isMining()) {
                miningController.stopMining();
            }
        });
    }

    private void onClientTick(MinecraftClient client) {

        if (lifecycleManager.handleTick(client)) return;

        if (TOGGLE_KEY.wasPressed()) {
            if (!miningController.isMining()) {
                miningController.startMining(selectionTool.getPos1(), selectionTool.getPos2());
            } else {
                miningController.stopMining();
            }
        }
        miningController.tick();
        selectionTool.endTick();
    }

    private void onRenderWorld(WorldRenderContext context) {
        if (selectionTool.getPos1() != null && selectionTool.getPos2() != null) {
            RegionRenderer.renderRegion(context, selectionTool.getPos1(), selectionTool.getPos2());
        }

        RegionRenderer.renderTargetBlock(context, miningController.getCurrentTargetPos());

        List<BlockPos> pathNodes = miningController.getDebugWalkPath();
        if (pathNodes != null) {
            RegionRenderer.renderPath(context, pathNodes);
        }
    }
}
