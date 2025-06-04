#!/bin/bash

function quit(){
	echo "Usage: $0 <input folder> <hotness folder> <output folder>"
	exit 0
}

if [ "$#" -ne 3 ]; then
	quit
fi

EXTRACTION_DIR=extract
INPUT=$1
HOTNESS=$2
BASE_OUTPUT=$3

if [ ! -d $BASE_OUTPUT ]; then
  mkdir $BASE_OUTPUT
fi

if [ ! -d $EXTRACTION_DIR ]; then
  mkdir $EXTRACTION_DIR
fi

rm -r $EXTRACTION_DIR/*

GREEN='\033[0;32m'
NC='\033[0m'

source .venv/bin/activate

for f in $INPUT/*; do
  echo -e "${GREEN}WORKING ON FILE $f${NC}"
  base=$(basename -- "$f")
  iteration="${base/ren_/}"
  iteration="${iteration/.tar.gz/}"
  name=$(echo "$iteration" | sed -E "s/_[0-9]+//")
  OUTPUT=$BASE_OUTPUT/$iteration
  HOTNESS_FILE=$HOTNESS/hot_methods_$name.csv
  tar -xzf $f -C $EXTRACTION_DIR
  python3 src-analysis/analyse_callsites.py --input-folder=$EXTRACTION_DIR/result --output-folder=$OUTPUT --hotness=$HOTNESS_FILE --name=$iteration

  if [ $? -ne 0 ]; then
    echo Something went wrong while analyzing $f
    rm -r $EXTRACTION_DIR/*
    continue
  fi
  rm -r $EXTRACTION_DIR/*
done


