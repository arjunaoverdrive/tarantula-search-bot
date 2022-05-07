FROM openjdk:14
MAINTAINER Igor Klimov
COPY target/TarantulaSearchBot-1.0-SNAPSHOT.jar /usr/src/tarantula-search-bot.jar
ENTRYPOINT ["java", "-jar", "/usr/src/tarantula-search-bot.jar"]