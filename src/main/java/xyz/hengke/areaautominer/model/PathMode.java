package xyz.hengke.areaautominer.model;

public enum PathMode {
    NONE("无"),
    GREEDY("贪心直走"),
    A_STAR("A* 僵尸寻路");

    private final String displayName;

    PathMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
