package xyz.hengke.areaautominer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import xyz.hengke.areaautominer.model.MinerMod;

;

public class MiningConfig {
    private static final String CONFIG_NAME = "areaautominer.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MiningConfig INSTANCE;

    public int facingWaitTicks = 15;
    public int shortFacingWaitTicks = 4;
    public int moveWaitTicks = 3;
    public int maxAirSkipPerTick = 5;
    public int maxWalkTicks = 200;
    public int maxStuckTicks = 20;
    public int maxBreakTicks = 400;
    public double maxReachSquared = 16.0;
    public double arriveThreshold = 1.2;
    public double fallDangerThreshold = 3.0;
    public double maxVerticalDistance = 4.0;
    public int maxWalkRetries = 2;
    public int maxFacingRetries = 2;
    public boolean debug = false;
    public MinerMod minerMod = MinerMod.FROM_TOP_DOWN;

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
                e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_NAME);
    }

    public int getFacingWaitTicks() {
        return facingWaitTicks;
    }

    public int getShortFacingWaitTicks() {
        return shortFacingWaitTicks;
    }

    public int getMoveWaitTicks() {
        return moveWaitTicks;
    }

    public int getMaxAirSkipPerTick() {
        return maxAirSkipPerTick;
    }

    public int getMaxWalkTicks() {
        return maxWalkTicks;
    }

    public int getMaxStuckTicks() {
        return maxStuckTicks;
    }

    public int getMaxBreakTicks() {
        return maxBreakTicks;
    }

    public double getMaxReachSquared() {
        return maxReachSquared;
    }

    public double getArriveThreshold() {
        return arriveThreshold;
    }

    public double getFallDangerThreshold() {
        return fallDangerThreshold;
    }

    public double getMaxVerticalDistance() {
        return maxVerticalDistance;
    }

    public int getMaxWalkRetries() {
        return maxWalkRetries;
    }

    public int getMaxFacingRetries() {
        return maxFacingRetries;
    }

    public boolean isDebug() {
        return debug;
    }
    
    public MinerMod getMinerMod() {
        return minerMod;
    }
}