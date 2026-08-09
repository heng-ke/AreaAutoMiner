package xyz.hengke.areaautominer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import xyz.hengke.areaautominer.client.LifecycleManager;
import xyz.hengke.areaautominer.client.SelectionTool;
import xyz.hengke.areaautominer.config.MiningConfigHolder;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.di.MinerComponents;
import xyz.hengke.areaautominer.render.RegionRenderer;

/**
 * 客户端入口：只负责注册事件与分发。
 * 业务逻辑已下沉到 SelectionTool（选区）/ LifecycleManager（死亡与暂停检测）/ MiningController（状态机）。
 */
public class AreaAutoMinerClient implements ClientModInitializer {
    /** 开始 / 停止挖掘的快捷键，可在游戏内「选项 → 控制」中重新绑定 */
    private static final KeyBinding TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.areaautominer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyBinding.Category.GAMEPLAY));

    private SelectionTool selectionTool;
    private LifecycleManager lifecycleManager;
    private MiningController miningController;

    @Override
    public void onInitializeClient() {
        MinerComponents components = new MinerComponents(MinecraftClient.getInstance());
        miningController = components.controller();
        selectionTool = new SelectionTool(MiningConfigHolder.get());
        lifecycleManager = new LifecycleManager(miningController);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        UseItemCallback.EVENT.register(selectionTool::onSwordUse);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onRenderWorld);
        // 帧级视角平滑:在相机更新后、地形绘制前推进转向视角(消除 tick 级 6° 步进)
        WorldRenderEvents.START_MAIN.register(ctx -> miningController.getCameraHelper().smoothFrame());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (miningController.isMining()) {
                miningController.stopMining();
            }
        });
    }

    private void onClientTick(MinecraftClient client) {
        // 玩家死亡 / 暂停检测
        if (lifecycleManager.handleTick(client)) return;

        // 通过注册的 KeyBinding 检测（可在控制设置中改键），wasPressed 自带边沿检测
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
        // 挖掘进行中时，用红色边框高亮当前目标方块
        RegionRenderer.renderTargetBlock(context, miningController.getCurrentTargetPos());
    }
}
