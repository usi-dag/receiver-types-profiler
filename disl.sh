#!/bin/sh


RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

DEBUG=false
CLEAN=false
SKIP=false

while :; do
    case $1 in
        -d|--debug) DEBUG=true            
        ;;
        -c|--clear) CLEAN=true
        ;;
        -s|--skip) SKIP=true
        ;;
        *) break
    esac
    shift
done


if [ "$CLEAN" = true ]; then
 if [ -d "output" ] && [ "$(ls -A output)" ]; then
  rm -rf output/*
 fi
 if [ -d "result" ] && [ "$(ls -A result)" ]; then
  rm -rf result/*
 fi
fi


ant clean
echo "${GREEN}ANT CLEANED${NC}"

ant -Ddislclass=profiler.Instrumentation -Ddisltransformer=profiler.MyTransformer

if [ $? -ne 0 ]; then
 echo "${RED}Failed building profiler.${NC}"
 exit 1
fi

processid=`$JAVA_HOME/bin/jps | grep DiSLServer | cut -d " " -f1`

if [ -n "$processid" ]; then
 echo "${RED}Existing disl server killed${NC}"
  kill -9 "$processid"
fi

./startDiSLServer.sh -d

sleep 2

echo "${GREEN}DiSL server started${NC}"
AGENT_FLAGS="$AGENT_FLAGS --patch-module java.base=lib/disl-bypass.jar --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED"
ARCH=`uname -p`
AGENT_EXT=.so

if [[ "$(uname)" == "Darwin" ]]; then
 AGENT_EXT=.jnilib
fi

if [ -n "$GRAAL" ]; then
 echo "Using GraalVM"
 GRAAL_FLAGS="-server -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI --add-exports=java.base/jdk.internal.misc=jdk.graal.compiler -Djdk.graal.CompilationFailureAction=Diagnose -Djdk.graal.DumpOnError=true -Djdk.graal.ShowDumpFiles=true -Djdk.graal.PrintGraph=Network -Djdk.graal.ObjdumpExecutables=objdump,gobjdump -Dgraalvm.locatorDisabled=true"
fi

LOG_FILE=compiler_log.xml
ANALYSISHEAP=10G


 $JAVA_HOME/bin/java $GRAAL_FLAGS \
  -agentpath:lib/libnativeagent.so \
  -agentpath:lib/$ARCH/libdislagent$AGENT_EXT \
  --patch-module java.base=lib/disl-bypass.jar \
  --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
  -Xbootclasspath/a:lib/disl-bypass.jar:build/profiler.jar \
  -cp build/app.jar -noverify -Xms5g -Xmx5g \
  -XX:+UnlockDiagnosticVMOptions  -XX:+LogCompilation \
  -XX:CompilationMode=high-only \
  -XX:LogFile=$LOG_FILE \
  Main
  # -XX:CompileCommand=dontinline,profiler/Profiler.* \


if [ "$SKIP" = true ]; then
 exit 0
fi

sleep 3
$JAVA_HOME/bin/java -Xmx$ANALYSISHEAP -classpath src-digest/target/classes/ com.msde.app.App -i output/ -c $LOG_FILE -d 1000 

if [ $? -ne 0 ]; then
  echo "${RED}Something went wrong analyzing the defaulte application${NC}"
  continue
fi
   
