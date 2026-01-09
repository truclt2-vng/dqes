#!/bin/sh
#export LOG_FILE=/app/logs/${APP_NAME}${POD_IP}/dqes.log
if [ -z "$POD_NAME" ]; then
  POD_NAME=${HOSTNAME}
fi
export LOG_FILE=/app/logs/${SPRING_APPLICATION_NAME}/${POD_NAME}.log
echo "The application will start in ${JHIPSTER_SLEEP}s..." && sleep ${JHIPSTER_SLEEP}

exec java ${JAVA_OPTS} -noverify -XX:+AlwaysPreTouch -Djava.security.egd=file:/dev/./urandom --add-opens java.base/java.lang=ALL-UNNAMED -cp /app/resources/:/app/classes/:/app/libs/* "com.a4b.dqes.DqesApp" "$@"
