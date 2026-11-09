package jp.tanuki.fablewind;

/** Dependency-free regression checks; also run by the Android build workflow. */
public final class WorldTravelTest {
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        World world=new World();
        for(int i=0;i<WarpPoint.ALL.size();i++) {
            WarpPoint point=WarpPoint.ALL.get(i);
            check(World.regionAt(point.x,point.y)==i,"region: "+point.name);
            for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++) {
                if(Math.hypot(dx,dy)>=2.8)continue;
                World.Tile tile=world.tile(point.x+dx,point.y+dy);
                check(tile.biome!=World.WATER && tile.road,"dry arrival: "+point.name);
                check(tile.object==((dx==0 && dy==0)?World.WARP:World.NONE),"clear arrival court");
                if(Math.hypot(dx,dy)<=2)check(tile.height==1f,"flat arrival center");
            }
            check(WarpPoint.nearest(point.x,point.y,1)==i,"nearest portal");
            WarpTravel travel=new WarpTravel();
            check(travel.start(i),"start travel");
            check(!travel.start((i+1)%7),"reject double tap while traveling");
            check(travel.update(.44f)==null,"no early teleport");
            check(travel.update(.01f)==point,"teleport at fade midpoint");
            check(travel.opacity()>.99f,"arrival hidden by fade");
            check(travel.update(.2f)==null,"arrival only once");
            check(travel.update(.3f)==null && !travel.active(),"transition finishes");
        }
        WarpTravel travel=new WarpTravel();
        check(!travel.start(-1) && !travel.start(7),"invalid destination");
        check(travel.start(2),"start after invalid index");
        check(travel.update(Float.NaN)==null && travel.opacity()==0,"ignore invalid delta");
        check(travel.update(-1)==null,"ignore negative delta");
        check(travel.update(2)==WarpPoint.ALL.get(2) && !travel.active(),"long frame delivers once");
        check(travel.update(2)==null,"long frame no duplicate");
        check(world.tile(42,-40).object==World.CASTLE,"castle retained");
        check(world.tile(27,32).object==World.WINDMILL,"windmill retained");
        check(world.tile(-38,45).object==World.TOWER,"lighthouse retained");
        check(world.tile(72,22).object==World.RUINS,"ruins retained");
        check(world.tile(-45,-38).object==World.GIANT_TREE,"wishing tree");
        World.Tile before=world.tile(-17,-33);
        // Evict the original chunk; regenerating negative coordinates is identical.
        for(int i=0;i<240;i++)world.tile(i*32,1000);
        World.Tile after=world.tile(-17,-33),preview=world.preview(-17,-33);
        check(before.seed==after.seed && before.object==after.object && before.height==after.height,
                "deterministic chunk eviction");
        check(preview.object==after.object && preview.biome==after.biome,"map matches world");
        check(WarpPoint.nearest(10000,10000,5)==-1,"no false nearest portal");
        System.out.println("PASS: 7 safe portals, landmarks, deterministic world, warp lifecycle");
    }
}
