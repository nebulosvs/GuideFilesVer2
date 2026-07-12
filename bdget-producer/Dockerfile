FROM maven:3.9.9-eclipse-temurin-17 AS buildstage

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests


FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=buildstage /app/target/*.jar bdget.jar

ENV TNS_ADMIN=/app/wallet

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "bdget.jar"]