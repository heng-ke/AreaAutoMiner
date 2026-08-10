package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkCache;
import xyz.hengke.areaautominer.config.MiningConfig;

import java.util.Set;

/**
 * 封装 vanilla 的 A* 寻路（net.minecraft.entity.ai.pathing）。
 *
 * <p>vanilla 的 {@link net.minecraft.entity.ai.pathing.PathNodeNavigator#findPathToAny} 需要
 * {@link MobEntity} 参数，而 {@code ClientPlayerEntity} 并非 {@code MobEntity} 的子类。
 * 这里通过创建一个一次性的虚拟 {@link ZombieEntity}（{@code PathAwareEntity} 子类，
 * 陆地寻路标准实体）解决：将其定位到玩家位置用于寻路计算，不加入世界、不 tick，
 * 因此其注册的 AI goal 不会执行。僵尸尺寸 0.6×1.95 与玩家 0.6×1.8 算出的
 * {@code entityBlockYSize} 均为 2，寻路结果一致。</p>
 *
 * <p>vanilla 的 {@link net.minecraft.entity.ai.pathing.EntityNavigation#tick()} 会直接操作
 * 实体 velocity 来移动，那是服务端 MobEntity 逻辑，不能用于客户端模拟玩家按键。
 * 因此本类只复用其<b>寻路计算</b>部分（算出 {@link Path}），移动由
 * {@link MovementHelper} 沿 {@code Path} 节点模拟按键完成。</p>
 */
public class PathfindingHelper {
    // 寻路目标精度（格），vanilla MobNavigation 常用 1
    private static final int PATH_DISTANCE = 1;
    // 范围乘数，1.0 即不放大 followRange
    private static final float RANGE_MULTIPLIER = 1.0f;
    // ChunkCache 半径上限，避免过大导致性能问题
    private static final int MAX_CACHE_RADIUS = 64;
    // ChunkCache 最小半径
    private static final int MIN_CACHE_RADIUS = 16;

    private final MinecraftClient client;
    private final MiningConfig config;

    /** 复用的临时虚拟实体，定位到玩家位置用于寻路计算，不加入世界、不 tick */
    private MobEntity phantomMob;
    /** 复用的节点生成器与导航器（followRange 变化时重建） */
    private LandPathNodeMaker nodeMaker;
    private PathNodeNavigator navigator;
    private int currentRange;

    public PathfindingHelper(MinecraftClient client, MiningConfig config) {
        this.client = client;
        this.config = config;
    }

    /**
     * 计算从玩家当前位置到 target 的路径。
     *
     * @return {@link Path}（可能为 null，表示不可达或目标在未加载区块）
     */
    public Path computePath(BlockPos target) {
        if (client.world == null || client.player == null) return null;

        int followRange = config.pathFollowRange;
        // 防御性钳制：UI 限制 8~64，但直接编辑配置文件可绕过；<1 会导致寻路器/范围异常
        if (followRange < 1) followRange = 1;

        // 方案A（M3）：寻路范围至少覆盖玩家→目标的水平距离（+2 格余量），
        // 避免目标稍远（> pathFollowRange 默认 32 格）时寻路必然失败、被误判"不可达"而跳过方块
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        double distanceToTarget = Math.sqrt(SpatialMath.calculateHorizontalDistanceSquared(
                playerPos.x, playerPos.z,
                SpatialMath.centerX(target), SpatialMath.centerZ(target)));
        int effectiveRange = Math.max(followRange, (int) Math.ceil(distanceToTarget) + 2);
        if (effectiveRange > MAX_CACHE_RADIUS) effectiveRange = MAX_CACHE_RADIUS;

        // 1) 确保 / 同步虚拟实体到玩家位置
        MobEntity mob = ensurePhantomMob(client.world);
        mob.refreshPositionAndAngles(
                playerPos.x, playerPos.y, playerPos.z,
                client.player.getYaw(), 0.0f);

        // 2) 构造 ChunkCache：以玩家为中心，半径 = min(MAX, max(MIN, effectiveRange))
        //    半径需覆盖玩家→目标距离，否则目标节点不在缓存内会寻路失败
        BlockPos origin = client.player.getBlockPos();
        int radius = Math.min(MAX_CACHE_RADIUS, Math.max(MIN_CACHE_RADIUS, effectiveRange));
        BlockPos min = origin.add(-radius, -radius, -radius);
        BlockPos max = origin.add(radius, radius, radius);
        ChunkCache cache = new ChunkCache(client.world, min, max);

        // 3) 重建 / 复用 navigator（range 变化时重建）
        if (navigator == null || currentRange != effectiveRange) {
            nodeMaker = new LandPathNodeMaker();
            nodeMaker.setCanSwim(false);
            // 玩家跳跃高度（约 1.25 格）不足以翻越栅栏（1.5 格高），false 使路径绕开栅栏而非直穿（直穿会卡在栅栏前）
            nodeMaker.setCanWalkOverFences(false);
            navigator = new PathNodeNavigator(nodeMaker, effectiveRange);
            currentRange = effectiveRange;
        }

        // 4) 执行 A* 寻路
        // 注：PathNodeNavigator.findPathToAny 内部第一步会调用 nodeMaker.init(cache, mob)
        //     （重置内部缓存、重建 PathContext），此处无需也不应手动 init
        // 失败原因（区块未加载 / 不可达）由 MovementHelper 按 isPosLoaded 区分并打印重试信息，
        // 此处不重复打印，避免同一事件输出两行
        return navigator.findPathToAny(
                cache, mob, Set.of(target), (float) effectiveRange, PATH_DISTANCE, RANGE_MULTIPLIER);
    }

    /**
     * 确保 phantomMob 存在且归属当前 ClientWorld（换维度 / 世界时重建）。
     * 直接用 {@link ZombieEntity} 的 public 单参数构造器，避免 EntityType.create 的
     * feature flag 检查与 SpawnReason 参数，且不会调用 world.spawnEntity。
     */
    private MobEntity ensurePhantomMob(ClientWorld world) {
        if (phantomMob == null || phantomMob.getEntityWorld() != world) {
            if (phantomMob != null) {
                phantomMob.discard();
            }
            phantomMob = new ZombieEntity(world);
        }
        return phantomMob;
    }

    /** 挖掘结束时清理虚拟实体与寻路器，避免内存泄漏 */
    public void cleanup() {
        if (phantomMob != null) {
            phantomMob.discard();
            phantomMob = null;
        }
        nodeMaker = null;
        navigator = null;
        currentRange = 0;
    }
}
