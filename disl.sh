#!/bin/sh

if [ "$#" -ne 1 ]; then
    echo "Usage: $0"
    exit;
fi

ant clean
echo "> CLEANED"

ant -Ddislclass=profiler.Instrumentation

processid=`jps | grep DiSLServer | cut -d " " -f1`

if [ -n "$processid" ]; then
  kill -9 "$processid"
  echo "AAAAAA"
fi


./startDiSLServer.sh

sleep 2

echo "> server started"
echo "running ex $1"
AGENT_FLAGS="$AGENT_FLAGS --patch-module java.base=lib/disl-bypass.jar --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED"
ARCH=`uname -p`
AGENT_EXT=.so


# ./runInstrumented.sh $1
 $JAVA_HOME/bin/java -agentpath:lib/$ARCH/libdislagent$AGENT_EXT \
  $AGENT_FLAGS -Xbootclasspath/a:lib/disl-bypass.jar:build/profiler.jar \
   -cp build/app.jar -noverify -Xms5g -Xmx5g \
   -XX:+UnlockDiagnosticVMOptions  -XX:+LogCompilation -XX:LogFile=compiler_log.xml $1


