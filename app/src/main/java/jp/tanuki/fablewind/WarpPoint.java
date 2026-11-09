package jp.tanuki.fablewind;

/** Stable, shared destinations for the world, map, HUD and save-independent travel. */
public final class WarpPoint {
    public final String name, description;
    public final int x, y, color;

    private WarpPoint(String name, String description, int x, int y, int color) {
        this.name = name;
        this.description = description;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public static final java.util.List<WarpPoint> ALL = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
            new WarpPoint("ひだまりの村", "赤い屋根とランタンの市場", 0, 0, 0xffefbd75),
            new WarpPoint("風鳴りの古城", "薔薇と尖塔、石畳の王国", 38, -36, 0xffdca8b8),
            new WarpPoint("黄金の風車丘", "麦畑を渡る風と夕陽", 23, 30, 0xffedcc71),
            new WarpPoint("霧灯台", "水鏡と葦に包まれた湿原", -34, 41, 0xff9fcee0),
            new WarpPoint("星降りの遺跡", "苔むす石柱と青い結晶", 68, 22, 0xffa5b9ec),
            new WarpPoint("願いの森", "巨大な古木と光るきのこ", -42, -34, 0xff9ed9bb),
            new WarpPoint("琥珀の谷", "紅葉と南瓜の小さな集落", -65, 5, 0xffe8a075)));

    public static int nearest(float x, float y, float radius) {
        int found = -1;
        float best = radius * radius;
        for (int i = 0; i < ALL.size(); i++) {
            WarpPoint point = ALL.get(i);
            float d = (x-point.x)*(x-point.x)+(y-point.y)*(y-point.y);
            if (d <= best) { best = d; found = i; }
        }
        return found;
    }
}
