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
    private int bigX = Integer.MIN_VALUE, bigY = Integer.MIN_VALUE;
    private float saveCountdown;
    private String lastPlace = "";

    public GameView(Context context) {
        super(context);
        saves = context.getSharedPreferences("fablewind_world", Context.MODE_PRIVATE);
        playerX = saves.getFloat("x", 0);
        playerY = saves.getFloat("y", 0);
        cameraX = playerX;
        cameraY = playerY;
        zoom = saves.getFloat("zoom", 1f);
        clock = saves.getFloat("clock", 144);
        setFocusable(true);
    }

    public void pause() {
        paused = true;
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
        int width = getWidth(), height = getHeight();
        if (width == 0 || height == 0) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? .016f : clamp((now-lastFrame) / 1_000_000_000f,0f,.05f);
        lastFrame = now;
        if (!paused) update(dt);

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
        int playerLayer = (int)Math.floor(playerX+playerY)+2;
        boolean playerDrawn = false;
        for (int sum=minX+minY; sum<=maxX+maxY; sum++) {
            int from = Math.max(minX,sum-maxY);
            int to = Math.min(maxX,sum-minY);
            for (int x=from;x<=to;x++) {
                int y=sum-x;
                float screenX=centerX+(x-y-cameraX+cameraY)*w;
                float approxY=centerY+(x+y-cameraX-cameraY)*h;
                if(screenX < -220*zoom || screenX > width+220*zoom ||
                   approxY < -250*zoom || approxY > height+210*zoom) continue;
                World.Tile tile=world.tile(x,y);
                float screenY=approxY-(tile.height-focusHeight)*groundLift;
                Art.ground(c,p,path,tile,screenX,screenY,w,h);
                Art.object(c,p,path,tile,screenX,screenY,w,clock);
            }
            if (!playerDrawn && sum>=playerLayer) {
                drawPlayer(c,w,h,groundLift,focusHeight,centerX,centerY);
                playerDrawn = true;
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
        drawFireflies(c,width,height,sun);
        drawHud(c,width,height);
        if (mapOpen) drawLargeMap(c,width,height);
        if (!paused) postInvalidateOnAnimation();
    }

    private void update(float dt) {
        clock+=dt;
        if (!mapOpen && (Math.abs(joystickX)+Math.abs(joystickY)>.04f)) {
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
        if(miniImage==null || Math.abs(miniX-Math.round(playerX))>10 ||
                Math.abs(miniY-Math.round(playerY))>10) {
            miniX=Math.round(playerX);miniY=Math.round(playerY);
            if(miniImage!=null)miniImage.recycle();
            miniImage=Bitmap.createBitmap(72,56,Bitmap.Config.RGB_565);
            Canvas mc=new Canvas(miniImage);
            p.setStyle(Paint.Style.FILL);
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
        p.setColor(0xfff2e6b9);
        c.drawCircle(left+width/2,top+height/2,4,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);
        c.drawCircle(left+width/2,top+height/2,7,p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawLargeMap(Canvas c,int width,int height) {
        c.save();
        c.scale(uiScale,uiScale);
        float sw=width/uiScale,sh=height/uiScale;
        float left=sw*.12f,top=sh*.11f,right=sw*.88f,bottom=sh*.90f;
        panel(c,left-16,top-14,right+16,bottom+15,17);
        text(c,"旅人の地図",left+12,top+24,23,0xfff5dfa7,true);
        text(c,"タップして閉じる  |  地形は歩くたびに広がる",left+12,top+45,11,0xffd4e6d8,false);
        float mapTop=top+55,mapBottom=bottom-15;
        if (bigImage==null || Math.abs(bigX-Math.round(playerX))>150 ||
                Math.abs(bigY-Math.round(playerY))>150) {
            bigX=Math.round(playerX);bigY=Math.round(playerY);
            if(bigImage!=null)bigImage.recycle();
            bigImage=Bitmap.createBitmap(128,72,Bitmap.Config.RGB_565);
            Canvas mc=new Canvas(bigImage);
            for(int y=0;y<72;y++)
                for(int x=0;x<128;x++) {
                    World.Tile t=world.preview(bigX+(x-64)*22,bigY+(y-36)*22);
                    p.setColor(Art.groundColor(t));
                    mc.drawPoint(x,y,p);
                }
        }
        RectF frame=new RectF(left+5,mapTop,right-5,mapBottom);
        p.setColor(Color.WHITE);p.setAlpha(255);p.setFilterBitmap(false);
        c.drawBitmap(bigImage,null,frame,p);
        float centerX=(frame.left+frame.right)*.5f;
        float centerY=(frame.top+frame.bottom)*.5f;
        // Exploration landmarks: only markers within this map window are visible.
        marker(c,frame,0,0,0xfff7dfa9,"村");
        marker(c,frame,42,-40,0xffe9ba8f,"城");
        marker(c,frame,27,32,0xffe9ba8f,"風車");
        marker(c,frame,-38,45,0xffe9ba8f,"灯台");
        marker(c,frame,72,22,0xffe9ba8f,"遺跡");
        p.setColor(0xfffcead6);
        c.drawCircle(centerX+(playerX-bigX)/2816f*frame.width(),
                     centerY+(playerY-bigY)/1584f*frame.height(),4,p);
        text(c,"● あなた",left+9,bottom-1,11,0xfff4e4bf,false);
        c.restore();
    }

    private void marker(Canvas c,RectF frame,float x,float y,int color,String title) {
        float mx=frame.centerX()+(x-bigX)/2816f*frame.width();
        float my=frame.centerY()+(y-bigY)/1584f*frame.height();
        if(mx<frame.left+5 || mx>frame.right-5 || my<frame.top+5 || my>frame.bottom-5)return;
        p.setColor(color);c.drawCircle(mx,my,3.5f,p);
        text(c,title,mx+5,my-4,9,0xffffebcc,false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x=event.getX(event.getActionIndex())/uiScale;
        float y=event.getY(event.getActionIndex())/uiScale;
        float sw=getWidth()/uiScale,sh=getHeight()/uiScale;
        int action=event.getActionMasked(), pointer=event.getPointerId(event.getActionIndex());
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            if(mapOpen) {
                mapOpen=false;
                return true;
            }
            if(x>sw-204 && y<168) {
                mapOpen=true;
                joystickX=joystickY=0; sprint=false;
                stickPointer=runPointer=-1;
                return true;
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
