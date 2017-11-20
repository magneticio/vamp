FROM openjdk:8-jre-alpine

RUN addgroup -S app && adduser -S -g app app

RUN apk add --no-cache su-exec

COPY target/App.jar /app/
COPY application.conf /app/
WORKDIR /app

ADD https://github.com/magneticio/vamp-ui/releases/download/0.9.5.1/dist.tar.gz /app/
RUN mkdir ui && tar -zxvf dist.tar.gz -C ui --strip 1 && rm dist.tar.gz

ENV UI_DIRECTORY /app/ui

CMD ["su-exec", "app:app", "java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:MaxRAMFraction=1", "-Dconfig.file=/app/application.conf","-jar", "App.jar"]

EXPOSE 8080