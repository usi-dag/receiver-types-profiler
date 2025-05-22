# Set up
Java 21 is required and should be installed.
Both 'ant' and 'maven' should be installed.

The renaissance suite must be downloaded.
```bash
  wget https://github.com/renaissance-benchmarks/renaissance/releases/download/v0.16.0/renaissance-gpl-0.16.0.jar
```

Likewise the dacapo suite must be downloaded.
```bash
  wget https://download.dacapobench.org/chopin/dacapo-23.11-MR2-chopin.zip
  unzip dacapo-23.11-MR2-chopin.zip
```

## Python
Create a virtual env, activate it and install the requirements.
```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```


# Structure
## Profiler
The folder 'src-profiler' contains the code that performs the insturmentation and profiling.

## Application
The folder 'src-profiler' contains the source code of a simply Java application used to test the profiling.

## Utilities
The folder 'src-utilities' contains script that can be used to make it easir to understand the output of the profiling and analysis.

## Digest Java
The folder 'src-digest' contains the code which is responsible to parse and digest the output of the profiler.
Beside some crude data digestion some basic analysis are performed as well.

## Analysis Python
The folder 'src-analysis' contains a set of script used to analyse the , digested' ouptut of the profiler.

# Scripts
## bench.sh
This script can be used to run and profile one of the benchmarks of one the two supported benchmark suites 

Use:
```bash
./bench.sh $SUITE $BENCHMARK
```

## disl.sh
This script can be used to run the test application alongside the profiler.
```bash
./disl.sh Main
```

## run_analysis.sh
This script can be used to profile and digest the data of  all the benchmarks in a benchmark suite.
```bash
	./run_analysis.sh <server or local> <dacapo or ren> <number of iterations per benchmark>
```

## hotness.sh
This script can be used to calculate the hotness of the methods of all benchmarks in a benchmark suite.
To be noted that it requires the 'collect' and 'er_print' tool from Oracle studio to be installed.
```bash
  ./hotness.sh $SUITE
```

## overhead.sh
This script is used to compute data regarding the completion time of the benchmarks in the suite with and without the instrumentation.
```bash
  ./overhead.sh $SUITE $ITERATION
```

## inversions.sh
This script can be used to invoke the analysis on all the data that was archived.

