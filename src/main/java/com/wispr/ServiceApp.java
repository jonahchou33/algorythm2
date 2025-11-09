package com.wispr;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ServiceApp {

  private static final Gson GSON = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String,Object>>(){}.getType();

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("PORT", "8080"));
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/health", ServiceApp::handleHealth);
    server.createContext("/run",    ServiceApp::handleRun);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    System.out.println("Server started on port " + port);
  }

  private static void handleHealth(HttpExchange ex) throws IOException {
    respond(ex, 200, "text/plain", "OK");
  }

  private static void handleRun(HttpExchange ex) throws IOException {
    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
      respond(ex, 405, "text/plain", "Method Not Allowed");
      return;
    }

    // === Java 8 友善讀法，取代 readAllBytes() ===
    InputStream is = ex.getRequestBody();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
    String body = new String(bos.toByteArray(), StandardCharsets.UTF_8);

    Map<String,Object> in = GSON.fromJson(body, MAP_TYPE);

    String action01     = getStr(in, "action01");
    String rowDataJson  = getStr(in, "rowDataJson");

    Integer binSizeSec  = getInt(in, "binSizeSec");
    Integer preSec      = getInt(in, "preSec");
    Integer actSec      = getInt(in, "actSec");
    Integer postSec     = getInt(in, "postSec");
    Integer startPad    = getInt(in, "startPaddingSplitSec");
    Integer shift       = getInt(in, "testTimeShiftSec");
    Integer endPad      = getInt(in, "endPaddingSec");
    Integer manualNo    = getInt(in, "manualNoActPeriodSec");
    Integer maxLag      = getInt(in, "maxLag");
    Double  minP        = getDbl(in, "minPThreshold");
    Integer minSpikes   = getInt(in, "minRequiredSpikes");
    Integer patience    = getInt(in, "earlyStopPatience");
    Integer defInterval = getInt(in, "defaultIntervalSec");
    Integer defNumActs  = getInt(in, "defaultNumActions");

    String out;
    try {
      out = AlgorithumTwoService.runAlgorithmFromJson(
          action01, rowDataJson,
          binSizeSec, preSec, actSec, postSec,
          startPad, shift, endPad, manualNo,
          maxLag, minP, minSpikes, patience,
          defInterval, defNumActs
      );
    } catch (Exception e) {
      out = "{\"error\":\"" + e.toString().replace("\"","'") + "\"}";
    }
    respond(ex, 200, "application/json", out);
  }

  private static String getStr(Map<String,Object> m, String k) {
    Object v = m.get(k); return v==null? null : String.valueOf(v);
  }
  private static Integer getInt(Map<String,Object> m, String k) {
    Object v = m.get(k); if (v==null) return null;
    return (v instanceof Number)? ((Number)v).intValue() : Integer.valueOf(String.valueOf(v));
  }
  private static Double getDbl(Map<String,Object> m, String k) {
    Object v = m.get(k); if (v==null) return null;
    return (v instanceof Number)? ((Number)v).doubleValue() : Double.valueOf(String.valueOf(v));
  }

  private static void respond(HttpExchange ex, int code, String ctype, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", ctype + "; charset=utf-8");
    ex.sendResponseHeaders(code, bytes.length);
    try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
  }
}
