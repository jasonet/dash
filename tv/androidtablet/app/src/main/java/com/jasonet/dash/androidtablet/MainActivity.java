package com.jasonet.dash.androidtablet;
import android.app.Activity;import android.os.Bundle;import android.view.KeyEvent;
public final class MainActivity extends Activity{
 private DashboardView dashboard;
 @Override public void onCreate(Bundle state){super.onCreate(state);dashboard=new DashboardView(this);setContentView(dashboard);}
 @Override public boolean dispatchKeyEvent(KeyEvent event){return dashboard.handleKey(event)||super.dispatchKeyEvent(event);}
 @Override public void onBackPressed(){if(!dashboard.closePanel())super.onBackPressed();}
}
