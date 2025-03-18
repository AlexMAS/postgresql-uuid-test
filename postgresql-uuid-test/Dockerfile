FROM gradle:8.0.2-jdk17-jammy AS builder

WORKDIR /src
COPY . /src
RUN gradle build

FROM eclipse-temurin:17.0.6_10-jre-jammy

ENV DATA_DIR=/data
ENV APP_NAME=postgresql-uuid-test
ENV APP_CLASS=org.sandbox.uuid.db.Program

COPY --from=builder /src/$APP_NAME/build/libs/*.jar $APP_NAME.jar
COPY --from=builder /src/$APP_NAME/build/libs/lib/* lib/

ENV APP_COMMAND="java -cp $APP_NAME.jar:lib/*:. $APP_CLASS \$@"

RUN echo "#!/bin/sh" > /usr/bin/app
RUN echo $APP_COMMAND >> /usr/bin/app
RUN chmod +x /usr/bin/app

ENTRYPOINT [ "app" ]
VOLUME [ "$DATA_DIR" ]
