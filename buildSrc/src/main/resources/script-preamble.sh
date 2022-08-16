#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0

echo "=== Utils located at '$UTILS'"
echo "=== CRaC JDK located at '$JDK'"

readexitcode() {
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
  else
    echo $e
  fi
}

testcheckpoint() {
  local JAR=$1
  echo "Vanilla test"
  local PROCESS=$($UTILS/start-bg.sh \
      -s "Startup completed" \
      java -jar $JAR)

  EXPECTED_RESPONSE='Hello test!'
  RESPONSE=$(curl -s localhost:8080/hello/test)
  if [ "$RESPONSE" != "$EXPECTED_RESPONSE" ]; then echo $RESPONSE && exit 1; fi
  kill $PROCESS

  echo "Prepare checkpoint"
  PROCESS=$($UTILS/start-bg.sh \
      -s "Startup completed" \
      -e exitcode \
      $JDK/bin/java \
      -XX:CRaCCheckpointTo=cr \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:+CRTraceStartupTime \
      -Djdk.crac.trace-startup-time=true \
      -jar $JAR)
  echo "Make checkpoint"
  jcmd $PROCESS JDK.checkpoint
  local foundExitCode="$(readexitcode exitcode)"
  if [ "137" != "$foundExitCode" ]; then
    echo "ERROR: Expected exit code 137, got $foundExitCode"
    kill $PROCESS
    return 1
  else
    echo "Test restore"
    PROCESS=$($UTILS/start-bg.sh \
        -s "restore-finish" \
        $JDK/bin/java -XX:CRaCRestoreFrom=cr)
    RESPONSE=$(curl -s localhost:8080/hello/test)
    if [ "$RESPONSE" != "$EXPECTED_RESPONSE" ]; then echo $RESPONSE && exit 1; fi
    kill $PROCESS
    echo "Remove Checkpoint"
    rm -rf cr
  fi
}
