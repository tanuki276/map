package jp.tanuki.fablewind;

public enum Item {
    WOOD("木材"), STONE("石"), FIBER("植物繊維"), FLINT("火打ち石"), ORE("鉄鉱石"),
    IRON("鉄の延べ棒"), CLAY("粘土"), BERRY("木の実"), MUSHROOM("きのこ"),
    WHEAT("小麦"), HERB("薬草"), WATER("水"), FISH("生魚"), STAR("星のかけら"),
    STEW("森のシチュー"), BREAD("焼きたてパン"), GRILLED_FISH("焼き魚"), TEA("薬草茶"),
    BUCKET("木のバケツ"), SWORD("旅人の剣"), SHIELD("木鉄の盾"), CANNONBALL("砲弾"),
    SEED("麦の種"), COIN("古い金貨");
    public final String title;
    Item(String title) { this.title=title; }
}
