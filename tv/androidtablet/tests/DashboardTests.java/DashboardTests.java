package com.jasonet.dash.androidtablet;

import java.io.*;
import java.util.*;

public final class DashboardTests {
  private static int checks;
  private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
  public static void main(String[] args) throws Exception {
    check(MarketData.SYMBOLS.length == 9, "symbol catalog");
    check(MarketData.PRICES.length == 9, "price catalog");
    for (double price : MarketData.PRICES) check(price > 0, "positive demo price");
    for (int[] size : new int[][] {{1280,800},{1920,1200},{2560,1600},{2560,1440}}) {
      DashboardLayout layout = new DashboardLayout(size[0], size[1]);
      check(layout.chart.height() > 300, "chart height");
      for (int i = 0; i < 18; i++) check(layout.targets[i].width() > 0, "target " + i);
    }
    MarketSnapshot original = MarketSnapshot.demo(System.currentTimeMillis());
    ByteArrayOutputStream out = new ByteArrayOutputStream(); original.write(out);
    MarketSnapshot loaded = MarketSnapshot.read(new ByteArrayInputStream(out.toByteArray()));
    check(loaded.savedAt == original.savedAt, "cache timestamp");
    check(Arrays.equals(loaded.series[0][0], original.series[0][0]), "cache round trip");
    check(NetworkStatus.from(false, false, false) == NetworkStatus.OFFLINE, "offline state");
    check(NetworkStatus.from(true, true, true) == NetworkStatus.ONLINE, "online state");
    check(NetworkStatus.from(true, true, false) == NetworkStatus.LIMITED, "limited state");
    System.out.println("PASS: " + checks + " checks; layouts, cache round-trip, network classification.");
  }
}
  
