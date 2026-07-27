package xyz.hengke.areaautominer.config;

public class MiningConfig {
    public static final int FACING_WAIT_TICKS = 15;
    public static final int SHORT_FACING_WAIT_TICKS = 4;
    public static final int MOVE_WAIT_TICKS = 3;
    public static final int MAX_AIR_SKIP_PER_TICK = 5;
    public static final int MAX_WALK_TICKS = 200;
    public static final int MAX_STUCK_TICKS = 20;
    public static final int MAX_BREAK_TICKS = 400;
    
    public static final double MAX_REACH_SQUARED = 16.0;
    public static final double ARRIVE_THRESHOLD = 1.2;
    public static final double FALL_DANGER_THRESHOLD = 3.0;
    public static final double MAX_VERTICAL_DISTANCE = 4.0;
    
    public static final int MAX_WALK_RETRIES = 2;
    public static final int MAX_FACING_RETRIES = 2;
    
    public static final boolean DEBUG = false;
}