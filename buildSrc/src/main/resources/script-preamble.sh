#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0

echo "=== Utils located at '$UTILS'"
echo "=== CRaC JDK located at '$JDK'"
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
  [ 137 = $($UTILS/read-exitcode.sh exitcode) ]

  echo "Test restore"
  PROCESS=$($UTILS/start-bg.sh \
      -s "restore-finish" \
      $JDK/bin/java -XX:CRaCRestoreFrom=cr)
  RESPONSE=$(curl -s localhost:8080/hello/test)
  if [ "$RESPONSE" != "$EXPECTED_RESPONSE" ]; then echo $RESPONSE && exit 1; fi
  kill $PROCESS
  echo "Remove Checkpoint"
  rm -rf cr
}
