package jp.tanuki.fablewind;

import java.util.*;

/** Authored landmarks supplement the streamed landscape with actual regional geometry. */
public final class SceneCatalog {
    public static final class Feature {
        public final String model,title; public final float x,z,scale,radius;
        public final boolean enter;
        public Feature(String model,String title,float x,float z,float scale,float radius,boolean enter) {
            this.model=model;this.title=title;this.x=x;this.z=z;this.scale=scale;this.radius=radius;this.enter=enter;
        }
    }
    public static final List<Feature> FEATURES=Collections.unmodifiableList(Arrays.asList(
        new Feature("inn","三日月亭",-4,-4,.8f,1.6f,true),
        new Feature("house","旅人の小屋",5,3,.7f,1.4f,true),
        new Feature("rose_manor","薔薇の回廊",34,-43,.8f,1.8f,true),
        new Feature("stilt_hut","葦舟の家",-40,38,.75f,1.6f,true),
        new Feature("treehouse","梢の図書室",-39,-41,.7f,1.8f,true),
        new Feature("observatory","星読みの天文台",72,14,.9f,2.3f,true),
        new Feature("pumpkin_house","琥珀の菓子屋",-67,1,.8f,1.8f,true),
        new Feature("market","旅商人の露店",-5,2,.8f,.6f,false),
        new Feature("shrine","七つ星の祭壇",-43,-29,.65f,.5f,false)
    ));
    public static String model(World.Tile tile) {
        switch(tile.object) {
            case World.TREE:
                switch(tile.region) {
                    case 1:return "cypress";case 2:return "acacia";case 3:return "willow";
                    case 4:return "crystal_tree";case 5:return "glow_tree";case 6:return "maple";
                    default:return tile.biome==World.AUTUMN?"maple":"olive";
                }
            case World.PINE:return "pine";
            case World.HOUSE:return tile.region==6?"pumpkin_house":"house";
            case World.INN:return "inn";
            case World.ROCK:return "boulder";
            case World.FLOWER:return tile.region==1?"rose_bush":(tile.seed&1)==0?"herb":"berry_bush";
            case World.CASTLE:return "castle";
            case World.WINDMILL:return "windmill";
            case World.TOWER:return "lighthouse";
            case World.RUINS:return "ruins";
            case World.SHRINE:return "shrine";
            case World.MUSHROOM:return "giant_mushroom";
            case World.CRYSTAL:return "crystal";
            case World.LANTERN:return "lamp";
            case World.MARKET:return "market";
            case World.WELL:return "well";
            case World.GIANT_TREE:return "wishing_tree";
            case World.PUMPKIN:return "pumpkin";
            case World.WARP:return "warp";
            default:return null;
        }
    }
    public static float scale(World.Tile tile) {
        switch(tile.object) {
            case World.CASTLE:return 1.2f;case World.WINDMILL:return .9f;
            case World.TOWER:return 1.1f;case World.GIANT_TREE:return 1.2f;
            case World.HOUSE:case World.INN:return .65f;
            case World.TREE:return .7f+((tile.seed>>>8)&7)*.05f;
            case World.WARP:return .7f;
            default:return .65f;
        }
    }
    public static boolean nearFeature(float x,float z,float padding) {
        for(Feature f:FEATURES)if(Math.hypot(x-f.x,z-f.z)<f.radius+padding)return true;
        return false;
    }
    public static boolean solid(int object) {
        return object==World.HOUSE || object==World.INN || object==World.CASTLE ||
            object==World.TOWER || object==World.GIANT_TREE || object==World.WINDMILL;
    }
    private SceneCatalog() {}
}
