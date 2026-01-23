#!/bin/bash

RED='\033[0;31m'
NC='\033[0m'

if [ ! -d result/ ]; then
  mkdir result/
fi


benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)

ITERATION=1
BENCH=renaissance-gpl-0.16.0.jar 

RESULT=$1
if [ ! -d "$RESULT" ]; then
  mkdir "$RESULT"
fi

for entry in "${benchmarks[@]}"; do
  for i in $(seq $ITERATION); do 
    echo -e "\e[32mWorking on $entry iteration $i\e[0m"
    $JAVA_HOME/bin/java\
    -jar $BENCH $entry --csv $RESULT/benchmark_"$entry"_"$i".csv
  done
done
