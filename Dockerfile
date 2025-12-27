# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/data /app/logs

# 复制jar包
COPY --from=build /app/target/*.jar app.jar

# 环境变量
ENV TZ=Asia/Shanghai

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8000/actuator/health || exit 1

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]
