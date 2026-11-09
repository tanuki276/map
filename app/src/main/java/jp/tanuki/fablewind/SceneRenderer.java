package jp.tanuki.fablewind;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Actual GLB geometry on OpenGL ES 2.0; Canvas is used only for the touch HUD. */
public final class SceneRenderer implements GLSurfaceView.Renderer {
    public interface Listener {void ready(SurvivalGame.Snapshot snapshot,String error,int models,int triangles);void save();void overlay();}
    private final Context context;private final SurvivalGame game;private final SurvivalGame.Input input;private final Listener listener;
    private final Map<String,GpuMesh> models=new HashMap<>();
    private final float[] projection=new float[16],view=new float[16],vp=new float[16],model=new float[16];
    private int program,texture,position,normal,uv,color,surface,rig,uModel,uVP,uEye,uFog,uDay,uTime,uAlpha,uMode,uMotion;
    private int hudProgram,hudTexture,hudBuffer,hudPosition,hudUV,hudSampler,hudWidth,hudHeight;
    private int width,height,modelTriangles;private GpuMesh terrain,shadow;
    private int terrainX=Integer.MIN_VALUE,terrainZ=Integer.MIN_VALUE;private long last;private float saveTimer;
    private String error="";private float eyeX,eyeY,eyeZ;private SurvivalGame.Snapshot scene;
    public volatile float cameraYaw=0,cameraDistance=10.5f,cameraPitch=.68f;
    private static final class GpuMesh {int buffer,count;GpuMesh(int b,int c){buffer=b;count=c;}}
    public SceneRenderer(Context c,SurvivalGame g,SurvivalGame.Input i,Listener l){context=c;game=g;input=i;listener=l;}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        models.clear();terrain=null;shadow=null;terrainX=Integer.MIN_VALUE;last=0;modelTriangles=0;error="";
        try{
            initializeHudGpu();
            program=link(text("shaders/world.vert"),text("shaders/world.frag"));
            position=GLES20.glGetAttribLocation(program,"aPosition");normal=GLES20.glGetAttribLocation(program,"aNormal");uv=GLES20.glGetAttribLocation(program,"aUV");color=GLES20.glGetAttribLocation(program,"aColor");surface=GLES20.glGetAttribLocation(program,"aSurface");rig=GLES20.glGetAttribLocation(program,"aRig");
            uModel=uniform("uModel");uVP=uniform("uViewProjection");uEye=uniform("uEye");uFog=uniform("uFog");uDay=uniform("uDay");uTime=uniform("uTime");uAlpha=uniform("uAlpha");uMode=uniform("uMode");uMotion=uniform("uMotion");
            int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            try(InputStream stream=context.getAssets().open("textures/fable_atlas.png")){
                Bitmap bitmap=BitmapFactory.decodeStream(stream);if(bitmap==null)throw new IOException("Missing material atlas");GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();
            }
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            Map<String,Object> manifest=Json.obj(Json.parse(text("models/manifest.json")));
            for(String name:Json.obj(manifest.get("assets")).keySet())try(InputStream stream=context.getAssets().open("models/"+name+".glb")){
                GlbModel mesh=GlbModel.load(stream);models.put(name,upload(mesh.vertices));modelTriangles+=mesh.triangles;
            }
            shadow=upload(shadowVertices());
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glDisable(GLES20.GL_CULL_FACE);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }catch(Exception e){error="3Dの読み込みに失敗: "+e.getMessage();android.util.Log.e("Fablewind3D",error,e);}
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,52,w/(float)Math.max(1,h),.08f,60);}
    @Override public void onDrawFrame(GL10 gl){
        long now=System.nanoTime();float dt=last==0?.016f:Math.min(.05f,(now-last)/1e9f);last=now;
        input.cameraYaw=game.interior==null?cameraYaw:0;
        synchronized(game){game.update(dt,input);scene=game.snapshot();}
        boolean requested; synchronized(game){requested=game.saveRequested;game.saveRequested=false;}
        if((saveTimer+=dt)>10 || requested){saveTimer=0;listener.save();}
        if(!error.isEmpty()){GLES20.glClearColor(.08f,.13f,.18f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);listener.overlay();listener.ready(scene,error,models.size(),modelTriangles);return;}
        float day=(float)Math.max(.08,Math.min(1,.28+Math.sin(scene.phase*Math.PI*2/700)*.9));
        float fogR=.10f+day*.46f,fogG=.14f+day*.57f,fogB=.24f+day*.47f;
        GLES20.glClearColor(fogR,fogG,fogB,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        float ground=scene.interior==null?Terrain.height(game.world,scene.x,scene.z):0;
        float distance=scene.interior==null?cameraDistance:8.4f,angle=(float)Math.toRadians(scene.interior==null?cameraYaw:0),pitch=scene.interior==null?cameraPitch:.85f;
        eyeX=scene.x+(float)Math.sin(angle)*distance;eyeZ=scene.z+(float)Math.cos(angle)*distance;eyeY=ground+distance*pitch+1;
        Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,scene.x,ground+.8f,scene.z,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);
        GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(uVP,1,false,vp,0);GLES20.glUniform3f(uEye,eyeX,eyeY,eyeZ);GLES20.glUniform3f(uFog,fogR,fogG,fogB);
        GLES20.glUniform1f(uDay,scene.interior==null?day:.82f);GLES20.glUniform1f(uTime,(float)(scene.time%10000));GLES20.glUniform1f(uAlpha,1);GLES20.glUniform1f(uMode,0);GLES20.glUniform3f(uMotion,(float)(scene.time%10000),0,0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(uniform("uAtlas"),0);
        pointLights();
        if(scene.interior==null)drawOutside();else draw("interior",0,0,0,0,1,1);
        drawShadow(scene.x,ground,scene.z,.44f,.24f);
        GLES20.glUniform3f(uMotion,(float)(scene.time%10000),scene.walking?1:0,scene.attack/.35f);
        draw("hero",scene.x,ground+(scene.walking?(float)Math.abs(Math.sin(scene.time*10))*.035f:0),scene.z,scene.yaw, .72f,1);
        GLES20.glUniform3f(uMotion,(float)(scene.time%10000),0,0);
        if(scene.sword)draw("sword",scene.x+(float)Math.cos(Math.toRadians(scene.yaw))*.27f,ground+.35f,scene.z-(float)Math.sin(Math.toRadians(scene.yaw))*.27f,scene.yaw+scene.attack*180,.65f,1);
        if(scene.shield)draw("shield",scene.x-(float)Math.cos(Math.toRadians(scene.yaw))*.27f,ground+.28f,scene.z+(float)Math.sin(Math.toRadians(scene.yaw))*.27f,scene.yaw+(scene.guarding?0:90),.55f,1);
        listener.overlay();
        int glError=GLES20.glGetError();if(glError!=GLES20.GL_NO_ERROR)error="OpenGL error 0x"+Integer.toHexString(glError);
        listener.ready(scene,error,models.size(),modelTriangles);
    }
    /** HUD and world share one EGL context; no separately animated View layer can stall. */
    private void initializeHudGpu()throws IOException{
        hudProgram=link("attribute vec2 p;attribute vec2 t;varying vec2 uv;void main(){uv=t;gl_Position=vec4(p,0.0,1.0);}",
                "precision mediump float;uniform sampler2D image;varying vec2 uv;void main(){gl_FragColor=texture2D(image,uv);}");
        hudPosition=GLES20.glGetAttribLocation(hudProgram,"p");hudUV=GLES20.glGetAttribLocation(hudProgram,"t");hudSampler=GLES20.glGetUniformLocation(hudProgram,"image");
        float[] quad={-1,1,0,0,-1,-1,0,1,1,1,1,0,1,1,1,0,-1,-1,0,1,1,-1,1,1};
        FloatBuffer data=ByteBuffer.allocateDirect(quad.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();data.put(quad).position(0);
        int[] id=new int[1];GLES20.glGenBuffers(1,id,0);hudBuffer=id[0];GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,hudBuffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,quad.length*4,data,GLES20.GL_STATIC_DRAW);
        GLES20.glGenTextures(1,id,0);hudTexture=id[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,hudTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);hudWidth=hudHeight=0;
    }
    void drawHud(Bitmap bitmap){
        if(hudProgram==0)return;
        GLES20.glUseProgram(hudProgram);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,hudTexture);
        if(hudWidth!=bitmap.getWidth() || hudHeight!=bitmap.getHeight()){
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);hudWidth=bitmap.getWidth();hudHeight=bitmap.getHeight();
        }else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,bitmap);
        GLES20.glUniform1i(hudSampler,0);GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        for(int a:new int[]{position,normal,uv,color,surface,rig})if(a>=0)GLES20.glDisableVertexAttribArray(a);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,hudBuffer);attrib(hudPosition,2,16,0);attrib(hudUV,2,16,8);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6);
        GLES20.glDisableVertexAttribArray(hudPosition);GLES20.glDisableVertexAttribArray(hudUV);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(true);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void pointLights(){
        java.util.List<float[]> lights=new java.util.ArrayList<>();
        if(scene.interior!=null){lights.add(new float[]{-2.4f,1.1f,-2.8f,2.2f});lights.add(new float[]{0,2.3f,0,1.3f});}
        else {
            for(SurvivalGame.Building b:scene.buildings)if(b.model.equals("lamp") || b.model.equals("campfire") && b.litUntil>scene.time)
                lights.add(new float[]{b.x,Terrain.height(game.world,b.x,b.z)+(b.model.equals("lamp")?1.3f:.5f),b.z,2});
            for(int[] point:new int[][]{{-3,-2},{3,-2},{-3,5},{3,5}})lights.add(new float[]{point[0],Terrain.height(game.world,point[0],point[1])+1.3f,point[1],1.4f});
        }
        lights.sort(java.util.Comparator.comparingDouble(a->(a[0]-scene.x)*(a[0]-scene.x)+(a[2]-scene.z)*(a[2]-scene.z)));
        for(int i=0;i<3;i++){
            float[] light=i<lights.size()?lights.get(i):new float[4];
            GLES20.glUniform4f(uniform("uPointLights["+i+"]"),light[0],light[1],light[2],light[3]);
        }
    }
    private void drawOutside(){
        if(terrain==null || Math.abs((long)terrainX-Math.round(scene.x))>3 || Math.abs((long)terrainZ-Math.round(scene.z))>3){
            terrainX=Math.round(scene.x);terrainZ=Math.round(scene.z);if(terrain!=null)GLES20.glDeleteBuffers(1,new int[]{terrain.buffer},0);terrain=upload(terrainVertices(terrainX,terrainZ));
        }
        Matrix.setIdentityM(model,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);mesh(terrain);
        int left=(int)Math.floor(scene.x)-23,right=(int)Math.ceil(scene.x)+23,top=(int)Math.floor(scene.z)-23,bottom=(int)Math.ceil(scene.z)+23;
        for(int z=top;z<=bottom;z++)for(int x=left;x<=right;x++){
            if(!visible(x,z,3.5f) || scene.hidden.contains(SurvivalGame.key(x,z)) || coveredByBuilding(x,z) || SceneCatalog.nearFeature(x,z,.6f))continue;
            World.Tile tile=game.world.tile(x,z);String name=SceneCatalog.model(tile);
            if(name==null){name=game.decor(x,z);if(name!=null && name.equals("chest") && scene.opened.contains(SurvivalGame.key(x,z)))name=null;}
            float y=Terrain.height(game.world,x,z);
            if(name!=null){
                float scale=tile.object==World.NONE?.55f:SceneCatalog.scale(tile);
                float yaw=(tile.object==World.HOUSE || tile.object==World.INN || tile.object==World.CASTLE || tile.object==World.WARP)?0:(tile.seed&255)*360f/255;
                if(tile.object==World.TREE || SceneCatalog.solid(tile.object))drawShadow(x,y,z,scale*.8f,scale*.5f);
                boolean fade=tile.object==World.TREE || tile.object==World.GIANT_TREE;
                fade=fade && occludesTraveler(x,z);
                if(fade)GLES20.glDepthMask(false);
                draw(name,x,y,z,yaw,scale,fade?.25f:1);
                if(fade)GLES20.glDepthMask(true);
            }else if(tile.object==World.NONE && tile.biome!=World.WATER && !tile.road){
                int h=Math.floorMod(World.hash(x,z,19),100);
                if(tile.region==2 && h<35)draw("wheat",x,y,z,0,.6f,1);
                if(tile.region==3 && h<17)draw("reed",x,y,z,0,.6f,1);
            }
        }
        for(SceneCatalog.Feature f:SceneCatalog.FEATURES)if(visible(f.x,f.z,6))draw(f.model,f.x,Terrain.height(game.world,f.x,f.z),f.z,0,f.scale,1);
        for(SurvivalGame.Building b:scene.buildings){
            if(!visible(b.x,b.z,5))continue;float y=Terrain.height(game.world,b.x,b.z);
            draw(b.model,b.x,y,b.z,b.yaw,b.model.equals("house")?.7f:.75f,1);
            if(b.model.equals("campfire") && b.litUntil>scene.time){
                draw("flame",b.x,y+.1f,b.z,(float)scene.time*40,.35f+(float)Math.sin(scene.time*9)*.02f,1);
            }
        }
        for(SurvivalGame.Mob mob:scene.mobs)if(visible(mob.x,mob.z,2)){
            float y=Terrain.height(game.world,mob.x,mob.z),bob=(float)Math.sin(mob.age*6+mob.id)*.025f;
            if(mob.model.equals("firefly") || mob.model.equals("wisp"))bob+=.4f+(float)Math.sin(mob.age*2)*.15f;
            drawShadow(mob.x,y,mob.z,.3f,.2f);draw(mob.model,mob.x,y+bob,mob.z,mob.yaw,.65f,1);
        }
        if(scene.pending!=null){
            Recipe r=Recipe.find(scene.pending);if(r!=null){
                float x,z;synchronized(game){x=game.buildX();z=game.buildZ();}
                draw(r.model,x,Terrain.height(game.world,x,z),z,scene.yaw,r.model.equals("house")?.7f:.75f,.45f);
            }
        }
    }
    private boolean coveredByBuilding(float x,float z){
        for(SurvivalGame.Building b:scene.buildings){float radius=b.model.equals("house")?1.8f:.7f;
            if(Math.abs(x-b.x)<radius && Math.abs(z-b.z)<radius)return true;}
        return false;
    }
    private boolean occludesTraveler(float x,float z){
        float vx=scene.x-eyeX,vz=scene.z-eyeZ,len=vx*vx+vz*vz;
        float t=((x-eyeX)*vx+(z-eyeZ)*vz)/Math.max(.001f,len);
        return t>.05f && t<.94f && Math.hypot(x-(eyeX+vx*t),z-(eyeZ+vz*t))<1.45;
    }
    private boolean visible(float x,float z,float radius){
        float dx=x-scene.x,dz=z-scene.z;if(dx*dx+dz*dz>29*29)return false;
        float cx=x-eyeX,cy=1-eyeY,cz=z-eyeZ;
        float w=vp[3]*x+vp[7]*1+vp[11]*z+vp[15];
        float sx=vp[0]*x+vp[4]*1+vp[8]*z+vp[12];
        return w>-.5f && Math.abs(sx)<w+radius*2.5f;
    }
    private void draw(String name,float x,float y,float z,float yaw,float scale,float alpha){
        GpuMesh gpu=models.get(name);if(gpu==null)return;
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.rotateM(model,0,yaw,0,1,0);Matrix.scaleM(model,0,scale,scale,scale);
        GLES20.glUniformMatrix4fv(uModel,1,false,model,0);GLES20.glUniform1f(uAlpha,alpha);GLES20.glUniform1f(uMode,0);mesh(gpu);GLES20.glUniform1f(uAlpha,1);
    }
    private void drawShadow(float x,float y,float z,float rx,float rz){
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x+.15f,y+.022f,z+.08f);Matrix.scaleM(model,0,rx,1,rz);
        GLES20.glUniformMatrix4fv(uModel,1,false,model,0);GLES20.glUniform1f(uMode,1);GLES20.glUniform1f(uAlpha,.25f);GLES20.glDepthMask(false);mesh(shadow);GLES20.glDepthMask(true);GLES20.glUniform1f(uMode,0);GLES20.glUniform1f(uAlpha,1);
    }
    private void mesh(GpuMesh m){
        if(m==null)return;GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.buffer);int stride=GlbModel.FLOATS*4;
        attrib(position,3,stride,0);attrib(normal,3,stride,12);attrib(uv,2,stride,24);attrib(color,4,stride,32);attrib(surface,3,stride,48);attrib(rig,4,stride,60);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);
    }
    private void attrib(int location,int size,int stride,int offset){if(location<0)return;GLES20.glEnableVertexAttribArray(location);GLES20.glVertexAttribPointer(location,size,GLES20.GL_FLOAT,false,stride,offset);}
    private GpuMesh upload(float[] data){
        int[] id=new int[1];GLES20.glGenBuffers(1,id,0);GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,id[0]);
        FloatBuffer buffer=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(data).position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,data.length*4,buffer,GLES20.GL_STATIC_DRAW);return new GpuMesh(id[0],data.length/GlbModel.FLOATS);
    }
    private float[] terrainVertices(int cx,int cz){
        int radius=33,side=radius*2;float[] data=new float[side*side*6*GlbModel.FLOATS];int at=0;
        for(int z=cz-radius;z<cz+radius;z++)for(int x=cx-radius;x<cx+radius;x++){
            for(int corner:new int[]{0,2,1,1,2,3}){
                int vx=x+(corner&1),vz=z+(corner>>1);World.Tile t=game.world.tile(vx,vz);float h=Terrain.height(game.world,vx,vz);
                float nx=Terrain.height(game.world,vx-.2f,vz)-Terrain.height(game.world,vx+.2f,vz),nz=Terrain.height(game.world,vx,vz-.2f)-Terrain.height(game.world,vx,vz+.2f);
                float len=(float)Math.sqrt(nx*nx+.16+nz*nz);float[] tint=Terrain.color(t);
                World.Tile base=game.world.tile(x,z);int cell=base.biome==World.WATER?9:base.road?0:7;int row=cell/4,col=cell%4;
                float uu=(col+.04f+(corner&1)*.91f)/4,vv=(row+.04f+(corner>>1)*.91f)/4;
                float[] v={vx,h,vz,nx/len,.4f/len,nz/len,uu,vv,tint[0],tint[1],tint[2],1,t.biome==World.WATER?.2f:.93f,0,t.biome==World.WATER?-1:0,0,0,0,0};
                System.arraycopy(v,0,data,at,v.length);at+=v.length;
            }
        }
        return data;
    }
    private static float[] shadowVertices(){float[] data=new float[6*GlbModel.FLOATS];int at=0;for(int c:new int[]{0,2,1,1,2,3}){float xx=(c&1)*2-1,zz=(c>>1)*2-1;float[] v={xx,0,zz,0,1,0,(xx+1)/2,(zz+1)/2,1,1,1,1,1,0,0,0,0,0,0};System.arraycopy(v,0,data,at,v.length);at+=v.length;}return data;}
    private int uniform(String name){return GLES20.glGetUniformLocation(program,name);}
    private String text(String path)throws IOException{try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)out.write(data,0,n);return out.toString("UTF-8");}}
    private static int shader(int type,String source)throws IOException{int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] status=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0)throw new IOException(GLES20.glGetShaderInfoLog(shader));return shader;}
    private static int link(String vertex,String fragment)throws IOException{int v=shader(GLES20.GL_VERTEX_SHADER,vertex),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] s=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,s,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(s[0]==0)throw new IOException(GLES20.glGetProgramInfoLog(p));return p;}
}
