import re
from datetime import datetime
from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List, Dict
import pandas as pd
import scipy
import numpy as np
import matplotlib.pyplot as plt
from collections import defaultdict
from statistics import geometric_mean


def main() -> None:
    parser: ArgumentParser = ArgumentParser("Compare instrumentation time")
    parser.add_argument(
        "--input-folder", dest="input_folder", type=Path, default=Path("./output/")
    )
    parser.add_argument(
        "--delta", type=int, default=1000, help="Timestamp in microseconds"
    )
    args: Namespace = parser.parse_args()
    input_folder: Path = args.input_folder
    # input_folder = Path("/home/ubuntu/receiver-types-profiler/overhead_times")
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    grouped = defaultdict(list)
    for input_file in input_files:
        name = input_file.stem.replace("default_", "").replace("instrumented_", "")
        grouped[name].append(input_file)
    benchmark = {}
    for k, v in grouped.items():
        times = {}
        if len(v) != 2:
            print(f"Files missing for benchmark {k}")
            continue
        for f in v:
            elapsed_times = []
            with open(f, "r") as input_file:
                content = input_file.readlines()
                for line in content:
                    matches = re.findall(r"\d:\d\d.\d\delapsed", line)
                    if len(matches) == 1:
                        try:
                            a = datetime.strptime(matches[0], "%M:%S.%felapsed")
                        except ValueError as e:
                            a = datetime.strptime(matches[0], "%M:%S:%felapsed")
                        elapsed_time = (
                            60 * a.minute + a.second + a.microsecond / 1000000
                        )
                        elapsed_times.append(elapsed_time)
                    pass
            if "instrumented" in f.name:
                times["instrumented"] = elapsed_times
            else:
                times["default"] = elapsed_times
        if len(times["default"]) != len(times["instrumented"]):
            continue
        benchmark[k] = times
    statistics = []
    for k, v in benchmark.items():
        data = compute_statistics(k, v)
        statistics.append(data)
    output_file: Path = plot_dir().joinpath("statistics.txt")
    for el in statistics:
        with output_file.open("a") as of:
            to_write = f"Benchmark: {el['name']}\n"
            to_write += f"    Defeault Mean: {el['default mean']}\n"
            to_write += f"    Instrumented Mean: {el['instrumented mean']}\n"
            to_write += f"    P-value: {el['will pvalue']}\n"
            to_write += f"    Will statistic: {el['will statistic']}\n"
            to_write += f"    Cohen d: {el['cohend']}\n"
            to_write += "\n"
            of.write(to_write)

    return


def cohen_d(deap: List[float], random: List[float]):
    return (np.mean(deap) - np.mean(random)) / (
        np.sqrt((np.std(deap) ** 2 + np.std(random) ** 2) / 2)
    )


def plot_dir() -> Path:
    p = Path(__file__).parents[1].joinpath("plots")
    if not p.is_dir():
        p.mkdir()
    return p


def compute_statistics(name: str, value: Dict[str, List[float]]):
    df: pd.DataFrame = pd.DataFrame(value)
    data = {}
    fig = plt.figure()
    bp = df.boxplot(column=["default", "instrumented"])
    plt.title(f"Statistics for {name}")
    fig.savefig(plot_dir().joinpath(f"{name}.png"))
    plt.close(fig)
    avg_default = np.mean(df["default"])
    avg_instrumented = np.mean(df["instrumented"])
    cohend = cohen_d(df["default"], df["instrumented"])
    wil = scipy.stats.wilcoxon(df["default"], df["instrumented"])
    geo_default = geometric_mean(df["default"])
    geo_instrumented = geometric_mean(df["instrumented"])
    print(f"Statistics for function {name}")
    print(f"    instrumented mean: {avg_instrumented}")
    print(f"    default mean: {avg_default}")
    print(f"    instrumented geometric mean: {geo_instrumented}")
    print(f"    default geometric mean: {geo_default}")
    print(f"    ratio: {geo_instrumented / geo_default}")
    print(f"    cohend: {cohend}")
    print(f"    wil: {wil}")
    data["name"] = name
    data["instrumented mean"] = avg_instrumented
    data["default mean"] = avg_default
    data["cohend"] = cohend
    data["will statistic"] = wil[0]
    data["will pvalue"] = wil[1]
    return data


if __name__ == "__main__":
    main()
