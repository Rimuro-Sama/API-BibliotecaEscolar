# Build em duas etapas: a imagem final leva só o JRE e o .jar, sem Maven nem código-fonte.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Os testes precisam de um PostgreSQL e rodam no CI (mvn verify); aqui só empacota.
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# curl só para o healthcheck do compose
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/target/api-biblioteca-escolar-*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
