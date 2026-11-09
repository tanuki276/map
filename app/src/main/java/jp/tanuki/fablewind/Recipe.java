package jp.tanuki.fablewind;

import java.util.*;

/** One transaction supplies every recipe: check the entire cost before consuming anything. */
public final class Recipe {
    public final String id,title,description,model;
    public final Item output;
    public final int quantity;
    public final String station;
    public final Map<Item,Integer> cost;
    private Recipe(String id,String title,String description,String model,Item output,int quantity,
                   String station,Object... pairs) {
        this.id=id;this.title=title;this.description=description;this.model=model;
        this.output=output;this.quantity=quantity;this.station=station;
        EnumMap<Item,Integer> map=new EnumMap<>(Item.class);
        for(int i=0;i<pairs.length;i+=2)map.put((Item)pairs[i],(Integer)pairs[i+1]);
        cost=Collections.unmodifiableMap(map);
    }
    public boolean building() { return model!=null; }
    public String ingredients() {
        StringBuilder s=new StringBuilder();
        for(Map.Entry<Item,Integer> e:cost.entrySet()) {
            if(s.length()>0)s.append(" / ");s.append(e.getKey().title).append(" ").append(e.getValue());
        }
        return s.toString();
    }
    public static final List<Recipe> ALL=Collections.unmodifiableList(Arrays.asList(
        new Recipe("fire","焚き火","設置後、木材と火打ち石で着火","campfire",null,1,"",Item.WOOD,4,Item.STONE,3),
        new Recipe("bench","作業台","剣・盾・バケツの製作場所","workbench",null,1,"",Item.WOOD,6,Item.STONE,2),
        new Recipe("furnace","石の炉","木材を燃料に鉄鉱石を精錬","furnace",null,1,"",Item.STONE,8,Item.CLAY,4),
        new Recipe("house","旅人の家","中へ入り、ベッドで朝まで休める","house",null,1,"",Item.WOOD,18,Item.STONE,12,Item.FIBER,8),
        new Recipe("cannon","守りの砲台","砲弾を使って夜の魔物を迎撃","cannon",null,1,"bench",Item.WOOD,10,Item.IRON,6,Item.STONE,8),
        new Recipe("lantern","道しるべの灯り","夜の魔物を周囲に寄せつけない","lamp",null,1,"bench",Item.WOOD,2,Item.IRON,1,Item.FIBER,2),
        new Recipe("garden","小さな麦畑","種を育て、繰り返し収穫する","garden",null,1,"",Item.WOOD,3,Item.SEED,2,Item.WATER,1),
        new Recipe("bucket","木のバケツ","井戸や水辺で水をくめる",null,Item.BUCKET,1,"bench",Item.WOOD,3,Item.IRON,1),
        new Recipe("sword","旅人の剣","装備すると攻撃力と届く距離が上がる",null,Item.SWORD,1,"bench",Item.IRON,3,Item.WOOD,2),
        new Recipe("shield","木鉄の盾","盾を構えて被害を軽減する",null,Item.SHIELD,1,"bench",Item.WOOD,4,Item.IRON,2,Item.FIBER,2),
        new Recipe("iron","鉄の延べ棒","炉の近くで木材を燃やして精錬",null,Item.IRON,1,"furnace",Item.ORE,2,Item.WOOD,1),
        new Recipe("shot","砲弾 ×3","砲台の弾薬",null,Item.CANNONBALL,3,"bench",Item.IRON,1,Item.STONE,2),
        new Recipe("stew","森のシチュー","満腹・体力を大きく回復",null,Item.STEW,1,"fire",Item.MUSHROOM,2,Item.HERB,1,Item.WATER,1),
        new Recipe("bread","焼きたてパン","満腹とスタミナを回復",null,Item.BREAD,1,"fire",Item.WHEAT,3,Item.WATER,1),
        new Recipe("fish","焼き魚","水辺で釣った魚を調理",null,Item.GRILLED_FISH,1,"fire",Item.FISH,1,Item.HERB,1),
        new Recipe("tea","薬草茶","渇きと体力を回復",null,Item.TEA,1,"fire",Item.HERB,2,Item.WATER,1)
    ));
    public static Recipe find(String id) { for(Recipe r:ALL)if(r.id.equals(id))return r;return null; }
}
