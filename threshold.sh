#!/bin/bash

function quit(){
	echo "Usage: $0 <number of iterations per benchmark> <tier4 threshold>"
	exit 0
}


if [ "$#" -ne 2 ]; then
	quit
fi

BENCH=renaissance-gpl-0.16.0.jar 
ITERATION=$1
TIER4=$2
FLAGS="-r $ITERATION"

benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
# benchmarks=(scrabble rx-scrabble dotty mnemonics)

RESULT=threshold_result/
if [ ! -d $RESULT ]; then
  mkdir $RESULT
fi

for entry in "${benchmarks[@]}"; do
  LOG_FILE=$RESULT/compiler_log_"$entry"_normal.xml
  $JAVA_HOME/bin/java \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:CompilationMode=high-only \
  -XX:+LogCompilation -XX:LogFile=$LOG_FILE \
  -jar $BENCH $entry $FLAGS --csv $RESULT/normal_"$entry".csv


  sleep 3

  LOG_FILE=$RESULT/compiler_log_"$entry"_th.xml
  $JAVA_HOME/bin/java \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:CompilationMode=high-only \
  -XX:+LogCompilation -XX:LogFile=$LOG_FILE \
  -XX:Tier4InvocationThreshold=$TIER4 \
  -jar $BENCH $entry $FLAGS --csv $RESULT/th_"$entry".csv

  if [ $? -ne 0 ]; then
    echo Something went wrong analyzing $SUITE $entry iteration $i
    continue
  fi
done
