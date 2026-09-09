FROM registry.access.redhat.com/ubi9/openjdk-17:1.23 AS build
WORKDIR /tmp/build
COPY --chown=185:0 pom.xml .
COPY --chown=185:0 src src
RUN mvn -B package dependency:copy-dependencies -DincludeScope=runtime

FROM registry.access.redhat.com/ubi9/openjdk-17:1.23
WORKDIR /deployments
COPY --from=build --chown=185:0 /tmp/build/target/classes /deployments/classes
COPY --from=build --chown=185:0 /tmp/build/target/dependency /deployments/lib
USER 185
ENTRYPOINT ["java","-Xms32m","-Xmx192m","-cp","/deployments/classes:/deployments/lib/*","fr.mayabank.Main"]
CMD ["process"]
