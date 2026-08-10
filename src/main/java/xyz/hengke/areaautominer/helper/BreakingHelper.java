package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.BreakOutcome;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 挖掘驱动：每 tick 检查环境（岩浆/空气/不可破坏/超范围/视角偏移），
 * 通过后按游戏模式执行挖掘（创造=breakBlock，生存=attackBlock+updateBlockBreakingProgress）。
 *
 * <p>方案 1A：本类不再持有 areaIterator/completionService（编排职责上提 Controller），
 * 目标方块由 Controller 传入，每 tick 返回 {@link BreakOutcome} 信号，由 Controller 经
 * {@link AdvanceCoordinator} 统一处理推进/完成。环境危险检测收敛到 {@link DangerChecker}。</p>
 *
 * <p>依赖的状态对象:BreakingState(进度)、FacingState(目标角度)、SessionState(状态机)；行走请求经 WalkRequester（DRY-1）。</p>
 */
public class BreakingHelper {
    /** 生存模式维持挖掘的挥手间隔（tick） */
    private static final int SWING_INTERVAL = 6;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final BreakingState breaking;
    private final FacingState facing;
    private final SessionState session;
    private final NotificationService notificationService;
    private final CameraHelper cameraHelper;
    private final SessionLifecycle lifecycle;
    private final ToolDurabilityGuard toolDurabilityGuard;
    private final WalkRequester walkRequester;

    public BreakingHelper(MinecraftClient client, MiningConfig config,
                          BreakingState breaking, FacingState facing, SessionState session,
                          NotificationService notificationService,
                          CameraHelper cameraHelper, SessionLifecycle lifecycle,
                          ToolDurabilityGuard toolDurabilityGuard, WalkRequester walkRequester) {
        this.client = client;
        this.config = config;
        this.breaking = breaking;
        this.facing = facing;
        this.session = session;
        this.notificationService = notificationService;
        this.cameraHelper = cameraHelper;
        this.lifecycle = lifecycle;
        this.toolDurabilityGuard = toolDurabilityGuard;
        this.walkRequester = walkRequester;
    }

    /** 每 tick 挖掘驱动：返回结果信号由 Controller 统一处理推进/完成 */
    public BreakOutcome startBreaking(BlockPos targetPos) {
        // === 安全保护：玩家周围有岩浆（站立层 3×3 + 脚下，与 MovementHelper 共用判定）→ 跳过当前方块 ===
        if (DangerChecker.isLavaAroundPlayer(client)) {
            // 只记调试日志：玩家可见的"跳过方块"由 completionService.onBlockSkipped 统一发送，
            // 避免同一跳过事件连发两条通知
            notificationService.logDebug("检测到玩家周围有岩浆，跳过方块: " + targetPos);
            return BreakOutcome.SKIPPED;
        }

        // === 目标已被外部破坏（TNT/其他玩家）→ 推进 ===
        if (client.world.getBlockState(targetPos).isAir()) {
            return BreakOutcome.EXTERNALLY_REMOVED;
        }

        // === 基岩/屏障等不可破坏方块（hardness < 0）→ 直接跳过，避免生存模式卡满 maxBreakTicks 超时 ===
        if (client.world.getBlockState(targetPos).getHardness(client.world, targetPos) < 0) {
            notificationService.logDebug("目标方块不可破坏，跳过: " + targetPos);
            return BreakOutcome.SKIPPED;
        }

        // === 超出挖掘范围或无视线 → 重新行走 ===
        if (!ReachChecker.isBlockWithinReach(client, targetPos, config)) {
            // F1 修复：同一目标连续多次进入 WALKING（水平已近但垂直超距/无视线）→ 跳过，防死循环
            Vec3d playerPos = SpatialMath.getPlayerPos(client);
            if (walkRequester.requestWalkOrSkip(targetPos, playerPos.x, playerPos.z)
                    == WalkRequester.Result.SKIPPED) {
                notificationService.logDebug("目标方块多次无法到达，跳过: " + targetPos);
                return BreakOutcome.SKIPPED;
            }
            notificationService.logDebug("挖掘时超出范围或无视线，重新行走");
            return BreakOutcome.ONGOING;
        }

        // === 视角偏移过大 → 中断挖掘，重新转向（目标角度未变，无需重算 targetLook）===
        if (isFacingDrifted()) {
            cameraHelper.beginFacing();
            session.setState(MiningState.FACING_BLOCK);
            notificationService.logDebug("挖掘时视角偏移过大，重新转向");
            return BreakOutcome.ONGOING;
        }

        // === 挖掘超时：方块持续未被破坏（多数已被硬度预检拦截）→ 跳过 ===
        breaking.setBreakTicks(breaking.getBreakTicks() + 1);
        if (breaking.getBreakTicks() > config.maxBreakTicks) {
            notificationService.logDebug("挖掘超时，跳过: " + targetPos);
            return BreakOutcome.SKIPPED;
        }

        // === 按游戏模式挖掘 ===
        GameMode gameMode = client.interactionManager.getCurrentGameMode();
        if (gameMode == GameMode.CREATIVE) {
            return breakBlockCreative(targetPos);
        } else {
            return breakBlockSurvival(targetPos);
        }
    }

    // ---------- 挖掘执行 ----------

    /** 创造模式：breakBlock 立即破坏，以世界状态确认结果（不可靠的返回值不用） */
    private BreakOutcome breakBlockCreative(BlockPos targetPos) {
        client.interactionManager.breakBlock(targetPos);
        client.player.swingHand(Hand.MAIN_HAND);

        if (client.world.getBlockState(targetPos).isAir()) {
            return BreakOutcome.MINED;
        } else {
            // 防御分支：硬度预检已拦截不可破坏方块，此处兜底
            notificationService.logDebug("方块无法破坏（创造模式），跳过: " + targetPos);
            return BreakOutcome.SKIPPED;
        }
    }

    /** 生存模式：首 tick attackBlock 建立挖掘，之后每 tick 维持进度 */
    private BreakOutcome breakBlockSurvival(BlockPos targetPos) {
        // 工具耐久不足 → 暂停挖掘（创造模式不消耗耐久，无需检查）
        if (toolDurabilityGuard.shouldPause()) {
            // 方案A（M1）：完整结束会话（isMining=false + 释放按键 + 清理寻路）。
            // 旧实现只置 IDLE 而 isMining 仍为 true，玩家换好工具后按 K 只会停止挖掘，
            // 必须连按两次 K 才能恢复；完整 teardown 后按一次 K 即可重新开始。
            lifecycle.teardown();
            return BreakOutcome.ONGOING;
        }

        Direction direction = SpatialMath.calculateDirection(client, targetPos);

        if (breaking.isFirstBreakTick()) {
            client.interactionManager.attackBlock(targetPos, direction);
            client.player.swingHand(Hand.MAIN_HAND);
            breaking.setFirstBreakTick(false);
        }
        client.interactionManager.updateBlockBreakingProgress(targetPos, direction);

        if (breaking.getBreakTicks() % SWING_INTERVAL == 0) {
            client.player.swingHand(Hand.MAIN_HAND);
        }

        if (client.world.getBlockState(targetPos).isAir()) {
            return BreakOutcome.MINED;
        }
        return BreakOutcome.ONGOING;
    }

    // ---------- 工具 ----------

    /** 视角是否偏离目标超过重对准阈值（阈值由配置 reFacingThresholdDegrees 控制） */
    private boolean isFacingDrifted() {
        float threshold = (float) config.reFacingThresholdDegrees;
        return SpatialMath.yawDiffTo(facing, client) > threshold
                || SpatialMath.pitchDiffTo(facing, client) > threshold;
    }
}
