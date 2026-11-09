package jp.tanuki.fablewind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic, streamed fantasy world. All scenery is created from code: no copyrighted
 * textures, meshes or character assets. A chunk is regenerated from its coordinates on demand.
 */
public final class World {
    public static final int CHUNK_SIZE = 16;
    public static final int MEADOW = 0, FOREST = 1, AUTUMN = 2, GOLDEN = 3,
            MOUNTAIN = 4, SNOW = 5, WATER = 6, TOWN = 7;
    public static final int NONE = 0, TREE = 1, PINE = 2, HOUSE = 3, ROCK = 4,
            FLOWER = 5, CASTLE = 6, WINDMILL = 7, TOWER = 8, RUINS = 9,
            SHRINE = 10, INN = 11, MUSHROOM = 12, CRYSTAL = 13,
            LANTERN = 14, MARKET = 15, WELL = 16, GIANT_TREE = 17,
            PUMPKIN = 18, WARP = 19;

    public static final class Tile {
        public final float height;
        public final int biome, object, seed, region;
        public final boolean road, river, village;
        Tile(float height, int biome, int object, int seed,
             boolean road, boolean river, boolean village, int region) {
            this.height = height;
            this.biome = biome;
            this.object = object;
            this.seed = seed;
            this.road = road;
            this.river = river;
            this.village = village;
            this.region = region;
        }
    }

    private final LinkedHashMap<Long, Tile[]> chunks =
            new LinkedHashMap<Long, Tile[]>(256, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Tile[]> e) {
                    return size() > 220;
                }
            };

    public Tile tile(int x, int y) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        long key = ((long) cx << 32) ^ (cy & 0xffffffffL);
        Tile[] data = chunks.get(key);
        if (data == null) {
            data = new Tile[CHUNK_SIZE * CHUNK_SIZE];
            for (int ly = 0; ly < CHUNK_SIZE; ly++)
                for (int lx = 0; lx < CHUNK_SIZE; lx++)
                    data[ly * CHUNK_SIZE + lx] =
                            generate(cx * CHUNK_SIZE + lx, cy * CHUNK_SIZE + ly);
            chunks.put(key, data);
        }
        return data[Math.floorMod(y, CHUNK_SIZE) * CHUNK_SIZE +
                    Math.floorMod(x, CHUNK_SIZE)];
    }

    /** Lightweight sampling for overview maps; avoids generating distant chunks. */
    public Tile preview(int x, int y) { return generate(x,y); }

    public String placeName(float x, float y) {
        int region = regionAt(x, y);
        if (region >= 0) return WarpPoint.ALL.get(region).name;
        if (distance(x, y, 0, 0) < 19) return "ひだまりの村";
        if (distance(x, y, 42, -40) < 18) return "風鳴りの古城";
        if (distance(x, y, 27, 32) < 15) return "黄金の風車丘";
        if (distance(x, y, -38, 45) < 16) return "霧灯台";
        if (distance(x, y, 72, 22) < 15) return "星降りの遺跡";
        Tile t = tile(Math.round(x), Math.round(y));
        if (t.village) return "街道沿いの集落";
        switch (t.biome) {
            case FOREST: return "緑影の森";
            case AUTUMN: return "琥珀の樹海";
            case GOLDEN: return "黄金の草原";
            case MOUNTAIN: return "灰色の高地";
            case SNOW: return "白銀の山脈";
            case WATER: return "青いせせらぎ";
            default: return "風渡る草原";
        }
    }

    private static float distance(float x, float y, float tx, float ty) {
        return (float) Math.hypot(x - tx, y - ty);
    }

    /** Authored regions sit inside the unbounded procedural world. */
    public static int regionAt(float x, float y) {
        int nearest = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < WarpPoint.ALL.size(); i++) {
            WarpPoint point = WarpPoint.ALL.get(i);
            float d = distance(x, y, point.x, point.y);
            float edge=(i==0?20:25)+(float)(Math.sin(x*.13)+Math.cos(y*.17))*2;
            if (d < edge && d < best) { best = d; nearest = i; }
        }
        return nearest;
    }

    private static float segmentDistance(float x, float y, float bx, float by) {
        float t = clamp((x*bx+y*by)/(bx*bx+by*by), 0, 1);
        float length=(float)Math.hypot(bx,by);
        float bend=(float)Math.sin(t*Math.PI*2)*2.3f;
        return distance(x,y,bx*t-by/length*bend,by*t+bx/length*bend);
    }

    private Tile generate(int x, int y) {
        int salt = hash(x, y, 1021);
        float continental = fractal(x * .0043, y * .0043, 17);
        float hills = fractal(x * .024, y * .024, 29);
        float height = clamp(.48f + Math.max(0f, continental - .29f) * 8.2f
                + (hills - .5f) * 1.75f, .12f, 6.5f);
        float moisture = fractal(x * .008, y * .008, 39);
        float warmth = fractal(x * .0035, y * .0035, 44);
        int biome = MEADOW;

        if (warmth > .61f && moisture < .48f) biome = GOLDEN;
        else if (moisture > .58f) biome = FOREST;
        else if (warmth < .43f && moisture > .43f) biome = AUTUMN;
        if (height > 3.9f) biome = SNOW;
        else if (height > 2.9f) biome = MOUNTAIN;

        int gx = Math.round(x / 160f), gy = Math.round(y / 160f);
        int vx = gx == 0 && gy == 0 ? 0 : gx * 160 + Math.floorMod(hash(gx, gy, 77), 37) - 18;
        int vy = gx == 0 && gy == 0 ? 0 : gy * 160 + Math.floorMod(hash(gx, gy, 78), 37) - 18;
        boolean enabled = (gx == 0 && gy == 0) || (hash(gx, gy, 91) & 7) < 5;
        float villageDistance = distance(x, y, vx, vy);
        boolean village = enabled && villageDistance < 15f;
        if (enabled && villageDistance < 20f) {
            float blend = clamp((villageDistance - 12f) / 8f, 0f, 1f);
            height = 1.0f * (1 - blend) + height * blend;
        }

        float riverCurve = 46f + 21f * (float) Math.sin(x / 61.0)
                + 11f * (float) Math.sin(x / 17.0);
        float riverDist = Math.abs(Math.floorMod(y - Math.round(riverCurve) + 160, 320) - 160);
        boolean river = riverDist < (3 + (salt & 1)) && !village;
        if (river || (height < .42f && !village)) biome = WATER;

        float roadX = Math.abs(x - Math.round(x / 160f) * 160f -
                1.7f * (float) Math.sin(y / 24.0));
        float roadY = Math.abs(y - Math.round(y / 160f) * 160f -
                1.7f * (float) Math.sin(x / 24.0));
        boolean road = roadX < .85f || roadY < .85f ||
                (village && (Math.abs(x - vx) <= 1 || Math.abs(y - vy) <= 1
                        || villageDistance < 3.4f));
        if (road && biome == WATER) height = .65f; // a small walkable bridge
        if (village && biome != WATER) biome = TOWN;

        int object = NONE;
        int chance = Math.floorMod(hash(x, y, 55), 100);
        if (village && !road) {
            if (villageDistance > 4 && villageDistance < 13 && chance < 70
                    && Math.floorMod(x-vx,3)==0 && Math.floorMod(y-vy,3)==0)
                object = (chance < 2) ? INN : HOUSE;
            else if (chance < 8) object = TREE;
            else if (chance < 13) object = FLOWER;
        } else if (!road && biome != WATER) {
            if (biome == FOREST && chance < 29) object = TREE;
            else if (biome == AUTUMN && chance < 24) object = TREE;
            else if (biome == MEADOW && chance < 7) object = TREE;
            else if (biome == GOLDEN && chance < 4) object = TREE;
            else if ((biome == MOUNTAIN || biome == SNOW) && chance < 14)
                object = chance < 7 ? ROCK : PINE;
            else if (chance < 27 && (biome == MEADOW || biome == GOLDEN))
                object = FLOWER;
        }
        int region = regionAt(x,y);
        // Each chapter has its own vegetation, palette and handmade points of interest.
        if (region >= 0) {
            switch (region) {
                case 0: break;
                case 1:
                    biome = MEADOW;
                    object = chance < 9 ? TREE : chance < 32 ? FLOWER : NONE;
                    break;
                case 2:
                    biome = GOLDEN;
                    object = chance < 3 ? TREE : NONE;
                    break;
                case 3:
                    biome = river || (Math.sin(x*.19)+Math.cos(y*.24) > .45) ? WATER : MEADOW;
                    object = biome == WATER ? NONE : chance < 9 ? TREE : chance < 18 ? ROCK : NONE;
                    break;
                case 4:
                    biome = FOREST;
                    object = chance < 12 ? TREE : chance < 21 ? CRYSTAL : chance < 27 ? ROCK : NONE;
                    break;
                case 5:
                    biome = FOREST;
                    object = chance < 16 ? TREE : chance < 30 ? MUSHROOM : chance < 34 ? CRYSTAL : NONE;
                    break;
                case 6:
                    biome = AUTUMN;
                    object = chance < 17 ? TREE : chance < 28 ? PUMPKIN : NONE;
                    if (Math.floorMod(x,5)==0 && Math.floorMod(y,5)==0) object=HOUSE;
                    break;
            }
        }
        // Walkable, gently winding roads connect all authored chapters.
        for (int i=1; i<WarpPoint.ALL.size(); i++) {
            WarpPoint point=WarpPoint.ALL.get(i);
            if(segmentDistance(x,y,point.x,point.y)<1.15f) road=true;
        }
        if (road) object = NONE;
        if(distance(x,y,42,-40)<4 || distance(x,y,-38,45)<3 ||
                distance(x,y,27,32)<3 || distance(x,y,72,22)<3) object=NONE;
        if (x == 42 && y == -40) object = CASTLE;
        if (x == 27 && y == 32) object = WINDMILL;
        if (x == -38 && y == 45) object = TOWER;
        if (x == 72 && y == 22) object = RUINS;
        if (x == -45 && y == -38) object = GIANT_TREE;
        if (x == -67 && y == 2) object = INN;
        if (x == 4 && y == -4) object = WELL;
        if ((x == -4 || x == 4) && y == 3) object = MARKET;
        if ((x == -3 || x == 3) && (y == -2 || y == 5)) object = LANTERN;
        // A flat, dry, clear arrival court surrounds every portal, even over rivers.
        for (WarpPoint point : WarpPoint.ALL) {
            float d=distance(x,y,point.x,point.y);
            if (d < 4.3f) {
                float blend=clamp((d-2f)/2.3f,0,1);
                height=1.0f*(1-blend)+height*blend;
                if (d < 2.8f) {
                    if(biome==WATER) biome=MEADOW;
                    river=false; road=true; object=NONE;
                }
                if(x==point.x && y==point.y) object=WARP;
            }
        }
        if(road && biome==WATER) height=.65f;
        // Additional unique discoveries scattered over the enormous map.
        if (x == gx * 160 + 59 && y == gy * 160 - 63
                && (hash(gx, gy, 681) & 3) == 1) object = CASTLE;
        if (x == gx * 160 - 49 && y == gy * 160 + 55
                && (hash(gx, gy, 683) & 3) == 2) object = WINDMILL;
        return new Tile(height, biome, object, salt, road, river, village, region);
    }

    public static int hash(int x, int y, int seed) {
        long h = ((long) x * 0x9E3779B97F4A7C15L)
                ^ ((long) y * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) seed * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        return (int) (h ^ (h >>> 32));
    }

    private static float noise(int x, int y, int seed) {
        return (hash(x, y, seed) & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float smoothNoise(double x, double y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float tx = (float) (x - ix), ty = (float) (y - iy);
        tx *= tx * (3 - 2 * tx);
        ty *= ty * (3 - 2 * ty);
        float a = noise(ix, iy, seed), b = noise(ix + 1, iy, seed);
        float c = noise(ix, iy + 1, seed), d = noise(ix + 1, iy + 1, seed);
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }

    private static float fractal(double x, double y, int seed) {
        float result = 0, amp = .5f, total = 0;
        for (int i = 0; i < 4; i++) {
            result += smoothNoise(x, y, seed + i * 31) * amp;
            total += amp;
            x *= 2.09;
            y *= 2.09;
            amp *= .5f;
        }
        return result / total;
    }

    private static float clamp(float x, float lo, float hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}

