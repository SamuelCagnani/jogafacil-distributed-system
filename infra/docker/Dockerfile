# syntax=docker/dockerfile:1
#
# Build multi-stage reutilizavel para os servicos Java.
# A mesma imagem e usada para reservation-service e match-service,
# escolhendo o modulo pelo argumento SERVICE.
#
# Build (a partir da raiz do repositorio):
#   docker build -f infra/docker/Dockerfile --build-arg SERVICE=reservation-service -t jogafacil/reservation-service .

# ---- Estagio 1: build com Maven + JDK 21 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace

# Copia o repositorio e compila apenas o modulo alvo (e suas dependencias).
COPY . .
RUN mvn -q -pl services/${SERVICE} -am package -DskipTests

# ---- Estagio 2: runtime apenas com o JRE 21 ----
FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/services/${SERVICE}/target/${SERVICE}-*.jar app.jar

# Porta padrao dos servicos (8081 reservas, 8082 partidas).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
