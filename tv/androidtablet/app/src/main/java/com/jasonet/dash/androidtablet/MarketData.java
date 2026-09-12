package com.jasonet.dash.androidtablet;
public final class MarketData {
    public static final String[] SYMBOLS={"SPX","IXIC","HSI","SSE","BTC","NVDA","AAPL","TSLA","BABA"};
    public static final String[] NAMES={"标普500","纳斯达克","恒生指数","上证指数","比特币","英伟达","苹果","特斯拉","阿里巴巴"};
    public static final double[] PRICES={5648.40,17713.62,17444.30,2765.81,58214,119.10,222.50,226.17,84.69};
    public static final double[] CHANGES={30.26,146.03,-67.86,-4.67,713,2.24,.40,-3.49,-.63};
    public static final String[] PERIODS={"1日","1周","1月","1年"};
    public static double[] series(int symbol,int period){double p=PRICES[symbol],d=period==0?CHANGES[symbol]:p*new double[]{0,.022,-.037,.19}[period]*(symbol%2==0?1:-1);double[] v=new double[61];for(int i=0;i<=60;i++){double t=i/60.0;v[i]=p-d+d*t+(Math.sin(i*.72+symbol)+.7*Math.sin(i*.27+period))*p*.0008*Math.sin(Math.PI*t);}return v;}
    private MarketData(){}
}
