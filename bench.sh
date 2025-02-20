function  quit(){
	echo "Usage: $0 <dacapo or ren> <benchmark name>"
	exit 0
}


if [ "$#" -ne 2 ]; then
	quit
fi

case "$1" in
    "dacapo")
    		BENCH=../receiver-types-profiler/dacapo-23.11-MR2-chopin/dacapo-23.11-MR2-chopin.jar ;;
    "ren")
        BENCH=../receiver-types-profiler/renaissance-gpl-0.16.0.jar ;;
    *)
    quit ;;
esac

ant -Ddislclass=profiler.Instrumentation -buildfile ../receiver-types-profiler/build.xml


processid=`jps | grep DiSLServer | cut -d " " -f1`

if [ -n "$processid" ]; then
  kill -9 "$processid"
  echo "Old DiSLServer killed"
fi

../receiver-types-profiler/startDiSLServer.sh

sleep 2

AGENT_PATH=../receiver-types-profiler/lib/aarch64/libdislagent.so
DISL_BYPASS=../receiver-types-profiler/lib/disl-bypass.jar
PROFILER=../receiver-types-profiler/build/profiler.jar

sudo java -agentpath:$AGENT_PATH --patch-module java.base=$DISL_BYPASS \
--add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
-Xbootclasspath/a:$DISL_BYPASS:$PROFILER -noverify -cp $PROFILER \
-Xmx6G -Xms6G \
-jar $BENCH $2

