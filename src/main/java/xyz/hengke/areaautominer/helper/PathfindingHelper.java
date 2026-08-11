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

public class PathfindingHelper {
    private static final int PATH_DISTANCE = 1;
    private static final float RANGE_MULTIPLIER = 1.0f;
    private static final int MAX_CACHE_RADIUS = 64;
    private static final int MIN_CACHE_RADIUS = 16;

    private final MinecraftClient client;
    private final MiningConfig config;
    private MobEntity phantomMob;
    private LandPathNodeMaker nodeMaker;
    private PathNodeNavigator navigator;
    private int currentRange;

    public PathfindingHelper(MinecraftClient client, MiningConfig config) {
        this.client = client;
        this.config = config;
    }

    public Path computePath(BlockPos target) {
        if (client.world == null || client.player == null) return null;

        int followRange = config.pathFollowRange;
        if (followRange < 1) followRange = 1;

        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        double distanceToTarget = Math.sqrt(SpatialMath.calculateHorizontalDistanceSquared(
                playerPos.x, playerPos.z,
                SpatialMath.centerX(target), SpatialMath.centerZ(target)));
        int effectiveRange = Math.max(followRange, (int) Math.ceil(distanceToTarget) + 2);
        if (effectiveRange > MAX_CACHE_RADIUS) effectiveRange = MAX_CACHE_RADIUS;

        MobEntity mob = ensurePhantomMob(client.world);
        mob.refreshPositionAndAngles(
                playerPos.x, playerPos.y, playerPos.z,
                client.player.getYaw(), 0.0f);

        BlockPos origin = client.player.getBlockPos();
        int radius = Math.min(MAX_CACHE_RADIUS, Math.max(MIN_CACHE_RADIUS, effectiveRange));
        BlockPos min = origin.add(-radius, -radius, -radius);
        BlockPos max = origin.add(radius, radius, radius);
        ChunkCache cache = new ChunkCache(client.world, min, max);

        if (navigator == null || currentRange != effectiveRange) {
            nodeMaker = new LandPathNodeMaker();
            nodeMaker.setCanSwim(false);
            nodeMaker.setCanWalkOverFences(false);
            navigator = new PathNodeNavigator(nodeMaker, effectiveRange);
            currentRange = effectiveRange;
        }

        return navigator.findPathToAny(
                cache, mob, Set.of(target), (float) effectiveRange, PATH_DISTANCE, RANGE_MULTIPLIER);
    }

    private MobEntity ensurePhantomMob(ClientWorld world) {
        if (phantomMob == null || phantomMob.getEntityWorld() != world) {
            if (phantomMob != null) {
                phantomMob.discard();
            }
            phantomMob = new ZombieEntity(world);
        }
        return phantomMob;
    }

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
