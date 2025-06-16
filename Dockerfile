FROM eclipse-temurin:21-jdk-jammy
WORKDIR /opt/app
COPY target/*.jar /opt/app/app.jar

CMD ["java", "-jar", "/opt/app/app.jar"]
