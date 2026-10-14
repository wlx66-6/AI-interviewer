# 构建阶段：用 JDK 镜像编译出可执行 JAR
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# 先复制构建脚本，命中 Docker 层缓存以跳过重复的依赖下载
COPY settings.gradle gradlew ./
COPY gradle gradle
COPY app/build.gradle app/
RUN ./gradlew dependencies --no-daemon || true

# 复制源码并打包（跳过测试，测试由 CI 单独执行）
COPY . .
RUN ./gradlew :app:bootJar --no-daemon -x test

# 运行阶段：只保留 JRE 与构建产物，显著减小镜像体积
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /workspace/app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]