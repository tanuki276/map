package jp.tanuki.fablewind;

import java.io.*;
import java.util.*;

public final class SurvivalTest {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static SurvivalGame rich(){SurvivalGame g=new SurvivalGame();for(Item i:Item.values())g.add(i,100);return g;}
    private static void advance(SurvivalGame g,float seconds){SurvivalGame.Input input=new SurvivalGame.Input();for(int i=0;i<(int)(seconds*10);i++)g.update(.1f,input);}
    public static void main(String[] args)throws Exception{
        SurvivalGame g=new SurvivalGame();
        int wood=g.count(Item.WOOD);check(!g.craft("house") && g.count(Item.WOOD)==wood,"failed craft is atomic");
        g=rich();g.inventory.remove(Item.SWORD);g.inventory.remove(Item.SHIELD);g.inventory.remove(Item.BUCKET);
        g.interior="test-room";
        check(g.craft("sword") && g.count(Item.SWORD)==1,"sword at workbench");
        check(g.craft("shield") && g.count(Item.SHIELD)==1,"shield at workbench");
        check(g.craft("bucket") && g.count(Item.BUCKET)==1,"bucket at workbench");
        check(!g.craft("sword"),"no duplicate equipment");
        check(g.craft("stew") && g.count(Item.STEW)==101,"interior stove cooks");
        g.interior=null;g.x=4;g.z=-4;int water=g.count(Item.WATER);check(g.fillWater() && g.count(Item.WATER)==water+3,"well fills bucket");
        g.x=200;g.z=200;check(!g.fillWater(),"distant water rejected");
        for(int zz=30;zz<60;zz++)for(int xx=-50;xx<-20;xx++)if(g.world.tile(xx,zz).biome==World.WATER){g.x=xx;g.z=zz;}
        advance(g,1);int fish=g.count(Item.FISH);g.fish();check(g.count(Item.FISH)==fish+1,"fishing remains available with bucket");
        g=rich();g.interior=null;g.pendingBuild="house";
        boolean found=false;
        for(int z=-20;z<20 && !found;z++)for(int x=-20;x<20 && !found;x++){g.x=x;g.z=z;g.yaw=0;if(g.canPlace())found=true;}
        check(found,"find buildable land");wood=g.count(Item.WOOD);check(g.place() && g.buildings.size()==1,"place house");check(g.count(Item.WOOD)==wood-18,"building cost once");
        g.pendingBuild="fire";wood=g.count(Item.WOOD);g.cancelBuild();check(g.count(Item.WOOD)==wood,"cancel has no cost");
        SurvivalGame.Building house=g.buildings.get(0);g.x=house.x;g.z=house.z+1.8f;
        check(g.interaction()!=null && g.interaction().action.equals("enter-built"),"built house entry");
        float outsideX=g.x,outsideZ=g.z;g.interact();check(g.interior!=null,"inside own house");advance(g,1);g.interact();check(g.interior==null && g.x==outsideX && g.z==outsideZ,"return outside exactly");
        g=rich();g.inventory.remove(Item.STEW);g.inventory.remove(Item.BREAD);g.inventory.remove(Item.GRILLED_FISH);g.health=40;g.hunger=30;g.eat();check(g.health>40 && g.hunger>30,"food recovery");
        g=rich();g.x=70;g.z=22;g.time=500;advance(g,30);int hostile=0;for(SurvivalGame.Mob m:g.mobs)if(m.hostile)hostile++;check(hostile>0 && hostile<=8,"bounded night spawns");
        g.time=144;advance(g,1);for(SurvivalGame.Mob m:g.mobs)check(!m.hostile,"hostiles leave at dawn");
        g=rich();g.time=500;g.x=0;g.z=0;advance(g,30);check(g.health==100,"village sanctuary");
        g=rich();g.x=35;g.z=10;g.mobs.clear();SurvivalGame.Mob m=new SurvivalGame.Mob(1,"thornling",true,35,11);m.hp=20;g.mobs.add(m);int stars=g.count(Item.STAR);g.attack();check(m.hp<=0 && g.count(Item.STAR)==stars+1,"sword combat and reward");
        g=rich();g.x=35;g.z=11;g.buildings.add(new SurvivalGame.Building(1,"cannon",35,10,0));g.mobs.add(new SurvivalGame.Mob(1,"thornling",true,35,14));int shots=g.count(Item.CANNONBALL);g.interact();check(g.count(Item.CANNONBALL)==shots-1 && g.mobs.get(0).hp<=0,"cannon consumes ammo and damages enemies");
        g=rich();g.buildings.add(new SurvivalGame.Building(1,"campfire",0,0,0));g.x=0;g.z=1.2f;g.interact();check(g.buildings.get(0).litUntil>g.time,"ignite fire");
        g.x=50;g.z=50;g.buildings.clear();g.inventory.remove(Item.STEW);check(!g.craft("stew"),"cold cooking rejected");
        g=rich();g.visited.addAll(Arrays.asList(0,1,2,3,4,5,6));g.x=-43;g.z=-29;g.interact();check(g.questReward,"seven-chapter quest");
        g=rich();g.buildings.add(new SurvivalGame.Building(9,"cannon",12,4,45));g.harvested.put(SurvivalGame.key(-15,4),g.time+200);g.openedChests.add(SurvivalGame.key(10,-4));g.visited.add(3);g.x=12.25f;g.z=-3.75f;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();g.write(new DataOutputStream(bytes));SurvivalGame copy=SurvivalGame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(copy.x==g.x && copy.z==g.z && copy.inventory.equals(g.inventory),"save player and inventory");check(copy.buildings.size()==1 && copy.harvested.equals(g.harvested) && copy.openedChests.equals(g.openedChests) && copy.visited.equals(g.visited),"save world changes");
        byte[] corrupt=bytes.toByteArray();corrupt[0]=0;try{SurvivalGame.read(new DataInputStream(new ByteArrayInputStream(corrupt)));throw new AssertionError("corrupt save accepted");}catch(IOException expected){}
        g=new SurvivalGame();double before=g.time;SurvivalGame.Input input=new SurvivalGame.Input();input.menu=true;g.update(1,input);check(g.time==before,"menus pause world");
        for(int i=0;i<7;i++){g.warp(i);input.menu=false;advance(g,1);check(g.x==WarpPoint.ALL.get(i).x && g.z==WarpPoint.ALL.get(i).y,"survival warp "+i);}
        System.out.println("PASS survival: crafting, construction, interiors, water, cooking, combat, night, quest, saves");
    }
}
