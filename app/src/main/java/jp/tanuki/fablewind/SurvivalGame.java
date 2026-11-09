package jp.tanuki.fablewind;

import java.io.*;
import java.util.*;

/** Android-independent simulation. All mutation is guarded by this object's monitor. */
public final class SurvivalGame {
    public static final int SAVE_MAGIC=0x46424c33, SAVE_VERSION=1;
    public final World world=new World();
    public final EnumMap<Item,Integer> inventory=new EnumMap<>(Item.class);
    public final List<Building> buildings=new ArrayList<>();
    public final List<Mob> mobs=new ArrayList<>();
    public final Map<Long,Double> harvested=new HashMap<>();
    public final Set<Long> openedChests=new HashSet<>();
    public final Set<Integer> visited=new HashSet<>();
    public final WarpTravel travel=new WarpTravel();
    public float x=0,z=2,yaw=180,health=100,hunger=100,thirst=100,stamina=100;
    public double time=144;
    public boolean guarding,walking,paused;
    public String interior=null,pendingBuild=null,message="木材と石を集めて、最初の焚き火を作ろう";
    public float messageTime=9,attackAnimation=0;
    public float outsideX,outsideZ;
    private float attackCooldown,interactCooldown,spawnTimer=1,passiveTimer;
    private int mobSerial,buildingSerial;
    private final Random random=new Random(20261109);
    public boolean questReward;
    public boolean saveRequested;
    public static final class Input {
        public volatile float horizontal,vertical,cameraYaw=0;
        public volatile boolean run,guard,attack,menu;
        public void clear(){horizontal=vertical=0;run=guard=attack=false;}
    }
    public static final class Building {
        public final int id;public final String model;public final float x,z,yaw;
        public double litUntil,grownAt;
        public Building(int id,String model,float x,float z,float yaw){this.id=id;this.model=model;this.x=x;this.z=z;this.yaw=yaw;}
        public Building copy(){Building b=new Building(id,model,x,z,yaw);b.litUntil=litUntil;b.grownAt=grownAt;return b;}
    }
    public static final class Mob {
        public final int id;public final String model;public final boolean hostile;
        public float x,z,yaw,hp,age,cooldown;
        public Mob(int id,String model,boolean hostile,float x,float z){this.id=id;this.model=model;this.hostile=hostile;this.x=x;this.z=z;hp=model.equals("golem")?85:40;}
        public Mob copy(){Mob m=new Mob(id,model,hostile,x,z);m.yaw=yaw;m.hp=hp;m.age=age;return m;}
    }
    public static final class Interaction {
        public final String action,title;public final float x,z;public final long key;
        public Interaction(String action,String title,float x,float z,long key){this.action=action;this.title=title;this.x=x;this.z=z;this.key=key;}
    }
    public static final class Snapshot {
        public float x,z,yaw,health,hunger,thirst,stamina,phase,attack,fade;
        public double time;public boolean night,walking,guarding,canBuild,sword,shield,fishable;
        public String interior,pending,message,place,quest;public Interaction interaction;
        public Map<Item,Integer> items;public List<Building> buildings;public List<Mob> mobs;
        public Set<Long> hidden,opened;
    }
    public SurvivalGame(){add(Item.BERRY,4);add(Item.FLINT,1);}
    public synchronized int count(Item item){return inventory.getOrDefault(item,0);}
    public synchronized void add(Item item,int amount){inventory.put(item,Math.max(0,Math.min(999,count(item)+amount)));}
    public synchronized boolean isNight(){return Math.sin((time%700)*Math.PI*2/700)<-.12;}
    public synchronized float ground(float wx,float wz){
        int ix=(int)Math.floor(wx),iz=(int)Math.floor(wz);float tx=wx-ix,tz=wz-iz;
        float a=world.tile(ix,iz).height,b=world.tile(ix+1,iz).height;
        float c=world.tile(ix,iz+1).height,d=world.tile(ix+1,iz+1).height;
        return ((a+(b-a)*tx)*(1-tz)+(c+(d-c)*tx)*tz)*.65f;
    }
    public static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    public boolean hidden(int x,int z){Double expiry=harvested.get(key(x,z));return expiry!=null && expiry>time;}
    public synchronized void tell(String text){message=text;messageTime=5;}
    public synchronized void update(float dt,Input input){
        if(paused || !Float.isFinite(dt) || dt<=0)return;
        if(input.menu){walking=false;guarding=false;return;}
        dt=Math.min(dt,.1f);time+=dt;messageTime=Math.max(0,messageTime-dt);
        attackCooldown=Math.max(0,attackCooldown-dt);interactCooldown=Math.max(0,interactCooldown-dt);
        attackAnimation=Math.max(0,attackAnimation-dt);
        WarpPoint arrival=travel.update(dt);
        if(arrival!=null){interior=null;x=arrival.x;z=arrival.y;outsideX=x;outsideZ=z;pendingBuild=null;mobs.clear();saveRequested=true;tell(arrival.name+" に到着");}
        boolean frozen=input.menu || travel.active();
        guarding=!frozen && input.guard && count(Item.SHIELD)>0 && stamina>1;
        walking=false;
        if(!frozen){
            float mx=input.horizontal,mz=input.vertical,len=(float)Math.hypot(mx,mz);
            if(len>.08f){
                if(len>1){mx/=len;mz/=len;}
                float angle=(float)Math.toRadians(input.cameraYaw);
                float dx=(float)(mx*Math.cos(angle)+mz*Math.sin(angle));
                float dz=(float)(-mx*Math.sin(angle)+mz*Math.cos(angle));
                float speed=input.run && stamina>2 && !guarding?3.3f:1.85f;
                if(guarding)speed*=.45f;
                if(world.tile(Math.round(x),Math.round(z)).biome==World.WATER && interior==null)speed*=.55f;
                if(speed>2.5f)stamina=Math.max(0,stamina-dt*11);
                float nx=x+dx*dt*speed,nz=z+dz*dt*speed;
                if(walkable(nx,z)){x=nx;walking=true;}
                if(walkable(x,nz)){z=nz;walking=true;}
                yaw=(float)Math.toDegrees(Math.atan2(dx,dz));
            }else stamina=Math.min(100,stamina+dt*15);
            if(input.attack)attack();
        }
        if(!walking)stamina=Math.min(100,stamina+dt*5);
        hunger=Math.max(0,hunger-dt*.035f);thirst=Math.max(0,thirst-dt*.045f);
        if(hunger<=0 || thirst<=0)health=Math.max(0,health-dt*.6f);
        if(hunger>65 && thirst>60)health=Math.min(100,health+dt*.25f);
        if(interior==null && !travel.active())updateMobs(dt,frozen);
        int region=World.regionAt(x,z);if(interior==null && region>=0)visited.add(region);
        if(health<=0){health=85;hunger=Math.max(35,hunger);thirst=Math.max(35,thirst);interior=null;x=0;z=2;mobs.clear();tell("旅の門に助けられた。持ち物と建築はそのままです");}
        if(harvested.size()>1600)harvested.entrySet().removeIf(e->e.getValue()<=time);
    }
    private boolean walkable(float wx,float wz){
        if(interior!=null)return Math.abs(wx)<3.25f && wz>-3.3f && wz<3.5f;
        for(SceneCatalog.Feature f:SceneCatalog.FEATURES)if(f.enter && Math.hypot(wx-f.x,wz-f.z)<f.radius*.7f)return false;
        for(Building b:buildings)if((b.model.equals("house")||b.model.equals("furnace")) && Math.hypot(wx-b.x,wz-b.z)<(b.model.equals("house")?1.15:.5))return false;
        int tx=Math.round(wx),tz=Math.round(wz);World.Tile tile=world.tile(tx,tz);
        if(!hidden(tx,tz) && (tile.object==World.TREE || tile.object==World.PINE || tile.object==World.ROCK)
                && Math.hypot(wx-tx,wz-tz)<.22f)return false;
        return !SceneCatalog.solid(tile.object) || Math.hypot(wx-tx,wz-tz)>.57f;
    }
    private boolean safe(float wx,float wz){
        if(Math.hypot(wx,wz)<11)return true;
        for(Building b:buildings)if((b.model.equals("lamp") || b.model.equals("campfire")&&b.litUntil>time) && Math.hypot(wx-b.x,wz-b.z)<5)return true;
        return false;
    }
    private void updateMobs(float dt,boolean frozen){
        if(!isNight())mobs.removeIf(m->m.hostile);
        spawnTimer-=dt;passiveTimer-=dt;
        if(isNight() && spawnTimer<=0){
            spawnTimer=7;
            int enemies=0;for(Mob m:mobs)if(m.hostile)enemies++;
            if(enemies<8){
                double a=random.nextDouble()*Math.PI*2;float wx=x+(float)Math.cos(a)*13,wz=z+(float)Math.sin(a)*13;
                if(!safe(wx,wz) && world.tile(Math.round(wx),Math.round(wz)).biome!=World.WATER){
                    int region=World.regionAt(wx,wz);
                    mobs.add(new Mob(++mobSerial,region==4?"golem":region==3||region==5?"wisp":"thornling",true,wx,wz));
                }
            }
        }
        if(passiveTimer<=0){
            passiveTimer=12;int passive=0;for(Mob m:mobs)if(!m.hostile)passive++;
            if(passive<6){
                float wx=x+random.nextFloat()*18-9,wz=z+random.nextFloat()*18-9;
                int r=World.regionAt(wx,wz);String model=r==3?"heron":r==2||r==6?"goat":r==5?"firefly":"rabbit";
                if(world.tile(Math.round(wx),Math.round(wz)).biome!=World.WATER || model.equals("heron") || model.equals("firefly"))mobs.add(new Mob(++mobSerial,model,false,wx,wz));
            }
        }
        Iterator<Mob> it=mobs.iterator();
        while(it.hasNext()){
            Mob m=it.next();m.age+=dt;m.cooldown=Math.max(0,m.cooldown-dt);
            float distance=(float)Math.hypot(x-m.x,z-m.z);
            if(distance>38 || m.hp<=0){it.remove();continue;}
            if(frozen)continue;
            float dx,dz;
            if(m.hostile && !safe(x,z)){
                dx=(x-m.x)/Math.max(.01f,distance);dz=(z-m.z)/Math.max(.01f,distance);
                if(distance>1.15f){float step=dt*(m.model.equals("golem")?.65f:1.1f),nx=m.x+dx*step,nz=m.z+dz*step;
                    if(walkable(nx,nz) && (m.model.equals("wisp") || world.tile(Math.round(nx),Math.round(nz)).biome!=World.WATER)){m.x=nx;m.z=nz;}}
                else if(m.cooldown<=0){
                    float damage=m.model.equals("golem")?16:9;
                    if(guarding && stamina>8){damage*=.18f;stamina-=8;}
                    health=Math.max(0,health-damage);m.cooldown=1.5f;
                }
            }else{
                double a=m.age*.4+m.id*2;dx=(float)Math.sin(a)*.24f;dz=(float)Math.cos(a*.7)*.24f;
                if(world.tile(Math.round(m.x+dx*dt),Math.round(m.z+dz*dt)).biome!=World.WATER || m.model.equals("heron")){m.x+=dx*dt;m.z+=dz*dt;}
            }
            m.yaw=(float)Math.toDegrees(Math.atan2(dx,dz));
        }
    }
    public synchronized void attack(){
        if(attackCooldown>0 || travel.active())return;
        attackCooldown=.48f;attackAnimation=.35f;
        float range=count(Item.SWORD)>0?2.65f:1.7f;
        Mob target=null;float closest=range;
        for(Mob m:mobs)if(m.hostile){float d=(float)Math.hypot(m.x-x,m.z-z);if(d<closest){closest=d;target=m;}}
        if(target!=null){
            yaw=(float)Math.toDegrees(Math.atan2(target.x-x,target.z-z));target.hp-=count(Item.SWORD)>0?28:8;
            if(target.hp<=0){add(Item.STAR,1);add(Item.COIN,1);tell("魔物を退けた · 星のかけら +1");}
        }
    }
    private boolean station(String type){
        if(type.isEmpty())return true;
        if(interior!=null)return type.equals("bench") || type.equals("fire");
        String model=type.equals("fire")?"campfire":type.equals("bench")?"workbench":"furnace";
        for(Building b:buildings)if(b.model.equals(model) && Math.hypot(x-b.x,z-b.z)<3.5 && (!type.equals("fire") || b.litUntil>time))return true;
        return false;
    }
    public synchronized String unavailable(Recipe recipe){
        if(recipe==null)return "レシピがありません";
        if(!station(recipe.station))return recipe.station.equals("fire")?"火のついた焚き火か屋内のかまどが必要":recipe.station.equals("bench")?"作業台の近くで作れます":"炉の近くで作れます";
        for(Map.Entry<Item,Integer> e:recipe.cost.entrySet())if(count(e.getKey())<e.getValue())return e.getKey().title+" が足りません";
        if((recipe.output==Item.SWORD || recipe.output==Item.SHIELD || recipe.output==Item.BUCKET) && count(recipe.output)>0)return "すでに持っています";
        return "";
    }
    private void consume(Recipe recipe){for(Map.Entry<Item,Integer> e:recipe.cost.entrySet())add(e.getKey(),-e.getValue());}
    public synchronized boolean craft(String id){
        Recipe recipe=Recipe.find(id);String reason=unavailable(recipe);
        if(!reason.isEmpty()){tell(reason);return false;}
        if(recipe.building()){
            if(interior!=null){tell("建築は外で行おう");return false;}
            pendingBuild=id;tell("緑の場所に設置 · キャンセルで素材は消費しません");return true;
        }
        consume(recipe);add(recipe.output,recipe.quantity);tell(recipe.title+" を作った");return true;
    }
    public synchronized float buildX(){return Math.round((x+(float)Math.sin(Math.toRadians(yaw))*2.8f)*2)/2f;}
    public synchronized float buildZ(){return Math.round((z+(float)Math.cos(Math.toRadians(yaw))*2.8f)*2)/2f;}
    public synchronized boolean canPlace(){
        Recipe recipe=Recipe.find(pendingBuild);if(recipe==null || !unavailable(recipe).isEmpty() || interior!=null || buildings.size()>=160)return false;
        float wx=buildX(),wz=buildZ(),radius=recipe.model.equals("house")?2:1;
        World.Tile tile=world.tile(Math.round(wx),Math.round(wz));
        if(tile.biome==World.WATER || tile.object==World.WARP || SceneCatalog.nearFeature(wx,wz,radius))return false;
        for(int iz=(int)Math.floor(wz-radius);iz<=Math.ceil(wz+radius);iz++)for(int ix=(int)Math.floor(wx-radius);ix<=Math.ceil(wx+radius);ix++) {
            World.Tile t=world.tile(ix,iz);
            if(SceneCatalog.solid(t.object) || t.object==World.WARP || t.biome==World.WATER)return false;
            if(!hidden(ix,iz) && (t.object==World.TREE || t.object==World.PINE || t.object==World.ROCK))return false;
        }
        if(Math.abs(Terrain.height(world,wx-radius,wz-radius)-Terrain.height(world,wx+radius,wz+radius))>.5f)return false;
        for(Building b:buildings)if(Math.hypot(wx-b.x,wz-b.z)<radius+(b.model.equals("house")?1.6f:.8f))return false;
        return true;
    }
    public synchronized boolean place(){
        if(!canPlace()){tell("水辺・建物・門から離れた平地を選ぼう");return false;}
        Recipe r=Recipe.find(pendingBuild);consume(r);
        Building b=new Building(++buildingSerial,r.model,buildX(),buildZ(),yaw);
        b.grownAt=time+100;buildings.add(b);pendingBuild=null;tell(r.title+" を建てた");return true;
    }
    public synchronized void cancelBuild(){pendingBuild=null;tell("設置をキャンセルしました");}
    private boolean waterNear(){return Math.hypot(x-4,z+4)<2.2 || riverNear();}
    private boolean riverNear(){
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)if(world.tile(Math.round(x)+dx,Math.round(z)+dz).biome==World.WATER && Math.hypot(dx,dz)<2.2)return true;
        return false;
    }
    public synchronized void fish(){
        if(interior!=null || !riverNear()){tell("川や湿原の水辺に近づいて釣ろう");return;}
        if(interactCooldown>0)return;interactCooldown=3;add(Item.FISH,1);tell("魚が釣れた · 生魚 +1");
    }
    public synchronized boolean fillWater(){
        if(count(Item.BUCKET)==0){tell("作業台でバケツを作ろう");return false;}
        if(!waterNear() || interior!=null){tell("井戸か水辺の近くで使おう");return false;}
        if(interactCooldown>0)return false;interactCooldown=.8f;add(Item.WATER,3);tell("水をくんだ · 水 +3");return true;
    }
    public synchronized void eat(){
        Item food=count(Item.STEW)>0?Item.STEW:count(Item.GRILLED_FISH)>0?Item.GRILLED_FISH:count(Item.BREAD)>0?Item.BREAD:count(Item.BERRY)>0?Item.BERRY:null;
        if(food==null){tell("木の実を採るか、料理を作ろう");return;}
        add(food,-1);hunger=Math.min(100,hunger+(food==Item.BERRY?8:40));health=Math.min(100,health+(food==Item.BERRY?2:25));stamina=100;tell(food.title+" を食べた");
    }
    public synchronized void drink(){
        if(count(Item.TEA)>0){add(Item.TEA,-1);thirst=100;health=Math.min(100,health+20);tell("薬草茶を飲んだ");}
        else if(count(Item.WATER)>0){add(Item.WATER,-1);thirst=Math.min(100,thirst+38);tell("水を飲んだ");}
        else tell("水をくむか、薬草茶を作ろう");
    }
    public synchronized void warp(int index){if(travel.start(index)){pendingBuild=null;guarding=false;tell("旅の門をくぐる…");}}
    public synchronized Interaction interaction(){
        if(pendingBuild!=null)return new Interaction("place",canPlace()?"ここに設置する":"ここには設置できません",buildX(),buildZ(),0);
        if(interior!=null){
            if(z>1.8f)return new Interaction("exit","外へ出る",0,3,0);
            if(x>1 && z<-1)return new Interaction("rest","ベッドで朝まで休む",2,-2,0);
            return new Interaction("cook","かまど・作業台を使う",-2,-2,0);
        }
        Interaction found=null;double best=2.4;
        for(SceneCatalog.Feature f:SceneCatalog.FEATURES){
            double d=Math.hypot(x-f.x,z-f.z);
            if(d<best){
                if(f.enter){found=new Interaction("enter",f.title+" に入る",f.x,f.z,key(Math.round(f.x),Math.round(f.z)));best=d;}
                else if(f.model.equals("market")){found=new Interaction("trade","旅商人と交換する",f.x,f.z,0);best=d;}
                else if(f.model.equals("shrine")){found=new Interaction("quest","七つ星の祭壇に祈る",f.x,f.z,0);best=d;}
            }
        }
        for(Building b:buildings){double d=Math.hypot(x-b.x,z-b.z);if(d<best){
            String a=b.model.equals("house")?"enter-built":b.model.equals("campfire")?"ignite":b.model.equals("cannon")?"cannon":b.model.equals("garden")?"harvest-garden":"craft";
            String label=b.model.equals("house")?"自分の家に入る":b.model.equals("campfire")?b.litUntil>time?"料理する / 薪を足す":"焚き火に火をつける":b.model.equals("cannon")?"砲台を撃つ":b.model.equals("garden")?"麦畑を収穫する":"設備で製作する";
            found=new Interaction(a,label,b.x,b.z,b.id);best=d;
        }}
        if(found!=null)return found;
        int px=Math.round(x),pz=Math.round(z);
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
            int tx=px+dx,tz=pz+dz;double d=Math.hypot(x-tx,z-tz);
            if(d>=best || hidden(tx,tz) || SceneCatalog.nearFeature(tx,tz,.5f))continue;
            World.Tile t=world.tile(tx,tz);String action=null,label=null;
            if(t.object==World.HOUSE || t.object==World.INN){action="enter";label="建物の中へ入る";}
            else if(t.object==World.TREE || t.object==World.PINE){action="gather";label="枝・木材を集める";}
            else if(t.object==World.ROCK || t.object==World.CRYSTAL){action="gather";label="石・鉱石を採掘する";}
            else if(t.object==World.FLOWER || t.object==World.MUSHROOM || t.object==World.PUMPKIN){action="gather";label="植物・食材を採る";}
            else if(t.object==World.WELL){action="water";label="井戸で水をくむ";}
            else if(t.object==World.WARP){action="map";label="旅の門を使う";}
            else if(t.object==World.RUINS || t.object==World.SHRINE){action="lore";label="古い文字を調べる";}
            if(action!=null){found=new Interaction(action,label,tx,tz,key(tx,tz));best=d;}
        }
        // Reproducible local resources ensure every chapter has a useful harvesting loop.
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
            int tx=px+dx,tz=pz+dz;double d=Math.hypot(x-tx,z-tz);long k=key(tx,tz);
            if(d>=best || hidden(tx,tz) || SceneCatalog.nearFeature(tx,tz,1))continue;
            String decor=decor(tx,tz);
            if(decor!=null){
                if(decor.equals("chest")){if(openedChests.contains(k))continue;found=new Interaction("chest","宝箱を開ける",tx,tz,k);}
                else found=new Interaction("forage","材料を採取する",tx,tz,k);
                best=d;
            }
        }
        if(found==null && waterNear())return new Interaction(count(Item.BUCKET)>0?"water":"fish",count(Item.BUCKET)>0?"水辺で水をくむ":"水辺で釣りをする",x,z,0);
        return found;
    }
    public synchronized String interact(){
        Interaction target=interaction();if(target==null){tell("木・岩・草花・建物に近づこう");return "";}
        if(interactCooldown>0)return "";
        interactCooldown=.6f;
        switch(target.action){
            case "place":place();break;
            case "enter":case "enter-built":
                outsideX=x;outsideZ=z;interior=target.action+":"+target.key;x=0;z=2.5f;pendingBuild=null;mobs.clear();tell("かまど・作業台・ベッドを使えます");break;
            case "exit":x=outsideX;z=outsideZ;interior=null;tell("外へ出た");break;
            case "rest":rest();break;
            case "ignite":
                Building fire=null;for(Building b:buildings)if(b.id==target.key)fire=b;
                if(fire!=null && fire.litUntil>time)return "cook";
                if(count(Item.FLINT)<1 || count(Item.WOOD)<1){tell("火打ち石1つと木材1つが必要です");break;}
                add(Item.WOOD,-1);if(fire!=null)fire.litUntil=time+120;tell("火がついた。120秒間、料理と魔物よけに使えます");break;
            case "cannon":fireCannon(target.key);break;
            case "harvest-garden":
                for(Building b:buildings)if(b.id==target.key){if(b.grownAt>time)tell("麦はまだ育っています");else{add(Item.WHEAT,4);add(Item.SEED,2);b.grownAt=time+100;tell("小麦 +4 / 種 +2 · 再び育ち始めた");}}break;
            case "water":interactCooldown=0;fillWater();break;
            case "fish":interactCooldown=0;fish();break;
            case "gather":gather(Math.round(target.x),Math.round(target.z));break;
            case "forage":forage(Math.round(target.x),Math.round(target.z));break;
            case "chest":openedChests.add(target.key);add(Item.COIN,3);add(Item.IRON,2);add(Item.STAR,1);tell("宝箱 · 金貨 +3 / 鉄 +2 / 星のかけら +1");break;
            case "trade":
                if(count(Item.COIN)>=2){add(Item.COIN,-2);add(Item.IRON,2);add(Item.BERRY,3);tell("金貨2枚を鉄2つと木の実3つに交換した");}
                else tell("旅商人：宝箱の金貨2枚で、鉄と食料を交換するよ");break;
            case "lore":tell("碑文：七地域の記憶と星3つを、願いの森の祭壇へ");break;
            case "quest":
                if(!questReward && visited.size()==7 && count(Item.STAR)>=3){add(Item.STAR,-3);add(Item.IRON,8);add(Item.COIN,10);questReward=true;tell("七つ星の旅を達成！ 鉄 +8 / 金貨 +10");}
                else tell(questReward?"七つの物語が、この土地の灯りになった":"七地域を訪れ、星のかけら3つを祭壇へ。現在 "+visited.size()+" / 7 地域");break;
            case "cook":case "craft":case "map":return target.action;
        }
        return "";
    }
    private void gather(int tx,int tz){
        if(hidden(tx,tz))return;World.Tile t=world.tile(tx,tz);
        if(t.object==World.TREE || t.object==World.PINE){add(Item.WOOD,3);add(Item.FIBER,1);if(t.region==0||t.region==6)add(Item.BERRY,2);tell("木材 +3 / 植物繊維 +1");}
        else if(t.object==World.ROCK || t.object==World.CRYSTAL){add(Item.STONE,3);add(Item.FLINT,1);add(Item.ORE,2);tell("石 +3 / 火打ち石 +1 / 鉄鉱石 +2");}
        else {add(Item.HERB,1);add(Item.FIBER,2);add(t.object==World.MUSHROOM?Item.MUSHROOM:Item.BERRY,2);tell("食材 +2 / 薬草 +1 / 植物繊維 +2");}
        harvested.put(key(tx,tz),time+280);
    }
    private void forage(int tx,int tz){
        if(hidden(tx,tz))return;String type=decor(tx,tz);if(type==null)return;
        switch(type){
            case "boulder":case "crystal":add(Item.STONE,3);add(Item.ORE,2);add(Item.FLINT,1);tell("石 +3 / 鉱石 +2 / 火打ち石 +1");break;
            case "wheat":add(Item.WHEAT,3);add(Item.SEED,2);tell("小麦 +3 / 種 +2");break;
            case "reed":add(Item.CLAY,3);add(Item.FIBER,3);tell("粘土 +3 / 植物繊維 +3");break;
            case "mushroom":add(Item.MUSHROOM,3);add(Item.HERB,1);tell("きのこ +3 / 薬草 +1");break;
            default:add(Item.BERRY,3);add(Item.HERB,2);add(Item.FIBER,2);tell("木の実 +3 / 薬草 +2 / 植物繊維 +2");
        }
        harvested.put(key(tx,tz),time+220);
    }
    public String decor(int tx,int tz){
        World.Tile tile=world.tile(tx,tz);
        if(tile.object!=World.NONE || tile.road || tile.biome==World.WATER)return null;
        int vegetation=Math.floorMod(World.hash(tx,tz,19),100);
        if(tile.region==2 && vegetation<35)return "wheat";
        if(tile.region==3 && vegetation<17)return "reed";
        int h=Math.floorMod(World.hash(tx,tz,445),100);
        if(h==0)return "chest";
        if(h<4)return "boulder";
        if(h<9)return tile.region==2?"wheat":tile.region==3?"reed":tile.region==4?"crystal":tile.region==5?"mushroom":"berry_bush";
        if(h==9 || h==10)return "herb";
        return null;
    }
    private void rest(){
        if(interior==null)return;
        double day=Math.floor(time/700);time=(day+1)*700+100;
        health=100;stamina=100;hunger=Math.max(25,hunger-12);thirst=Math.max(25,thirst-15);mobs.clear();tell("朝になった。新しい旅を始めよう");
    }
    private void fireCannon(long id){
        if(count(Item.CANNONBALL)<1){tell("作業台で砲弾を作ろう");return;}
        Building cannon=null;for(Building b:buildings)if(b.id==id)cannon=b;
        if(cannon==null)return;add(Item.CANNONBALL,-1);int hits=0;
        for(Mob m:mobs)if(m.hostile && Math.hypot(m.x-cannon.x,m.z-cannon.z)<19){m.hp-=65;hits++;if(m.hp<=0){add(Item.STAR,1);add(Item.COIN,1);}}
        tell("砲撃！ "+hits+"体の魔物に命中");attackAnimation=.5f;
    }
    public synchronized Snapshot snapshot(){
        Snapshot s=new Snapshot();s.x=x;s.z=z;s.yaw=yaw;s.health=health;s.hunger=hunger;s.thirst=thirst;s.stamina=stamina;
        s.time=time;s.phase=(float)(time%700);s.night=isNight();s.walking=walking;s.guarding=guarding;s.attack=attackAnimation;
        s.fishable=interior==null && riverNear();s.fade=travel.opacity();s.interior=interior;s.pending=pendingBuild;s.canBuild=canPlace();s.sword=count(Item.SWORD)>0;s.shield=count(Item.SHIELD)>0;
        s.message=messageTime>0?message:"";s.place=interior==null?world.placeName(x,z):"旅人の部屋";
        s.quest=questReward?"七つ星の旅 · 達成": "七つ星の旅  "+visited.size()+" / 7";
        s.interaction=interaction();s.items=Collections.unmodifiableMap(new EnumMap<>(inventory));
        s.buildings=new ArrayList<>();for(Building b:buildings)s.buildings.add(b.copy());
        s.mobs=new ArrayList<>();for(Mob m:mobs)s.mobs.add(m.copy());
        s.hidden=new HashSet<>();for(Map.Entry<Long,Double> e:harvested.entrySet())if(e.getValue()>time)s.hidden.add(e.getKey());
        s.opened=new HashSet<>(openedChests);return s;
    }
    public synchronized void ensureSafeSpawn(){
        if(walkable(x,z))return;float ox=x,oz=z;
        for(int r=1;r<10;r++)for(int dz=-r;dz<=r;dz++)for(int dx=-r;dx<=r;dx++)if(walkable(ox+dx,oz+dz)){x=ox+dx;z=oz+dz;return;}
        x=0;z=2;
    }
    public synchronized void write(DataOutput out)throws IOException{
        out.writeInt(SAVE_MAGIC);out.writeInt(SAVE_VERSION);
        out.writeFloat(interior==null?x:outsideX);out.writeFloat(interior==null?z:outsideZ);out.writeFloat(yaw);out.writeDouble(time);
        out.writeFloat(health);out.writeFloat(hunger);out.writeFloat(thirst);out.writeFloat(stamina);out.writeBoolean(questReward);
        out.writeInt(inventory.size());for(Map.Entry<Item,Integer> e:inventory.entrySet()){out.writeUTF(e.getKey().name());out.writeInt(e.getValue());}
        out.writeInt(buildings.size());for(Building b:buildings){out.writeInt(b.id);out.writeUTF(b.model);out.writeFloat(b.x);out.writeFloat(b.z);out.writeFloat(b.yaw);out.writeDouble(b.litUntil);out.writeDouble(b.grownAt);}
        out.writeInt(harvested.size());for(Map.Entry<Long,Double> e:harvested.entrySet()){out.writeLong(e.getKey());out.writeDouble(e.getValue());}
        out.writeInt(openedChests.size());for(long key:openedChests)out.writeLong(key);
        out.writeInt(visited.size());for(int region:visited)out.writeInt(region);
    }
    private static int bounded(DataInput in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("Invalid save count");return n;}
    private static float finite(float v)throws IOException{if(!Float.isFinite(v)||Math.abs(v)>1000000)throw new IOException("Invalid coordinate");return v;}
    public static SurvivalGame read(DataInput in)throws IOException{
        if(in.readInt()!=SAVE_MAGIC || in.readInt()!=SAVE_VERSION)throw new IOException("Unsupported save");
        SurvivalGame game=new SurvivalGame();game.inventory.clear();
        game.x=finite(in.readFloat());game.z=finite(in.readFloat());game.yaw=finite(in.readFloat());game.time=in.readDouble();
        if(!Double.isFinite(game.time)||game.time<0)throw new IOException("Invalid time");
        game.health=stat(in.readFloat());game.hunger=stat(in.readFloat());game.thirst=stat(in.readFloat());game.stamina=stat(in.readFloat());game.questReward=in.readBoolean();
        int n=bounded(in,Item.values().length);for(int i=0;i<n;i++){String item=in.readUTF();int value=bounded(in,999);try{game.add(Item.valueOf(item),value);}catch(IllegalArgumentException ignored){}}
        n=bounded(in,160);for(int i=0;i<n;i++){
            int id=bounded(in,1000000);String model=in.readUTF();
            if(!Arrays.asList("house","campfire","workbench","furnace","lamp","cannon","garden").contains(model))throw new IOException("Invalid building");
            Building b=new Building(id,model,finite(in.readFloat()),finite(in.readFloat()),finite(in.readFloat()));
            b.litUntil=in.readDouble();b.grownAt=in.readDouble();game.buildings.add(b);game.buildingSerial=Math.max(game.buildingSerial,id);
        }
        n=bounded(in,10000);for(int i=0;i<n;i++)game.harvested.put(in.readLong(),in.readDouble());
        n=bounded(in,10000);for(int i=0;i<n;i++)game.openedChests.add(in.readLong());
        n=bounded(in,7);for(int i=0;i<n;i++){int region=in.readInt();if(region<0||region>6)throw new IOException("Invalid region");game.visited.add(region);}
        game.ensureSafeSpawn();
        game.tell("旅の続きへ。建築と持ち物を復元しました");return game;
    }
    private static float stat(float value)throws IOException{finite(value);return Math.max(0,Math.min(100,value));}
}
