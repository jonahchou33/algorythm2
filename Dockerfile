cat > Dockerfile <<'EOF'
FROM eclipse-temurin:17-jdk
WORKDIR /app

# 複製專案原始碼
COPY . .

# 確保 wrapper 可執行，然後建置可執行目錄
RUN chmod +x ./gradlew || true \
 && ./gradlew --no-daemon clean installDist

# 設定埠與 JVM 記憶體（Free 方案較安全）
ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m"

EXPOSE 8080
# 啟動你本地已驗證可跑的腳本
CMD ["sh","-lc","PORT=${PORT} ./build/install/wispr_server/bin/wispr_server"]
EOF
