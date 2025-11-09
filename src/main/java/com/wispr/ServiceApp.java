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

    // Java 8 讀取 body
    InputStream is = ex.getRequestBody();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
    String body = new String(bos.toByteArray(), StandardCharsets.UTF_8);

    Map<String,Object> in = GSON.fromJson(body, MAP_TYPE);

    // 允許 action01: "1,0,1" / "[1,0,1]" / "101" / 含空白換行
    String action01 = normalizeAction01(getStr(in, "action01"));
    if (action01 == null) {
      respond(ex, 400, "application/json", "{\"error\":\"missing or invalid action01 (expect 0/1 string; commas/brackets allowed)\"}");
      return;
    }

    // 允許 rowDataJson: 字串化 JSON 陣列 或 直接給陣列/物件
    String rowDataJson = normalizeRowDataJson(in.get("rowDataJson"));
    if (rowDataJson == null || rowDataJson.trim().isEmpty()) {
      respond(ex, 400, "application/json", "{\"error\":\"missing rowDataJson\"}");
      return;
    }

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

  // —— utils ——
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

  // 接受 "1,0,1" / "[1,0,1]" / "1 0 1" / "101" → 標準化成 "101"
  private static String normalizeAction01(String s) {
    if (s == null) return null;
    s = s.replaceAll("\\s+", "");             // 去空白
    s = s.replace("[", "").replace("]", "");  // 去中括號
    s = s.replace(",", "");                   // 去逗號
    if (s.isEmpty() || !s.matches("[01]+")) return null;
    return s;
  }

  // 若傳的是字串就原樣回傳；若傳的是陣列/物件就序列化成字串
  private static String normalizeRowDataJson(Object v) {
    if (v == null) return null;
    if (v instanceof String) return (String) v;
    try { return GSON.toJson(v); } catch (Exception e) { return null; }
  }

  private static void respond(HttpExchange ex, int code, String ctype, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", ctype + "; charset=utf-8");
    ex.sendResponseHeaders(code, bytes.length);
    try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
  }
}
