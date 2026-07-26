package xyz.hengke.areaautominer.context;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.model.MiningState;

public class MiningContext {
    public MinecraftClient client;
    public MiningListener listener;
    
    public boolean isMining = false;
    public BlockPos pos1 = null, pos2 = null;
    public int currentY = 0;
    public int currentX = 0;
    public int currentZ = 0;
    public MiningState state = MiningState.IDLE;
    public int waitTicks = 0;
    
    public float targetYaw = 0.0f;
    public float targetPitch = 0.0f;
    public boolean firstBreakTick = false;
    public float jitterOffset = 0.0f;
    public long lastJitterUpdate = 0;
    public float currentJitterYaw = 0.0f;
    public float currentJitterPitch = 0.0f;
    public BlockPos lastMinedPos = null;
    public boolean isAdjacentBlock = false;
    public boolean movingWait = false;
    
    public int walkTicks = 0;
    public double lastPlayerX = 0, lastPlayerZ = 0;
    public int stuckCounter = 0;
    public int breakTicks = 0;
    public int jumpCooldown = 0;
    public int walkRetryCount = 0;
    public int facingRetryCount = 0;

    public MiningContext(MinecraftClient client) {
        this.client = client;
    }
}