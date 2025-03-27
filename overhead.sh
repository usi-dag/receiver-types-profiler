#!/bin/bash

function quit(){
	echo "Usage: $0 <dacapo or ren> <number of iterations per benchmark>"
	exit 0
}


if [ "$#" -ne 2 ]; then
	quit
fi


case "$1" in
    "dacapo")
        FLAGS="-n 1"
    		BENCH=dacapo-23.11-MR2-chopin.jar 
        # benchmarks=(avrora batik biojava cassandra eclipse fop graphchi h2 h2o jme jython kafka luindex lusearch pmd spring sunflow tomcat tradebeans tradesoap xalan zxing)
        benchmarks=(jme)
        ;;
    "ren")
    		FLAGS="-r 1"
        BENCH=renaissance-gpl-0.16.0.jar 
        benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
        ;;

    *)
    quit ;;
esac

GREEN='\033[0;32m'
NC='\033[0m' # No Color
echo -e "${GREEN}START RUNNING DEFAULT BENCHMARKS${NC}"

if [ ! -d overhead_times/ ]; then
  mkdir overhead_times/
fi

for entry in "${benchmarks[@]}"; do
  echo -e "${GREEN}WORKING ON BENCHMARK: ${entry}${NC}"
  TIME_FILE=overhead_times/default_"$1"_"$entry".txt
  for i in $(seq $2); do
    echo -e "${GREEN}ITERATION ${i}/${2} ${NC}"
    /usr/bin/time -o $TIME_FILE -a java -jar $BENCH $entry $FLAGS
  done
done

ant -Ddislclass=profiler.Instrumentation -buildfile build.xml
ARCH=`uname -p`


AGENT_PATH=lib/$ARCH/libdislagent.so
DISL_BYPASS=lib/disl-bypass.jar
PROFILER=build/profiler.jar


echo -e "${GREEN}START RUNNING INSTRUMENTED BENCHMARKS${NC}"
for entry in "${benchmarks[@]}"; do
  echo -e "${GREEN}WORKING ON BENCHMARK: ${entry}${NC}"
  TIME_FILE=overhead_times/instrumented_"$1"_"$entry".txt
  for i in $(seq $2); do 
    echo -e "${GREEN}ITERATION ${i}/${2} ${NC}"
    processid=`$JAVA_HOME/bin/jps | grep DiSLServer | cut -d " " -f1`

    if [ -n "$processid" ]; then
      kill -9 "$processid"
      echo "Old DiSLServer killed"
    fi

    ./startDiSLServer.sh

    sleep 2

    LOG_FILE=result/compiler_log_"$1"_"$entry"_"$i".xml

    /usr/bin/time -o $TIME_FILE -a \
    $JAVA_HOME/bin/java -agentpath:$AGENT_PATH --patch-module java.base=$DISL_BYPASS \
    -Djava.security.manager=allow \
    --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
    -Xbootclasspath/a:$DISL_BYPASS:$PROFILER -noverify -cp $PROFILER \
    -Xmx6G -Xms6G \
    -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=$LOG_FILE \
    -jar $BENCH $entry $FLAGS

    rm output/*
    rm $LOG_FILE
    # rm result/*
  done
done
