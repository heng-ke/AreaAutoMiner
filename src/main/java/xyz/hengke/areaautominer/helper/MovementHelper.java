package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 玩家行走驱动：沿 vanilla A* 寻路算出的 {@link Path} 节点模拟按键移动。
 *
 * <p>原实现是"朝目标 XZ 直走 + 手写障碍/悬崖检测"的贪心逻辑，遇复杂地形易反复卡住。
 * 现改用 {@link PathfindingHelper} 调用 vanilla {@code PathNodeNavigator} 规划完整路径，
 * 再沿 {@code Path} 的节点逐个行走（转向当前节点 → 按 forward → 到达节点后 next()）。
 * 障碍/悬崖规避由 vanilla 路径本身（{@code PathNodeType} 惩罚）完成，手写检测已移除。</p>
 *
 * <p>保留：岩浆/虚空安全跳过（玩家生命安全保护）、卡住检测、超时/重试、转向平滑、跳跃冷却。</p>
 */
public class MovementHelper {
    // 判定玩家是否卡住的最小移动距离（每 tick 移动小于此值视为静止）
    private static final double STUCK_MOVEMENT_THRESHOLD = 0.05;
    // 终点到达判定：玩家距目标方块 XZ 中心足够近，可开始转向（方块+玩家碰撞框的大约距离）
    private static final double CLOSE_ENOUGH_DISTANCE = 1.5;
    // 中间路径节点到达判定：距当前节点 XZ 中心小于此值即前进到下一节点
    private static final double NODE_ARRIVE_THRESHOLD = 1.5;
    // 行走重试延迟（负值表示从0开始反向计数10 tick再开始，即给予10 tick的宽限时间）
    private static final int WALK_RETRY_DELAY_TICKS = -10;
    // 跳跃后的冷却时间（tick），防止连续跳跃
    private static final int JUMP_COOLDOWN_TICKS = 10;
    // 重试跳跃的冷却时间（tick），比普通冷却稍长
    private static final int JUMP_COOLDOWN_RETRY_TICKS = 15;
    // 每 tick 最大的视角 Y 轴旋转步数（度），限制转向速度以模拟自然视角（指数平滑的硬上限保护）
    private static final float MAX_YAW_STEP = 15.0f;
    // 指数平滑因子：每 tick 修正 yaw 偏差的比例（0.9 时前进阶段曲率半径≈0.92格，小于节点到达阈值，避免圆弧绕圈）
    private static final float YAW_EMA_FACTOR = 0.9f;
    // 朝向与节点偏差超过此角度时只原地转向、不前进（转向/前进解耦，从机制上消除绕圈）
    private static final float TURN_BEFORE_WALK_THRESHOLD = 30.0f;

    private final MiningContext context;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final AreaIterator areaIterator;
    private final NotificationService notificationService;
    private final MiningCompletionService completionService;
    private final PathfindingHelper pathfindingHelper;

    public MovementHelper(MiningContext context, InputHelper inputHelper, CameraHelper cameraHelper,
                          AreaIterator areaIterator, NotificationService notificationService,
                          MiningCompletionService completionService, PathfindingHelper pathfindingHelper) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.cameraHelper = cameraHelper;
        this.areaIterator = areaIterator;
        this.notificationService = notificationService;
        this.completionService = completionService;
        this.pathfindingHelper = pathfindingHelper;
    }

    public void walkToBlock() {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.getClient();

        if (context.getJumpCooldown() > 0) {
            context.setJumpCooldown(context.getJumpCooldown() - 1);
        }

        BlockPos targetPos = areaIterator.getCurrentPos();

        // === 安全保护（保留）：岩浆/虚空直接跳过方块 ===
        if (isLavaDanger(targetPos) || isVoidDanger(targetPos)) {
            inputHelper.releaseAllKeys();
            notificationService.sendMessage("§c检测到危险环境（岩浆/虚空），跳过方块: " + targetPos);
            resetWalkAndSkipOrAdvance(targetPos);
            return;
        }

        context.setWalkTicks(context.getWalkTicks() + 1);

        // === 卡住检测（保留）===
        double currentPlayerX = client.player.getX();
        double currentPlayerZ = client.player.getZ();
        double movedDistance = Math.sqrt(
            Math.pow(currentPlayerX - context.getLastPlayerX(), 2) +
            Math.pow(currentPlayerZ - context.getLastPlayerZ(), 2)
        );
        if (movedDistance < STUCK_MOVEMENT_THRESHOLD) {
            context.setStuckCounter(context.getStuckCounter() + 1);
        } else {
            context.setStuckCounter(0);
            context.setLastPlayerX(currentPlayerX);
            context.setLastPlayerZ(currentPlayerZ);
        }

        // === 重试延迟期：释放按键让玩家稳定，不寻路 ===
        if (context.getWalkTicks() < 0) {
            inputHelper.releaseAllKeys();
            return;
        }

        // === 超时 / 卡住：先看是否已到目标近旁，否则重试或跳过 ===
        if (context.getWalkTicks() > config.getMaxWalkTicks() ||
            context.getStuckCounter() > config.getMaxStuckTicks()) {

            double dxT = targetPos.getX() + 0.5 - client.player.getX();
            double dzT = targetPos.getZ() + 0.5 - client.player.getZ();
            if (Math.sqrt(dxT * dxT + dzT * dzT) < CLOSE_ENOUGH_DISTANCE) {
                arriveAndFace(targetPos);
                return;
            }
            triggerRetryOrSkip(targetPos, "行走超时或卡住", WALK_RETRY_DELAY_TICKS);
            return;
        }

        // === 终点到达判定（保留）===
        double dxEnd = targetPos.getX() + 0.5 - client.player.getX();
        double dzEnd = targetPos.getZ() + 0.5 - client.player.getZ();
        double endDist = Math.sqrt(dxEnd * dxEnd + dzEnd * dzEnd);
        if (endDist < config.getArriveThreshold()) {
            // 终点在头顶上方且可跳跃上去 → 跳跃（保留原逻辑）
            if (targetPos.getY() > client.player.getY()) {
                BlockPos targetTopPos = targetPos.up();
                BlockPos targetAboveTopPos = targetPos.up(2);
                boolean hasSpaceOnTop = client.world.getBlockState(targetTopPos).isAir() &&
                                         client.world.getBlockState(targetAboveTopPos).isAir();

                if (hasSpaceOnTop && client.player.isOnGround() && context.getJumpCooldown() == 0) {
                    inputHelper.setKeyPressed(client.options.jumpKey, true);
                    context.setJumpCooldown(JUMP_COOLDOWN_TICKS);
                    notificationService.logDebug("跳跃到目标方块顶部");
                    return;
                } else if (!hasSpaceOnTop) {
                    notificationService.logDebug("目标方块上方没有足够空间，改为站旁转向后挖掘");
                }
            }
            arriveAndFace(targetPos);
            return;
        }

        // === 寻路 + 节点行走（核心）===
        Path path = context.getCurrentPath();
        if (path == null || path.isFinished()) {
            // 重新计算路径
            path = pathfindingHelper.computePath(targetPos);
            context.setCurrentPath(path);
            if (path == null) {
                // 寻路失败：区分"区块未加载"与"不可达"，未加载给更长等待让区块加载完成
                boolean unloaded = !client.world.isPosLoaded(targetPos);
                triggerRetryOrSkip(targetPos,
                        unloaded ? "目标区块未加载" : "目标不可达",
                        unloaded ? -20 : WALK_RETRY_DELAY_TICKS);
                return;
            }
        }

        // 当前目标节点
        BlockPos nodePos = path.getCurrentNodePos();
        double dxN = nodePos.getX() + 0.5 - client.player.getX();
        double dzN = nodePos.getZ() + 0.5 - client.player.getZ();
        double nodeDist = Math.sqrt(dxN * dxN + dzN * dzN);

        // 到达当前节点 → 前进到下一节点
        if (nodeDist < NODE_ARRIVE_THRESHOLD) {
            path.next();
            if (path.isFinished()) {
                // 路径走完但未到终点（精度误差），下 tick 重算
                context.setCurrentPath(null);
                arriveAndFace(targetPos);
                return;
            }
            nodePos = path.getCurrentNodePos();
            dxN = nodePos.getX() + 0.5 - client.player.getX();
            dzN = nodePos.getZ() + 0.5 - client.player.getZ();
        }

        // 转向当前节点（atan2 指向最近节点）+ 指数平滑（EMA）替代线性 clamp
        // 指数平滑消除小偏差时"跳变到目标"感，节点切换时大角度分散到多 tick 平滑完成
        float walkYaw = (float) Math.atan2(dzN, dxN) * (180.0F / (float) Math.PI) - 90.0F;
        float yawDiff = SpatialHelper.normalizeYawDiff(walkYaw - client.player.getYaw());
        float clampedDiff = Math.max(-MAX_YAW_STEP, Math.min(MAX_YAW_STEP, yawDiff));
        float smoothedYaw = client.player.getYaw() + clampedDiff * YAW_EMA_FACTOR;
        client.player.setYaw(smoothedYaw);

        // 大角度偏差：先原地转向对准节点，再前进（避免转向慢+前进快导致的圆弧绕圈）。
        // 原地转向期间同步卡住锚点并清零卡住计数，防止误判卡住
        if (Math.abs(yawDiff) > TURN_BEFORE_WALK_THRESHOLD) {
            inputHelper.setKeyPressed(client.options.forwardKey, false);
            inputHelper.setKeyPressed(client.options.jumpKey, false);
            context.setStuckCounter(0);
            context.setLastPlayerX(client.player.getX());
            context.setLastPlayerZ(client.player.getZ());
            return;
        }

        // 跳跃判定：当前节点高于玩家方块 Y（上坡/上台阶）且 onGround
        boolean needJump = nodePos.getY() > client.player.getBlockPos().getY()
                && client.player.isOnGround()
                && context.getJumpCooldown() == 0;

        inputHelper.setKeyPressed(client.options.forwardKey, true);
        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            context.setJumpCooldown(JUMP_COOLDOWN_TICKS);
            notificationService.logDebug("沿路径跳跃上坡");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }
    }

    /**
     * 行走失败（超时/卡住/寻路失败）的统一处理：递增重试计数，
     * 未超限则进入重试延迟（清空路径下 tick 重算），超限则跳过当前方块。
     *
     * @param retryDelayTicks 重试前等待的 tick 数（负值，从 0 反向计数），未加载用更长等待
     */
    private void triggerRetryOrSkip(BlockPos targetPos, String reason, int retryDelayTicks) {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.getClient();
        context.setWalkRetryCount(context.getWalkRetryCount() + 1);

        if (context.getWalkRetryCount() <= config.getMaxWalkRetries()) {
            notificationService.logDebug(reason + "，第 " + context.getWalkRetryCount() + " 次重试（重算路径）");
            inputHelper.releaseAllKeys();
            context.setWalkTicks(retryDelayTicks);
            context.setStuckCounter(0);
            context.setLastPlayerX(client.player.getX());
            context.setLastPlayerZ(client.player.getZ());
            context.setCurrentPath(null);
            // 重试时原地跳跃尝试脱困
            if (context.getWalkRetryCount() > 1 && client.player.isOnGround() && context.getJumpCooldown() == 0) {
                inputHelper.setKeyPressed(client.options.jumpKey, true);
                context.setJumpCooldown(JUMP_COOLDOWN_RETRY_TICKS);
                notificationService.logDebug("重试时尝试跳跃");
            }
            return;
        }
        notificationService.logDebug(reason + "，重试 " + config.getMaxWalkRetries() + " 次仍失败，跳过方块");
        inputHelper.releaseAllKeys();
        resetWalkAndSkipOrAdvance(targetPos);
    }

    /** 到达目标，转入 FACING_BLOCK（提取原到达逻辑） */
    private void arriveAndFace(BlockPos targetPos) {
        MiningConfig config = MiningConfig.getInstance();
        inputHelper.releaseAllKeys();
        context.setWaitTicks(config.getMoveWaitTicks());
        context.setMovingWait(true);
        context.setAdjacentBlock(false);
        context.setCurrentPath(null);
        cameraHelper.calculateTargetLook(targetPos);
        context.setState(MiningState.FACING_BLOCK);
        context.setWalkTicks(0);
        context.setStuckCounter(0);
        context.setWalkRetryCount(0);
        notificationService.logDebug("到达目标位置，准备转向");
    }

    /** 重置行走状态并前进到下一方块或完成（提取原跳过逻辑） */
    private void resetWalkAndSkipOrAdvance(BlockPos targetPos) {
        context.setWalkTicks(0);
        context.setStuckCounter(0);
        context.setWalkRetryCount(0);
        context.setCurrentPath(null);
        completionService.onBlockSkipped(targetPos);
        if (!areaIterator.advancePosition()) {
            completionService.completeMining();
            return;
        }
        context.setState(MiningState.FINDING_BLOCK);
    }

    // === 保留：玩家生命安全保护（非寻路逻辑）===
    private boolean isLavaDanger(BlockPos targetPos) {
        MinecraftClient client = context.getClient();
        // 使用 FluidTags.LAVA 涵盖岩浆源块与流动岩浆（两者均造成伤害）
        if (client.world.getFluidState(targetPos).isIn(FluidTags.LAVA)) return true;
        if (client.world.getFluidState(targetPos.down()).isIn(FluidTags.LAVA)) return true;
        BlockPos playerPos = new BlockPos(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (client.world.getFluidState(playerPos.add(dx, 0, dz)).isIn(FluidTags.LAVA)) return true;
            }
        }
        return false;
    }

    private boolean isVoidDanger(BlockPos targetPos) {
        MinecraftClient client = context.getClient();
        if (targetPos.getY() < client.world.getBottomY()) return true;
        // 接近世界底部时检查下方支撑（避免玩家走到悬空方块后坠入虚空）
        if (targetPos.getY() < client.world.getBottomY() + 8) {
            for (int dy = -1; dy >= -5; dy--) {
                if (!client.world.getBlockState(targetPos.add(0, dy, 0)).isAir()) {
                    return false;  // 下方有支撑，安全
                }
            }
            return true;  // 下方 5 格全空气，虚空危险
        }
        return false;
    }
}
