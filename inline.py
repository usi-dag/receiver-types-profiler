
from argparse import ArgumentParser, Namespace
from pathlib import Path
import pandas as pd
from typing import List


def main():
    normal_folder = Path("INLINE_NORMAL")
    fix_folder = Path("INLINE_FIX")

    normal_files: List[Path] = [f for f in normal_folder.iterdir()]
    fix_files: List[Path] = [f for f in fix_folder.iterdir()]
    bench = {"name":[], "normal_uptime": [], "normal_duration": [], "fix_uptime":[], "fix_duration":[],
            "dur_impr":[], "uptime_impr":[]}
    bench_names = set()
    for input_file in normal_files:
        name = input_file.stem
        name = name[:(name.rfind("_")-len(name))]
        bench_names.add(name)

    for bench_name in bench_names:
        normal_d, normal_u = helper(normal_files, bench_name)
        fix_d, fix_u = helper(fix_files, bench_name)
        bench["name"].append(bench_name)
        bench["normal_uptime"].append(normal_u)
        bench["normal_duration"].append(normal_d)
        bench["fix_uptime"].append(fix_u)
        bench["fix_duration"].append(fix_d)
        bench["dur_impr"].append((normal_d-fix_d)/normal_d)
        bench["uptime_impr"].append((normal_u-fix_u)/normal_u)
    df: pd.DataFrame = pd.DataFrame(bench)
    df.to_csv("EXP_RES.csv")

    return

def helper(input_files: List[Path], bench_name: str):
    durations = []
    uptimes = []
    bench_files = [f for f in input_files if bench_name in f.name]
    for bench_file in bench_files:
        d,u = extract_from_file(bench_file)
        durations.append(d)
        uptimes.append(u)
    return sum(durations)/len(durations), sum(uptimes)/len(uptimes)

def extract_from_file(bench_file: Path):
    durations = []
    uptimes = []
    with open(bench_file) as f:
        lines = f.readlines()
        for line in lines[1:]:
            split_line = line.split(",")
            dur = int(split_line[1])
            uptime = int(split_line[2])
            durations.append(dur)
            uptimes.append(uptime)
    return sum(durations)/len(durations), sum(uptimes)/len(uptimes)




if __name__ == "__main__":    
    main()
