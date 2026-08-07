package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkCache;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.service.NotificationService;

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

    private final MiningContext context;
    private final NotificationService notificationService;

    /** 复用的临时虚拟实体，定位到玩家位置用于寻路计算，不加入世界、不 tick */
    private MobEntity phantomMob;
    /** 复用的节点生成器与导航器（followRange 变化时重建） */
    private LandPathNodeMaker nodeMaker;
    private PathNodeNavigator navigator;
    private int currentRange;

    public PathfindingHelper(MiningContext context, NotificationService notificationService) {
        this.context = context;
        this.notificationService = notificationService;
    }

    /**
     * 计算从玩家当前位置到 target 的路径。
     *
     * @return {@link Path}（可能为 null，表示不可达或目标在未加载区块）
     */
    public Path computePath(BlockPos target) {
        MinecraftClient client = context.getClient();
        if (client.world == null || client.player == null) return null;

        MiningConfig config = MiningConfig.getInstance();
        int followRange = config.getPathFollowRange();

        // 1) 确保 / 同步虚拟实体到玩家位置
        MobEntity mob = ensurePhantomMob(client.world);
        mob.refreshPositionAndAngles(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYaw(), 0.0f);

        // 2) 构造 ChunkCache：以玩家为中心，半径 = min(MAX, max(MIN, followRange))
        //    半径需覆盖玩家→目标距离，否则目标节点不在缓存内会寻路失败
        BlockPos origin = client.player.getBlockPos();
        int radius = Math.min(MAX_CACHE_RADIUS, Math.max(MIN_CACHE_RADIUS, followRange));
        BlockPos min = origin.add(-radius, -radius, -radius);
        BlockPos max = origin.add(radius, radius, radius);
        ChunkCache cache = new ChunkCache(client.world, min, max);

        // 3) 重建 / 复用 navigator（range 变化时重建）
        if (navigator == null || currentRange != followRange) {
            nodeMaker = new LandPathNodeMaker();
            nodeMaker.setCanSwim(false);
            nodeMaker.setCanWalkOverFences(true);
            navigator = new PathNodeNavigator(nodeMaker, followRange);
            currentRange = followRange;
        }

        // 4) 初始化节点生成器（每次寻路都要 init，重置内部缓存）
        nodeMaker.init(cache, mob);

        // 5) 执行 A* 寻路
        Path path = navigator.findPathToAny(
                cache, mob, Set.of(target), (float) followRange, PATH_DISTANCE, RANGE_MULTIPLIER);

        if (path == null) {
            notificationService.logDebug("vanilla 寻路失败（不可达或区块未加载）: " + target);
        }
        return path;
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
