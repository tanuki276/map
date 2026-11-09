package jp.tanuki.fablewind;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.opengl.GLSurfaceView;
import android.util.AtomicFile;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.*;

/** Native GLB playfield with a density-independent Canvas HUD and multi-touch controls. */
public final class GameView extends FrameLayout {
    private final GLSurfaceView surface;
    private final Hud hud;
    private final SurvivalGame game;
    private final SurvivalGame.Input input=new SurvivalGame.Input();
    private final SceneRenderer renderer;
    private final AtomicFile saveFile;
    private volatile SurvivalGame.Snapshot snapshot,hudSnapshot;
    private volatile String renderError="",saveError="";
    private volatile int loadedModels,triangles;
    private volatile long renderedFrames;
    private boolean active;
    private static final String PLAY="play",CRAFT="craft",BAG="bag",MAP="map",HELP="help";
    private volatile String menu=PLAY;private int recipeCategory,recipePage,selectedRecipe,selectedWarp;
    public GameView(Context context){
        super(context);
        saveFile=new AtomicFile(new File(context.getFilesDir(),"fablewind-survival-v1.bin"));
        SurvivalGame loaded=null;
        if(saveFile.getBaseFile().exists() || new File(saveFile.getBaseFile()+".bak").exists())try(DataInputStream in=new DataInputStream(saveFile.openRead())){loaded=SurvivalGame.read(in);}catch(IOException e){
            File recovery=new File(context.getFilesDir(),"fablewind-unreadable-"+System.currentTimeMillis()+".bin");
            if(!saveFile.getBaseFile().renameTo(recovery))saveError="セーブを保護しています。保存できません";
            else saveError="以前のセーブを復旧用に保護しました";
        }
        if(loaded==null){
            loaded=new SurvivalGame();SharedPreferences legacy=context.getSharedPreferences("fablewind_world",Context.MODE_PRIVATE);
            loaded.x=legacy.getFloat("x",0);loaded.z=legacy.getFloat("y",2);loaded.time=legacy.getFloat("clock",144);
            if(!Float.isFinite(loaded.x)||!Float.isFinite(loaded.z)||!Double.isFinite(loaded.time)){loaded.x=0;loaded.z=2;loaded.time=144;}
        }
        loaded.ensureSafeSpawn();game=loaded;snapshot=game.snapshot();
        surface=new GLSurfaceView(context);surface.setEGLContextClientVersion(2);surface.setEGLConfigChooser(8,8,8,0,16,0);surface.setPreserveEGLContextOnPause(true);
        renderer=new SceneRenderer(context,game,input,new SceneRenderer.Listener(){
            public void ready(SurvivalGame.Snapshot s,String error,int models,int tris){snapshot=s;renderError=error;loadedModels=models;triangles=tris;renderedFrames++;refreshHud();}
            public void save(){saveGame();}
        });
        surface.setRenderer(renderer);addView(surface,new LayoutParams(-1,-1));hud=new Hud(context);addView(hud,new LayoutParams(-1,-1));
        setFocusableInTouchMode(true);
    }
    private void refreshHud(){if(hud!=null)hud.postInvalidateOnAnimation();}
    SurvivalGame.Snapshot testHudSnapshot(){return hudSnapshot;}
    void testSyncFrame()throws InterruptedException{
        java.util.concurrent.CountDownLatch complete=new java.util.concurrent.CountDownLatch(1);
        surface.queueEvent(()->{android.opengl.GLES20.glFinish();complete.countDown();});
        if(!complete.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("GPU completion timeout");
    }
    String testMenu(){return menu;}
    SurvivalGame.Snapshot testSnapshot(){return snapshot;}
    long testFrames(){return renderedFrames;}
    SurvivalGame testGame(){return game;}
    int testModels(){return loadedModels;}
    String testError(){return renderError;}
    public void pause(){active=false;input.clear();synchronized(game){game.paused=true;}saveGame();surface.onPause();}
    public void resume(){active=true;input.clear();synchronized(game){game.paused=false;}surface.onResume();hud.invalidate();}
    private synchronized void saveGame(){
        if(saveError.startsWith("セーブを保護"))return;
        FileOutputStream out=null;
        try{
            out=saveFile.startWrite();DataOutputStream data=new DataOutputStream(out);game.write(data);data.flush();saveFile.finishWrite(out);
        }catch(IOException e){if(out!=null)saveFile.failWrite(out);saveError="保存できません: "+e.getMessage();}
    }
    private void command(Runnable action){surface.queueEvent(()->{synchronized(game){action.run();}saveGame();});}
    private void setMenu(String value){menu=value;input.menu=!PLAY.equals(value);input.clear();hud.resetPointers();hud.invalidate();}
    private void interact(){surface.queueEvent(()->{String action; synchronized(game){action=game.interact();}saveGame();if(action.equals("cook")||action.equals("craft"))hud.post(()->{recipeCategory=action.equals("cook")?2:0;recipePage=0;selectedRecipe=filteredRecipes().get(0);setMenu(CRAFT);});else if(action.equals("map"))hud.post(()->setMenu(MAP));});}
    private java.util.List<Integer> filteredRecipes(){
        java.util.List<Integer> list=new ArrayList<>();
        for(int i=0;i<Recipe.ALL.size();i++){Recipe r=Recipe.ALL.get(i);int category=r.building()?0:r.station.equals("fire")?2:1;if(category==recipeCategory)list.add(i);}
        return list;
    }
    public void back(){if(!PLAY.equals(menu))setMenu(PLAY);else if(snapshot.pending!=null)command(game::cancelBuild);else setMenu(HELP);}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_BACK){if(!PLAY.equals(menu)){setMenu(PLAY);return true;}if(snapshot.pending!=null){command(game::cancelBuild);return true;}setMenu(HELP);return true;}
        return super.onKeyDown(key,event);
    }
    private final class Hud extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Typeface serif=Typeface.create("serif",Typeface.BOLD),sans=Typeface.create("sans-serif",Typeface.NORMAL);
        private float scale=1,sw=960,sh=540,joyX,joyY,cameraX,cameraY;private int stick=-1,run=-1,attack=-1,look=-1;
        private Bitmap mapImage;private final RectF mapRect=new RectF();
        Hud(Context context){super(context);setFocusable(true);}
        void resetPointers(){stick=run=attack=look=-1;joyX=joyY=0;}
        private void text(Canvas c,String value,float x,float y,float size,int color,boolean fancy){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTypeface(fancy?serif:sans);p.setTextSize(size);c.drawText(value,x,y,p);}
        private void panel(Canvas c,float l,float t,float r,float b,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,12,12,p);p.setColor(0x7798b2a1);p.setStrokeWidth(1);p.setStyle(Paint.Style.STROKE);c.drawRoundRect(l,t,r,b,12,12,p);p.setStyle(Paint.Style.FILL);}
        private void button(Canvas c,String label,float l,float t,float r,float b,boolean selected){p.setTextSize(15);p.setTypeface(serif);panel(c,l,t,r,b,selected?0xea6b816d:0xdd213a40);text(c,label,(l+r-p.measureText(label))/2,(t+b)/2+6,15,0xffffe5af,true);}
        private void bar(Canvas c,String label,float value,float x,float y,float width,int color){text(c,label,x,y,10,0xffe1e7d7,false);p.setColor(0x9941504c);c.drawRoundRect(x+34,y-9,x+34+width,y-2,4,4,p);p.setColor(color);c.drawRoundRect(x+34,y-9,x+34+width*Math.max(0,Math.min(1,value/100)),y-2,4,4,p);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);scale=Math.min(getWidth()/960f,getHeight()/540f);if(scale<=0)return;sw=getWidth()/scale;sh=getHeight()/scale;
            c.save();c.scale(scale,scale);SurvivalGame.Snapshot s=snapshot;
            if(s==null){text(c,"物語をひらいています…",sw/2-100,sh/2,20,0xfff1dda8,true);c.restore();postInvalidateOnAnimation();return;}
            if(loadedModels==0){panel(c,sw/2-190,sh/2-45,sw/2+190,sh/2+45,0xf0223941);text(c,"GLBの世界をひらいています…",sw/2-155,sh/2+7,19,0xffffe5af,true);}
            panel(c,16,14,340,112,0xd1223941);
            text(c,"FABLEWIND  /  風の国の旅人",28,37,16,0xffffe2a4,true);
            text(c,s.place+"   "+(s.night?"月夜":"昼")+"  ·  "+s.quest,28,57,11,0xffe4dfc4,false);
            bar(c,"体力",s.health,28,77,95,0xffd48776);bar(c,"満腹",s.hunger,190,77,95,0xffd4ba77);
            bar(c,"渇き",s.thirst,28,97,95,0xff7fbcd3);bar(c,"持久",s.stamina,190,97,95,0xff9ac895);
            button(c,"製作",sw-310,15,sw-242,55,false);button(c,"持ち物",sw-234,15,sw-145,55,false);button(c,"地図",sw-137,15,sw-68,55,false);button(c,"?",sw-60,15,sw-17,55,false);
            button(c,"食べる",sw-225,64,sw-128,99,false);button(c,"飲む",sw-120,64,sw-17,99,false);
            text(c,"右の空いている場所をドラッグして見回す",sw-312,119,10,0xfff2e6ca,false);
            if(PLAY.equals(menu) && s.fishable)button(c,"釣りをする",sw-225,128,sw-128,164,false);
            if(PLAY.equals(menu))drawControls(c,s);else drawMenu(c,s);
            if(!s.message.isEmpty()){
                p.setTextSize(13);p.setTypeface(sans);float w=Math.min(sw-70,p.measureText(s.message)+40);panel(c,(sw-w)/2,sh-168,(sw+w)/2,sh-126,0xe4263f42);text(c,s.message,(sw-w)/2+18,sh-142,13,0xffffe9b7,false);
            }
            if(!saveError.isEmpty())text(c,saveError,20,sh-12,11,0xffffb89d,false);
            if(!renderError.isEmpty()){panel(c,25,145,sw-25,sh-30,0xf02c3036);text(c,renderError,45,185,15,0xffffb9a1,false);}
            if(s.fade>0){p.setColor(Color.argb((int)(255*s.fade),18,30,39));c.drawRect(0,0,sw,sh,p);}
            hudSnapshot=s;c.restore();if(active)postInvalidateOnAnimation();
        }
        private void drawControls(Canvas c,SurvivalGame.Snapshot s){
            float jx=103,jy=sh-95;p.setColor(0x55516b65);c.drawCircle(jx,jy,65,p);p.setColor(0x55d1d5b2);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(jx,jy,63,p);p.setStyle(Paint.Style.FILL);p.setColor(0xcce6dfbd);c.drawCircle(jx+joyX*42,jy+joyY*42,24,p);
            button(c,input.run?"疾走中":"走る",179,sh-78,247,sh-29,input.run);
            p.setColor(input.attack?0xe8c39b63:0xc5385253);c.drawCircle(sw-83,sh-80,53,p);p.setStyle(Paint.Style.STROKE);p.setColor(0xffe1c58c);p.setStrokeWidth(2);c.drawCircle(sw-83,sh-80,51,p);p.setStyle(Paint.Style.FILL);
            text(c,s.sword?"剣で攻撃":"攻撃",sw-116,sh-75,18,0xffffe5b2,true);
            String action=s.interaction==null?"調べる":s.interaction.title;
            button(c,action,sw-365,sh-108,sw-151,sh-58,s.pending!=null && s.canBuild);
            if(s.pending!=null)button(c,"設置をやめる",sw-365,sh-49,sw-151,sh-16,false);
            else button(c,s.guarding?"盾を構え中":"盾を構える",sw-299,sh-49,sw-151,sh-16,s.guarding);
            button(c,"＋",sw-58,147,sw-20,185,false);button(c,"−",sw-58,193,sw-20,231,false);
            text(c,String.format(java.util.Locale.US,"X %.0f  Z %.0f",s.x,s.z),22,132,10,0xfff4e3c0,false);
        }
        private void drawMenu(Canvas c,SurvivalGame.Snapshot s){
            p.setColor(0xd21a2c32);c.drawRect(0,0,sw,sh,p);panel(c,28,24,sw-28,sh-24,0xf0273e43);
            text(c,menu.equals(CRAFT)?"旅人の工房":menu.equals(BAG)?"旅の持ち物":menu.equals(MAP)?"七つの物語をめぐる地図":"旅の手引き",48,61,25,0xffffe1a4,true);
            button(c,"閉じる ×",sw-151,37,sw-47,76,false);
            if(menu.equals(CRAFT))drawCraft(c,s);else if(menu.equals(BAG))drawBag(c,s);else if(menu.equals(MAP))drawMap(c,s);else drawHelp(c,s);
        }
        private void drawCraft(Canvas c,SurvivalGame.Snapshot s){
            for(int i=0;i<3;i++)button(c,new String[]{"建築","道具・精錬","料理"}[i],48+i*145,88,183+i*145,124,recipeCategory==i);
            java.util.List<Integer> list=filteredRecipes();int start=recipePage*5;
            for(int row=0;row<5 && start+row<list.size();row++){
                int index=list.get(start+row);Recipe r=Recipe.ALL.get(index);float y=140+row*57;
                panel(c,48,y,sw-365,y+50,index==selectedRecipe?0xe7657c68:0xff314c4d);
                text(c,r.title,63,y+22,16,0xfff6e1b6,true);text(c,r.description,63,y+41,11,0xffcbd9ca,false);
            }
            if(list.size()>5)button(c,"ページ "+(recipePage+1)+" / "+((list.size()+4)/5)+"  →",48,sh-68,274,sh-36,false);
            Recipe r=Recipe.ALL.get(Math.max(0,Math.min(selectedRecipe,Recipe.ALL.size()-1)));
            float x=sw-341;text(c,r.title,x,160,22,0xffffe3ac,true);text(c,r.building()?"設置して使う設備":"製作後、持ち物へ追加",x,184,12,0xffbfcebf,false);
            int row=0;for(Map.Entry<Item,Integer> cost:r.cost.entrySet()){
                int have=s.items.getOrDefault(cost.getKey(),0);text(c,cost.getKey().title+"  "+have+" / "+cost.getValue(),x,220+row*29,15,have>=cost.getValue()?0xffd6e6bf:0xffeda88a,false);row++;
            }
            String station=r.station.equals("bench")?"作業台の近く":r.station.equals("furnace")?"炉の近く":r.station.equals("fire")?"着火した焚き火 / 屋内のかまど":"どこでも製作できます";
            text(c,"必要な場所",x,360,11,0xff9fbbae,false);text(c,station,x,383,13,0xffdce6c8,false);
            button(c,r.building()?"設置場所を選ぶ":"これを作る",x,sh-106,sw-48,sh-54,true);
            text(c,"足りない素材は消費されません",x,sh-35,10,0xffbdcbb9,false);
        }
        private void drawBag(Canvas c,SurvivalGame.Snapshot s){
            text(c,"剣・盾・バケツは製作すると自動で使えます。",49,94,13,0xffcdd8c5,false);
            int i=0;for(Item item:Item.values()){
                int col=i%6,row=i/6;float cell=(sw-115)/6,x=48+col*cell,y=116+row*77;
                panel(c,x,y,x+cell-10,y+65,0xff314d4d);text(c,item.title,x+10,y+25,13,0xfff1dfb5,true);
                text(c,String.valueOf(s.items.getOrDefault(item,0)),x+12,y+52,19,0xffa9cbb3,false);i++;
            }
            text(c,"木材と石 → 焚き火 / 作業台 → 炉で鉄を精錬 → 装備・家・砲台",49,sh-40,12,0xffd0dbc4,false);
        }
        private void drawMap(Canvas c,SurvivalGame.Snapshot s){
            text(c,"行き先を選んでワープ。各地で異なる素材・建築・生き物に出会えます。",48,92,12,0xffccdacb,false);
            mapRect.set(48,114,sw-370,sh-75);
            if(mapImage==null){
                mapImage=Bitmap.createBitmap(160,112,Bitmap.Config.RGB_565);Canvas m=new Canvas(mapImage);p.setStrokeWidth(1);
                for(int y=0;y<112;y++)for(int x=0;x<160;x++){
                    World.Tile t=game.world.preview(Math.round(-105+x*1.4f),Math.round(-68+y*1.4f));float[] a=Terrain.color(t);
                    p.setColor(Color.rgb((int)Math.min(255,a[0]*110),(int)Math.min(255,a[1]*135),(int)Math.min(255,a[2]*113)));m.drawPoint(x,y,p);
                }
            }
            p.setColor(Color.WHITE);p.setFilterBitmap(true);c.drawBitmap(mapImage,null,mapRect,p);
            for(int i=1;i<7;i++){
                WarpPoint point=WarpPoint.ALL.get(i);p.setColor(0x88dbc99b);p.setStrokeWidth(2);c.drawLine(mapX(0),mapZ(0),mapX(point.x),mapZ(point.y),p);
            }
            String[] symbols={"村","城","風車","灯台","遺跡","大樹","南瓜"};
            for(int i=0;i<7;i++){
                WarpPoint point=WarpPoint.ALL.get(i);float x=mapX(point.x),y=mapZ(point.y);p.setColor(point.color);c.drawCircle(x,y,i==selectedWarp?10:6,p);text(c,symbols[i],x+10,y-8,12,0xffffe7b2,true);
            }
            if(mapRect.contains(mapX(s.x),mapZ(s.z))){p.setColor(0xfffaf5d6);c.drawCircle(mapX(s.x),mapZ(s.z),4,p);}
            float rowH=(sh-213)/7;for(int i=0;i<7;i++){
                float y=111+i*rowH;WarpPoint point=WarpPoint.ALL.get(i);
                if(i==selectedWarp)panel(c,sw-346,y,sw-49,y+rowH-3,0xff667e68);
                text(c,(i+1)+"  "+point.name,sw-330,y+18,15,0xffffe3b1,true);text(c,point.description,sw-330,y+33,10,0xffc7d9c7,false);
            }
            button(c,"この門へワープ →",sw-346,sh-80,sw-49,sh-36,true);
            text(c,s.quest+"  /  祭壇は願いの森にあります",49,sh-47,12,0xffedddb7,false);
        }
        private float mapX(float x){return mapRect.left+(x+105)/224f*mapRect.width();}
        private float mapZ(float z){return mapRect.top+(z+68)/156.8f*mapRect.height();}
        private void drawHelp(Canvas c,SurvivalGame.Snapshot s){
            String[] lines={
                "左のスティックで歩く。『走る』と同時押しで疾走。右側をドラッグするとカメラを回せます。",
                "木・岩・草花に近づき、右下のボタンで採集。各地域の宝箱には鉄や金貨も入っています。",
                "製作 → 焚き火・作業台を設置。焚き火は木材1つと火打ち石で着火し、120秒燃えます。",
                "湿原の葦から粘土を集めて炉を作り、岩から得た鉄鉱石と木材を鉄へ精錬します。",
                "バケツを製作したら井戸・川で水くみ。水・小麦・きのこ・薬草を料理に使えます。",
                "家や一部の建物には入れます。屋内のかまど・作業台を使い、ベッドの近くで朝まで休めます。",
                "夜は村の外に魔物が現れます。剣で攻撃、盾で防御。灯りや火のついた焚き火の近くは安全です。",
                "砲台は作業台で作る砲弾を消費。麦畑は約100秒で育ち、収穫後も再び育ちます。",
                "金貨は旅商人と交換。七地域を巡り、星のかけら3つを願いの森の祭壇へ届けよう。"
            };
            for(int i=0;i<lines.length;i++)text(c,(i+1)+".  "+lines[i],48,111+i*37,12,0xffe0e6d1,false);
            text(c,loadedModels+" original GLB models  /  オフライン・自動保存",49,sh-48,11,0xffa9c6b5,false);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked(),index=e.getActionIndex(),id=e.getPointerId(index);float x=e.getX(index)/scale,y=e.getY(index)/scale;
            if(snapshot!=null && snapshot.fade>0){input.clear();return true;}
            if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN){
                if(!PLAY.equals(menu)){menuTouch(x,y);return true;}
                if(y<60 && x>sw-315){if(x>sw-65)setMenu(HELP);else if(x>sw-141)setMenu(MAP);else if(x>sw-238)setMenu(BAG);else{recipeCategory=0;recipePage=0;selectedRecipe=0;setMenu(CRAFT);}return true;}
                if(y>=64 && y<=100 && x>sw-225){command(x>sw-124?game::drink:game::eat);return true;}
                if(snapshot.fishable && x>sw-225 && x<sw-128 && y>=128 && y<=164){command(game::fish);return true;}
                if(x>sw-63 && y>145 && y<234){renderer.cameraDistance=Math.max(6,Math.min(18,renderer.cameraDistance+(y<190?-1.5f:1.5f)));return true;}
                if(x<170 && y>sh-180){stick=id;setStick(x,y);return true;}
                if(x>=179 && x<=247 && y>sh-80){run=id;input.run=true;return true;}
                if(Math.hypot(x-(sw-83),y-(sh-80))<58){attack=id;input.attack=true;return true;}
                if(x>=sw-365 && x<=sw-151 && y>=sh-108 && y<=sh-58){interact();return true;}
                if(x>=sw-365 && x<=sw-151 && y>sh-52){if(snapshot.pending!=null)command(game::cancelBuild);else input.guard=!input.guard;return true;}
                if(x>sw*.38f){look=id;cameraX=x;cameraY=y;return true;}
            }else if(action==MotionEvent.ACTION_MOVE){
                if(stick>=0){int i=e.findPointerIndex(stick);if(i>=0)setStick(e.getX(i)/scale,e.getY(i)/scale);}
                if(look>=0){int i=e.findPointerIndex(look);if(i>=0){float xx=e.getX(i)/scale,yy=e.getY(i)/scale;renderer.cameraYaw-= (xx-cameraX)*.3f;renderer.cameraPitch=Math.max(.35f,Math.min(1.15f,renderer.cameraPitch+(yy-cameraY)*.003f));cameraX=xx;cameraY=yy;}}
            }else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP){
                if(id==stick){stick=-1;input.horizontal=input.vertical=joyX=joyY=0;}if(id==run){run=-1;input.run=false;}if(id==attack){attack=-1;input.attack=false;}if(id==look)look=-1;
                performClick();
            }else if(action==MotionEvent.ACTION_CANCEL){resetPointers();input.clear();}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        private void setStick(float x,float y){float dx=x-103,dy=y-(sh-95),len=(float)Math.hypot(dx,dy);if(len>55){dx*=55/len;dy*=55/len;}joyX=dx/55;joyY=dy/55;input.horizontal=joyX;input.vertical=joyY;}
        private void menuTouch(float x,float y){
            if(x>sw-160 && y<83){setMenu(PLAY);return;}
            if(menu.equals(CRAFT)){
                if(y>=88 && y<=125 && x>=48 && x<483){recipeCategory=Math.min(2,(int)((x-48)/145));recipePage=0;selectedRecipe=filteredRecipes().get(0);return;}
                java.util.List<Integer> list=filteredRecipes();
                if(x>=48 && x<sw-365 && y>=140 && y<425){int row=(int)((y-140)/57)+recipePage*5;if(row<list.size())selectedRecipe=list.get(row);return;}
                if(x>=48 && x<275 && y>=sh-70){recipePage=(recipePage+1)%((list.size()+4)/5);selectedRecipe=list.get(recipePage*5);return;}
                if(x>=sw-341 && x<=sw-48 && y>=sh-106 && y<=sh-54){Recipe recipe=Recipe.ALL.get(selectedRecipe);surface.queueEvent(()->{boolean result; synchronized(game){result=game.craft(recipe.id);}saveGame();if(result && recipe.building())hud.post(()->setMenu(PLAY));});return;}
            }else if(menu.equals(MAP)){
                float rowH=(sh-213)/7;
                if(x>=sw-346 && x<=sw-49 && y>=111 && y<111+7*rowH){selectedWarp=Math.max(0,Math.min(6,(int)((y-111)/rowH)));return;}
                if(x>=sw-346 && x<=sw-49 && y>=sh-80 && y<=sh-36){int target=selectedWarp;command(()->game.warp(target));setMenu(PLAY);return;}
                if(mapRect.contains(x,y)){float best=28*28;for(int i=0;i<7;i++){WarpPoint point=WarpPoint.ALL.get(i);float dx=x-mapX(point.x),dz=y-mapZ(point.y),d=dx*dx+dz*dz;if(d<best){best=d;selectedWarp=i;}}}
            }
        }
    }
}
