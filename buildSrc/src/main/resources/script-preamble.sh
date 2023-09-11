#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0
DELAY=30

# Running in a GH action, we need a base-image with glibc 2.34 for the native docker builds
FIXED_IMAGE_FOR_NATIVE_ON_GITHUB=frolvlad/alpine-glibc:alpine-3.17_glibc-2.34
CRAC_NETWORK_NAME=crac-network
export LOCALHOST=localhost

# Create a docker network for external services
docker network create $CRAC_NETWORK_NAME

echo "=== Utils located at '$UTILS'"
echo "=== CRaC JDK located at '$JDK'"

# This can be used by local JAR executions to detect that they are not running in docker (for choosing a db host, etc)
export LOCALHOST=localhost

containerHealth() {
  docker inspect --format "{{.State.Health.Status}}" "$1"
}

waitContainer() {
  while STATUS=$(containerHealth "$1"); [ "$STATUS" != "healthy" ]; do
    if [ "$STATUS" == "unhealthy" ]; then
      echo "Failed!"
      exit 2
    fi
    echo -n .
    sleep 1
  done
  echo ".ok!"
}

# Requirements for the tests, called by name
requirement_mysql() {
  docker run \
    --detach \
    --rm \
    --name mysqlhost \
    --publish 3306:3306 \
    --health-cmd='mysqladmin ping --silent' \
    --network $CRAC_NETWORK_NAME \
    --network-alias mysql \
    --env MYSQL_ROOT_PASSWORD=mysql \
    --env MYSQL_DATABASE=crac \
    mysql
  waitContainer mysqlhost
  echo "-------------------------------------------------------------------------------------------"
  echo "MYSQL status ------------------------------------------------------------------------------"
  docker inspect mysqlhost | jq '.[] | {health: .State.Health.Status, network: .NetworkSettings.Networks, ports: .NetworkSettings.Ports}'
  echo "MYSQL status ------------------------------------------------------------------------------"
  echo "-------------------------------------------------------------------------------------------"
}

stop_requirement_mysql() {
  docker stop mysqlhost
}

read_exit_code() {
  local exitcode=$1
  local retries=60 # 30 seconds of 0.5 second sleeps
  e=$(head $exitcode 2>/dev/null)
  while [ ! $e ] && [ $retries -gt 0 ]; do
    sleep 0.5
    e=$(head $exitcode 2>/dev/null)
    retries=$((retries-1))
  done
  rm $exitcode
  if [ $retries -le 0 ]; then
    echo "Timeout waiting for $1"
    return 1
  else
    echo $e
  fi
}

execute() {
  # We only want to wait  seconds for success
  local end=$((SECONDS+DELAY))
  while ! curl -o /dev/null -s http://localhost:8080/; do
    if [ $SECONDS -gt $end ]; then
      echo "No response from the app in $DELAY seconds"
      return 1
    fi
    sleep 0.001;
  done
}

# From http://mywiki.wooledge.org/BashFAQ/032 capture the elapsed time in seconds
mytime() {
  exec 3>&1 4>&2
  mytime=$(TIMEFORMAT="%R"; { time $1 1>&3 2>&4; } 2>&1)
  exec 3>&- 4>&-
  echo $mytime
}

time_to_first_request_docker() {
  CONTAINER=$(docker run \
      --detach \
      --rm \
      --publish 8080:8080 \
      --network $CRAC_NETWORK_NAME \
      --env DB_HOST=db \
      --privileged \
      $1)
  result=$(mytime execute)
  echo "=== Logs from $CONTAINER" >&2
  docker logs $CONTAINER >&2
  echo "=== Killing $CONTAINER" >&2
  docker kill $CONTAINER > /dev/null
  echo $result
}

time_to_first_request() {
  $JDK/bin/java -jar $1 > /dev/null 2>&1 &
  PID=$!
  result=$(mytime execute)
  kill $PID
  echo $result
}

time_to_first_request_checkpoint() {
  local JAR=$1

  PID=$($UTILS/start-bg.sh \
      -s "Startup completed" \
      -e exitcode \
      LOCALHOST=localhost sudo $JDK/bin/java \
      -XX:CRaCCheckpointTo=cr \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:+CRTraceStartupTime \
      -Djdk.crac.trace-startup-time=true \
      -jar $JAR)

  # The PID is the PID of the sudo command, so get the java command:
  JAVA_PID=$(ps --ppid $PID -o pid=)

  echo "-- Curl response" 1>&2
  curl -o /dev/null localhost:8080 1>&2
  echo "-- Sending JDK.checkpoint to $JAVA_PID" 1>&2

  sudo jcmd $JAVA_PID JDK.checkpoint 1>&2
  local foundExitCode="$(read_exit_code exitcode)"
  if [ "137" != "$foundExitCode" ]; then
    echo "ERROR: Expected checkpoint exit code 137, got $foundExitCode" 1>&2
    sudo kill $JAVA_PID 1>&2
    return 1
  else
    sudo $JDK/bin/java -XX:CRaCRestoreFrom=cr > /dev/null 1>&2 &
    PID=$!
    result=$(mytime execute)
    JAVA_PID=$(ps --ppid $PID -o pid=)
    sudo kill $JAVA_PID 1>&2
    echo $result
  fi
}

build_gradle_docker() {
  ./gradlew dockerBuild || EXIT_STATUS=$?
}

build_maven_docker() {
  ./mvnw --no-transfer-progress package -Dpackaging=docker || EXIT_STATUS=$?
}

build_gradle_docker_native() {
  ./gradlew dockerBuildNative || EXIT_STATUS=$?
}

build_maven_docker_native() {
  ./mvnw --no-transfer-progress package -Dpackaging=docker-native -Pgraalvm -Dmicronaut.native-image.base-image-run=$FIXED_IMAGE_FOR_NATIVE_ON_GITHUB || EXIT_STATUS=$?
}

build_gradle_docker_crac() {
  ./gradlew dockerBuildCrac || EXIT_STATUS=$?
}

build_maven_docker_crac() {
  ./mvnw --no-transfer-progress package -Dpackaging=docker-crac -Dcrac.checkpoint.network=$CRAC_NETWORK_NAME || EXIT_STATUS=$?
}

assemble_gradle() {
  ./gradlew assemble
}

assemble_maven() {
  ./mvnw --no-transfer-progress clean package
}

gradle() {
  ### PATCH THE GRADLE BUILD FOR GLIBC AND DOCKER NETWORK
  cat << EOF >> build.gradle
tasks.named('dockerfileNative') {
    baseImage = '$FIXED_IMAGE_FOR_NATIVE_ON_GITHUB'
}

micronaut {
  crac {
    network = '$CRAC_NETWORK_NAME'
  }
}
EOF

  echo ""
  echo "--------------------------------------------"
  echo "Running ./gradlew dockerBuild"
  echo "--------------------------------------------"
  echo ""
  # Build regular app in docker, and rename image to micronautguide-standard
  build_gradle_docker
  docker tag micronautguide:latest micronautguide-standard:latest

  echo ""
  echo "--------------------------------------------"
  echo "Running ./gradlew dockerBuildNative"
  echo "--------------------------------------------"
  echo ""
  # Build native app in docker, and rename image to micronautguide-native
  build_gradle_docker_native
  docker tag micronautguide:latest micronautguide-native:latest

  echo ""
  echo "--------------------------------------------"
  echo "Running ./gradlew dockerBuildCrac"
  echo "--------------------------------------------"
  echo ""
  # Build crac app in docker
  build_gradle_docker_crac

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard docker image"
  echo "--------------------------------------------"
  echo ""
  standard=$(time_to_first_request_docker micronautguide-standard:latest)

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard native image"
  echo "--------------------------------------------"
  echo ""
  native=$(time_to_first_request_docker micronautguide-native:latest)

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard crac image"
  echo "--------------------------------------------"
  echo ""
  crac=$(time_to_first_request_docker micronautguide:latest)

  echo ""
  echo "--------------------------------------------"
  echo "Running ./gradlew assemble"
  echo "--------------------------------------------"
  echo ""
  assemble_gradle

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard jar"
  echo "--------------------------------------------"
  echo ""
  jar=$(time_to_first_request 'build/libs/micronautguide-0.1-all.jar')

  echo ""
  echo "--------------------------------------------"
  echo "Snapshotting and timing crac jar"
  echo "--------------------------------------------"
  echo ""
  jar_crac=$(time_to_first_request_checkpoint 'build/libs/micronautguide-0.1-all.jar')

  echo "## Summary" >> $GITHUB_STEP_SUMMARY
  echo "### Docker" >> $GITHUB_STEP_SUMMARY
  echo "| Build type | Time to First Request (secs) | Scale |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- | --- |" >> $GITHUB_STEP_SUMMARY
  echo "| Standard Docker | $standard | $(bc -l <<< "scale=3; $standard/$standard") ($(bc -l <<< "scale=1; $standard/$standard")x) |" >> $GITHUB_STEP_SUMMARY
  echo "| Native Docker | $native | $(bc -l <<< "scale=3; $native/$standard")  ($(bc -l <<< "scale=1; $standard/$native")x) |" >> $GITHUB_STEP_SUMMARY
  echo "| CRaC Docker | $crac | $(bc -l <<< "scale=3; $crac/$standard")  ($(bc -l <<< "scale=1; $standard/$crac")x) |" >> $GITHUB_STEP_SUMMARY
  echo "### FatJar" >> $GITHUB_STEP_SUMMARY
  echo "| Build type | Time to First Request (secs) | Scale |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- | --- |" >> $GITHUB_STEP_SUMMARY
  echo "| Standard FatJar | $jar | $(bc -l <<< "scale=3; $jar/$jar")  ($(bc -l <<< "scale=1; $jar/$jar")x) |" >> $GITHUB_STEP_SUMMARY
  echo "| CRaC FatJar | $jar_crac | $(bc -l <<< "scale=3; $jar_crac/$jar")  ($(bc -l <<< "scale=1; $jar/$jar_crac")x) |" >> $GITHUB_STEP_SUMMARY
  echo "" >> $GITHUB_STEP_SUMMARY

  # Remove images
  docker image rm micronautguide-standard:latest
  docker image rm micronautguide-native:latest
  docker image rm micronautguide:latest
}

maven() {
  echo ""
  echo "--------------------------------------------"
  echo "Building standard docker image"
  echo "--------------------------------------------"
  echo ""
  # Build regular app in docker, and rename image to micronautguide-standard
  build_maven_docker
  docker tag micronautguide:latest micronautguide-standard-maven:latest

  echo ""
  echo "--------------------------------------------"
  echo "Building native docker image"
  echo "--------------------------------------------"
  echo ""
  # Build native app in docker, and rename image to micronautguide-native
  build_maven_docker_native
  docker tag micronautguide:latest micronautguide-native-maven:latest

  echo ""
  echo "--------------------------------------------"
  echo "Building crac docker image"
  echo "--------------------------------------------"
  echo ""
  # Build crac app in docker
  build_maven_docker_crac
  docker tag micronautguide:latest micronautguide-maven:latest

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard docker image"
  echo "--------------------------------------------"
  echo ""
  standard=$(time_to_first_request_docker micronautguide-standard-maven:latest)

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard native image"
  echo "--------------------------------------------"
  echo ""
  native=$(time_to_first_request_docker micronautguide-native-maven:latest)

  echo ""
  echo "--------------------------------------------"
  echo "Timing standard crac image"
  echo "--------------------------------------------"
  echo ""
  crac=$(time_to_first_request_docker micronautguide-maven:latest)

  assemble_maven
  jar=$(time_to_first_request 'target/micronautguide-0.1.jar')
  jar_crac=$(time_to_first_request_checkpoint 'target/micronautguide-0.1.jar')

  echo "## Summary" >> $GITHUB_STEP_SUMMARY
  echo "### Docker" >> $GITHUB_STEP_SUMMARY
  echo "| Build type | Time to First Request (secs) | Scale |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- | --- |" >> $GITHUB_STEP_SUMMARY
  echo "| Standard Docker | $standard | $(bc -l <<< "scale=3; $standard/$standard") ($(bc -l <<< "scale=1; $standard/$standard")x) |" >> $GITHUB_STEP_SUMMARY
  echo "| Native Docker | $native | $(bc -l <<< "scale=3; $native/$standard")  ($(bc -l <<< "scale=1; $standard/$native")x) |" >> $GITHUB_STEP_SUMMARY
  echo "| CRaC Docker | $crac | $(bc -l <<< "scale=3; $crac/$standard")  ($(bc -l <<< "scale=1; $standard/$crac")x) |" >> $GITHUB_STEP_SUMMARY
  echo "### FatJar" >> $GITHUB_STEP_SUMMARY
  echo "| Build type | Time to First Request (secs) | Scale |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- | --- |" >> $GITHUB_STEP_SUMMARY
  echo "| Standard FatJar | $jar | $(bc -l <<< "scale=3; $jar/$jar")  ($(bc -l <<< "scale=1; $jar/$jar")x) |" >> $GITHUB_STEP_SUMMARY
  echo "| CRaC FatJar | $jar_crac | $(bc -l <<< "scale=3; $jar_crac/$jar")  ($(bc -l <<< "scale=1; $jar/$jar_crac")x) |" >> $GITHUB_STEP_SUMMARY
  echo "" >> $GITHUB_STEP_SUMMARY

  # Remove images
  docker image rm micronautguide-maven:latest
  docker image rm micronautguide-native-maven:latest
  docker image rm micronautguide-standard-maven:latest
}
