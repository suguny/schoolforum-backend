FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]