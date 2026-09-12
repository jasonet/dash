package com.jasonet.dash.androidtablet;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

final class MarketApi {
    private static final String BASE="https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final int TIMEOUT_MS=5000;

    static MarketSnapshot fetchAll(MarketSnapshot fallback) throws IOException {
        double[][] day=new double[9][];
        double[] prices=fallback.prices.clone(), changes=fallback.changes.clone();
        int successes=0;
        for(int i=0;i<9;i++) {
            try {
                Quote quote=fetch(MarketData.REMOTE_SYMBOLS[i],"1d","5m");
                day[i]=quote.series;prices[i]=quote.price;changes[i]=quote.change;
                successes++;
            } catch(IOException ignored) { }
        }
        if(successes==0) throw new IOException("No live quotes available");
        return MarketSnapshot.live(System.currentTimeMillis(),fallback,day,prices,changes);
    }

    static double[] fetchHistory(String symbol,int period) throws IOException {
        String[] ranges={"1d","5d","1mo","1y"};
        String[] intervals={"5m","30m","1d","1d"};
        return fetch(symbol,ranges[period],intervals[period]).series;
    }

    private static Quote fetch(String symbol,String range,String interval) throws IOException {
        String url=BASE+symbol+"?range="+range+"&interval="+interval+"&includePrePost=false&events=div%2Csplits";
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","Dash-Market-Wall/0.1.1");
        try {
            if(connection.getResponseCode()!=HttpURLConnection.HTTP_OK) throw new IOException("HTTP "+connection.getResponseCode());
            String body=read(connection.getInputStream());
            JSONObject root=new JSONObject(body).getJSONObject("chart");
            if(!root.isNull("error")) throw new IOException("Provider error");
            JSONObject result=root.getJSONArray("result").getJSONObject(0);
            JSONObject meta=result.getJSONObject("meta");
            double price=meta.optDouble("regularMarketPrice",Double.NaN);
            double previous=meta.optDouble("previousClose",Double.NaN);
            if(Double.isNaN(price) || price<=0) throw new IOException("Missing price");
            if(Double.isNaN(previous) || previous<=0) previous=price;
            JSONArray timestamps=result.optJSONArray("timestamp");
            JSONArray closes=result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).optJSONArray("close");
            ArrayList<Double> points=new ArrayList<>();
            if(closes!=null) for(int i=0;i<closes.length();i++) if(!closes.isNull(i)) {
                double value=closes.getDouble(i);if(value>0 && !Double.isNaN(value) && !Double.isInfinite(value)) points.add(value);
            }
            if(points.size()<2) points.add(price);
            double[] series=resample(points,61);
            return new Quote(price,price-previous,series);
        } catch(org.json.JSONException error) { throw new IOException("Invalid provider response",error); }
        finally { connection.disconnect(); }
    }

    private static double[] resample(List<Double> source,int size) {
        double[] result=new double[size];
        for(int i=0;i<size;i++) {
            double position=i*(source.size()-1)/(double)(size-1);
            int left=(int)Math.floor(position),right=Math.min(source.size()-1,left+1);
            double fraction=position-left;
            result[i]=source.get(left)+(source.get(right)-source.get(left))*fraction;
        }
        return result;
    }

    private static String read(InputStream stream) throws IOException {
        StringBuilder body=new StringBuilder();char[] buffer=new char[4096];
        try(Reader reader=new InputStreamReader(stream,"UTF-8")) { int count;while((count=reader.read(buffer))!=-1) body.append(buffer,0,count); }
        return body.toString();
    }

    private static final class Quote {
        final double price,change;final double[] series;
        Quote(double price,double change,double[] series){this.price=price;this.change=change;this.series=series;}
    }
    private MarketApi() { }
}
