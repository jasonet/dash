package com.jasonet.dash.androidtablet;

import java.io.*;

final class MarketSnapshot {
    private static final int MAGIC=0x4d573031;
    final double[][][] series=new double[9][4][61];
    final double[] prices=new double[9];
    final double[] changes=new double[9];
    final long savedAt;
    final boolean live;
    private MarketSnapshot(long savedAt,boolean live) { this.savedAt=savedAt;this.live=live; }

    static MarketSnapshot demo(long now) {
        MarketSnapshot snapshot=new MarketSnapshot(now,false);
        System.arraycopy(MarketData.PRICES,0,snapshot.prices,0,9);
        System.arraycopy(MarketData.CHANGES,0,snapshot.changes,0,9);
        for(int s=0;s<9;s++) for(int p=0;p<4;p++) snapshot.series[s][p]=MarketData.series(s,p);
        return snapshot;
    }

    static MarketSnapshot live(long now,MarketSnapshot base,double[][] daySeries,double[] livePrices,double[] liveChanges) {
        MarketSnapshot snapshot=new MarketSnapshot(now,true);
        for(int s=0;s<9;s++) for(int p=0;p<4;p++) snapshot.series[s][p]=base.series[s][p].clone();
        System.arraycopy(base.prices,0,snapshot.prices,0,9);
        System.arraycopy(base.changes,0,snapshot.changes,0,9);
        for(int s=0;s<9;s++) {
            if(daySeries[s]!=null) snapshot.series[s][0]=daySeries[s];
            if(livePrices[s]>0) snapshot.prices[s]=livePrices[s];
            if(!Double.isNaN(liveChanges[s])) snapshot.changes[s]=liveChanges[s];
        }
        return snapshot;
    }

    MarketSnapshot withHistory(int symbol,int selectedPeriod,double[] values,long now) {
        MarketSnapshot copy=new MarketSnapshot(now,live);
        System.arraycopy(prices,0,copy.prices,0,9);
        System.arraycopy(changes,0,copy.changes,0,9);
        for(int s=0;s<9;s++) for(int p=0;p<4;p++) copy.series[s][p]=series[s][p].clone();
        copy.series[symbol][selectedPeriod]=values;
        return copy;
    }

    static MarketSnapshot read(InputStream stream) throws IOException {
        DataInputStream in=new DataInputStream(stream);
        if(in.readInt()!=MAGIC) throw new IOException("Unsupported snapshot");
        long time=in.readLong();
        if(time<=0 || time>System.currentTimeMillis()+86400000L) throw new IOException("Invalid timestamp");
        MarketSnapshot snapshot=new MarketSnapshot(time,in.readBoolean());
        for(int s=0;s<9;s++) {
            snapshot.prices[s]=in.readDouble();snapshot.changes[s]=in.readDouble();
            if(!valid(snapshot.prices[s]) || Double.isNaN(snapshot.changes[s]) || Double.isInfinite(snapshot.changes[s])) throw new IOException("Invalid quote metadata");
        }
        for(int s=0;s<9;s++) for(int p=0;p<4;p++) for(int i=0;i<61;i++) {
            double value=in.readDouble();
            if(!valid(value)) throw new IOException("Invalid quote");
            snapshot.series[s][p][i]=value;
        }
        if(in.read()!=-1) throw new IOException("Unexpected trailing data");
        return snapshot;
    }

    private static boolean valid(double value) { return !Double.isNaN(value) && !Double.isInfinite(value) && value>0; }

    void write(OutputStream stream) throws IOException {
        DataOutputStream out=new DataOutputStream(stream);
        out.writeInt(MAGIC);
        out.writeLong(savedAt);
        out.writeBoolean(live);
        for(int s=0;s<9;s++) { out.writeDouble(prices[s]);out.writeDouble(changes[s]); }
        for(double[][] symbol:series) for(double[] period:symbol) for(double value:period) out.writeDouble(value);
        out.flush();
    }
}
