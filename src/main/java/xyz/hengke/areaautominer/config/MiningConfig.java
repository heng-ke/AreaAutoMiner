package xyz.hengke.areaautominer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import xyz.hengke.areaautominer.model.MinerMod;

public class MiningConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiningConfig.class);
    private static final String CONFIG_NAME = "areaautominer.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MiningConfig INSTANCE;

    private int facingWaitTicks = 15;
    private int shortFacingWaitTicks = 4;
    private int moveWaitTicks = 3;
    private int maxAirSkipPerTick = 5;
    private int maxWalkTicks = 200;
    private int maxStuckTicks = 20;
    private int maxBreakTicks = 400;
    private double maxReachSquared = 16.0;
    private double arriveThreshold = 1.2;
    private double fallDangerThreshold = 3.0;
    private double maxVerticalDistance = 4.0;
    private int maxWalkRetries = 2;
    private int maxFacingRetries = 2;
    private boolean debug = false;
    private MinerMod minerMod = MinerMod.FROM_TOP_DOWN;
    private int maxRollbackRetries = 3;
    private int rollbackCheckInterval = 20;
    private boolean enableRollbackDetection = true;
    private int minToolDurability = 10;

    private MiningConfig() {}

    public static MiningConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static MiningConfig load() {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                MiningConfig config = GSON.fromJson(reader, MiningConfig.class);
                return config;
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        MiningConfig config = new MiningConfig();
        config.save();
        return config;
    }

    public void save() {
        File configFile = getConfigFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_NAME);
    }

    public int getFacingWaitTicks() {
        return facingWaitTicks;
    }

    public void setFacingWaitTicks(int facingWaitTicks) {
        this.facingWaitTicks = facingWaitTicks;
    }

    public int getShortFacingWaitTicks() {
        return shortFacingWaitTicks;
    }

    public void setShortFacingWaitTicks(int shortFacingWaitTicks) {
        this.shortFacingWaitTicks = shortFacingWaitTicks;
    }

    public int getMoveWaitTicks() {
        return moveWaitTicks;
    }

    public void setMoveWaitTicks(int moveWaitTicks) {
        this.moveWaitTicks = moveWaitTicks;
    }

    public int getMaxAirSkipPerTick() {
        return maxAirSkipPerTick;
    }

    public void setMaxAirSkipPerTick(int maxAirSkipPerTick) {
        this.maxAirSkipPerTick = maxAirSkipPerTick;
    }

    public int getMaxWalkTicks() {
        return maxWalkTicks;
    }

    public void setMaxWalkTicks(int maxWalkTicks) {
        this.maxWalkTicks = maxWalkTicks;
    }

    public int getMaxStuckTicks() {
        return maxStuckTicks;
    }

    public void setMaxStuckTicks(int maxStuckTicks) {
        this.maxStuckTicks = maxStuckTicks;
    }

    public int getMaxBreakTicks() {
        return maxBreakTicks;
    }

    public void setMaxBreakTicks(int maxBreakTicks) {
        this.maxBreakTicks = maxBreakTicks;
    }

    public double getMaxReachSquared() {
        return maxReachSquared;
    }

    public void setMaxReachSquared(double maxReachSquared) {
        this.maxReachSquared = maxReachSquared;
    }

    public double getArriveThreshold() {
        return arriveThreshold;
    }

    public void setArriveThreshold(double arriveThreshold) {
        this.arriveThreshold = arriveThreshold;
    }

    public double getFallDangerThreshold() {
        return fallDangerThreshold;
    }

    public void setFallDangerThreshold(double fallDangerThreshold) {
        this.fallDangerThreshold = fallDangerThreshold;
    }

    public double getMaxVerticalDistance() {
        return maxVerticalDistance;
    }

    public void setMaxVerticalDistance(double maxVerticalDistance) {
        this.maxVerticalDistance = maxVerticalDistance;
    }

    public int getMaxWalkRetries() {
        return maxWalkRetries;
    }

    public void setMaxWalkRetries(int maxWalkRetries) {
        this.maxWalkRetries = maxWalkRetries;
    }

    public int getMaxFacingRetries() {
        return maxFacingRetries;
    }

    public void setMaxFacingRetries(int maxFacingRetries) {
        this.maxFacingRetries = maxFacingRetries;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public MinerMod getMinerMod() {
        return minerMod;
    }

    public void setMinerMod(MinerMod minerMod) {
        this.minerMod = minerMod;
    }

    public int getMaxRollbackRetries() {
        return maxRollbackRetries;
    }

    public void setMaxRollbackRetries(int maxRollbackRetries) {
        this.maxRollbackRetries = maxRollbackRetries;
    }

    public int getRollbackCheckInterval() {
        return rollbackCheckInterval;
    }

    public void setRollbackCheckInterval(int rollbackCheckInterval) {
        this.rollbackCheckInterval = rollbackCheckInterval;
    }

    public boolean isRollbackDetectionEnabled() {
        return enableRollbackDetection;
    }

    public void setEnableRollbackDetection(boolean enableRollbackDetection) {
        this.enableRollbackDetection = enableRollbackDetection;
    }

    public int getMinToolDurability() {
        return minToolDurability;
    }
}