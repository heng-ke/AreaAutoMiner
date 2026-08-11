package xyz.hengke.areaautominer.state;

public class FacingState {
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;

    private int faceTicks = 0;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private float lastTickProgress = 0.0F;

    public float getTargetYaw() {
        return targetYaw;
    }

    public void setTargetYaw(float targetYaw) {
        this.targetYaw = targetYaw;
    }

    public float getTargetPitch() {
        return targetPitch;
    }

    public void setTargetPitch(float targetPitch) {
        this.targetPitch = targetPitch;
    }

    public int getFaceTicks() {
        return faceTicks;
    }

    public void setFaceTicks(int faceTicks) {
        this.faceTicks = faceTicks;
    }

    public double getLastMouseX() {
        return lastMouseX;
    }

    public void setLastMouseX(double lastMouseX) {
        this.lastMouseX = lastMouseX;
    }

    public double getLastMouseY() {
        return lastMouseY;
    }

    public void setLastMouseY(double lastMouseY) {
        this.lastMouseY = lastMouseY;
    }

    public float getLastTickProgress() {
        return lastTickProgress;
    }

    public void setLastTickProgress(float lastTickProgress) {
        this.lastTickProgress = lastTickProgress;
    }

    public void beginFacingSession() {
        faceTicks = 0;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
        lastTickProgress = 0.0F;
    }

    public void reset() {
        targetYaw = 0.0f;
        targetPitch = 0.0f;
        beginFacingSession();
    }
}
