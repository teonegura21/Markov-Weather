# Dockerfile multi-etapă pentru aplicația Prognoza Meteo
# Etapa 1: compilare cu Maven
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copiază fișierele de build și sursele
COPY pom.xml .
COPY src ./src

# Compilează și împachetează aplicația (fără teste)
RUN mvn clean package -DskipTests

# Etapa 2: runtime minimal cu JRE 21
FROM eclipse-temurin:21-jre

# Instalează librăriile necesare pentru JavaFX în mediu headless/GUI
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libgtk-3-0 \
    libx11-6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiază JAR-ul executabil din etapa de build
COPY --from=build /app/target/prognoza-meteo-1.0.0.jar .

# Variabile de mediu implicite pentru conectarea la PostgreSQL
ENV DB_URL=jdbc:postgresql://postgres:5432/prognoza_meteo
ENV DB_USER=postgres
ENV DB_PASSWORD=postgres

# Punctul de intrare al aplicației
ENTRYPOINT ["java", "-jar", "prognoza-meteo-1.0.0.jar"]
