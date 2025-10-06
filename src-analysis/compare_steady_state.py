from argparse import ArgumentParser, Namespace
from collections import defaultdict
from pathlib import Path
from typing import List
from scipy import stats
import numpy as np
import pandas as pd
import re


def main():
    parser: ArgumentParser = ArgumentParser("Compare steady state.")
    parser.add_argument(
        "--input-folder",
        dest="input_folder",
        type=Path,
        default=Path("./threshold_result/"),
    )
    parser.add_argument(
        "--output-folder",
        dest="output_folder",
        type=Path,
        default=Path("./threshold_result/"),
    )
    args: Namespace = parser.parse_args()
    input_folder: Path = args.input_folder
    output_folder: Path = args.output_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    normal_runs = [f for f in input_files if f.name.startswith("normal_")]
    tweaked_runs = [f for f in input_files if f.name.startswith("th_")]
    normal_data = extract_data(normal_runs)
    tweaked_data = extract_data(tweaked_runs)
    statistics = {}
    for bench, n in normal_data.items():
        normal_durations = n["duration"]
        normal_uptime = n["uptime"]
        nd_mean = np.mean(normal_durations)
        np_mean = np.mean(normal_uptime)
        nd_std = np.std(normal_durations)
        np_std = np.std(normal_uptime)

        tweaked_durations = tweaked_data[bench]["duration"]
        tweaked_uptime = tweaked_data[bench]["uptime"]
        td_mean = np.mean(tweaked_durations)
        tp_mean = np.mean(tweaked_uptime)
        td_std = np.std(tweaked_durations)
        tp_std = np.std(tweaked_uptime)
        duration_percentage_dif = 100 - (td_mean / nd_mean) * 100
        uptime_percentage_dif = 100 - (tp_mean / np_mean) * 100
        # TODO: add percentage difference
        #       and maybe the statistical stuff.
        res_durations = stats.ttest_rel(normal_durations, tweaked_durations)
        res_uptime = stats.ttest_rel(normal_uptime, tweaked_uptime)
        statistics[bench] = {
            "nd_mean": nd_mean,
            "np_mean": np_mean,
            "nd_std": nd_std,
            "np_std": np_std,
            "td_mean": td_mean,
            "tp_mean": tp_mean,
            "td_std": td_std,
            "tp_std": tp_std,
            "durations p-value": res_durations.pvalue,
            "uptime p-value": res_uptime.pvalue,
            "duration percentage difference": duration_percentage_dif,
            "uptime percentage difference": uptime_percentage_dif,
        }
    df = pd.DataFrame(statistics)
    df = df.T.sort_values("duration percentage difference", ascending=False)
    df_sig = df[df["durations p-value"] < 0.05]
    stats_file = output_folder.joinpath("statistics.csv")
    latex = df.to_latex()
    with open(output_folder.joinpath("latex.tex"), "w") as f:
        f.write(latex)
    stats_file_sig = output_folder.joinpath("statistics_sig.csv")
    df.to_csv(stats_file)
    df_sig.to_csv(stats_file_sig)
    return


def extract_data(files: List[Path]):
    bench_to_data = defaultdict(lambda: defaultdict(list))
    for f in files:
        bench = re.sub("th_|normal_|_\d+.csv", "", f.name)
        with open(f) as infile:
            line = infile.readlines()[-1]
            duration = int(line.split(",")[1])
            uptime = int(line.split(",")[2])
            bench_to_data[bench]["duration"].append(duration)
            bench_to_data[bench]["uptime"].append(uptime)
    return bench_to_data



def get_duration_uptime(input_file: Path):
    with open(input_file) as f:
        last_lines = f.readlines()[-100:]
        duration_uptime = [
            (int(l.split(",")[1]), int(l.split(",")[2])) for l in last_lines
        ]
        durations = [el[0] for el in duration_uptime]
        uptimes = [el[1] for el in duration_uptime]
        return durations, uptimes


if __name__ == "__main__":
    main()
