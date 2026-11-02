# ── Build stage: full JDK + Maven, dependencies cached in their own layer ──
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI; the image build only packages
RUN ./mvnw -B -q package -DskipTests && cp target/cloud-service-recommendation-*.jar app.jar

# ── Runtime stage: JRE only, non-root ──
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
