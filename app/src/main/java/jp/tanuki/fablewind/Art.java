package jp.tanuki.fablewind;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Hand-built vector scenery, given depth by isometric ground, extrusion, roof faces and shadows. */
public final class Art {
    private Art() {}

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
        int v = (t.seed >>> 6) & 7;
        switch(t.biome) {
            case World.FOREST: return rgb(91 + v, 152 + v, 108 + v);
            case World.AUTUMN: return rgb(166 + v, 156 + v, 92 + v);
            case World.GOLDEN: return rgb(202 + v, 189 + v, 111 + v);
            case World.MOUNTAIN: return rgb(146 + v, 150 + v, 138 + v);
            case World.SNOW: return rgb(224 + v, 232 + v, 221 + v);
            case World.WATER: return rgb(73 + v, 156 + v, 186 + v);
            case World.TOWN: return rgb(166 + v, 185 + v, 132 + v);
            default: return rgb(130 + v, 186 + v, 122 + v);
        }
    }

    public static void ground(Canvas c, Paint p, Path path, World.Tile t,
                              float x, float y, float w, float h) {
        int top = groundColor(t);
        int depth = Math.min(17, 3 + (int)(t.height * 4.8f));
        if (t.biome == World.WATER) depth = 2;
        // Edge extrusion only on the two visible faces.
        quad(c,p,path,adjust(top,-42),
                x-w,y,x,y+h,x,y+h+depth,x-w,y+depth);
        quad(c,p,path,adjust(top,-56),
                x,y+h,x+w,y,x+w,y+depth,x,y+h+depth);
        quad(c,p,path,top,
                x,y-h,x+w,y,x,y+h,x-w,y);

        if (t.road) {
            int r = t.biome == World.WATER ? rgb(165,130,89)
                    : t.village ? rgb(207,180,143) : rgb(190,167,117);
            quad(c,p,path,r,
                    x,y-h*.68f,x+w*.67f,y,x,y+h*.68f,x-w*.67f,y);
            if ((t.seed & 3) == 0) {
                p.setColor(adjust(r,-15));
                p.setStrokeWidth(1.15f);
                c.drawLine(x-w*.24f,y-h*.20f,x+w*.22f,y+h*.19f,p);
                c.drawLine(x+w*.13f,y-h*.20f,x-w*.15f,y+h*.18f,p);
            }
        } else if (t.biome == World.WATER) {
            p.setColor(0x7ad6edf0);
            p.setStrokeWidth(2);
            float side = (t.seed & 3) * .09f;
            c.drawLine(x-w*.35f+side,y,x-w*.04f+side,y-h*.10f,p);
            c.drawLine(x+w*.07f,y+h*.20f,x+w*.36f,y+h*.06f,p);
        } else if ((t.seed & 15) < 3 && t.object == World.NONE) {
            p.setColor(adjust(top,18));
            p.setStrokeWidth(1.8f);
            float ox = ((t.seed >>> 9) & 15) / 16f * w*.55f - w*.27f;
            c.drawLine(x+ox,y-3,x+ox-2,y-8,p);
            c.drawLine(x+ox,y-3,x+ox+3,y-7,p);
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
            case World.CASTLE: castle(c,p,path); break;
            case World.WINDMILL: windmill(c,p,path,seconds); break;
            case World.TOWER: tower(c,p,path); break;
            case World.RUINS: ruins(c,p,path); break;
            case World.SHRINE: shrine(c,p,path); break;
        }
        c.restore();
    }

    private static void shadow(Canvas c, Paint p, float radius) {
        oval(c,p,0x483b5242,-radius,-5,radius,10);
    }

    private static void tree(Canvas c, Paint p, Path path, int seed, int biome) {
        float size = .84f + ((seed >>> 12) & 7) * .065f;
        c.save();
        c.scale(size,size);
        shadow(c,p,17);
        p.setColor(0xff725340); c.drawRect(-4,-31,4,2,p);
        p.setColor(0xff987054); c.drawRect(-3,-31,0,1,p);
        int dark = biome == World.AUTUMN ? 0xffa35c37 : 0xff386b4a;
        int mid = biome == World.AUTUMN ? 0xffcf8844 : 0xff4d9560;
        int light = biome == World.AUTUMN ? 0xfff2bd69 : 0xff80bc72;
        oval(c,p,dark,-25,-56,17,-18);
        oval(c,p,mid,-18,-69,24,-28);
        oval(c,p,light,-25,-62,6,-39);
        oval(c,p,adjust(light,7),-7,-67,16,-49);
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
        quad(c,p,path,inn ? 0xfff3d9a0 : 0xffe6c69d,
                -5,-51,26,-39,26,4,-5,-8);
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
        // Layered towers and ramparts, designed as a miniature silhouette.
        quad(c,p,path,0xff78868a,-32,-61,26,-61,26,1,-32,1);
        quad(c,p,path,0xffb0b9ac,26,-61,38,-68,38,-8,26,1);
        quad(c,p,path,0xffd6cbbb,-32,-61,26,-61,26,-9,-32,-9);
        for (int i=-3;i<=2;i++) {
            p.setColor(0xffd6cbbb); c.drawRect(i*10+1,-70,i*10+8,-58,p);
        }
        p.setColor(0xff4b575b); c.drawRoundRect(-9,-27,8,-8,7,7,p);
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
        p.setColor(0xffd1c8b7); c.drawRect(x-10,y,x+11,2,p);
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
