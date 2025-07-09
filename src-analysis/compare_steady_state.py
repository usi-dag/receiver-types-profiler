from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List
from scipy import stats
import numpy as np
import pandas as pd


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
    n = [(f.stem.replace("normal_", ""), f) for f in normal_runs]
    t = [(f.stem.replace("th_", ""), f) for f in tweaked_runs]
    normal_run_data = [(el[0], get_duration_uptime(el[1])) for el in n]
    tweaked_run_data = [(el[0], get_duration_uptime(el[1])) for el in t]
    normal_run_data = sorted(normal_run_data, key=lambda el: el[0])
    tweaked_run_data = sorted(tweaked_run_data, key=lambda el: el[0])
    assert len(tweaked_run_data) == len(normal_run_data)
    statistics = {}
    for normals, tweakeds in zip(normal_run_data, tweaked_run_data):
        normal_durations = normals[1][0]
        normal_uptime = normals[1][1]
        nd_mean = np.mean(normal_durations)
        np_mean = np.mean(normal_uptime)
        tweaked_durations = tweakeds[1][0]
        tweaked_uptime = tweakeds[1][1]
        td_mean = np.mean(tweaked_durations)
        tp_mean = np.mean(tweaked_uptime)
        res_durations = stats.ttest_rel(normal_durations, tweaked_durations)
        res_uptime = stats.ttest_rel(normal_uptime, tweaked_uptime)
        statistics[normals[0]] = {
            "nd_mean": nd_mean,
            "np_mean": np_mean,
            "td_mean": td_mean,
            "tp_mean": tp_mean,
            "durations p-value": res_durations.pvalue,
            "uptime p-value": res_uptime.pvalue,
        }
    df = pd.DataFrame(statistics)
    stats_file = output_folder.joinpath("statistics.csv")
    df.to_csv(stats_file)
    return


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
