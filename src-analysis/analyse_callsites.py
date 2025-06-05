import re
import csv
from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List, Dict
from dataclasses import dataclass
from collections import defaultdict
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import matplotlib as mpl
from functools import reduce


def main():
    parser: ArgumentParser = ArgumentParser(
        "Analysis",
        description="This script can be used to analyse the result of runnig the instrumentation and data digestion",
    )
    parser.add_argument(
        "--input-folder", dest="input_folder", type=Path, default=Path("./result/")
    )
    parser.add_argument("--hotness", dest="hotness", type=Path, required=True)
    parser.add_argument(
        "--output-folder", dest="output_folder", type=Path, default=Path("./result/")
    )
    parser.add_argument("--name", dest="name", type=Path, required=True)
    args: Namespace = parser.parse_args()
    input_folder = args.input_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    # input_files = [f for f in input_files if f.name == "result_114.txt"]
    output_folder: Path = args.output_folder
    if not output_folder.is_dir():
        output_folder.mkdir()
    hotness: Path = args.hotness
    result_files = [f for f in input_files if f.name.startswith("result")]
    statistics = {}
    out = output_folder.joinpath(args.name)
    out.mkdir(exist_ok=True)
    callsite_id = 0
    callsite_to_id = {}
    for f in result_files:
        callsites = extract_callsite_information(f)
        for callsite in callsites:
            c, i = extract_compilation_decompilation(callsite)
            # NOTE: this mapping between callsite and id is necessary since the callsite might exceed
            # the limit of a valid file name length on linux
            callsite_to_id[callsite.callsite] = callsite_id
            callsite_id += 1
            oscillations, inversions_to_count = find_oscillations(i)
            save_oscillations(oscillations, out, callsite_to_id[callsite.callsite])
            most_frequent_oscillation = max(
                oscillations, key=oscillations.get, default=()
            )
            max_oscillation_frequency = oscillations.get(most_frequent_oscillation)
            most_frequent_inversion = max(
                inversions_to_count, key=inversions_to_count.get, default=()
            )
            max_inversion_frequency = inversions_to_count.get(most_frequent_inversion)
            comp_id_to_count = number_of_windows_before_decompilation(callsite)
            if len(comp_id_to_count) > 0:
                average_inversions_before_decompilations = reduce(
                    lambda a, b: a + b, comp_id_to_count.values(), 0
                ) / len(comp_id_to_count)
            else:
                average_inversions_before_decompilations = 0
            raw = callsite.raw
            statistics[callsite.callsite] = {
                "changes": len(callsite.changes),
                "inversions": len(callsite.inversions),
                "compilations": len(callsite.compilations()),
                "decompilations": len(callsite.decompilations()),
                "changes after compilation": len(c),
                "inversions after compilation": len(i),
                "most frequent oscillation": str(most_frequent_oscillation),
                "most frequent oscillation value": max_oscillation_frequency,
                "most frequent inversion": str(most_frequent_inversion),
                "most frequent inversion value": max_inversion_frequency,
                "average_inversions_before_decompilation": average_inversions_before_decompilations,
            }
            compile_id_to_receiver_count = find_receiver_reduction(raw)
            save_receiver_reduction(
                compile_id_to_receiver_count, out, callsite_to_id[callsite.callsite]
            )
    df = pd.DataFrame(statistics)
    df = df.T
    df = df.reset_index()
    df["id"] = df.index

    def extract_method_name(callsite):
        m = callsite["index"].split(" ")[1]
        m = m[: m.index(")") + 1]
        return m

    df["method_descriptor"] = df.apply(extract_method_name, axis=1)
    normalized_df = normalize_by_hotness(df, hotness)
    normalized_csv = out.joinpath(f"{args.name}_normalized.csv")
    normalized_df.to_csv(normalized_csv)
    df = df.sort_values("inversions after compilation", ascending=False)
    df.to_csv(output_folder.joinpath(f"{args.name}_statistics.csv"))
    # save_plots(df, out, args.name)
    with open(out.joinpath("callside_to_id.csv"), "w", newline="") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["key", "value"])
        for key, value in callsite_to_id.items():
            writer.writerow([key, value])
    return


def save_receiver_reduction(compile_id_to_receiver_count, out, callsite):
    if not compile_id_to_receiver_count:
        return
    reduction_folder = out.joinpath("reduction")
    if not reduction_folder.is_dir():
        reduction_folder.mkdir()

    receiver_count_data = {}
    for k, v in compile_id_to_receiver_count.items():
        before, after = v
        receiver_count_data[f"{k} - before"] = len(before)
        receiver_count_data[f"{k} - after"] = len(after)
    df = pd.DataFrame(receiver_count_data, index=[0])
    callsite_output_csv = reduction_folder.joinpath(f"reduction_{callsite}.csv")
    df.to_csv(callsite_output_csv)
    return


def save_oscillations(oscillations, output_folder, callsite):
    if not oscillations:
        return
    oscillation_folder = output_folder.joinpath("oscillations")
    if not oscillation_folder.is_dir():
        oscillation_folder.mkdir()
    df = pd.DataFrame(oscillations, index=[0, 1])
    output_file = oscillation_folder.joinpath(f"oscillation_{callsite}.csv")
    df.to_csv(output_file)
    return


def find_receiver_reduction(raw: List[str]):
    compile_id_to_receiver_count = dict()
    last_id = "interpreter"
    before = set()
    current_receivers = set()
    raw = [el for el in raw if not el.strip().startswith("window")]

    for el in raw:
        if el.strip().startswith("Compilation"):
            compile_id_to_receiver_count[last_id] = (before, current_receivers)
            before = current_receivers
            current_receivers = set()
            matches = re.findall(r"id = \d+", el)
            id = matches[0].replace("id = ", "")
            last_id = id
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            id = matches[0].replace("compile_id = ", "")
            if id in compile_id_to_receiver_count:
                continue
            if last_id not in compile_id_to_receiver_count:
                compile_id_to_receiver_count[last_id] = (before, current_receivers)
            # before = set()

            if last_id == id:
                last_id = "interpreter"
                before = set()
                current_receivers = set()
        else:
            # compile_id_to_receiver_count[last_id].append(el)
            receivers = {
                e.split(": ")[0].strip()
                for e in el.split(", ")
                if float(e.split(": ")[1]) != 0
            }
            current_receivers.update(receivers)
            pass

    if last_id not in compile_id_to_receiver_count:
        compile_id_to_receiver_count[last_id] = (before, current_receivers)

    return compile_id_to_receiver_count


def find_oscillations(i: List[str]):
    inversions = []
    for window, first, second in zip(*[iter(i)] * 3):
        first_window_elements = [
            (el.split(": ")[0].strip(), float(el.split(": ")[1].strip()))
            for el in first.replace("First Window: ", "").split(", ")
        ]
        second_window_elements = [
            (el.split(": ")[0].strip(), float(el.split(": ")[1].strip()))
            for el in second.replace("Second Window: ", "").split(", ")
        ]
        window1_receivers = [
            el[0]
            for el in sorted(first_window_elements, key=lambda e: e[1], reverse=True)
        ]
        window2_receivers = [
            el[0]
            for el in sorted(second_window_elements, key=lambda e: e[1], reverse=True)
        ]
        inversions.append((" - ".join(window1_receivers), " -".join(window2_receivers)))
        pass
    inversion_to_count = defaultdict(int)
    for i in inversions:
        inversion_to_count[i] += 1
    oscillations = defaultdict(int)
    for i in range(len(inversions) - 1):
        i1 = inversions[i]
        i2 = inversions[i + 1]
        oscillations[(i1, i2)] += 1
    for i in range(1, len(inversions) - 1):
        i1 = inversions[i]
        i2 = inversions[i + 1]
        oscillations[(i1, i2)] += 1
    return oscillations, inversion_to_count


def normalize_by_hotness(df: pd.DataFrame, hotness: Path) -> pd.DataFrame:
    hot_df: pd.DataFrame = pd.read_csv(hotness)
    new_df = pd.merge(df, hot_df, on="method_descriptor", how="left")

    def custom_aggregate(x):
        return x.fillna(0).iloc[0]

    aggregated_metrics = new_df.groupby("method_descriptor").agg(
        {
            "changes": "sum",
            "inversions": "sum",
            "compilations": "sum",
            "decompilations": "sum",
            "changes after compilation": "sum",
            "inversions after compilation": "sum",
            "exclusive cpu sec": custom_aggregate,
            "cpu cycles": custom_aggregate,
        }
    )
    aggregated_metrics["normalized inversions after compilation"] = (
        aggregated_metrics.apply(
            lambda r: 0
            if r["cpu cycles"] == 0
            else r["inversions after compilation"] / r["cpu cycles"],
            axis=1,
        )
    )
    aggregated_metrics = aggregated_metrics.sort_values(
        "normalized inversions after compilation", ascending=False
    )
    return aggregated_metrics


def save_plots(df: pd.DataFrame, output_folder: Path, name: str):
    data = {}
    # bar plot
    columns = [
        e
        for e in df.columns
        if e
        not in [
            "id",
            "index",
            "method_descriptor",
            "most frequent inversion",
            "most frequent oscillation",
            "average_inversions_before_decompilation",
            "most frequent inversion value",
            "most frequent oscillation value",
            "inversions after compilation",
            "changes after compilation",
        ]
    ]
    for column in columns:
        sorted = df.sort_values(by=column, ascending=False).head(30)
        color = mpl.cm.inferno_r(np.linspace(0.4, 0.8, len(sorted)))
        p = sorted.plot.bar(
            x="id", y=column, figsize=(40, 20), legend=True, color=color
        )
        plt.title(f"{column.capitalize()} for {name}")
        plt.savefig(
            output_folder.joinpath(f"{name}_{column.replace(' ', '_')}_bar.png")
        )
        plt.close()
    # scatter plot
    for column in columns:
        cleaned = df[df[column] > 0]
        color = mpl.cm.inferno_r(np.linspace(0.4, 0.8, len(cleaned)))
        xticks = [] if len(cleaned) > 30 else cleaned["id"]
        p = cleaned.plot.scatter(
            y=column,
            x="id",
            figsize=(30, 15),
            xticks=xticks,
            legend=True,
            color=color,
            rot=90,
        )
        plt.title(f"{column.capitalize()} for {name}")
        plt.savefig(
            output_folder.joinpath(f"{name}_{column.replace(' ', '_')}_scatter.png")
        )
        plt.close()
    for column in columns:
        cleaned = df[df[column] > 0]
        color = mpl.cm.inferno_r(np.linspace(0.4, 0.8, len(cleaned)))
        cleaned[column] = cleaned[column].astype(int)
        p = cleaned.boxplot(column=column, grid=False)
        plt.title(f"{column.capitalize()} for {name}")
        plt.savefig(
            output_folder.joinpath(f"{name}_{column.replace(' ', '_')}_boxplot.png")
        )
        plt.close()
    return data


def cohen_d(deap: List[float], random: List[float]):
    return (np.mean(deap) - np.mean(random)) / (
        np.sqrt((np.std(deap) ** 2 + np.std(random) ** 2) / 2)
    )


@dataclass
class CallSite:
    callsite: str
    changes: List[str]
    inversions: List[str]
    raw: List[str]

    def compilations(self) -> List[str]:
        return [e for e in self.changes if e.strip().startswith("Compilation")]

    def decompilations(self) -> List[str]:
        return [e for e in self.changes if e.strip().startswith("Decompilation")]


def extract_callsite_information(f: Path) -> List[CallSite]:
    handle = open(f, "r")
    current_callsite = ""
    changes = []
    inversions = []
    raw = []
    processing_type = "changes"

    callsites = []
    for line in handle:
        if line.startswith("Callsite"):
            callsite = line.split(":")[1].strip()
            if current_callsite:
                # save callsite information
                callsites.append(CallSite(current_callsite, changes, inversions, raw))
                processing_type = "changes"
                changes = []
                inversions = []
                raw = []
                pass
            current_callsite = callsite
        elif line.strip().startswith("Changes"):
            processing_type = "changes"
        elif line.strip().startswith("Inversions"):
            processing_type = "inversions"
        elif line.strip().startswith("RawWindowInformation"):
            processing_type = "raw"
        else:
            match processing_type:
                case "changes":
                    changes.append(line)
                case "inversions":
                    inversions.append(line)
                case "raw":
                    raw.append(line)

    if current_callsite:
        callsites.append(CallSite(current_callsite, changes, inversions, raw))
    return callsites


def extract_compilation_decompilation(callsite: CallSite):
    compile_id_to_changes = defaultdict(list)
    last_id = "interpreter"
    for el in callsite.changes:
        if el.strip().startswith("Compilation"):
            matches = re.findall(r"id = \d+", el)
            id = matches[0].replace("id = ", "")
            last_id = id
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            id = matches[0].replace("compile_id = ", "")
            if last_id == id:
                last_id = "interpreter"
        else:
            compile_id_to_changes[last_id].append(el)

    compile_id_to_inv = defaultdict(list)
    last_id = "interpreter"
    for el in callsite.inversions:
        if el.strip().startswith("Compilation"):
            matches = re.findall(r"id = \d+", el)
            id = matches[0].replace("id = ", "")
            last_id = id
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            id = matches[0].replace("compile_id = ", "")
            if last_id == id:
                last_id = "interpreter"
        else:
            compile_id_to_inv[last_id].append(el)
    c = [e for k, v in compile_id_to_changes.items() for e in v if k != "interpreter"]
    i = [e for k, v in compile_id_to_inv.items() for e in v if k != "interpreter"]
    return c, i


def number_of_windows_before_decompilation(callsite: CallSite) -> Dict[str, int]:
    compile_id_to_inv_count = defaultdict(int)
    compile_id_to_inv = defaultdict(list)
    last_id = "interpreter"
    for el in callsite.inversions:
        if el.strip().startswith("Compilation") and "kind = c2" in el:
            matches = re.findall(r"id = \d+", el)
            id = matches[0].replace("id = ", "")
            last_id = id
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            id = matches[0].replace("compile_id = ", "")
            if id in compile_id_to_inv:
                # the inversions are always reported as triplets of lines.
                compile_id_to_inv_count[id] = len(compile_id_to_inv[id]) / 3
            if last_id == id:
                last_id = "interpreter"
        else:
            compile_id_to_inv[last_id].append(el)
    return compile_id_to_inv_count


if __name__ == "__main__":
    main()
