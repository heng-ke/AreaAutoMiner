package xyz.hengke.areaautominer.context.state;

/**
 * 转向状态：视角转向的目标角度，由 CameraHelper 写入并逐步逼近。
 */
public class FacingState {
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;

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

    public void reset() {
        targetYaw = 0.0f;
        targetPitch = 0.0f;
    }
}
