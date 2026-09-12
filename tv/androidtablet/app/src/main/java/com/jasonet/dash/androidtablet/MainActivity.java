package com.jasonet.dash.androidtablet;
import android.app.Activity;import android.os.Bundle;import android.view.KeyEvent;
public final class MainActivity extends Activity{
 private DashboardView dashboard;
 @Override public void onCreate(Bundle state){super.onCreate(state);dashboard=new DashboardView(this);setContentView(dashboard);}
 @Override public boolean dispatchKeyEvent(KeyEvent event){return dashboard.handleKey(event)||super.dispatchKeyEvent(event);}
 @Override protected void onResume(){super.onResume();dashboard.start();}
 @Override protected void onPause(){dashboard.stop();super.onPause();}
 @Override protected void onDestroy(){dashboard.destroy();super.onDestroy();}
}
