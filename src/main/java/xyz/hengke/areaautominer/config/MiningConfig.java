package xyz.hengke.areaautominer.config;

/**
 * MiningConfig 类，用于存储挖掘相关的配置参数
 * 包含各种时间阈值、距离阈值和调试标志等常量
 */
public class MiningConfig {
    // 面向等待的 ticks 数量（标准）
    public static final int FACING_WAIT_TICKS = 15;
    // 面向等待的 ticks 数量（短）
    public static final int SHORT_FACING_WAIT_TICKS = 4;
    // 移动等待的 ticks 数量
    public static final int MOVE_WAIT_TICKS = 3;
    // 每个 ticks 最多跳过的空气方块数量
    public static final int MAX_AIR_SKIP_PER_TICK = 5;
    // 最大行走 ticks 数量
    public static final int MAX_WALK_TICKS = 200;
    // 最大卡住 ticks 数量
    public static final int MAX_STUCK_TICKS = 20;
    // 最大挖掘 ticks 数量
    public static final int MAX_BREAK_TICKS = 400;
    
    // 最大到达距离的平方
    public static final double MAX_REACH_SQUARED = 16.0;
    // 到达阈值
    public static final double ARRIVE_THRESHOLD = 1.2;
    // 坠落危险阈值
    public static final double FALL_DANGER_THRESHOLD = 3.0;
    // 最大垂直距离
    public static final double MAX_VERTICAL_DISTANCE = 4.0;
    
    // 最大行走重试次数
    public static final int MAX_WALK_RETRIES = 2;
    // 最大面向重试次数
    public static final int MAX_FACING_RETRIES = 2;
    
    // 调试标志
    public static final boolean DEBUG = false;
}