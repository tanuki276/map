package jp.tanuki.fablewind;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;

/** Runs against the actual APK and EGL surface. No external test libraries are required. */
public final class SmokeInstrumentation extends Instrumentation {
    private Activity activity;private GameView view;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);waitForIdleSync();
            View root=activity.findViewById(android.R.id.content);
            view=(GameView)((android.view.ViewGroup)root).getChildAt(0);
            long timeout=SystemClock.uptimeMillis()+60000;
            while(view.testModels()<54 && view.testError().isEmpty() && SystemClock.uptimeMillis()<timeout)SystemClock.sleep(250);
            check(view.testModels()==54,"load all 54 GLB models: "+view.testError());check(view.testError().isEmpty(),view.testError());
            check(view.getWidth()>view.getHeight(),"landscape viewport");
            SurvivalGame game=view.testGame();
            for(int i=0;i<7;i++){
                WarpPoint point=WarpPoint.ALL.get(i);
                synchronized(game){game.paused=true;game.interior=null;game.x=point.x;game.z=point.y;game.time=144;}
                awaitScene(point.x,point.y,null);capture("region-"+i);
                check(view.testError().isEmpty(),"GL error in region "+i+": "+view.testError());
            }
            synchronized(game){game.interior="instrumented-room";game.x=0;game.z=2;}
            awaitScene(0,2,"instrumented-room");capture("interior");
            synchronized(game){game.interior=null;game.x=68;game.z=22;game.time=500;game.paused=false;SurvivalGame.Input input=new SurvivalGame.Input();for(int n=0;n<200;n++)game.update(.1f,input);game.paused=true;}
            awaitScene(68,22,null);capture("night");
            // Real touch events open and close the crafting modal.
            float scale=Math.min(view.getWidth()/960f,view.getHeight()/540f),sw=view.getWidth()/scale;
            tap((sw-275)*scale,35*scale);SystemClock.sleep(300);check(view.testMenu().equals("craft"),"craft modal opens");capture("craft");
            tap((sw-100)*scale,55*scale);SystemClock.sleep(100);
            tap((sw-100)*scale,35*scale);SystemClock.sleep(300);check(view.testMenu().equals("map"),"map modal opens");capture("map");
            check(view.testError().isEmpty(),"final GL error: "+view.testError());
            result.putString("result","PASS");result.putString("models",String.valueOf(view.testModels()));
            result.putString("coverage","EGL shader + 54 GLBs + 7 regions + interior + night + crafting/map touch + screenshots");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){android.util.Log.e("FablewindSmoke","FAIL",e);result.putString("result","FAIL");result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    private void awaitScene(float x,float z,String interior)throws InterruptedException{
        long frame=view.testFrames()+2,deadline=SystemClock.uptimeMillis()+45000;
        while(SystemClock.uptimeMillis()<deadline){
            SurvivalGame.Snapshot s=view.testSnapshot(),h=view.testHudSnapshot();
            if(view.testFrames()>=frame && s!=null && s.x==x && s.z==z && java.util.Objects.equals(s.interior,interior) && h!=null && h.x==x && h.z==z && java.util.Objects.equals(h.interior,interior)){view.testSyncFrame();waitForIdleSync();SystemClock.sleep(200);return;}
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for rendered scene "+x+","+z);
    }
    private void tap(float x,float y){
        int[] origin=new int[2];view.getLocationOnScreen(origin);x+=origin[0];y+=origin[1];
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,0,x,y,0),up=MotionEvent.obtain(now,now+50,1,x,y,0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        check(getUiAutomation().injectInputEvent(down,true),"touch down");check(getUiAutomation().injectInputEvent(up,true),"touch up");
        down.recycle();up.recycle();waitForIdleSync();
    }
    private void capture(String name)throws IOException{
        Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"screenshot "+name);
        File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");if(!dir.exists()&&!dir.mkdirs())throw new IOException("QA directory");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){check(b.compress(Bitmap.CompressFormat.PNG,100,out),"PNG capture");}
        Bitmap small=Bitmap.createScaledBitmap(b,960,540,true);
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".jpg"))){small.compress(Bitmap.CompressFormat.JPEG,78,out);}
        int colored=0,total=0;
        for(int y=b.getHeight()/4;y<b.getHeight()*3/5;y+=Math.max(1,b.getHeight()/25))
            for(int x=b.getWidth()*3/10;x<b.getWidth()*7/10;x+=Math.max(1,b.getWidth()/25)){
                int pixel=b.getPixel(x,y);total++;
                if(android.graphics.Color.red(pixel)<235 || android.graphics.Color.green(pixel)<235 || android.graphics.Color.blue(pixel)<235)colored++;
            }
        if(small!=b)small.recycle();b.recycle();
        check(colored>total/2,"playfield is visible, not a blank surface: "+name+" colored="+colored+"/"+total);
    }
}
