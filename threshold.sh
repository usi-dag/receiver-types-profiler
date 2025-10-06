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
# FLAGS="-r $ITERATION"

benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
# benchmarks=(scrabble rx-scrabble dotty mnemonics)

RESULT=threshold_result/
if [ ! -d $RESULT ]; then
  mkdir $RESULT
fi

for entry in "${benchmarks[@]}"; do
  case "$entry" in
    "dacapo")
        FLAGS="-n 1"
    		BENCH=dacapo-23.11-MR2-chopin.jar 
        # benchmarks=(avrora batik biojava cassandra eclipse fop graphchi h2 h2o jme jython kafka luindex lusearch pmd spring sunflow tomcat tradebeans tradesoap xalan zxing)
        benchmarks=(jme)
        ;;
    "ren")
    		FLAGS="-r 1"
        BENCH=renaissance-gpl-0.16.0.jar 
        # benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
        benchmarks=(rx-scrabble)
        ;;

    *)
    quit ;;
  esac

    for i in $(seq $ITERATION); do   
      $JAVA_HOME/bin/java \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:CompilationMode=high-only \
      -jar $BENCH $entry --csv $RESULT/normal_"$entry"_"$i".csv


      sleep 3

      $JAVA_HOME/bin/java \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:CompilationMode=high-only \
      -XX:Tier4InvocationThreshold=$TIER4 \
      -jar $BENCH $entry --csv $RESULT/th_"$entry"_"$i".csv

      if [ $? -ne 0 ]; then
        echo Something went wrong analyzing $SUITE $entry iteration $i
        continue
      fi
  done
done



