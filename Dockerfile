FROM ghcr.io/graalvm/native-image-community:25 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -Pnative -DskipTests native:compile

FROM oraclelinux:9-slim
WORKDIR /app
COPY --from=build /workspace/target/sonnect-backend /app/sonnect-backend
EXPOSE 10000
ENTRYPOINT ["/app/sonnect-backend"]