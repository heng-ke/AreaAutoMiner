package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.state.BreakingState;
import xyz.hengke.areaautominer.context.state.FacingState;
import xyz.hengke.areaautominer.context.state.MovementState;
import xyz.hengke.areaautominer.context.state.SessionState;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 挖掘驱动：每 tick 检查环境（岩浆/空气/不可破坏/超范围/视角偏移），
 * 通过后按游戏模式执行挖掘（创造=breakBlock，生存=attackBlock+updateBlockBreakingProgress）。
 *
 * <p>所有"推进到下一方块"统一走 {@link #advanceToNext()}，挖完/跳过/遍历结束
 * 统一由 {@link #minedAndAdvance} / {@link #skipAndAdvance} 处理，保证 breakTicks 等状态一致。</p>
 *
 * <p>依赖的状态对象:BreakingState(进度)、FacingState(目标角度)、MovementState(重新行走)、SessionState(状态机)。</p>
 */
public class BreakingHelper {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final AreaIterator areaIterator;
    private final BreakingState breaking;
    private final FacingState facing;
    private final MovementState movement;
    private final SessionState session;
    private final NotificationService notificationService;
    private final MiningCompletionService completionService;
    private final CameraHelper cameraHelper;
    private final SessionLifecycle lifecycle;

    public BreakingHelper(MinecraftClient client, MiningConfig config, AreaIterator areaIterator,
                          BreakingState breaking, FacingState facing, MovementState movement, SessionState session,
                          NotificationService notificationService, MiningCompletionService completionService,
                          CameraHelper cameraHelper, SessionLifecycle lifecycle) {
        this.client = client;
        this.config = config;
        this.areaIterator = areaIterator;
        this.breaking = breaking;
        this.facing = facing;
        this.movement = movement;
        this.session = session;
        this.notificationService = notificationService;
        this.completionService = completionService;
        this.cameraHelper = cameraHelper;
        this.lifecycle = lifecycle;
    }

    public void startBreaking() {
        BlockPos targetPos = areaIterator.getCurrentPos();

        // === 安全保护：玩家周围有岩浆（站立层 3×3 + 脚下，与 MovementHelper 共用判定）→ 跳过当前方块 ===
        if (SpatialHelper.isLavaAroundPlayer(client)) {
            // 只记调试日志：玩家可见的"跳过方块"由 completionService.onBlockSkipped 统一发送，
            // 避免同一跳过事件连发两条通知
            notificationService.logDebug("检测到玩家周围有岩浆，跳过方块: " + targetPos);
            skipAndAdvance(targetPos);
            return;
        }

        // === 目标已被外部破坏（TNT/其他玩家）→ 推进 ===
        if (client.world.getBlockState(targetPos).isAir()) {
            advanceToNext();
            return;
        }

        // === 基岩/屏障等不可破坏方块（hardness < 0）→ 直接跳过，避免生存模式卡满 maxBreakTicks 超时 ===
        if (client.world.getBlockState(targetPos).getHardness(client.world, targetPos) < 0) {
            notificationService.logDebug("目标方块不可破坏，跳过: " + targetPos);
            skipAndAdvance(targetPos);
            return;
        }

        // === 超出挖掘范围或无视线 → 重新行走 ===
        if (!SpatialHelper.isBlockWithinReach(client, targetPos, config)) {
            movement.startWalkingToBlock(client.player.getX(), client.player.getZ());
            session.setState(MiningState.WALKING_TO_BLOCK);
            notificationService.logDebug("挖掘时超出范围或无视线，重新行走");
            return;
        }

        // === 视角偏移过大 → 中断挖掘，重新转向（目标角度未变，无需重算 targetLook）===
        if (isFacingDrifted()) {
            cameraHelper.beginFacing();
            session.setState(MiningState.FACING_BLOCK);
            notificationService.logDebug("挖掘时视角偏移过大，重新转向");
            return;
        }

        // === 挖掘超时：方块持续未被破坏（多数已被硬度预检拦截）→ 跳过 ===
        breaking.setBreakTicks(breaking.getBreakTicks() + 1);
        if (breaking.getBreakTicks() > config.maxBreakTicks) {
            notificationService.logDebug("挖掘超时，跳过: " + targetPos);
            skipAndAdvance(targetPos);
            return;
        }

        // === 按游戏模式挖掘 ===
        GameMode gameMode = client.interactionManager.getCurrentGameMode();
        if (gameMode == GameMode.CREATIVE) {
            breakBlockCreative(client, targetPos);
        } else {
            breakBlockSurvival(client, targetPos);
        }
    }

    // ---------- 挖掘执行 ----------

    /** 创造模式：breakBlock 立即破坏，以世界状态确认结果（不可靠的返回值不用） */
    private void breakBlockCreative(MinecraftClient client, BlockPos targetPos) {
        client.interactionManager.breakBlock(targetPos);
        client.player.swingHand(Hand.MAIN_HAND);

        if (client.world.getBlockState(targetPos).isAir()) {
            minedAndAdvance(client, targetPos);
        } else {
            // 防御分支：硬度预检已拦截不可破坏方块，此处兜底
            notificationService.logDebug("方块无法破坏（创造模式），跳过: " + targetPos);
            skipAndAdvance(targetPos);
        }
    }

    /** 生存模式：首 tick attackBlock 建立挖掘，之后每 tick 维持进度 */
    private void breakBlockSurvival(MinecraftClient client, BlockPos targetPos) {
        // 工具耐久不足 → 暂停挖掘（创造模式不消耗耐久，无需检查）
        ItemStack toolStack = client.player.getStackInHand(Hand.MAIN_HAND);
        if (toolStack.isDamageable() && toolStack.getMaxDamage() > 0) {
            int currentDurability = toolStack.getMaxDamage() - toolStack.getDamage();
            if (currentDurability < config.minToolDurability) {
                notificationService.sendMessage(String.format(Messages.TOOL_LOW_DURABILITY, currentDurability));
                // 方案A（M1）：完整结束会话（isMining=false + 释放按键 + 清理寻路）。
                // 旧实现只置 IDLE 而 isMining 仍为 true，玩家换好工具后按 K 只会停止挖掘，
                // 必须连按两次 K 才能恢复；完整 teardown 后按一次 K 即可重新开始。
                lifecycle.teardown();
                return;
            }
        }

        Direction direction = SpatialHelper.calculateDirection(client, targetPos);

        if (breaking.isFirstBreakTick()) {
            client.interactionManager.attackBlock(targetPos, direction);
            client.player.swingHand(Hand.MAIN_HAND);
            breaking.setFirstBreakTick(false);
        }
        client.interactionManager.updateBlockBreakingProgress(targetPos, direction);

        if (breaking.getBreakTicks() % 6 == 0) {
            client.player.swingHand(Hand.MAIN_HAND);
        }

        if (client.world.getBlockState(targetPos).isAir()) {
            minedAndAdvance(client, targetPos);
        }
    }

    // ---------- 状态推进（统一出口）----------

    /** 方块已挖掉：记录 + 推进（统一清零 breakTicks，供下次挖掘重新计时） */
    private void minedAndAdvance(MinecraftClient client, BlockPos targetPos) {
        completionService.onBlockMined(targetPos);
        breaking.setBreakTicks(0);
        notificationService.logDebug("方块挖掘完成: " + targetPos);
        advanceToNext();
    }

    /** 方块被跳过：通知 + 推进 */
    private void skipAndAdvance(BlockPos targetPos) {
        completionService.onBlockSkipped(targetPos);
        breaking.setBreakTicks(0);
        advanceToNext();
    }

    /** 推进到下一个遍历方块；遍历结束则完成挖掘 */
    private void advanceToNext() {
        if (!areaIterator.advancePosition()) {
            completionService.completeMining();
            return;
        }
        session.setState(MiningState.FINDING_BLOCK);
    }

    // ---------- 工具 ----------

    /** 视角是否偏离目标超过重对准阈值（阈值由配置 reFacingThresholdDegrees 控制） */
    private boolean isFacingDrifted() {
        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(facing.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(facing.getTargetPitch() - client.player.getPitch());
        float threshold = (float) config.reFacingThresholdDegrees;
        return Math.abs(yawDiff) > threshold || pitchDiff > threshold;
    }
}
