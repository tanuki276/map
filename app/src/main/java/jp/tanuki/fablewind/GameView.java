package jp.tanuki.fablewind;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Landscape-first, touch-controlled, self-contained offline exploration game. */
public final class GameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final World world = new World();
    private final SharedPreferences saves;
    private final Typeface serif = Typeface.create("serif", Typeface.BOLD);
    private final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    private float playerX, playerY, cameraX, cameraY;
    private float zoom, clock, joystickX, joystickY, facing;
    private int stickPointer = -1, runPointer = -1;
    private boolean sprint, mapOpen, paused;
    private long lastFrame;
    private float uiScale = 1;
    private Bitmap miniImage, bigImage;
    private int miniX = Integer.MIN_VALUE, miniY = Integer.MIN_VALUE;
    private float saveCountdown;
    private String lastPlace = "";
    private final WarpTravel travel = new WarpTravel();
    private int selectedWarp;
    private float arrivalNotice;
    private final RectF mapFrame = new RectF();
    private World.Tile[] frameTiles = new World.Tile[0];
    private int frameLeft, frameTop, frameColumns;

    public GameView(Context context) {
        super(context);
        saves = context.getSharedPreferences("fablewind_world", Context.MODE_PRIVATE);
        playerX = saves.getFloat("x", 0);
        playerY = saves.getFloat("y", 0);
        cameraX = playerX;
        cameraY = playerY;
        zoom = clamp(saves.getFloat("zoom", 1f), .62f, 1.55f);
        clock = saves.getFloat("clock", 144);
        setFocusable(true);
    }

    public void pause() {
        paused = true;
        clearInput();
        save();
    }
    public void resume() {
        paused = false;
        lastFrame = 0;
        invalidate();
    }
    private void save() {
        saves.edit().putFloat("x", playerX).putFloat("y", playerY)
                .putFloat("zoom", zoom).putFloat("clock", clock).apply();
    }

    private static float clamp(float value, float a,float b) {
        return Math.min(b, Math.max(a,value));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int displayWidth = getWidth(), displayHeight = getHeight();
        if (displayWidth == 0 || displayHeight == 0) return;
        uiScale=clamp(Math.min(displayWidth/910f,displayHeight/510f),.65f,2.45f);
        // Keep the world scale consistent across phone resolutions, just like the HUD.
        int width=Math.round(displayWidth/uiScale),height=Math.round(displayHeight/uiScale);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? .016f : clamp((now-lastFrame) / 1_000_000_000f,0f,.05f);
        lastFrame = now;
        if (!paused) update(dt);

        c.save();c.scale(uiScale,uiScale);
        // The screen is rendered as a field of depth-sorted extruded 2D diamond tiles.
        c.drawColor(0xffa0cfca);
        float w = 42f*zoom, h = 21f*zoom, groundLift = 11f*zoom;
        float centerX = width*.5f, centerY = height*.53f;
        float focusHeight = world.tile(Math.round(cameraX), Math.round(cameraY)).height;
        float[] sx = {0, width, 0, width};
        float[] sy = {0, 0, height, height};
        float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE;
        float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;
        for (int i=0;i<4;i++) {
            float a = (sx[i]-centerX)/w;
            float b = (sy[i]-centerY)/h;
            float xx = cameraX+(a+b)*.5f;
            float yy = cameraY+(b-a)*.5f;
            xMin = Math.min(xMin,xx); xMax = Math.max(xMax,xx);
            yMin = Math.min(yMin,yy); yMax = Math.max(yMax,yy);
        }
        int minX=(int)Math.floor(xMin)-8, maxX=(int)Math.ceil(xMax)+8;
        int minY=(int)Math.floor(yMin)-8, maxY=(int)Math.ceil(yMax)+8;
        cacheFrame(minX-1,minY-1,maxX+1,maxY+1);
        int playerLayer = (int)Math.floor(playerX+playerY);
        boolean playerDrawn = false;
        // Terrain first; props and traveler then share one painter's depth order.
        // Neighboring tiles reuse exactly the same averaged vertex heights.
        for (int pass=0;pass<2;pass++) {
            for (int sum=minX+minY; sum<=maxX+maxY; sum++) {
                int from = Math.max(minX,sum-maxY), to = Math.min(maxX,sum-minY);
                for (int x=from;x<=to;x++) {
                    int y=sum-x;
                    float screenX=centerX+(x-y-cameraX+cameraY)*w;
                    float approxY=centerY+(x+y-cameraX-cameraY)*h;
                    if(screenX < -230*zoom || screenX > width+230*zoom ||
                            approxY < -250*zoom || approxY > height+350*zoom) continue;
                    World.Tile tile=frameTile(x,y);
                    float screenY=approxY-(tile.height-focusHeight)*groundLift;
                    if(pass==0) {
                        Art.ground(c,p,path,tile,screenX,screenY,w,h,
                                approxY-h-(vertexHeight(x,y)-focusHeight)*groundLift,
                                approxY-(vertexHeight(x+1,y)-focusHeight)*groundLift,
                                approxY+h-(vertexHeight(x+1,y+1)-focusHeight)*groundLift,
                                approxY-(vertexHeight(x,y+1)-focusHeight)*groundLift,clock);
                    } else {
                        // Fade nearby foreground canopies so the traveler stays visible.
                        float px=centerX+(playerX-playerY-cameraX+cameraY)*w;
                        float py=centerY+(playerX+playerY-cameraX-cameraY)*h;
                        boolean canopy=tile.object==World.TREE || tile.object==World.GIANT_TREE;
                        boolean obscure=canopy && sum>playerLayer && Math.abs(screenX-px)<50*zoom
                                && screenY>py && screenY-py<150*zoom;
                        int layer=obscure?c.saveLayerAlpha(screenX-180*zoom,screenY-320*zoom,
                                screenX+180*zoom,screenY+20*zoom,125):0;
                        Art.object(c,p,path,tile,screenX,screenY,w,clock);
                        if(obscure)c.restoreToCount(layer);
                    }
                }
                if (pass==1 && !playerDrawn && sum>=playerLayer) {
                    drawPlayer(c,w,h,groundLift,focusHeight,centerX,centerY);
                    playerDrawn = true;
                }
            }
        }
        if (!playerDrawn) drawPlayer(c,w,h,groundLift,focusHeight,centerX,centerY);

        // Atmospheric fade provides soft afternoon/evening moods without external assets.
        double sun = Math.sin(clock*(Math.PI*2/700));
        int darkness = (int)(clamp((float)(.23-sun)*.47f,0,.53f)*255);
        if(darkness > 0) {
            p.setColor(Color.argb(darkness,18,28,63));
            c.drawRect(0,0,width,height,p);
        }
        drawAtmosphere(c,width,height,sun);
        drawFireflies(c,width,height,sun);
        c.restore();
        drawHud(c,displayWidth,displayHeight);
        if (mapOpen) drawLargeMap(c,displayWidth,displayHeight);
        if(travel.active()) {
            p.setColor(Color.argb((int)(travel.opacity()*255),27,42,48));
            c.drawRect(0,0,displayWidth,displayHeight,p);
        }
        if (!paused) postInvalidateOnAnimation();
    }

    private void update(float dt) {
        clock=(clock+dt)%700;
        arrivalNotice=Math.max(0,arrivalNotice-dt);
        WarpPoint arrival=travel.update(dt);
        if(arrival!=null) {
            playerX=cameraX=arrival.x; playerY=cameraY=arrival.y;
            miniX=miniY=Integer.MIN_VALUE;
            arrivalNotice=3.5f;
            save();
        }
        if (!mapOpen && !travel.active() && (Math.abs(joystickX)+Math.abs(joystickY)>.04f)) {
            float mx=joystickX*.65f+joystickY*1.23f;
            float my=-joystickX*.65f+joystickY*1.23f;
            float len=(float)Math.hypot(mx,my);
            if(len>1){mx/=len;my/=len;}
            float speed=sprint? 5.9f:3.2f;
            World.Tile step=world.tile(Math.round(playerX+mx*dt*speed),
                    Math.round(playerY+my*dt*speed));
            if (step.biome==World.WATER && !step.road) speed*=.57f;
            playerX+=mx*dt*speed; playerY+=my*dt*speed;
            facing=joystickX;
        }
        float follow=1-(float)Math.exp(-5.5f*dt);
        cameraX+=(playerX-cameraX)*follow;
        cameraY+=(playerY-cameraY)*follow;
        if((saveCountdown+=dt)>12) {
            saveCountdown=0;
            save();
        }
    }

    private void cacheFrame(int left,int top,int right,int bottom) {
        frameLeft=left;frameTop=top;frameColumns=right-left+1;
        int count=frameColumns*(bottom-top+1);
        if(frameTiles.length<count)frameTiles=new World.Tile[count];
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)
            frameTiles[(y-top)*frameColumns+x-left]=world.tile(x,y);
    }

    private World.Tile frameTile(int x,int y) {
        return frameTiles[(y-frameTop)*frameColumns+x-frameLeft];
    }

    private float vertexHeight(int x,int y) {
        return (frameTile(x,y).height+frameTile(x-1,y).height+
                frameTile(x,y-1).height+frameTile(x-1,y-1).height)*.25f;
    }

    private void clearInput() {
        joystickX=joystickY=0; sprint=false; stickPointer=runPointer=-1;
    }

    private void openMap() {
        clearInput();
        int nearby=WarpPoint.nearest(playerX,playerY,5);
        if(nearby>=0)selectedWarp=nearby;
        mapOpen=true;
    }

    private void drawAtmosphere(Canvas c,int width,int height,double sun) {
        // Soft depth haze and drifting mist, kept behind the legible HUD.
        p.setShader(new android.graphics.LinearGradient(0,0,0,height*.65f,
                0x60d7d7b9,0x00d7d7b9,android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0,0,width,height,p);p.setShader(null);
        int region=World.regionAt(playerX,playerY);
        if(region==3 || region==5) {
            for(int i=0;i<5;i++) {
                float x=(float)((i*width*.31+clock*8)%(width*1.5))-width*.25f;
                float y=height*(.2f+i*.14f);
                p.setColor(region==3?0x14e1e6dc:0x0fc0e5d6);
                c.drawOval(x,y,x+width*.55f,y+height*.11f,p);
            }
        }
        if(region==6 || region==1) {
            for(int i=0;i<16;i++) {
                float x=(i*173+clock*15)%width;
                float y=(i*97+clock*11)%height;
                p.setColor(region==6?0xaaa96837:0x99d5989d);
                c.drawOval(x,y,x+5,y+2,p);
            }
        }
    }

    private void drawPlayer(Canvas c,float w,float h,float lift,
                            float focusHeight,float centerX,float centerY) {
        float px=centerX+(playerX-playerY-cameraX+cameraY)*w;
        float py=centerY+(playerX+playerY-cameraX-cameraY)*h
                -(world.tile(Math.round(playerX),Math.round(playerY)).height-focusHeight)*lift;
        boolean walking=(Math.abs(joystickX)+Math.abs(joystickY)>.06f);
        Art.traveler(c,p,path,px,py,zoom*.98f,walking ? clock : 0f,
                sprint && walking,facing);
        if (walking) {
            p.setColor(0x4aefead0);
            for (int i=0;i<2;i++)
                c.drawCircle(px-((int)(clock*11+i*4)%12)+6,py+5,
                        (2+i)*zoom,p);
        }
    }

    private void drawFireflies(Canvas c,int width,int height,double sun) {
        if (sun > .23) return;
        for(int i=0;i<22;i++) {
            int hash=World.hash(i,61,131);
            float x=((hash&1023)/1023f)*width;
            float y=(((hash>>>10)&1023)/1023f)*height;
            x+=Math.sin(clock*.3f+i)*16;
            y+=Math.sin(clock*.8f+i*1.6f)*10;
            float pulse=(float)(.5+.5*Math.sin(clock*2+i*2.6));
            p.setColor(Color.argb((int)(52+110*pulse),255,231,141));
            c.drawCircle(x,y,1.2f+2f*pulse,p);
        }
    }

    private void panel(Canvas c,float x,float y,float right,float bottom,float radius) {
        p.setColor(0xc5303d4a);
        c.drawRoundRect(x,y,right,bottom,radius,radius,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.3f);
        p.setColor(0x6df5e1b1);
        c.drawRoundRect(x,y,right,bottom,radius,radius,p);
        p.setStyle(Paint.Style.FILL);
    }

    private void text(Canvas c,String s,float x,float y,float size,int color,boolean fancy) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(fancy?serif:sans);
        p.setShadowLayer(1.6f,0,1f,0x66333e44);
        c.drawText(s,x,y,p);
        p.clearShadowLayer();
    }

    private void drawHud(Canvas c,int width,int height) {
        uiScale=clamp(Math.min(width/910f,height/510f),.65f,2.45f);
        c.save();
        c.scale(uiScale,uiScale);
        float sw=width/uiScale,sh=height/uiScale;
        panel(c,15,14,257,88,13);
        text(c,"F A B L E W I N D",29,38,19,0xffffe3a7,true);
        String name=world.placeName(playerX,playerY);
        if(!name.equals(lastPlace)) lastPlace=name;
        text(c,lastPlace,29,61,13,0xfff0f0de,false);
        String position=String.format(java.util.Locale.US,
                "X %+.0f    Y %+.0f",playerX,playerY);
        text(c,position,29,78,10,0xffc4d7d1,false);

        panel(c,15,98,205,139,11);
        text(c,"◇  ワープ · 旅の門",29,124,15,0xffffe4ad,true);
        if(arrivalNotice>0) {
            panel(c,sw*.5f-160,sh-58,sw*.5f+160,sh-18,12);
            text(c,"✦  "+world.placeName(playerX,playerY)+" に到着",sw*.5f-138,sh-32,15,0xffffe4ad,true);
        }
        drawMiniMap(c,sw-191,14,176,143);
        p.setColor(0xfff3e1c0);
        p.setTextSize(11);
        panel(c,sw-185,20,sw-27,43,8);
        text(c,"地図をひらく  ▣",sw-177,35,12,0xffffe9be,false);

        panel(c,sw-51,sh*.42f-31,sw-13,sh*.42f+12,12);
        text(c,"＋",sw-42,sh*.42f-2,23,0xffffeac2,true);
        panel(c,sw-51,sh*.42f+20,sw-13,sh*.42f+63,12);
        text(c,"－",sw-42,sh*.42f+48,23,0xffffeac2,true);

        float joyX=101, joyY=sh-102;
        p.setColor(0x725b7277); c.drawCircle(joyX,joyY,70,p);
        p.setColor(0x647f9c99); c.drawCircle(joyX,joyY,52,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        p.setColor(0x9ff5dfba);c.drawCircle(joyX,joyY,69,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xe7edd9bb);
        c.drawCircle(joyX+joystickX*48,joyY+joystickY*48,26,p);

        float runX=sw-92,runY=sh-95;
        p.setColor(sprint?0xe1e4ac64:0xaa42585b);
        c.drawCircle(runX,runY,52,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        p.setColor(0xffefddae); c.drawCircle(runX,runY,51,p);
        p.setStyle(Paint.Style.FILL);
        text(c,"駆ける",runX-27,runY+5,17,0xffffeacc,true);
        text(c,"RUN",runX-12,runY+22,10,0xffe7d6b4,false);

        String time=(Math.sin(clock*2*Math.PI/700)>.1)?"☀  DAY":"☾  NIGHT";
        text(c,time,sw/2-24,29,11,0xffffe6c1,true);
        c.restore();
    }

    private void drawMiniMap(Canvas c,float left,float top,float width,float height) {
        if(miniImage==null || Math.abs((long)miniX-Math.round(playerX))>10 ||
                Math.abs((long)miniY-Math.round(playerY))>10) {
            miniX=Math.round(playerX);miniY=Math.round(playerY);
            if(miniImage!=null)miniImage.recycle();
            miniImage=Bitmap.createBitmap(72,56,Bitmap.Config.RGB_565);
            Canvas mc=new Canvas(miniImage);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(1);
            for(int y=0;y<56;y++)
                for(int x=0;x<72;x++) {
                    World.Tile t=world.preview(miniX+(x-36)*3,miniY+(y-28)*3);
                    p.setColor(Art.groundColor(t));
                    mc.drawPoint(x,y,p);
                }
        }
        panel(c,left,top,left+width,top+height,12);
        p.setAlpha(245);
        p.setFilterBitmap(false);
        c.drawBitmap(miniImage,null,
                new RectF(left+7,top+8,left+width-7,top+height-8),p);
        p.setAlpha(255);
        for(WarpPoint point:WarpPoint.ALL) {
            float mx=left+width/2+(point.x-miniX)/216f*(width-14);
            float my=top+height/2+(point.y-miniY)/168f*(height-16);
            if(mx>left+8 && mx<left+width-8 && my>top+9 && my<top+height-9) {
                p.setColor(point.color);c.drawCircle(mx,my,2.5f,p);
            }
        }
        float playerMapX=left+width/2+(playerX-miniX)/216f*(width-14);
        float playerMapY=top+height/2+(playerY-miniY)/168f*(height-16);
        p.setColor(0xfff2e6b9);
        c.drawCircle(playerMapX,playerMapY,4,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);
        c.drawCircle(playerMapX,playerMapY,7,p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawLargeMap(Canvas c,int width,int height) {
        c.save();c.scale(uiScale,uiScale);
        float sw=width/uiScale,sh=height/uiScale;
        p.setColor(0xdd192d32);c.drawRect(0,0,sw,sh,p);
        panel(c,24,22,sw-24,sh-22,18);
        text(c,"旅の門をめぐる地図",44,58,23,0xfff5dfa7,true);
        text(c,"行き先を選び、ワープで次の物語へ。すべての門を自由に使えます。",
                45,80,11,0xffc1d3c8,false);
        panel(c,sw-73,34,sw-38,68,9);
        text(c,"×",sw-64,58,23,0xfff5dfa7,false);
        mapFrame.set(44,102,sw-353,sh-87);
        if (bigImage==null) {
            bigImage=Bitmap.createBitmap(160,112,Bitmap.Config.RGB_565);
            Canvas mc=new Canvas(bigImage);
            p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);
            for(int y=0;y<112;y++)for(int x=0;x<160;x++) {
                World.Tile tile=world.preview(Math.round(-105+x*1.4f),Math.round(-68+y*1.4f));
                p.setColor(tile.road?0xffc1ae83:Art.groundColor(tile));
                mc.drawPoint(x,y,p);
            }
        }
        p.setColor(Color.WHITE);p.setAlpha(255);p.setFilterBitmap(true);
        c.drawBitmap(bigImage,null,mapFrame,p);
        for(int i=0;i<WarpPoint.ALL.size();i++) {
            WarpPoint point=WarpPoint.ALL.get(i);
            float mx=mapX(point.x),my=mapY(point.y);
            p.setColor(0x55323e35);c.drawCircle(mx+1,my+2,8,p);
            p.setColor(point.color);c.drawCircle(mx,my,i==selectedWarp?7:4.5f,p);
            if(i==selectedWarp) {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xffffe6aa);
                c.drawCircle(mx,my,11,p);p.setStyle(Paint.Style.FILL);
            }
            text(c,String.valueOf(i+1),mx-3,my+3,9,0xff273d3a,true);
            text(c,point.name,mx+10,my-8,10,0xffffefd0,false);
        }
        if(mapFrame.contains(mapX(playerX),mapY(playerY))) {
            float mx=mapX(playerX),my=mapY(playerY);
            p.setColor(0xfffcf7db);c.drawCircle(mx,my,3,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);
            c.drawCircle(mx,my,13,p);p.setStyle(Paint.Style.FILL);
        }
        text(c,"◇ 旅の門     ◎ あなた    ·    門はミニマップにも表示されます",
                45,sh-63,10,0xffd4d8bb,false);
        text(c,"THE SEVEN CHAPTERS",sw-326,108,10,0xffb2c4b8,true);
        float rowH=(sh-208)/7f;
        for(int i=0;i<WarpPoint.ALL.size();i++) {
            WarpPoint point=WarpPoint.ALL.get(i);
            float y=118+i*rowH;
            if(i==selectedWarp) {
                p.setColor(0x80597565);c.drawRoundRect(sw-331,y,sw-39,y+rowH-4,8,8,p);
            }
            p.setColor(point.color);c.drawCircle(sw-312,y+16,5,p);
            text(c,(i+1)+"  "+point.name,sw-296,y+18,14,0xfff5e5bf,true);
            text(c,point.description,sw-296,y+32,10,0xffbdcfc3,false);
        }
        panel(c,sw-331,sh-78,sw-39,sh-36,10);
        text(c,"この門へワープ  →",sw-287,sh-51,17,0xffffe2a2,true);
        c.restore();
    }

    private float mapX(float x) { return mapFrame.left+(x+105)/224f*mapFrame.width(); }
    private float mapY(float y) { return mapFrame.top+(y+68)/156.8f*mapFrame.height(); }

    private void mapTouch(float x,float y,float sw,float sh) {
        if(x>sw-80 && y<85) { mapOpen=false;clearInput();return; }
        float rowH=(sh-208)/7f;
        if(x>=sw-331 && x<=sw-39 && y>=118 && y<118+rowH*7) {
            selectedWarp=Math.min(6,(int)((y-118)/rowH));
        } else if(x>=sw-331 && x<=sw-39 && y>=sh-78 && y<=sh-36) {
            if(travel.start(selectedWarp)) { mapOpen=false;clearInput(); }
        } else if(mapFrame.contains(x,y)) {
            float best=25*25;
            for(int i=0;i<WarpPoint.ALL.size();i++) {
                WarpPoint point=WarpPoint.ALL.get(i);
                float dx=x-mapX(point.x),dy=y-mapY(point.y),distance=dx*dx+dy*dy;
                if(distance<best) { best=distance;selectedWarp=i; }
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x=event.getX(event.getActionIndex())/uiScale;
        float y=event.getY(event.getActionIndex())/uiScale;
        float sw=getWidth()/uiScale,sh=getHeight()/uiScale;
        int action=event.getActionMasked(), pointer=event.getPointerId(event.getActionIndex());
        if(travel.active()) { clearInput();return true; }
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            if(mapOpen) { mapTouch(x,y,sw,sh);return true; }
            if((x>sw-204 && y<168) || (x>=15 && x<=205 && y>=98 && y<=139)) {
                openMap();return true;
            }
            if(x>sw-66 && y>sh*.42f-47 && y<sh*.42f+82) {
                zoom=clamp(zoom+(y<sh*.42f+16?.13f:-.13f),.62f,1.55f);
                return true;
            }
            if(x<218 && y>sh-215) {
                stickPointer=pointer;
                setStick(x,y,sh);
                return true;
            }
            if(x>sw-181 && y>sh-182) {
                runPointer=pointer;sprint=true;return true;
            }
        } else if(action==MotionEvent.ACTION_MOVE) {
            if(mapOpen)return true;
            if(stickPointer!=-1) {
                int index=event.findPointerIndex(stickPointer);
                if(index>=0)setStick(event.getX(index)/uiScale,event.getY(index)/uiScale,sh);
            }
        } else if(action==MotionEvent.ACTION_UP ||
                  action==MotionEvent.ACTION_POINTER_UP ||
                  action==MotionEvent.ACTION_CANCEL) {
            if(pointer==stickPointer || action==MotionEvent.ACTION_CANCEL) {
                stickPointer=-1;joystickX=joystickY=0;
            }
            if(pointer==runPointer || action==MotionEvent.ACTION_CANCEL) {
                runPointer=-1;sprint=false;
            }
        }
        return true;
    }

    private void setStick(float x,float y,float sh) {
        float dx=x-101,dy=y-(sh-102);
        float len=(float)Math.hypot(dx,dy);
        if(len>57){dx*=57/len;dy*=57/len;}
        joystickX=dx/57;
        joystickY=dy/57;
    }
}

