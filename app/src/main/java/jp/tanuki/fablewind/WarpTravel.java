package jp.tanuki.fablewind;

/** A destination is applied exactly once, while the screen is fully faded. */
public final class WarpTravel {
    private static final float DURATION = .9f;
    private int destination = -1;
    private float elapsed;
    private boolean arrived;

    public boolean start(int index) {
        if (active() || index < 0 || index >= WarpPoint.ALL.size()) return false;
        destination=index; elapsed=0; arrived=false;
        return true;
    }

    public boolean active() { return destination >= 0; }
    public float opacity() {
        return active() ? Math.max(0,1-Math.abs(elapsed/DURATION*2-1)) : 0;
    }

    public WarpPoint update(float dt) {
        if(!active() || !Float.isFinite(dt) || dt < 0) return null;
        elapsed += dt;
        WarpPoint point=null;
        if(!arrived && elapsed >= DURATION*.5f) {
            arrived=true;
            point=WarpPoint.ALL.get(destination);
        }
        if(elapsed >= DURATION) destination=-1;
        return point;
    }
}
