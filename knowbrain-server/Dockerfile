# ---- 使用本地构建的 JAR（Maven 已在宿主机完成编译）----
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S knowbrain && adduser -S knowbrain -G knowbrain

WORKDIR /app
COPY target/*.jar app.jar

# JVM 参数：堆内存 512M，容器感知，UTF-8
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8"

EXPOSE 8080

USER knowbrain
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
