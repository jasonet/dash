package com.jasonet.dash.androidtablet;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.net.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardView extends View {
    private static final int BG=Color.rgb(5,15,23), CARD=Color.rgb(13,31,43);
    private static final int WHITE=Color.rgb(233,243,249), MUTED=Color.rgb(142,164,179);
    private static final int GREEN=Color.rgb(84,231,171), RED=Color.rgb(255,115,105), AMBER=Color.rgb(255,194,92);
    private static final ExecutorService CACHE_IO=Executors.newSingleThreadExecutor();
    private static final ExecutorService DATA_IO=Executors.newSingleThreadExecutor();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Path graph=new Path();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Set<String> favorites;
    private final ConnectivityManager network;
    private final AtomicFile cacheFile;
    private MarketSnapshot snapshot=MarketSnapshot.demo(System.currentTimeMillis());
    private DashboardLayout layout;
    private int selected, period, focus, pressed=-1;
    private float downX, downY;
    private boolean active, registered, keyboardFocus, destroyed;
    private Network currentNetwork;
    private ConnectivityManager.NetworkCallback callback;
    private NetworkStatus status=NetworkStatus.CHECKING;
    private boolean sawUnavailable;
    private long reconnectedUntil, lastNetworkCheck, lastLiveRequest;
    private boolean liveLoading;
    private String cacheLabel="内置演示数据已就绪 · 正在读取缓存";
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(!active) return;
            invalidate();
            if(status==NetworkStatus.ONLINE && SystemClock.uptimeMillis()-lastLiveRequest>=60000) requestLiveData();
            if(SystemClock.uptimeMillis()-lastNetworkCheck>=5000) refreshNetwork();
            main.postDelayed(this,status==NetworkStatus.ONLINE?50:1000);
        }
    };

    public DashboardView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        prefs=context.getSharedPreferences("marketwall-tablet",Context.MODE_PRIVATE);
        selected=Math.max(0,Math.min(8,prefs.getInt("symbol",0)));
        period=Math.max(0,Math.min(3,prefs.getInt("period",0)));
        favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));
        network=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        describe();
        cacheFile=new AtomicFile(new File(context.getFilesDir(),"market-snapshot-v2.bin"));
        CACHE_IO.execute(()->loadSnapshot(cacheFile));
    }

    private void loadSnapshot(AtomicFile file) {
        MarketSnapshot loaded;
        String message;
        try(InputStream in=file.openRead()) {
            loaded=MarketSnapshot.read(in);
            message=loaded.live?"已加载实时行情缓存":"已加载本地演示快照";
        } catch(IOException error) {
            loaded=MarketSnapshot.demo(System.currentTimeMillis());
            FileOutputStream out=null;
            try {
                out=file.startWrite();
                loaded.write(out);
                file.finishWrite(out);
                message="内置演示快照已缓存";
            } catch(IOException writeError) {
                file.failWrite(out);
                message="内置演示数据 · 缓存暂不可写";
            }
        }
        MarketSnapshot result=loaded;
        String label=message+" · "+new SimpleDateFormat("MM-dd HH:mm",Locale.US).format(new Date(loaded.savedAt));
        main.post(()->{
            if(!destroyed && (!snapshot.live || (result.live && result.savedAt>=snapshot.savedAt))) {
                snapshot=result;cacheLabel=label;describe();invalidate();
            }
        });
    }

    public void start() {
        if(active || destroyed) return;
        active=true;
        refreshNetwork();
        callback=new ConnectivityManager.NetworkCallback() {
            private void dispatch(Runnable action) {
                main.post(()->{if(active && callback==this) action.run();});
            }
            @Override public void onAvailable(Network n) {
                dispatch(()->{
                    if(Build.VERSION.SDK_INT>=24) {currentNetwork=n;setStatus(NetworkStatus.CHECKING);}
                    else refreshNetwork();
                });
            }
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities caps) {
                dispatch(()->{
                    if(Build.VERSION.SDK_INT<24) refreshNetwork();
                    else if(n.equals(currentNetwork)) applyCapabilities(caps);
                });
            }
            @Override public void onLost(Network n) {
                dispatch(()->{
                    if(Build.VERSION.SDK_INT<24) refreshNetwork();
                    else if(n.equals(currentNetwork)) {currentNetwork=null;setStatus(NetworkStatus.OFFLINE);}
                });
            }
        };
        try {
            if(network!=null) {
                if(Build.VERSION.SDK_INT>=24) network.registerDefaultNetworkCallback(callback);
                else network.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),callback);
                registered=true;
            }
        } catch(RuntimeException error) { registered=false; }
        main.post(tick);
    }

    public void stop() {
        active=false;
        main.removeCallbacks(tick);
        if(registered) {
            try {network.unregisterNetworkCallback(callback);} catch(RuntimeException ignored) { }
        }
        registered=false;
        callback=null;
    }

    public void destroy() { stop();destroyed=true;main.removeCallbacksAndMessages(null); }

    private void refreshNetwork() {
        lastNetworkCheck=SystemClock.uptimeMillis();
        try {
            currentNetwork=network==null?null:network.getActiveNetwork();
            if(currentNetwork==null) setStatus(NetworkStatus.OFFLINE);
            else applyCapabilities(network.getNetworkCapabilities(currentNetwork));
        } catch(RuntimeException error) {setStatus(NetworkStatus.UNKNOWN);}
    }

    private void applyCapabilities(NetworkCapabilities caps) {
        setStatus(NetworkStatus.from(currentNetwork!=null,caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
    }

    private void setStatus(NetworkStatus next) {
        if(next==NetworkStatus.ONLINE) {
            if(sawUnavailable) reconnectedUntil=SystemClock.uptimeMillis()+5000;
            sawUnavailable=false;
        }
        if(next==NetworkStatus.OFFLINE || next==NetworkStatus.LIMITED) sawUnavailable=true;
        status=next;
        describe();
        invalidate();
    }

    private void requestLiveData() {
        if(liveLoading || destroyed || status!=NetworkStatus.ONLINE) return;
        liveLoading=true;lastLiveRequest=SystemClock.uptimeMillis();
        cacheLabel="正在更新实时行情 · 网络数据可能有延迟";invalidate();
        MarketSnapshot base=snapshot;
        DATA_IO.execute(()->{
            try {
                MarketSnapshot result=MarketApi.fetchAll(base);
                CACHE_IO.execute(()->writeSnapshot(result));
                main.post(()->{
                    if(!destroyed) {
                        snapshot=result;liveLoading=false;
                        cacheLabel="实时行情 · 可能延迟 · "+timeLabel(result.savedAt);invalidate();
                    }
                });
            } catch(IOException error) {
                main.post(()->{if(!destroyed){liveLoading=false;cacheLabel="实时行情暂不可用 · 使用本地缓存";invalidate();}});
            }
        });
    }

    private void writeSnapshot(MarketSnapshot value) {
        FileOutputStream out=null;
        try { out=cacheFile.startWrite();value.write(out);cacheFile.finishWrite(out); }
        catch(IOException error) { cacheFile.failWrite(out); }
    }

    private String timeLabel(long time) { return new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date(time)); }

    private void requestHistory(int symbol,int selectedPeriod) {
        if(selectedPeriod==0 || status!=NetworkStatus.ONLINE) return;
        final int requestedSymbol=symbol, requestedPeriod=selectedPeriod;
        DATA_IO.execute(()->{
            try {
                double[] values=MarketApi.fetchHistory(MarketData.REMOTE_SYMBOLS[requestedSymbol],requestedPeriod);
                main.post(()->{
                    if(!destroyed && selected==requestedSymbol && period==requestedPeriod) {
                        snapshot=snapshot.withHistory(requestedSymbol,requestedPeriod,values,System.currentTimeMillis());
                        cacheLabel="历史行情已更新 · 可能延迟 · "+timeLabel(System.currentTimeMillis());invalidate();
                    }
                });
            } catch(IOException error) {
                main.post(()->{if(!destroyed && selected==requestedSymbol && period==requestedPeriod){cacheLabel="历史行情暂不可用 · 使用缓存";invalidate();}});
            }
        });
    }

    private String networkLabel() {
        switch(status) {
            case ONLINE: return SystemClock.uptimeMillis()<reconnectedUntil?"网络已恢复":"网络在线";
            case OFFLINE: return "已断线 · 本地数据可用";
            case LIMITED: return "网络未验证 · 请检查连接";
            case UNKNOWN: return "网络状态暂不可用";
            default: return "正在检查网络";
        }
    }

    private void describe() {
        setContentDescription("Market Wall，演示行情，"+MarketData.NAMES[selected]+"，"+MarketData.PERIODS[period]+"，"+networkLabel());
    }

    private void saveSelection() {
        prefs.edit().putInt("symbol",selected).putInt("period",period).putStringSet("favorites",new HashSet<>(favorites)).apply();
        describe();
        invalidate();
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        if(w>0 && h>0) layout=new DashboardLayout(w,h);
    }

    private void text(Canvas canvas,String value,float x,float y,float size,int color) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(value,x,y,paint);
    }

    private void rightText(Canvas canvas,String value,float right,float y,float size,int color) {
        paint.setTextSize(size);
        text(canvas,value,right-paint.measureText(value),y,size,color);
    }

    private void card(Canvas canvas,DashboardLayout.Box box,boolean selected,int target) {
        rect.set(box.left,box.top,box.right,box.bottom);
        paint.setStyle(Paint.Style.FILL);paint.setColor(selected?Color.rgb(18,67,64):CARD);
        canvas.drawRoundRect(rect,14,14,paint);
        if(selected || (keyboardFocus && target==focus)) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(keyboardFocus&&target==focus?4:2);
            paint.setColor(keyboardFocus&&target==focus?WHITE:GREEN);
            canvas.drawRoundRect(rect,14,14,paint);paint.setStyle(Paint.Style.FILL);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BG);
        if(layout==null) return;
        canvas.save();canvas.scale(layout.scale,layout.scale);
        text(canvas,"MARKET WALL",28,46,28,WHITE);
        text(canvas,"全球金融看板 / ANDROID TABLET",28,74,16,MUTED);
        int color=status==NetworkStatus.ONLINE?GREEN:status==NetworkStatus.OFFLINE?RED:AMBER;
        float pulse=(float)(.5+.5*Math.sin(SystemClock.uptimeMillis()*Math.PI/1400));
        float right=layout.width-28;
        paint.setTextSize(17);
        float dotX=right-paint.measureText(networkLabel())-20;
        paint.setColor(color);
        if(status==NetworkStatus.ONLINE) {paint.setAlpha((int)(35+45*pulse));canvas.drawCircle(dotX,39,11+3*pulse,paint);}
        paint.setAlpha(255);canvas.drawCircle(dotX,39,5,paint);
        rightText(canvas,networkLabel(),right,46,17,color);
        rightText(canvas,cacheLabel,right,74,16,snapshot.live?GREEN:AMBER);
        for(int i=0;i<4;i++) {
            DashboardLayout.Box b=layout.targets[i];
            card(canvas,b,selected==i,i);
            text(canvas,MarketData.NAMES[i],b.left+15,b.top+28,18,MUTED);
            text(canvas,fmt(snapshot.prices[i]),b.left+15,b.top+59,25,WHITE);
            text(canvas,String.format(Locale.US,"%+.2f",snapshot.changes[i]),b.left+15,b.top+83,16,snapshot.changes[i]>=0?GREEN:RED);
        }
        drawChart(canvas);
        drawList(canvas);
        text(canvas,cacheLabel,28,layout.height-25,15,MUTED);
        canvas.restore();
    }

    private void drawChart(Canvas canvas) {
        DashboardLayout.Box b=layout.chart;
        card(canvas,b,false,-1);
        text(canvas,MarketData.NAMES[selected]+" / "+MarketData.SYMBOLS[selected],b.left+20,b.top+34,20,MUTED);
        double[] values=snapshot.series[selected][period];
        double low=values[0],high=low;
        for(double value:values) {low=Math.min(low,value);high=Math.max(high,value);}
        double delta=values[60]-values[0];
        int color=delta>=0?GREEN:RED;
        text(canvas,fmt(values[60]),b.left+20,b.top+90,44,WHITE);
        rightText(canvas,String.format(Locale.US,"%+.2f / %+.2f%%",delta,delta/values[0]*100),b.right-20,b.top+82,20,color);
        for(int i=0;i<4;i++) {
            DashboardLayout.Box r=layout.targets[4+i];card(canvas,r,period==i,4+i);
            text(canvas,MarketData.PERIODS[i],r.left+18,r.top+31,18,WHITE);
        }
        DashboardLayout.Box favorite=layout.targets[8];
        boolean saved=favorites.contains(MarketData.SYMBOLS[selected]);
        card(canvas,favorite,saved,8);
        text(canvas,saved?"已加入自选":"加入自选",favorite.left+20,favorite.top+31,18,WHITE);
        float left=b.left+20,right=b.right-20,top=b.top+188,bottom=b.bottom-88;
        paint.setColor(Color.rgb(29,51,63));paint.setStrokeWidth(1);
        for(int i=0;i<5;i++) canvas.drawLine(left,top+i*(bottom-top)/4,right,top+i*(bottom-top)/4,paint);
        graph.reset();
        for(int i=0;i<values.length;i++) {
            float x=left+i*(right-left)/60,y=(float)(bottom-8-(values[i]-low)/Math.max(.0001,high-low)*(bottom-top-16));
            if(i==0) graph.moveTo(x,y);else graph.lineTo(x,y);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(color);canvas.drawPath(graph,paint);paint.setStyle(Paint.Style.FILL);
        text(canvas,"区间起点",left,bottom+24,14,MUTED);
        rightText(canvas,"区间终点",right,bottom+24,14,MUTED);
        text(canvas,"起点 "+fmt(values[0]),left,b.bottom-25,15,MUTED);
        text(canvas,"最高 "+fmt(high),left+(right-left)*.36f,b.bottom-25,15,MUTED);
        rightText(canvas,"最低 "+fmt(low),right,b.bottom-25,15,MUTED);
    }

    private void drawList(Canvas canvas) {
        DashboardLayout.Box b=layout.list;
        card(canvas,b,false,-1);
        text(canvas,"关联行情 / 推荐行情 · 自选 "+favorites.size(),b.left+18,b.top+32,19,WHITE);
        for(int i=0;i<9;i++) {
            DashboardLayout.Box r=layout.targets[9+i];int symbol=listSymbolAt(i);card(canvas,r,selected==symbol,9+i);
            String group=i==0?"当前 ":i<=relatedCount(selected)?"关联 ":"推荐 ";
            text(canvas,group+(favorites.contains(MarketData.SYMBOLS[symbol])?"★ ":"")+MarketData.SYMBOLS[symbol],r.left+12,layout.landscape?r.y()+6:r.y()-10,17,WHITE);
            rightText(canvas,fmt(snapshot.prices[symbol]),r.right-12,layout.landscape?r.y()+6:r.y()+20,17,snapshot.changes[symbol]>=0?GREEN:RED);
        }
    }

    private int relatedCount(int symbol) { return Math.min(9,MarketData.RELATED[symbol].length); }

    private int listSymbolAt(int row) {
        if(row==0) return selected;
        boolean[] used=new boolean[9];used[selected]=true;
        int related=0;
        for(int candidate:MarketData.RELATED[selected]) {
            if(candidate!=selected && related<9) {used[candidate]=true;related++;if(related==row-1)return candidate;}
        }
        int[] order=new int[9];int count=0;
        for(int i=0;i<9;i++) if(!used[i]) order[count++]=i;
        for(int i=0;i<count;i++) for(int j=i+1;j<count;j++) {
            if(Math.abs(snapshot.changes[order[j]])>Math.abs(snapshot.changes[order[i]])) {
                int temp=order[i];order[i]=order[j];order[j]=temp;
            }
        }
        int recommendationRow=row-1-related;
        return recommendationRow>=0 && recommendationRow<count?order[recommendationRow]:selected;
    }

    private String fmt(double value) {return String.format(Locale.US,"%,.2f",value);}

    private void choose(int index) {
        if(index<0 || index>=18) return;
        if(index<4) selected=index;
        else if(index<8) period=index-4;
        else if(index==8) {
            String symbol=MarketData.SYMBOLS[selected];
            if(!favorites.add(symbol)) favorites.remove(symbol);
            announceForAccessibility(favorites.contains(symbol)?"已加入自选":"已移出自选");
        } else selected=listSymbolAt(index-9);
        if(index>=4 && index<8) requestHistory(selected,period);
        saveSelection();
    }

    public boolean handleKey(KeyEvent event) {
        int key=event.getKeyCode();
        boolean navigation=key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN;
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER;
        if((!navigation && !confirm) || layout==null) return false;
        if(event.getAction()==KeyEvent.ACTION_UP) return true;
        if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
        keyboardFocus=true;
        if(confirm) {if(event.getRepeatCount()==0) choose(focus);}
        else focus=layout.next(focus,key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);
        invalidate();return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(layout==null) return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed=layout.hit(event.getX(),event.getY());downX=event.getX();downY=event.getY();return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.hypot(event.getX()-downX,event.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()) pressed=-1;
                return true;
            case MotionEvent.ACTION_UP:
                if(pressed>=0 && pressed==layout.hit(event.getX(),event.getY())) {focus=pressed;keyboardFocus=false;performClick();}
                pressed=-1;return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL: pressed=-1;return true;
            default: return true;
        }
    }

    @Override public boolean performClick() {super.performClick();choose(focus);return true;}
}
