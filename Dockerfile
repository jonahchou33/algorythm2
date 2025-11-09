FROM eclipse-temurin:17-jdk
WORKDIR /app

# 複製原始碼（包含 gradlew 與 wrapper）
COPY . .

# 建置可執行目錄（用你本地已驗證的 installDist）
RUN chmod +x ./gradlew || true \
 && ./gradlew --no-daemon clean installDist

# 記憶體與埠（Render Free 較穩）
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m"
ENV PORT=8080
EXPOSE 8080

# 啟動腳本（等同本地：PORT=8080 ./build/install/wispr_server/bin/wispr_server）
CMD ["sh","-lc","PORT=${PORT} ./build/install/wispr_server/bin/wispr_server"]
