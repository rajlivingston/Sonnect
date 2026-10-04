#FROM ghcr.io/graalvm/native-image-community:25 AS build
#WORKDIR /workspace
#COPY . .
#ENV NATIVE_IMAGE_OPTIONS="--parallelism=1 -J-Xmx6.5g"
#RUN chmod +x mvnw && ./mvnw -Pnative -DskipTests native:compile
#
#FROM oraclelinux:9-slim
#WORKDIR /app
#COPY --from=build /workspace/target/sonnect-backend /app/sonnect-backend
#EXPOSE 10000
#ENTRYPOINT ["/app/sonnect-backend"]

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/sonnect-backend-*.jar /app/app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]