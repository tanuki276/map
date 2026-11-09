package jp.tanuki.fablewind;

/** Shared 3D terrain sampling; simulation, rendering and collision use the same height field. */
public final class Terrain {
    private Terrain(){}
    public static float height(World world,float x,float z){
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z);float tx=x-ix,tz=z-iz;
        float a=world.tile(ix,iz).height,b=world.tile(ix+1,iz).height,c=world.tile(ix,iz+1).height,d=world.tile(ix+1,iz+1).height;
        return ((a+(b-a)*tx)*(1-tz)+(c+(d-c)*tx)*tz)*.65f;
    }
    public static float[] color(World.Tile t){
        if(t.road)return new float[]{1.2f,1.03f,.78f};
        if(t.biome==World.WATER)return new float[]{.25f,.6f,.66f};
        if(t.region==3)return new float[]{.65f,.92f,.85f};
        if(t.region==4)return new float[]{.63f,.75f,.89f};
        if(t.region==5)return new float[]{.48f,.84f,.76f};
        if(t.region==6 || t.biome==World.AUTUMN)return new float[]{1.1f,.76f,.38f};
        if(t.region==2 || t.biome==World.GOLDEN)return new float[]{1.35f,1.15f,.53f};
        if(t.biome==World.SNOW)return new float[]{1.7f,1.9f,1.9f};
        if(t.biome==World.MOUNTAIN)return new float[]{.85f,.92f,.9f};
        return new float[]{.75f,1.08f,.69f};
    }
}
