#!/bin/sh

if [ "$#" -ne 1 ]; then
    echo "Usage: $0"
    exit;
fi

ant clean
echo "> CLEANED"

ant -Ddislclass=extractor.Instrumentation

processid=`jps | grep DiSLServer | cut -d " " -f1`

if [ -n "$processid" ]; then
  kill -9 "$processid"
  echo "AAAAAA"
fi


$JAVA_HOME/bin/java -Ddisl.exclusionList="exclusion.lst" \
   -cp build/extractor.jar:lib/disl-server.jar ch.usi.dag.dislserver.DiSLServer &

sleep 2

echo "> server started"
echo "running ex $1"
AGENT_FLAGS="$AGENT_FLAGS --patch-module java.base=lib/disl-bypass.jar --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED"
ARCH=`uname -p`
AGENT_EXT=.so

AGENT_PATH=lib/$ARCH/libdislagent.so
DISL_BYPASS=lib/disl-bypass.jar
PROFILER=build/extractor.jar
LOG_FILE=result/compiler_log_"$SUITE"_"$entry"_"$i".xml
BENCH=renaissance-gpl-0.16.0.jar 


$JAVA_HOME/bin/java -agentpath:$AGENT_PATH --patch-module java.base=$DISL_BYPASS \
-Djava.security.manager=allow \
--add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
-Xbootclasspath/a:$DISL_BYPASS:$PROFILER -noverify -cp $PROFILER \
-jar $BENCH dotty -r 1

# ./runInstrumented.sh $1
 # $JAVA_HOME/bin/java -agentpath:lib/$ARCH/libdislagent$AGENT_EXT \
 #  $AGENT_FLAGS -Xbootclasspath/a:lib/disl-bypass.jar:build/extractor.jar \
 #   -cp build/app.jar -noverify -Xms5g -Xmx5g \
 #   -XX:+UnlockDiagnosticVMOptions  -XX:+LogCompilation -XX:LogFile=compiler_log.xml $1


