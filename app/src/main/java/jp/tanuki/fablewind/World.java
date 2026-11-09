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
            SHRINE = 10, INN = 11;

    public static final class Tile {
        public final float height;
        public final int biome, object, seed;
        public final boolean road, river, village;
        Tile(float height, int biome, int object, int seed,
             boolean road, boolean river, boolean village) {
            this.height = height;
            this.biome = biome;
            this.object = object;
            this.seed = seed;
            this.road = road;
            this.river = river;
            this.village = village;
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
        if (distance(x, y, 0, 0) < 19) return "ひだまりの村";
        if (distance(x, y, 42, -40) < 18) return "風鳴りの古城";
        if (distance(x, y, 27, 32) < 15) return "黄金の風車丘";
        if (distance(x, y, -38, 45) < 16) return "霧灯台";
        if (distance(x, y, 72, 22) < 15) return "忘れられた遺跡";
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
            if (villageDistance > 4 && villageDistance < 13 && chance < 23)
                object = (chance < 3) ? INN : HOUSE;
            else if (chance < 34) object = TREE;
            else if (chance < 51) object = FLOWER;
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
        if (x == 42 && y == -40) object = CASTLE;
        if (x == 27 && y == 32) object = WINDMILL;
        if (x == -38 && y == 45) object = TOWER;
        if (x == 72 && y == 22) object = RUINS;
        if (x == -45 && y == -37) object = SHRINE;
        // Additional unique discoveries scattered over the enormous map.
        if (x == gx * 160 + 59 && y == gy * 160 - 63
                && (hash(gx, gy, 681) & 3) == 1) object = CASTLE;
        if (x == gx * 160 - 49 && y == gy * 160 + 55
                && (hash(gx, gy, 683) & 3) == 2) object = WINDMILL;
        return new Tile(height, biome, object, salt, road, river, village);
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
