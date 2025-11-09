package com.wispr;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

  private static void handleHealth(HttpExchange ex) {
    respond(ex, 200, "text/plain", "OK");
  }

  // 僅轉送：actionSignalStr、rowDataJson 必須是字串；其餘如實傳遞（可為 null）
  private static void handleRun(HttpExchange ex) {
    try {
      if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
        respond(ex, 405, "text/plain", "Method Not Allowed");
        return;
      }

      String body = readBody(ex);
      Map<String,Object> in = GSON.fromJson(body, MAP_TYPE);
      if (in == null) {
        respond(ex, 400, "application/json","{\"error\":\"invalid json\"}");
        return;
      }

      // 這裡改成讀新鍵名
      Object aObj = in.get("actionSignalStr");
      Object bObj = in.get("rowDataJson");
      if (!(aObj instanceof String) || !(bObj instanceof String)) {
        respond(ex, 400, "application/json","{\"error\":\"actionSignalStr and rowDataJson must be strings\"}");
        return;
      }
      String actionSignalStr = (String) aObj;
      String rowDataJson     = (String) bObj;

      // 其他參數：有給且可轉就轉；沒給或字串"null"→null
      Integer binSizeSec           = toInt(in.get("binSizeSec"));
      Integer preSec               = toInt(in.get("preSec"));
      Integer actSec               = toInt(in.get("actSec"));
      Integer postSec              = toInt(in.get("postSec"));
      Integer startPaddingSplitSec = toInt(in.get("startPaddingSplitSec"));
      Integer testTimeShiftSec     = toInt(in.get("testTimeShiftSec"));
      Integer endPaddingSec        = toInt(in.get("endPaddingSec"));
      Integer manualNoActPeriodSec = toInt(in.get("manualNoActPeriodSec"));
      Integer maxLag               = toInt(in.get("maxLag"));
      Double  minPThreshold        = toDbl(in.get("minPThreshold"));
      Integer minRequiredSpikes    = toInt(in.get("minRequiredSpikes"));
      Integer earlyStopPatience    = toInt(in.get("earlyStopPatience"));
      Integer defaultIntervalSec   = toInt(in.get("defaultIntervalSec"));
      Integer defaultNumActions    = toInt(in.get("defaultNumActions"));

      String out = AlgorithumTwoService.runAlgorithmFromJson(
          actionSignalStr, rowDataJson,
          binSizeSec, preSec, actSec, postSec,
          startPaddingSplitSec, testTimeShiftSec, endPaddingSec, manualNoActPeriodSec,
          maxLag, minPThreshold, minRequiredSpikes, earlyStopPatience,
          defaultIntervalSec, defaultNumActions
      );

      respond(ex, 200, "application/json", out);
    } catch (Exception e) {
      respond(ex, 500, "application/json",
          "{\"error\":\"" + e.toString().replace("\"","'") + "\"}");
    }
  }

  // —— minimal helpers ——
  private static Integer toInt(Object v) {
    if (v == null) return null;
    if (v instanceof Number) return ((Number)v).intValue();
    if (v instanceof String) {
      String s = ((String)v).trim();
      if (s.isEmpty() || s.equalsIgnoreCase("null")) return null;
      try { return Integer.valueOf(s); } catch (Exception ignore) { return null; }
    }
    return null;
  }

  private static Double toDbl(Object v) {
    if (v == null) return null;
    if (v instanceof Number) return ((Number)v).doubleValue();
    if (v instanceof String) {
      String s = ((String)v).trim();
      if (s.isEmpty() || s.equalsIgnoreCase("null")) return null;
      try { return Double.valueOf(s); } catch (Exception ignore) { return null; }
    }
    return null;
  }

  private static String readBody(HttpExchange ex) throws Exception {
    try (InputStream is = ex.getRequestBody();
         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
      return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static void respond(HttpExchange ex, int code, String ctype, String body) {
    try {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", ctype + "; charset=utf-8");
      ex.sendResponseHeaders(code, bytes.length);
      try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    } catch (Exception ignore) {}
  }
}
