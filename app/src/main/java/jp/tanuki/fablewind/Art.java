package jp.tanuki.fablewind;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Hand-built vector scenery, given depth by isometric ground, extrusion, roof faces and shadows. */
public final class Art {
    private Art() {}
    private static final Shader LEAVES = new LinearGradient(-20,-75,25,-20,
            0xff8aa46a,0xff244c42,Shader.TileMode.CLAMP);
    private static final Shader AMBER = new LinearGradient(-20,-75,25,-20,
            0xffefbc72,0xff984b36,Shader.TileMode.CLAMP);
    private static final Shader PLASTER = new LinearGradient(-22,-60,24,3,
            0xfff0dfb8,0xffaf9274,Shader.TileMode.CLAMP);
    private static final Shader STONE = new LinearGradient(-42,-125,37,0,
            0xffded8c4,0xff8b998e,Shader.TileMode.CLAMP);
    private static final Shader GLOW = new RadialGradient(0,0,1,
            new int[]{0x99ffdf9c,0x28ffd783,0x00ffd783},null,Shader.TileMode.CLAMP);

    private static int rgb(int r, int g, int b) {
        return Color.rgb(Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
    }
    private static int adjust(int color, int offset) {
        return rgb(Color.red(color) + offset,
                Color.green(color) + offset, Color.blue(color) + offset);
    }

    private static void quad(Canvas c, Paint p, Path path, int color,
            float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3) {
        path.reset();
        path.moveTo(x0,y0);
        path.lineTo(x1,y1);
        path.lineTo(x2,y2);
        path.lineTo(x3,y3);
        path.close();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path,p);
    }

    private static void tri(Canvas c, Paint p, Path path, int color,
            float x0,float y0,float x1,float y1,float x2,float y2) {
        path.reset();
        path.moveTo(x0,y0);
        path.lineTo(x1,y1);
        path.lineTo(x2,y2);
        path.close();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path,p);
    }

    private static void oval(Canvas c, Paint p, int color,
            float x0, float y0, float x1, float y1) {
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        c.drawOval(x0,y0,x1,y1,p);
    }

    public static int groundColor(World.Tile t) {
        int v = (t.seed >>> 6) & 3;
        if(t.region==5) return rgb(64+v,110+v,91+v);
        if(t.region==4) return rgb(99+v,130+v,117+v);
        if(t.region==1) return rgb(125+v,151+v,109+v);
        if(t.region==3 && t.biome!=World.WATER) return rgb(106+v,137+v,119+v);
        switch(t.biome) {
            case World.FOREST: return rgb(83 + v, 127 + v, 94 + v);
            case World.AUTUMN: return rgb(162 + v, 137 + v, 83 + v);
            case World.GOLDEN: return rgb(188 + v, 169 + v, 99 + v);
            case World.MOUNTAIN: return rgb(146 + v, 150 + v, 138 + v);
            case World.SNOW: return rgb(224 + v, 232 + v, 221 + v);
            case World.WATER: return rgb(70 + v, 130 + v, 144 + v);
            case World.TOWN: return rgb(133 + v, 153 + v, 103 + v);
            default: return rgb(116 + v, 151 + v, 96 + v);
        }
    }

    /** Shared corner heights remove the old floating, checkerboard tile edges. */
    public static void ground(Canvas c, Paint p, Path path, World.Tile t,
            float x, float y, float w, float h, float topY, float rightY,
            float bottomY, float leftY, float seconds) {
        int top = groundColor(t);
        int slope = Math.round((leftY-rightY)*.9f);
        p.setAntiAlias(false);
        quad(c,p,path,adjust(top,Math.max(-16,Math.min(16,slope))),
                x,topY,x+w,rightY,x,bottomY,x-w,leftY);
        if (t.road) {
            int roadColor=t.biome==World.WATER ? 0xff9e7953 :
                    t.region==1 || t.village ? 0xffbaac90 : 0xffb8a077;
            quad(c,p,path,roadColor,x,topY,x+w,rightY,x,bottomY,x-w,leftY);
            p.setAntiAlias(true);
            p.setStrokeWidth(1);
            p.setColor(adjust(roadColor,-22));
            for(int i=0;i<4;i++) {
                float ox=(((t.seed >>> (i*4))&15)-7.5f)*w*.05f;
                float oy=(((t.seed >>> (i*3+3))&7)-3.5f)*h*.11f;
                c.drawLine(x+ox-3,y+oy,x+ox+3,y+oy+1,p);
            }
            if(t.biome==World.WATER) {
                p.setStrokeWidth(2);p.setColor(0xff5e5848);
                for(int i=-2;i<=2;i++)c.drawLine(x-w*.48f+i*4,y-h*.25f+i*2,
                        x+w*.33f+i*4,y+h*.48f+i*2,p);
            }
        } else if (t.biome == World.WATER) {
            p.setAntiAlias(true);
            p.setColor(0x68cee7da);p.setStrokeWidth(1.3f);
            float drift=(float)Math.sin(seconds*1.5+(t.seed&31))*w*.08f;
            c.drawLine(x-w*.38f+drift,y+2,x-w*.03f+drift,y-h*.13f,p);
            c.drawLine(x+w*.06f,y+h*.23f,x+w*.39f,y+h*.08f,p);
            if((t.seed&7)==0) {
                oval(c,p,0xff4b7a61,x-w*.25f,y-h*.12f,x+w*.03f,y+h*.1f);
                oval(c,p,0xffedc4b0,x-w*.14f,y-h*.14f,x-w*.04f,y-h*.03f);
            }
        } else {
            p.setAntiAlias(true);
            // Restrained, deterministic clusters of grass, earth and seed heads.
            for(int i=0;i<5;i++) {
                int seed=World.hash(t.seed,i,9);
                float ox=((seed&255)/255f-.5f)*w*1.2f;
                float oy=(((seed>>>8)&255)/255f-.5f)*h*.65f;
                float sway=(float)Math.sin(seconds*1.7+(seed&15))*1.3f;
                p.setColor(adjust(top,(seed&1)==0?16:-18));p.setStrokeWidth(.9f);
                float blade=t.biome==World.GOLDEN?8:t.region==3?11:3+(seed&3);
                c.drawLine(x+ox,y+oy,x+ox-1+sway,y+oy-blade,p);
                c.drawLine(x+ox,y+oy,x+ox+3+sway,y+oy-blade*.7f,p);
                if(t.biome==World.GOLDEN) {
                    oval(c,p,0xffe2c77f,x+ox-2+sway,y+oy-blade-3,
                            x+ox+1+sway,y+oy-blade+1);
                } else if((seed&15)==0) {
                    oval(c,p,t.region==1?0xffbb6077:0xffe2d1a0,
                            x+ox-1,y+oy-5,x+ox+2,y+oy-2);
                }
            }
        }
    }

    public static void object(Canvas c, Paint p, Path path, World.Tile tile,
                              float x, float y, float tileW, float seconds) {
        if (tile.object == World.NONE) return;
        c.save();
        c.translate(x,y);
        c.scale(tileW / 44f, tileW / 44f);
        int seed = tile.seed;
        switch(tile.object) {
            case World.TREE: tree(c,p,path,seed,tile.biome); break;
            case World.PINE: pine(c,p,path,seed); break;
            case World.HOUSE: house(c,p,path,seed,false); break;
            case World.INN: house(c,p,path,seed,true); break;
            case World.ROCK: rock(c,p,path,seed); break;
            case World.FLOWER: flower(c,p,seed); break;
            case World.CASTLE: c.scale(2.3f,2.3f);castle(c,p,path); break;
            case World.WINDMILL: c.scale(1.8f,1.8f);windmill(c,p,path,seconds); break;
            case World.TOWER: c.scale(2.0f,2.0f);tower(c,p,path); break;
            case World.RUINS: c.scale(1.9f,1.9f);ruins(c,p,path); break;
            case World.SHRINE: shrine(c,p,path); break;
            case World.MUSHROOM: mushroom(c,p,path,seed,seconds); break;
            case World.CRYSTAL: crystal(c,p,path,seconds); break;
            case World.LANTERN: lantern(c,p,path); break;
            case World.MARKET: market(c,p,path,seed); break;
            case World.WELL: well(c,p,path); break;
            case World.GIANT_TREE: giantTree(c,p,path,seconds); break;
            case World.PUMPKIN: pumpkin(c,p,seed); break;
            case World.WARP: c.scale(1.45f,1.45f);portal(c,p,path,seconds); break;
        }
        c.restore();
    }

    private static void shadow(Canvas c, Paint p, float radius) {
        oval(c,p,0x18303d32,-radius,-6,radius*1.8f,13);
        oval(c,p,0x22303d32,-radius*.65f,-3,radius*1.25f,8);
    }

    private static void tree(Canvas c, Paint p, Path path, int seed, int biome) {
        float size = .84f + ((seed >>> 12) & 7) * .065f;
        c.save();
        c.scale(size,size);
        shadow(c,p,17);
        quad(c,p,path,0xff644c39,-5,2,-2,-49,4,-47,6,2);
        p.setColor(0xffa08052);p.setStrokeWidth(1.5f);
        c.drawLine(-2,0,-1,-38,p);
        c.drawLine(0,-25,-13,-43,p);c.drawLine(2,-28,16,-48,p);
        int dark = biome == World.AUTUMN ? 0xffa35c37 : 0xff386b4a;
        int mid = biome == World.AUTUMN ? 0xffcf8844 : 0xff4d9560;
        int light = biome == World.AUTUMN ? 0xfff2bd69 : 0xff80bc72;
        oval(c,p,dark,-25,-56,17,-18);
        oval(c,p,mid,-18,-69,24,-28);
        oval(c,p,light,-25,-62,6,-39);
        oval(c,p,adjust(light,7),-7,-67,16,-49);
        p.setShader(biome==World.AUTUMN?AMBER:LEAVES);
        path.reset();path.moveTo(-21,-28);
        path.cubicTo(-37,-38,-29,-53,-24,-55);
        path.cubicTo(-32,-68,-17,-75,-10,-70);
        path.cubicTo(-7,-86,14,-79,17,-66);
        path.cubicTo(31,-69,34,-51,25,-46);
        path.cubicTo(35,-31,15,-20,8,-26);
        path.cubicTo(1,-18,-12,-20,-21,-28);path.close();
        c.drawPath(path,p);
        p.setShader(null);
        for(int i=0;i<17;i++) {
            int v=World.hash(seed,i,8);
            float ox=((v&255)/255f-.5f)*38;
            float oy=-31-((v>>>8)&255)/255f*36;
            oval(c,p,(v&1)==0?0x228fd080:0x18303d32,ox-3,oy-2,ox+4,oy+3);
        }
        c.restore();
    }

    private static void pine(Canvas c, Paint p, Path path, int seed) {
        c.save();
        c.scale(.80f + ((seed >>> 4) & 7) * .05f,
                .80f + ((seed >>> 4) & 7) * .05f);
        shadow(c,p,13);
        p.setColor(0xff805d43); c.drawRect(-3,-29,3,3,p);
        tri(c,p,path,0xff234f52,-23,-19,0,-76,23,-19);
        tri(c,p,path,0xff356b60,-19,-31,0,-79,11,-31);
        tri(c,p,path,0xff4e8572,-11,-54,0,-79,9,-54);
        c.restore();
    }

    private static void house(Canvas c, Paint p, Path path, int seed, boolean inn) {
        c.save();
        c.scale(.87f + ((seed >>> 9) & 3)*.055f,
                .87f + ((seed >>> 9) & 3)*.055f);
        shadow(c,p,28);
        // West wall and south-facing facade with different lighting.
        quad(c,p,path,0xffad8b70,-25,-44,-5,-51,-5,-8,-25,-1);
        p.setShader(PLASTER);
        quad(c,p,path,inn ? 0xfff3d9a0 : 0xffe6c69d,
                -5,-51,26,-39,26,4,-5,-8);
        p.setShader(null);
        quad(c,p,path,0xffb75f4f,-30,-48,-9,-72,9,-71,28,-46);
        tri(c,p,path,0xff864a48,-9,-72,28,-46,9,-71);
        quad(c,p,path,0xffd16d54,-30,-48,9,-71,27,-46,-5,-53);
        p.setColor(0xff6d4b3b); c.drawRect(9,-21,20,0,p);
        p.setColor(0xfff5ce7b); c.drawRect(11,-18,18,-11,p);
        p.setColor(0xff8e624c); c.drawRect(12,-18,13,-11,p);
        p.setColor(0xff594c42); c.drawRect(-3,-26,7,-13,p);
        p.setColor(0xffffd987); c.drawRect(-1,-24,5,-15,p);
        p.setColor(0xff76583b); c.drawRect(-18,-32,-11,-20,p);
        p.setColor(0xffffdfa0); c.drawRect(-17,-30,-13,-23,p);
        p.setColor(0xffd3b17c); c.drawRect(8,-45,14,-43,p);
        if (inn) {
            // Hanging tavern sign, made entirely from original primitives.
            p.setStrokeWidth(2); p.setColor(0xff76533f);
            c.drawLine(22,-36,33,-39,p); c.drawLine(30,-39,30,-32,p);
            oval(c,p,0xff52765b,24,-32,37,-19);
            p.setColor(0xfff6df9c); p.setTextSize(8); p.setTypeface(null);
            c.drawText("★",26,-22,p);
        } else {
            p.setColor(0xffb4a277); c.drawRect(-13,-62,-9,-52,p);
        }
        // Plaster, timber framing, roof tiles and a little chimney smoke.
        p.setColor(0xff87674e);p.setStrokeWidth(2);
        c.drawLine(-4,-46,-4,-9,p);c.drawLine(25,-38,25,3,p);
        c.drawLine(-4,-32,25,-21,p);
        c.drawLine(-4,-12,25,0,p);
        p.setStrokeWidth(.8f);p.setColor(0x6583483d);
        for(int i=0;i<5;i++)c.drawLine(-25+i*5,-49-i*3,0+i*5,-47+i*.5f,p);
        oval(c,p,0x28efddc0,-17,-76,-9,-67);
        oval(c,p,0x18efddc0,-20,-86,-9,-74);
        p.setColor(0xff57734c);c.drawRect(-4,-13,8,-10,p);
        for(int i=0;i<3;i++)oval(c,p,0xffc77e80,-3+i*4,-15,1+i*4,-11);
        c.restore();
    }

    private static void rock(Canvas c, Paint p, Path path, int seed) {
        shadow(c,p,16);
        tri(c,p,path,0xff748184,-17,1,-7,-18,10,-17);
        tri(c,p,path,0xffb5b6ac,-17,1,10,-17,16,0);
        tri(c,p,path,0xffd0c8b5,-7,-18,10,-17,0,-6);
    }

    private static void flower(Canvas c, Paint p, int seed) {
        p.setStrokeWidth(2); p.setColor(0xff4f8c47);
        c.drawLine(-4,2,-4,-13,p);
        int color = (seed & 1) == 0 ? 0xffffd6b3 : 0xfff7e398;
        oval(c,p,color,-9,-18,1,-9);
        oval(c,p,color,-4,-20,5,-10);
        oval(c,p,0xffc77b55,-5,-15,-1,-11);
        c.drawLine(6,3,7,-9,p);
        oval(c,p,0xfff3eded,3,-13,11,-7);
    }

    private static void castle(Canvas c, Paint p, Path path) {
        shadow(c,p,53);
        p.setShader(STONE);
        // Layered towers and ramparts, designed as a miniature silhouette.
        quad(c,p,path,0xff78868a,-32,-61,26,-61,26,1,-32,1);
        quad(c,p,path,0xffb0b9ac,26,-61,38,-68,38,-8,26,1);
        quad(c,p,path,0xffd6cbbb,-32,-61,26,-61,26,-9,-32,-9);
        p.setShader(null);
        for (int i=-3;i<=2;i++) {
            p.setColor(0xffd6cbbb); c.drawRect(i*10+1,-70,i*10+8,-58,p);
        }
        p.setColor(0xff4b575b); c.drawRoundRect(-9,-27,8,-8,7,7,p);
        p.setColor(0x448a846e);p.setStrokeWidth(.7f);
        for(int row=0;row<5;row++) {
            float yy=-55+row*9;
            c.drawLine(-30,yy,24,yy,p);
            for(int col=0;col<4;col++)c.drawLine(-25+col*15+(row%2)*6,yy,
                    -25+col*15+(row%2)*6,yy+8,p);
        }
        p.setColor(0xfff1d896); c.drawRect(-21,-49,-15,-36,p);
        c.drawRect(13,-49,19,-36,p);
        castleTower(c,p,path,-38,-68);
        castleTower(c,p,path,24,-78);
        tri(c,p,path,0xff8d4e61,-22,-92,2,-143,26,-92);
        tri(c,p,path,0xffc76f68,-22,-92,2,-143,7,-92);
        p.setColor(0xff594d57); c.drawRect(0,-156,2,-138,p);
        tri(c,p,path,0xffe5c16d,2,-155,23,-148,2,-143);
    }

    private static void castleTower(Canvas c, Paint p, Path path, int x,int y) {
        p.setShader(STONE);
        p.setColor(0xffd1c8b7); c.drawRect(x-10,y,x+11,2,p);
        p.setShader(null);
        p.setColor(0xff6c8290); c.drawRect(x-8,y+15,x-3,y+31,p);
        tri(c,p,path,0xffa15761,x-17,y,x,y-35,x+18,y);
        tri(c,p,path,0xffd87972,x-17,y,x,y-35,x+2,y);
    }

    private static void windmill(Canvas c, Paint p, Path path, float seconds) {
        shadow(c,p,35);
        quad(c,p,path,0xffa98d73,-19,-11,-12,-77,20,-77,27,-9);
        quad(c,p,path,0xffead3ad,-12,-77,20,-77,27,-9,-19,-11);
        tri(c,p,path,0xffb46457,-25,-73,2,-102,28,-74);
        tri(c,p,path,0xffdf8a64,-25,-73,2,-102,5,-74);
        p.setColor(0xff685047); c.drawRoundRect(-4,-31,7,-10,5,5,p);
        c.save();
        c.translate(2,-75);
        c.rotate(seconds*19f);
        for (int i=0;i<4;i++) {
            c.rotate(90);
            p.setColor(0xff705e53); c.drawRect(-2,-39,2,0,p);
            quad(c,p,path,0xffefe4be,-12,-40,1,-40,1,-15,-12,-18);
            p.setColor(0xffae9b73); p.setStrokeWidth(1);
            c.drawLine(-12,-25,1,-25,p);
        }
        oval(c,p,0xff72584b,-5,-5,5,5);
        c.restore();
    }

    private static void tower(Canvas c, Paint p, Path path) {
        shadow(c,p,31);
        quad(c,p,path,0xffa3afa6,-14,-70,18,-70,26,1,-20,1);
        quad(c,p,path,0xffe9ddbd,-14,-70,18,-70,25,-14,-19,-14);
        tri(c,p,path,0xff4e6a83,-24,-72,2,-111,27,-72);
        tri(c,p,path,0xff7a93a3,-24,-72,2,-111,5,-72);
        p.setColor(0xff445a62); c.drawRoundRect(-4,-62,6,-44,4,4,p);
        oval(c,p,0xffffdf91,-5,-63,7,-50);
        p.setColor(0xff86664d); c.drawRect(-3,-28,9,0,p);
    }

    private static void ruins(Canvas c, Paint p, Path path) {
        shadow(c,p,35);
        p.setColor(0xffb9b49b);
        c.drawRect(-27,-56,-15,-2,p);
        c.drawRect(13,-61,26,-2,p);
        c.drawRect(-28,-57,25,-47,p);
        p.setColor(0xff7e8c77); c.drawRect(-30,-60,-13,-54,p);
        c.drawRect(10,-65,28,-58,p);
        oval(c,p,0xff67916e,-23,-12,-5,2);
        oval(c,p,0xff86a87d,6,-11,21,4);
        p.setColor(0xffe0d4ae); c.drawRect(-5,-7,8,3,p);
    }

    private static void shrine(Canvas c, Paint p, Path path) {
        shadow(c,p,20);
        p.setColor(0xffe4d2b4); c.drawRect(-18,-39,-12,-1,p);
        c.drawRect(12,-39,18,-1,p);
        quad(c,p,path,0xff6a5867,-27,-42,-12,-54,18,-54,27,-42);
        p.setColor(0xfff0dea2); c.drawRect(-3,-35,3,-9,p);
        c.drawRect(-10,-24,10,-19,p);
    }

    private static void glow(Canvas c, Paint p, float x,float y,float radius) {
        c.save();c.translate(x,y);c.scale(radius,radius);
        p.setColor(0xffffffff);p.setShader(GLOW);c.drawCircle(0,0,1,p);
        p.setShader(null);c.restore();
    }

    private static void portal(Canvas c, Paint p, Path path,float seconds) {
        shadow(c,p,32);
        oval(c,p,0xff7a8577,-29,-13,29,14);
        oval(c,p,0xffbebc9b,-26,-11,26,11);
        oval(c,p,0xff476d70,-21,-8,21,8);
        glow(c,p,0,-7,47);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(0xffeeddab);
        c.drawOval(-22,-9,22,9,p);
        p.setStyle(Paint.Style.FILL);
        quad(c,p,path,0xff6b7d78,-30,2,-27,-56,-20,-54,-19,4);
        quad(c,p,path,0xffabb49a,20,4,21,-54,28,-56,31,2);
        for(int side=-1;side<=1;side+=2) {
            p.setColor(0xffe7cb8a);p.setStrokeWidth(1.4f);
            c.drawLine(side*25,-42,side*25,-25,p);
            c.drawLine(side*22,-35,side*28,-35,p);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x99f5dfa0);
        path.reset();path.moveTo(-24,-53);path.cubicTo(-17,-83,17,-83,25,-53);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<8;i++) {
            float a=seconds*.8f+i*.785f;
            float px=(float)Math.cos(a)*21,py=-24+(float)Math.sin(a)*27;
            oval(c,p,0xffe9e9ae,px-1,py-1,px+1.5f,py+1.5f);
        }
    }

    private static void mushroom(Canvas c,Paint p,Path path,int seed,float seconds) {
        c.save();float size=.55f+(seed&7)*.1f;c.scale(size,size);
        shadow(c,p,18);
        quad(c,p,path,0xffc2c6a6,-5,1,-3,-27,4,-27,7,1);
        oval(c,p,0xff435f61,-22,-35,23,-18);
        path.reset();path.moveTo(-23,-28);path.cubicTo(-21,-61,19,-61,24,-28);
        path.cubicTo(13,-20,-13,-20,-23,-28);path.close();
        p.setColor(0xff729e9a);c.drawPath(path,p);
        oval(c,p,0xffbfd8b8,-15,-40,-8,-35);oval(c,p,0xffbfd8b8,3,-43,10,-38);
        oval(c,p,0xffd6e6bb,13,-32,18,-28);glow(c,p,0,-27,21);
        c.restore();
    }

    private static void crystal(Canvas c,Paint p,Path path,float seconds) {
        shadow(c,p,14);glow(c,p,0,-13,24);
        quad(c,p,path,0xff729dba,-8,-3,-12,-25,-3,-44,2,-22);
        tri(c,p,path,0xffbdded9,-3,-44,8,-23,-8,-3);
        tri(c,p,path,0xff526b9c,-8,-3,8,-23,5,1);
        tri(c,p,path,0xff8abdc7,6,1,10,-24,18,-15);
        oval(c,p,0xaafff1ca,-5,-30,-3,-28);
    }

    private static void lantern(Canvas c,Paint p,Path path) {
        shadow(c,p,9);p.setStrokeWidth(3);p.setColor(0xff62594c);
        c.drawLine(0,2,0,-53,p);c.drawLine(0,-53,15,-53,p);
        c.drawLine(15,-53,15,-47,p);glow(c,p,15,-37,30);
        quad(c,p,path,0xffd5af67,9,-45,21,-45,19,-30,11,-30);
        tri(c,p,path,0xff535b50,6,-45,15,-53,24,-45);
        p.setColor(0xffffe0a1);c.drawRect(12,-43,18,-33,p);
    }

    private static void market(Canvas c,Paint p,Path path,int seed) {
        shadow(c,p,31);p.setColor(0xff796049);
        c.drawRect(-25,-43,-22,3,p);c.drawRect(24,-38,27,6,p);
        quad(c,p,path,0xffa17954,-27,-15,28,-10,28,5,-27,0);
        quad(c,p,path,0xffe5cc95,-28,-18,26,-13,33,-21,-20,-26);
        for(int i=0;i<6;i++)quad(c,p,path,i%2==0?0xffb85c52:0xffe9d7ac,
                -30+i*10,-43,-20+i*10,-43,-18+i*10,-29,-28+i*10,-29);
        for(int i=0;i<8;i++)oval(c,p,i%2==0?0xffb77548:0xff728351,
                -21+i*6,-23,-16+i*6,-18);
    }

    private static void well(Canvas c,Paint p,Path path) {
        shadow(c,p,29);oval(c,p,0xff7b8172,-24,-16,24,10);
        oval(c,p,0xffbbb79d,-25,-21,25,-1);oval(c,p,0xff476c70,-17,-17,17,-6);
        p.setColor(0xff705c49);c.drawRect(-22,-55,-18,-8,p);c.drawRect(18,-55,22,-8,p);
        quad(c,p,path,0xffa65f4e,-31,-51,-7,-73,31,-51,8,-40);
        p.setStrokeWidth(1);p.setColor(0xffd8bf8a);c.drawLine(0,-52,0,-9,p);
    }

    private static void pumpkin(Canvas c,Paint p,int seed) {
        shadow(c,p,12);
        oval(c,p,0xffaa6639,-13,-16,12,3);oval(c,p,0xffd89347,-9,-17,5,3);
        oval(c,p,0xffe2a650,-4,-16,3,2);
        p.setStrokeWidth(3);p.setColor(0xff62734b);c.drawLine(0,-15,3,-23,p);
        oval(c,p,0xff76824c,2,-22,13,-18);
    }

    private static void giantTree(Canvas c,Paint p,Path path,float seconds) {
        shadow(c,p,65);
        path.reset();path.moveTo(-35,7);path.cubicTo(-8,-46,-32,-75,-15,-149);
        path.lineTo(16,-149);path.cubicTo(9,-68,20,-18,46,9);
        path.cubicTo(18,0,-6,-5,-35,7);path.close();
        p.setColor(0xff685541);c.drawPath(path,p);
        p.setColor(0xffb29260);p.setStrokeWidth(3);
        c.drawLine(-8,-8,-8,-116,p);c.drawLine(5,-23,9,-99,p);
        c.save();c.translate(0,-77);c.scale(2.9f,2.3f);tree(c,p,path,1,World.FOREST);c.restore();
        p.setColor(0xff344c44);c.drawRoundRect(-9,-35,13,1,12,12,p);
        glow(c,p,2,-19,32);
        p.setColor(0xffe4ca8b);c.drawRoundRect(-4,-28,8,-4,6,6,p);
        for(int i=0;i<4;i++) {
            float x=-38+i*24,y=-72-(float)Math.sin(i)*12;
            p.setStrokeWidth(1);p.setColor(0xff8e9c6c);c.drawLine(x,y-42,x,y,p);
            glow(c,p,x,y,18);oval(c,p,0xffeed49b,x-3,y-5,x+3,y+3);
        }
        c.save();c.translate(33,4);mushroom(c,p,path,2,seconds);c.restore();
    }

    /** Original wandering fox with a wine-colored hat and teal cloak. */
    public static void traveler(Canvas c, Paint p, Path path, float x,float y,
                                float size,float seconds, boolean running,
                                float directionX) {
        c.save();
        c.translate(x,y);
        c.scale(size,size);
        shadow(c,p,18);
        float stride = (float) Math.sin(seconds*(running ? 14f : 9f))*5f;
        p.setColor(0xff533f40);
        c.drawRoundRect(-11+stride,-15,-3+stride,4,3,3,p);
        c.drawRoundRect(3-stride,-15,11-stride,4,3,3,p);
        // Sweep of the cloak and a russet tail
        tri(c,p,path,0xffb26a4b,12,-21,30,-30,24,-10);
        tri(c,p,path,0xffe9bd83,24,-10,30,-30,30,-17);
        quad(c,p,path,0xff245d69,-14,-43,13,-43,19,-11,-19,-11);
        tri(c,p,path,0xff398b8c,-14,-43,1,-45,-19,-11);
        p.setColor(0xffc6a56d); c.drawRect(-3,-24,12,-21,p);
        // Face, cream muzzle, amber ears
        tri(c,p,path,0xffaa664d,-14,-49,-9,-70,-1,-54);
        tri(c,p,path,0xffaa664d,3,-54,12,-70,16,-49);
        oval(c,p,0xffc9865b,-15,-66,16,-38);
        oval(c,p,0xfff2d2a4,-10,-51,11,-39);
        oval(c,p,0xff312e37,-7,-57,-3,-52);
        oval(c,p,0xff312e37,5,-57,9,-52);
        oval(c,p,0xff5c3b3d,-2,-46,3,-42);
        // Burgundy wide-brimmed traveling hat, feather (not a copied character).
        oval(c,p,0xff714f58,-21,-72,23,-62);
        quad(c,p,path,0xff8d5164,-13,-67,-10,-85,13,-86,16,-67);
        tri(c,p,path,0xffac6c72,-10,-85,13,-86,-1,-91);
        p.setColor(0xffe9c67e); c.drawRect(-10,-70,15,-68,p);
        tri(c,p,path,0xff9fbba4,9,-82,26,-103,14,-81);
        tri(c,p,path,0xffe6e2bb,14,-81,26,-103,20,-86);
        c.restore();
    }
}

