import re
import csv
import json
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
        description="This script can be used to analyse the result of running the instrumentation and data digestion",
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
    ccu_id = 0
    ccu_to_id = {}
    ccu_to_red = {}
    for f in result_files:
        ccus = extract_ccus_information(f)
        for ccu in ccus:
            c, i, id_was_decompiled = extract_compilation_decompilation(ccu)
            # NOTE: this mapping between ccu and id is necessary since the ccus might exceed
            # the limit of a valid file name length on linux
            ccu_name = f"{ccu.callsite} - {ccu.cid}"
            ccu_to_id[ccu_name] = ccu_id
            ccu_id += 1
            oscillations, inversions_to_count = find_oscillations(i)
            save_oscillations(oscillations, out, ccu_to_id[ccu_name])
            most_frequent_oscillation = max(
                oscillations, key=oscillations.get, default=()
            )
            max_oscillation_frequency = oscillations.get(most_frequent_oscillation)
            most_frequent_inversion = max(
                inversions_to_count, key=inversions_to_count.get, default=()
            )
            max_inversion_frequency = inversions_to_count.get(most_frequent_inversion)
            comp_id_to_count = number_of_windows_before_decompilation(ccu)
            id_to_sub_time = compute_suboptimal_time(
                comp_id_to_count, id_was_decompiled, ccu.window_size
            )
            if len(comp_id_to_count) > 0:
                average_inversions_before_decompilations = reduce(
                    lambda a, b: a + b, comp_id_to_count.values(), 0
                ) / len(comp_id_to_count)
            else:
                average_inversions_before_decompilations = 0
            raw = ccu.raw
            statistics[ccu_name] = {
                "changes": len(ccu.changes),
                "inversions": len(ccu.inversions),
                "compilations": len(ccu.compilations()),
                "decompilations": len(ccu.decompilations()),
                "changes after compilation": len(c) / 3,
                "inversions after compilation": len(i) / 3,
                "total suboptimal time": sum(id_to_sub_time.values()),
                "most frequent oscillation": str(most_frequent_oscillation),
                "most frequent oscillation value": max_oscillation_frequency,
                "most frequent inversion": str(most_frequent_inversion),
                "most frequent inversion value": max_inversion_frequency,
                "average_inversions_before_decompilation": average_inversions_before_decompilations,
            }
            compile_id_to_receiver_count = find_receiver_reduction(raw)
            rcd = save_receiver_reduction(
                compile_id_to_receiver_count,
            )
            ccu_to_red[ccu_to_id[ccu_name]] = rcd
            pass
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
    df.to_csv(out.joinpath(f"{args.name}_statistics.csv"))
    # save_plots(df, out, args.name)
    with open(out.joinpath("ccu_to_id.csv"), "w", newline="") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["key", "value"])
        for key, value in ccu_to_id.items():
            writer.writerow([key, value])

    reduction_folder = out.joinpath("reduction")
    if not reduction_folder.is_dir():
        reduction_folder.mkdir()
    with open(out.joinpath("reduction.json"), "w") as f:
        json.dump(ccu_to_red, f)
    return


def compute_suboptimal_time(id_to_count, id_was_decompiled, window_size):
    # The time is given in microseconds.
    id_to_estimated_suboptimal_time = {}
    for k in id_was_decompiled.keys():
        if k not in id_to_count:
            continue
        estimated_suboptimal_time = id_to_count[k] * window_size
        id_to_estimated_suboptimal_time[k] = estimated_suboptimal_time
    return id_to_estimated_suboptimal_time


def save_receiver_reduction(compile_id_to_receiver_count):
    if not compile_id_to_receiver_count:
        return

    receiver_count_data = {}
    for k, v in compile_id_to_receiver_count.items():
        before, after = v
        receiver_count_data[f"{k} - before"] = len(before)
        receiver_count_data[f"{k} - after"] = len(after)
    return receiver_count_data


def save_oscillations(oscillations, output_folder, ccu_id):
    if not oscillations:
        return
    oscillation_folder = output_folder.joinpath("oscillations")
    if not oscillation_folder.is_dir():
        oscillation_folder.mkdir()
    df = pd.DataFrame(oscillations, index=[0, 1])
    output_file = oscillation_folder.joinpath(f"oscillation_{ccu_id}.csv")
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
            cid = matches[0].replace("id = ", "")
            last_id = cid
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            cid = matches[0].replace("compile_id = ", "")
            if cid in compile_id_to_receiver_count:
                continue
            if last_id not in compile_id_to_receiver_count:
                compile_id_to_receiver_count[last_id] = (before, current_receivers)
            # before = set()

            if last_id == cid:
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
    # for i in range(1, len(inversions) - 1):
    #     i1 = inversions[i]
    #     i2 = inversions[i + 1]
    #     oscillations[(i1, i2)] += 1
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
            # "inversions after compilation",
            # "changes after compilation",
        ]
    ]
    for column in columns:
        sorted = df.sort_values(by=column, ascending=False).head(30)
        color = mpl.cm.inferno_r(np.linspace(0.4, 0.8, len(sorted)))
        _ = sorted.plot.bar(
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
        _ = cleaned.plot.scatter(
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
    # boxplot
    for column in columns:
        # print(column)
        cleaned = df[df[column] > 0]
        color = mpl.cm.inferno_r(np.linspace(0.4, 0.8, len(cleaned)))

        cleaned[column] = cleaned[column].astype(int)
        _ = cleaned.boxplot(column=column, grid=False)
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
class CCU:
    callsite: str
    cid: int
    changes: List[str]
    inversions: List[str]
    raw: List[str]
    window_size: float

    def compilations(self) -> List[str]:
        return [e for e in self.changes if e.strip().startswith("Compilation")]

    def decompilations(self) -> List[str]:
        return [e for e in self.changes if e.strip().startswith("Decompilation")]


def extract_ccus_information(f: Path) -> List[CCU]:
    handle = open(f, "r")
    current_callsite = ""
    current_cid = 0
    changes = []
    inversions = []
    raw = []
    processing_type = "changes"

    ccus = []
    window_size = 0
    rx = r"CCU \[callsite=(?P<callsite>.*), compile_id=(?P<cid>.*)\]"
    for line in handle:
        if line.startswith("CCU"):
            res = re.search(rx, line)
            callsite = res["callsite"]
            cid = res["cid"]
            if current_callsite:
                ccus.append(
                    CCU(
                        current_callsite,
                        current_cid,
                        changes,
                        inversions,
                        raw,
                        window_size,
                    )
                )
                processing_type = "changes"
                changes = []
                inversions = []
                raw = []
                pass
            current_callsite = callsite
            current_cid = int(cid)
        elif line.strip().startswith("Changes"):
            window_size = float(line.split("size = ")[1].replace("]:", "").strip())
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
        ccus.append(
            CCU(current_callsite, current_cid, changes, inversions, raw, window_size)
        )
    ccus = clean_up_inversions(ccus)
    return ccus


def clean_up_inversions(ccus: List[CCU]):
    new_ccus = []
    for ccu in ccus:
        i = 0
        inv = []
        while i < len(ccu.inversions):
            line = ccu.inversions[i]
            if line.strip().startswith("Compilation") or line.strip().startswith(
                "Decompilation"
            ):
                inv.append(line)
                i += 1
                continue
            f = ccu.inversions[i + 1]
            s = ccu.inversions[i + 2]

            first_window_elements = [
                (el.split(": ")[0].strip(), float(el.split(": ")[1].strip()))
                for el in f.replace("First Window: ", "").split(", ")
            ]
            second_window_elements = [
                (el.split(": ")[0].strip(), float(el.split(": ")[1].strip()))
                for el in s.replace("Second Window: ", "").split(", ")
            ]
            sf = sorted(first_window_elements, key=lambda e: e[1], reverse=True)
            ss = sorted(second_window_elements, key=lambda e: e[1], reverse=True)
            top_elements = []
            top_elements.append(sf[0])
            if ss[0] not in top_elements:
                top_elements.append(ss[0])
            if sf[1] not in top_elements:
                top_elements.append(sf[1])
            if ss[1] not in top_elements:
                top_elements.append(ss[1])
            top_elements = top_elements[:2]
            top_elements = [e[0] for e in top_elements]
            first_window_elements = [
                e for e in first_window_elements if e[0] in top_elements
            ]
            second_window_elements = [
                e for e in second_window_elements if e[0] in top_elements
            ]
            fw = [f"{e[0]}: {e[1]}" for e in first_window_elements]
            sw = [f"{e[0]}: {e[1]}" for e in second_window_elements]
            f = f"        First Window: {', '.join(fw)}\n"
            s = f"        Second Window: {', '.join(sw)}\n"
            inv.append(line)
            inv.append(f)
            inv.append(s)
            i += 3
        new_ccus.append(
            CCU(
                ccu.callsite,
                ccu.cid,
                ccu.changes,
                inv,
                ccu.raw,
                ccu.window_size,
            )
        )

    return new_ccus


def extract_compilation_decompilation(ccu: CCU):
    compile_id_to_changes, compile_id_was_decompiled = compile_id_to_event(ccu.changes)
    compile_id_to_inv, cid_was_decompiled = compile_id_to_event(ccu.inversions)
    compile_id_was_decompiled.update(cid_was_decompiled)
    c = [e for k, v in compile_id_to_changes.items() for e in v if k != "interpreter"]
    i = [e for k, v in compile_id_to_inv.items() for e in v if k != "interpreter"]
    return c, i, compile_id_was_decompiled


def compile_id_to_event(events):
    compile_id_to_inv = defaultdict(list)
    last_id = "interpreter"
    compile_id_was_decompiled = {}
    for el in events:
        if el.strip().startswith("Compilation"):
            matches = re.findall(r"id = \d+", el)
            cid = matches[0].replace("id = ", "")
            last_id = cid
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            cid = matches[0].replace("compile_id = ", "")
            compile_id_was_decompiled[cid] = True
            if last_id == cid:
                last_id = "interpreter"
        else:
            compile_id_to_inv[last_id].append(el)
    return compile_id_to_inv, compile_id_was_decompiled


def number_of_windows_before_decompilation(ccu: CCU) -> Dict[str, int]:
    compile_id_to_inv_count = defaultdict(int)
    compile_id_to_inv = defaultdict(list)
    last_id = "interpreter"
    for el in ccu.inversions:
        if el.strip().startswith("Compilation") and "kind = c2" in el:
            matches = re.findall(r"id = \d+", el)
            cid = matches[0].replace("id = ", "")
            last_id = cid
        elif el.strip().startswith("Decompilation"):
            matches = re.findall(r"compile_id = \d+", el)
            cid = matches[0].replace("compile_id = ", "")
            if cid in compile_id_to_inv:
                # the inversions are always reported as triplets of lines.
                compile_id_to_inv_count[cid] = len(compile_id_to_inv[cid]) / 3
            if last_id == cid:
                last_id = "interpreter"
        else:
            compile_id_to_inv[last_id].append(el)
    return compile_id_to_inv_count


if __name__ == "__main__":
    main()
