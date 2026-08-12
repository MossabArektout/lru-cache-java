FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY src ./src

RUN javac -d out src/*.java

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/out ./out

EXPOSE 6380

CMD ["java", "-cp", "out", "CacheServer"]