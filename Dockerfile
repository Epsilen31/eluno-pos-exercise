FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY billing-service/pom.xml ./pom.xml
COPY billing-service/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/till-1.0.0.jar billing-service/target/till-1.0.0.jar
COPY starter-kit starter-kit
COPY run.py run.py
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
CMD ["sh", "-c", "python3 run.py --no-build --port ${PORT:-8080}"]
