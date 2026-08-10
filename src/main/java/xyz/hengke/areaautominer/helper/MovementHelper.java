package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.state.MovementState;
import xyz.hengke.areaautominer.context.state.SessionState;
import xyz.hengke.areaautominer.model.DangerType;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.model.WalkResult;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 玩家行走驱动：沿 vanilla A* 寻路算出的 {@link Path} 节点模拟按键移动。
 *
 * <p>原实现是"朝目标 XZ 直走 + 手写障碍/悬崖检测"的贪心逻辑，遇复杂地形易反复卡住。
 * 现改用 {@link PathfindingHelper} 调用 vanilla {@code PathNodeNavigator} 规划完整路径，
 * 再沿 {@code Path} 的节点逐个行走（转向当前节点 → 按 forward → 到达节点后 next()）。
 * 障碍/悬崖规避由 vanilla 路径本身（{@code PathNodeType} 惩罚）完成，手写检测已移除。</p>
 *
 * <p>性能补充（近距贪心直走）：vanilla A* 的 ChunkCache 重建 + 全区域节点扫描成本与路径
 * 长度无关，短跳与长距离行走开销相同。当 maxReachSquared 较小时（如 4 → 水平距离 2 格）
 * 玩家需对几乎每个方块做一次"短前进"，A* 调用次数被放大数倍。为此 {@link #followPath}
 * 在近距（≤ {@value #SHORT_HOP_MAX_DISTANCE} 格）、视线畅通（眼高 + 地面层双射线）且区块
 * 已加载时走 {@link #walkStraight} 贪心直走（O(1) 按键模拟，不跑 A*），被挡时经
 * {@code MovementState.shortHopBlocked} 快速回落 A* 兜底，超时/卡住逃生不受影响。</p>
 *
 * <p>保留：岩浆/虚空安全跳过（玩家生命安全保护）、卡住检测、超时/重试、转向平滑、跳跃冷却。</p>
 *
 * <p>方案 1A：本类不再持有 areaIterator/completionService（编排职责上提 Controller），
 * 目标方块由 Controller 传入，每 tick 返回 {@link WalkResult} 信号，由 Controller 经
 * {@link AdvanceCoordinator} 统一处理跳过/推进/完成。环境危险检测收敛到 {@link DangerChecker}。</p>
 *
 * <p>每 tick 流程（{@link #walkToBlock}）：跳跃冷却 → 危险跳过 → 重试延迟（静止等待，
 * 期间不做卡住检测，避免静止被计为卡住）→ 卡住检测 → 超时/卡住处理 → 终点到达处理 →
 * 沿路径节点行走。</p>
 */
public class MovementHelper {
    // 判定玩家是否卡住的最小移动距离（每 tick 移动小于此值视为静止）
    private static final double STUCK_MOVEMENT_THRESHOLD = 0.05;
    // 卡住锚点重置阈值：相对锚点累计位移达此值才重置锚点。
    // 原实现"位移 > 阈值即重置"等价于单 tick 位移判据，顶墙时的位置回退抖动会
    // 反复清零卡住计数，导致卡住永远不触发；改为累计位移判据后更稳。
    private static final double STUCK_ANCHOR_RESET_DISTANCE = 0.5;
    // 终点到达判定（超时兜底）：玩家距目标方块 XZ 中心足够近，可开始转向。比正常到达阈值
    // (config.arriveThreshold) 稍宽松，允许"走不动但已贴近"时容错到达
    private static final double CLOSE_ENOUGH_DISTANCE = 1.5;
    // 中间路径节点到达判定：距当前节点 XZ 中心小于此值即前进到下一节点（与 CLOSE_ENOUGH_DISTANCE
    // 数值相同但职责不同：前者是路径中途节点，后者是最终目标）
    private static final double NODE_ARRIVE_THRESHOLD = 1.5;
    // 行走失败重试延迟（负值：从 0 反向计数 N tick 后再恢复，给玩家稳定时间）
    private static final int RETRY_DELAY_TICKS = -10;
    // 目标区块未加载时的重试延迟（更长，等待区块加载完成）
    private static final int UNLOADED_RETRY_DELAY_TICKS = -20;
    // 跳跃后的冷却时间（tick），防止连续跳跃
    private static final int JUMP_COOLDOWN_TICKS = 10;
    // 重试跳跃的冷却时间（tick），比普通冷却稍长
    private static final int JUMP_COOLDOWN_RETRY_TICKS = 15;
    // 朝向与节点偏差超过此角度时只原地转向、不前进（转向/前进解耦，从机制上消除绕圈）
    // 注意：实际转向写入已帧级化（CameraHelper.smoothFrame 以 10°/tick 推进，与本阈值配套；
    // 曲率半径 v/ω≈1.24 格 < 节点阈值 1.5，不会绕圈，勿再降低行走转向速度）
    private static final float TURN_BEFORE_WALK_THRESHOLD = 30.0f;
    // 短距离直走（贪心）模式：目标水平距离（到中心，格）超过此值不走贪心，改走全量 A*。
    // maxReachSquared 较小时（如 4 → 水平距离 2 格）每个方块都要短跳，而 vanilla A* 的
    // ChunkCache 重建 + 全区域节点扫描成本与路径长度无关，短跳与长距离行走开销相同；
    // 贪心把这类短跳降为 O(1) 按键模拟，是解决"多次短前进修正"性能问题的核心。
    private static final double SHORT_HOP_MAX_DISTANCE = 6.0;
    // 短距离直走允许的垂直差上限（格，玩家脚部方块 Y 与目标 Y 之差）
    private static final int SHORT_HOP_MAX_DY = 1;
    // 贪心直走受阻判定：stuckCounter 达此值即置 shortHopBlocked，下一 tick 转入 A* 兜底。
    // 远小于 maxStuckTicks(20)，被墙/地形挡住时能快速降级而不是原地顶满超时。
    private static final int GREEDY_FALLBACK_STUCK_TICKS = 6;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final MovementState movement;
    private final SessionState session;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final NotificationService notificationService;
    private final PathfindingHelper pathfindingHelper;

    public MovementHelper(MinecraftClient client, MiningConfig config, MovementState movement, SessionState session,
                          InputHelper inputHelper, CameraHelper cameraHelper,
                          NotificationService notificationService, PathfindingHelper pathfindingHelper) {
        this.client = client;
        this.config = config;
        this.movement = movement;
        this.session = session;
        this.inputHelper = inputHelper;
        this.cameraHelper = cameraHelper;
        this.notificationService = notificationService;
        this.pathfindingHelper = pathfindingHelper;
    }

    /** 每 tick 行走驱动：按固定顺序执行各检查，返回结果信号由 Controller 统一处理 */
    public WalkResult walkToBlock(BlockPos targetPos) {
        tickJumpCooldown();

        // === 安全保护（保留）：岩浆/虚空直接跳过方块 ===
        if (checkDangerAndSkip(targetPos)) return WalkResult.SKIPPED;

        movement.setWalkTicks(movement.getWalkTicks() + 1);

        // === 重试延迟期：释放按键让玩家稳定，不寻路、不做卡住检测（静止等待不计入卡住）===
        // <= 0 包含恢复后的首个 tick（此时位移必然≈0，若用 < 0 会误计 1 次卡住）
        if (movement.getWalkTicks() <= 0) {
            inputHelper.releaseAllKeys();
            return WalkResult.ONGOING;
        }

        // === 卡住检测（保留）===
        updateStuckDetection();

        // === 超时 / 卡住：先看是否已到目标近旁，否则重试或跳过 ===
        WalkResult timeoutOrStuck = checkTimeoutOrStuck(targetPos);
        if (timeoutOrStuck != WalkResult.ONGOING) return timeoutOrStuck;

        // === 终点到达判定（保留）===
        if (checkArrive(targetPos)) return WalkResult.ARRIVED;

        // === 寻路 + 节点行走（核心）===
        return followPath(targetPos);
    }

    // ---------- 每 tick 检查子步骤 ----------

    private void tickJumpCooldown() {
        if (movement.getJumpCooldown() > 0) {
            movement.setJumpCooldown(movement.getJumpCooldown() - 1);
        }
    }

    /** @return true 表示检测到危险，已跳过（Controller 收到 SKIPPED 后统一推进） */
    private boolean checkDangerAndSkip(BlockPos targetPos) {
        if (DangerChecker.evaluate(client, targetPos) == DangerType.NONE) return false;
        inputHelper.releaseAllKeys();
        // 只记调试日志：玩家可见的"跳过方块"由 completionService.onBlockSkipped 统一发送，
        // 避免同一跳过事件连发两条通知
        notificationService.logDebug("检测到危险环境（岩浆/虚空），跳过方块: " + targetPos);
        movement.resetWalkSession();
        return true;
    }

    /** 基于累计位移的卡住检测：相对锚点移动过小则累计计数；累计位移达显著值才重置锚点 */
    private void updateStuckDetection() {
        // 方案B（M2）：原地转向期间不计卡住、不清零计数——转向是合法状态（XZ 位移必然≈0），
        // 但此前已累计的卡住计数不会被转向"清零掩盖"，顶墙等异常可跨转向持续累计直至触发
        if (movement.isTurningInPlace()) {
            movement.setLastPlayerX(client.player.getX());
            movement.setLastPlayerZ(client.player.getZ());
            return;
        }
        double movedDistance = Math.sqrt(SpatialMath.calculateHorizontalDistanceSquared(
                client.player.getX(), client.player.getZ(),
                movement.getLastPlayerX(), movement.getLastPlayerZ()));
        if (movedDistance < STUCK_MOVEMENT_THRESHOLD) {
            movement.setStuckCounter(movement.getStuckCounter() + 1);
        } else if (movedDistance >= STUCK_ANCHOR_RESET_DISTANCE) {
            // 真实前进达到显著距离才重置锚点；顶墙位置回退导致的单 tick 抖动
            // （0.05~0.2 格）既不会累加卡住计数、也不会反复清零锚点
            resetStuckAnchor();
        }
    }

    /** @return 超时/卡住已处理时的结果信号（ARRIVED 或 SKIPPED），未触发则 ONGOING */
    private WalkResult checkTimeoutOrStuck(BlockPos targetPos) {
        // 严格边界：walkTicks / stuckCounter 达到上限即触发（< 而非 <=，避免多放行 1 tick）
        if (movement.getWalkTicks() < config.maxWalkTicks
                && movement.getStuckCounter() < config.maxStuckTicks) {
            return WalkResult.ONGOING;
        }
        if (horizontalDistanceTo(targetPos) < CLOSE_ENOUGH_DISTANCE) {
            arriveAndFace(targetPos);
            return WalkResult.ARRIVED;
        } else {
            return triggerRetryOrSkip(targetPos, "行走超时或卡住", RETRY_DELAY_TICKS);
        }
    }

    /** @return true 表示已到达目标（含头顶跳跃处理），调用方直接返回 */
    private boolean checkArrive(BlockPos targetPos) {
        if (horizontalDistanceTo(targetPos) >= config.arriveThreshold) {
            return false;
        }
        // 跳跃只在"不跳就够不着"时进行：够得着直接转向，避免目标高 1 格也空跳一下
        boolean needJumpToReach = targetPos.getY() > client.player.getY()
                && !ReachChecker.isBlockWithinReach(client, targetPos, config);
        if (needJumpToReach) {
            BlockPos targetTopPos = targetPos.up();
            BlockPos targetAboveTopPos = targetPos.up(2);
            boolean hasSpaceOnTop = client.world.getBlockState(targetTopPos).isAir()
                    && client.world.getBlockState(targetAboveTopPos).isAir();

            // 跳跃进行中：释放跳跃键并等待落地；落地即转向，不再重复起跳
            if (movement.isJumpInProgress()) {
                inputHelper.setKeyPressed(client.options.jumpKey, false);
                if (!client.player.isOnGround()) {
                    return true;  // 仍在空中，继续等待
                }
                movement.setJumpInProgress(false);
                notificationService.logDebug("跳跃落地，准备转向");
                arriveAndFace(targetPos);
                return true;
            }
            // 静止在地面且可跳：起跳（每次到达只尝试一次）
            if (hasSpaceOnTop && client.player.isOnGround() && movement.getJumpCooldown() == 0) {
                inputHelper.setKeyPressed(client.options.jumpKey, true);
                movement.setJumpCooldown(JUMP_COOLDOWN_TICKS);
                movement.setJumpInProgress(true);
                notificationService.logDebug("跳跃到目标方块顶部");
                return true;
            }
            // 无空间 / 冷却中：站旁转向后挖掘
            if (!hasSpaceOnTop) {
                notificationService.logDebug("目标方块上方没有足够空间，改为站旁转向后挖掘");
            }
        }
        arriveAndFace(targetPos);
        return true;
    }

    /** 沿当前路径节点行走：贪心直走（近距） / 重算路径 / 前进节点 / 转向 / 前进 / 跳跃 */
    private WalkResult followPath(BlockPos targetPos) {
        Path path = movement.getCurrentPath();
        if (path == null || path.isFinished()) {
            // 近距目标：短距离直走（贪心）。避免每次短跳都重建 ChunkCache + 全区域 A*
            // （A* 成本与路径长度无关，短跳与长距离行走开销相同；maxReachSquared 较小时
            // 每个方块都要短跳，A* 调用次数被放大数倍）。视线受阻/被挡时自动回落 A*。
            if (!movement.isShortHopBlocked() && isShortHopCandidate(targetPos)) {
                return walkStraight(targetPos);
            }
            // 重新计算路径
            path = pathfindingHelper.computePath(targetPos);
            movement.setCurrentPath(path);
            if (path == null) {
                // 寻路失败：区分"区块未加载"与"不可达"，未加载给更长等待让区块加载完成
                boolean unloaded = !client.world.isPosLoaded(targetPos);
                return triggerRetryOrSkip(targetPos,
                        unloaded ? "目标区块未加载" : "目标不可达",
                        unloaded ? UNLOADED_RETRY_DELAY_TICKS : RETRY_DELAY_TICKS);
            }
        }

        // 到达当前节点 → 前进到下一节点
        BlockPos nodePos = path.getCurrentNodePos();
        if (nodePos == null) {
            // 防御：Path 在边界态下可能返回 null，重算路径兜底（避免 NPE）
            movement.setCurrentPath(null);
            return WalkResult.ONGOING;
        }
        if (horizontalDistanceTo(nodePos) < NODE_ARRIVE_THRESHOLD) {
            path.next();
            if (path.isFinished()) {
                // 路径走完但未到终点（精度误差），下 tick 重算
                movement.setCurrentPath(null);
                arriveAndFace(targetPos);
                return WalkResult.ARRIVED;
            }
            nodePos = path.getCurrentNodePos();
            if (nodePos == null) {
                movement.setCurrentPath(null);
                return WalkResult.ONGOING;
            }
        }

        // 计算朝向当前节点的目标角度，用于下方"先转向再前进"判定；
        // 实际视角写入由 CameraHelper.smoothFrame 按渲染帧推进（帧级平滑，无 tick 步进）
        float walkYaw = SpatialMath.calculateYawTo(
                client.player.getX(), client.player.getZ(),
                SpatialMath.centerX(nodePos), SpatialMath.centerZ(nodePos));
        float yawDiff = SpatialMath.normalizeYawDiff(walkYaw - client.player.getYaw());

        // 大角度偏差：先原地转向对准节点，再前进（避免转向慢+前进快导致的圆弧绕圈）。
        // 方案B（M2）：标记 turningInPlace（updateStuckDetection 跳过卡住累计），
        // 只同步锚点、不清零 stuckCounter——正常转向不计卡住，但顶墙等异常场景的
        // 卡住计数可跨转向持续累计，不再被反复清零掩盖
        if (turnInPlaceIfNeeded(yawDiff)) return WalkResult.ONGOING;

        // 跳跃判定：当前节点高于玩家方块 Y（上坡/上台阶）且 onGround
        boolean needJump = nodePos.getY() > client.player.getBlockPos().getY()
                && client.player.isOnGround()
                && movement.getJumpCooldown() == 0;

        inputHelper.setKeyPressed(client.options.forwardKey, true);
        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            movement.setJumpCooldown(JUMP_COOLDOWN_TICKS);
            notificationService.logDebug("沿路径跳跃上坡");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }
        return WalkResult.ONGOING;
    }

    // ---------- 短距离直走（贪心） ----------

    /**
     * 短距离直走候选判定：目标近距、垂直差小、区块已加载且视线畅通。
     * 视线畅通 = 眼高射线与地面层射线均未被目标之外的方块阻挡（目标自身不视为阻挡）。
     */
    private boolean isShortHopCandidate(BlockPos targetPos) {
        if (targetPos == null || client.world == null) return false;
        if (!client.world.isPosLoaded(targetPos)) return false;
        double dx = SpatialMath.centerX(targetPos) - client.player.getX();
        double dz = SpatialMath.centerZ(targetPos) - client.player.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > SHORT_HOP_MAX_DISTANCE) return false;
        if (Math.abs(targetPos.getY() - client.player.getBlockPos().getY()) > SHORT_HOP_MAX_DY) return false;
        return lineOfSightClear(targetPos);
    }

    /** 玩家眼/脚到目标中心的直线是否畅通（目标自身不视为阻挡） */
    private boolean lineOfSightClear(BlockPos targetPos) {
        Vec3d target = SpatialMath.center(targetPos);
        // 眼高射线：拦截高于眼位的障碍（1.5 格高栅栏等）
        Vec3d eye = new Vec3d(client.player.getX(),
                SpatialMath.getPlayerEyeY(client),
                client.player.getZ());
        if (!ReachChecker.isLineClear(client, eye, target, targetPos)) return false;
        // 地面层射线：拦截 1 格高的矮墙（眼高射线会从其上方越过）；目标低于玩家时跳过
        if (targetPos.getY() >= client.player.getBlockPos().getY()) {
            Vec3d feet = new Vec3d(client.player.getX(), client.player.getY() + 0.2, client.player.getZ());
            Vec3d lowTarget = new Vec3d(SpatialMath.centerX(targetPos), targetPos.getY() + 0.2, SpatialMath.centerZ(targetPos));
            if (!ReachChecker.isLineClear(client, feet, lowTarget, targetPos)) return false;
        }
        return true;
    }

    /**
     * 短距离直走（贪心）：不重建 ChunkCache、不跑全区域 A*，直接转向 + 按住前进逼近目标；
     * 目标高于玩家时沿用上坡跳跃。转向由 CameraHelper.smoothFrame 按帧推进（贪心目标经
     * MovementState.shortHopTarget 传入）。被挡（stuckCounter 达阈值）时置 shortHopBlocked，
     * 下一 tick 由 followPath 转入 A* 兜底；stuckCounter 仍正常累计，超时逃生不受影响。
     */
    private WalkResult walkStraight(BlockPos targetPos) {
        movement.setShortHopTarget(targetPos);

        float walkYaw = SpatialMath.calculateYawTo(
                client.player.getX(), client.player.getZ(),
                SpatialMath.centerX(targetPos), SpatialMath.centerZ(targetPos));
        float yawDiff = SpatialMath.normalizeYawDiff(walkYaw - client.player.getYaw());

        // 大角度偏差：先原地转向对准目标再前进（与 followPath 的转向分支一致，DRY-2）
        if (turnInPlaceIfNeeded(yawDiff)) return WalkResult.ONGOING;

        boolean needJump = targetPos.getY() > client.player.getBlockPos().getY()
                && client.player.isOnGround()
                && movement.getJumpCooldown() == 0;
        inputHelper.setKeyPressed(client.options.forwardKey, true);
        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            movement.setJumpCooldown(JUMP_COOLDOWN_TICKS);
            notificationService.logDebug("短距离直走上坡");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }

        // 阻挡降级：按住前进但位移过小（stuckCounter 达阈值）→ 置 blocked，下 tick 走 A* 兜底。
        // 阈值远小于 maxStuckTicks，被墙/地形挡住能快速降级而非原地顶满超时
        if (movement.getStuckCounter() >= GREEDY_FALLBACK_STUCK_TICKS) {
            movement.setShortHopBlocked(true);
            notificationService.logDebug("短距离直走受阻，转入 A* 寻路");
        }
        return WalkResult.ONGOING;
    }

    // ---------- 状态处理与重置 ----------

    /**
     * 大角度偏差（> {@link #TURN_BEFORE_WALK_THRESHOLD}）→ 原地转向对准：释放前进/跳跃、
     * 标记 turningInPlace、同步锚点（DRY-2：followPath 与 walkStraight 共用）。
     *
     * @return true 表示本 tick 已原地转向处理，调用方应直接返回 ONGOING
     */
    private boolean turnInPlaceIfNeeded(float yawDiff) {
        if (Math.abs(yawDiff) <= TURN_BEFORE_WALK_THRESHOLD) {
            movement.setTurningInPlace(false);
            return false;
        }
        inputHelper.setKeyPressed(client.options.forwardKey, false);
        inputHelper.setKeyPressed(client.options.jumpKey, false);
        movement.setTurningInPlace(true);
        movement.setLastPlayerX(client.player.getX());
        movement.setLastPlayerZ(client.player.getZ());
        return true;
    }

    /** 玩家到方块中心（XZ 平面）的水平距离（+0.5 = BlockPos 中心偏移） */
    private double horizontalDistanceTo(BlockPos pos) {
        return Math.sqrt(SpatialMath.calculateHorizontalDistanceSquared(
                client.player.getX(), client.player.getZ(),
                SpatialMath.centerX(pos), SpatialMath.centerZ(pos)));
    }

    /** 重置卡住锚点：以当前位置为基准，后续位移与其比较 */
    private void resetStuckAnchor() {
        movement.setStuckCounter(0);
        movement.setLastPlayerX(client.player.getX());
        movement.setLastPlayerZ(client.player.getZ());
    }

    /**
     * 行走失败（超时/卡住/寻路失败）的统一处理：递增重试计数，
     * 未超限则进入重试延迟（清空路径下 tick 重算），超限则返回 SKIPPED 由 Controller 跳过。
     *
     * @param retryDelayTicks 重试前等待的 tick 数（负值，从 0 反向计数），未加载用更长等待
     * @return ONGOING（进入重试）或 SKIPPED（重试超限，应跳过当前方块）
     */
    private WalkResult triggerRetryOrSkip(BlockPos targetPos, String reason, int retryDelayTicks) {
        movement.setWalkRetryCount(movement.getWalkRetryCount() + 1);

        if (movement.getWalkRetryCount() <= config.maxWalkRetries) {
            notificationService.logDebug(reason + "，第 " + movement.getWalkRetryCount() + " 次重试（重算路径）");
            inputHelper.releaseAllKeys();
            movement.setWalkTicks(retryDelayTicks);
            resetStuckAnchor();
            movement.setCurrentPath(null);
            // 重试即脱离转向态，防止延迟结束后 updateStuckDetection 被残留的 turningInPlace 跳过
            movement.setTurningInPlace(false);
            // 重试时原地跳跃尝试脱困
            if (movement.getWalkRetryCount() > 1 && client.player.isOnGround() && movement.getJumpCooldown() == 0) {
                inputHelper.setKeyPressed(client.options.jumpKey, true);
                movement.setJumpCooldown(JUMP_COOLDOWN_RETRY_TICKS);
                notificationService.logDebug("重试时尝试跳跃");
            }
            return WalkResult.ONGOING;
        }
        notificationService.logDebug(reason + "，重试 " + config.maxWalkRetries + " 次仍失败，跳过方块");
        inputHelper.releaseAllKeys();
        movement.resetWalkSession();
        return WalkResult.SKIPPED;
    }

    /** 到达目标，转入 FACING_BLOCK（提取原到达逻辑） */
    private void arriveAndFace(BlockPos targetPos) {
        inputHelper.releaseAllKeys();
        movement.setWaitTicks(config.moveWaitTicks);
        movement.setMovingWait(true);
        movement.resetWalkSession();
        cameraHelper.calculateTargetLook(targetPos);
        session.setState(MiningState.FACING_BLOCK);
        notificationService.logDebug("到达目标位置，准备转向");
    }
}
