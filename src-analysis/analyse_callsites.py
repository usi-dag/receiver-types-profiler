import re
from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List
from dataclasses import dataclass
from collections import defaultdict
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import matplotlib as mpl


def main():
    parser: ArgumentParser = ArgumentParser()
    parser.add_argument("--input-folder", dest="input_folder", type=Path, default=Path("./result/"))
    parser.add_argument("--output-folder", dest="output_folder", type=Path, default=Path("./result/"))
    parser.add_argument("--name", dest="name", type=Path, required=True)
    args: Namespace = parser.parse_args()
    input_folder = args.input_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    result_files = [f for f in input_files if f.name.startswith("result")]
    statistics = {}
    for f in result_files:
        callsites = parse_results(f)
        for callsite in callsites:
            c, i = compute_statistics(callsite)
            statistics[callsite.callsite] = {"changes": len(callsite.changes),
                "inversions": len(callsite.inversions),
                "compilations": len(callsite.compilations()),
                "decompilations": len(callsite.decompilations()),
                "changes after compilation": len(c),
                "inversions after compilation": len(i)
            }
    df = pd.DataFrame(statistics)
    df = df.T
    save_statistics(df, args.output_folder, args.name)
    return


def save_statistics(df: pd.DataFrame, output_folder: Path, name: str):
    data = {}
    # bar plot
    df = df.reset_index()
    df["id"] = df.index
    columns = [e for e in df.columns if e not in ["id", "index"]]
    for column in columns:
        sorted = df.sort_values(by=column, ascending=False).head(30)
        color = mpl.cm.inferno_r(np.linspace(.4, .8, len(sorted)))
        p = sorted.plot.bar(x="id", y=column, figsize=(40, 20), legend=True, color=color)
        plt.title(f"{column.capitalize()} for {name}")
        plt.savefig(output_folder.joinpath(f"{name}_{column}_bar.png"))
        plt.close()
    # scatter plot
    for column in columns:
        cleaned = df[df[column] > 0]
        color = mpl.cm.inferno_r(np.linspace(.4, .8, len(cleaned)))
        xticks = [] if len(cleaned) > 30 else cleaned["id"]
        p = cleaned.plot.scatter(y=column, x="id", figsize=(30, 15), xticks=xticks, legend=True, color=color, rot=90)
        plt.title(f"{column.capitalize()} for {name}")
        plt.savefig(output_folder.joinpath(f"{name}_{column}_scatter.png"))
        plt.close()
    for column in columns:
        cleaned = df[df[column] > 0]
        color = mpl.cm.inferno_r(np.linspace(.4, .8, len(cleaned)))
        p = cleaned.boxplot(column=column, grid=False)
        plt.title(f"{column.capitalize()} for {name}")
        plt.savefig(output_folder.joinpath(f"{name}_{column}_boxplot.png"))
        plt.close()
    df.to_csv(output_folder.joinpath(f"{name}_statistics.csv"))
    c_75 = np.percentile(df["changes after compilation"], 75)
    i_75 = np.percentile(df["inversions after compilation"], 75)
    cc = df[df["changes after compilation"] > c_75]
    cc.to_csv(output_folder.joinpath(f"{name}_cc.csv"))
    ic = df[df["inversions after compilation"] > i_75]
    ic.to_csv(output_folder.joinpath(f"{name}_ic.csv"))
    return data

def cohen_d(deap: List[float], random: List[float]):
    return (np.mean(deap) - np.mean(random)) / (np.sqrt((np.std(deap) ** 2 + np.std(random) ** 2) / 2))

@dataclass
class CallSite:
    callsite: str
    changes: List[str]
    inversions: List[str]

    def compilations(self) -> List[str]:
        return [e for e in self.changes if e.strip().startswith("Compilation")]

    def decompilations(self) -> List[str]:
        return [e for e in self.changes if e.strip().startswith("Decompilation")]


def parse_results(f: Path) -> List[CallSite]:
    handle = open(f, "r")
    current_callsite = ""
    changes = []
    inversions = []
    processing_changes = False
    callsites = []
    for line in handle:
        if line.startswith("Callsite"):
            callsite = line.split(":")[1].strip()
            if current_callsite:
                # save callsite information
                callsites.append(CallSite(current_callsite, changes, inversions))
                processing_changes = False
                changes = []
                inversions = []
                pass
            current_callsite = callsite
        elif line.strip().startswith("Changes"):
            processing_changes = True
        elif line.strip().startswith("Inversions"):
            processing_changes = False
        else:
            if processing_changes:
                changes.append(line)
            else:
                inversions.append(line)

    if current_callsite:
        callsites.append(CallSite(current_callsite, changes, inversions))
    return callsites


def compute_statistics(callsite: CallSite):
    compile_id_to_changes = defaultdict(list)
    last_id = "interpreter"
    for el in callsite.changes:
        if el.strip().startswith("Compilation"):
            matches = re.findall("id = \d+", el)
            id = matches[0].replace("id = ", "")
            last_id = id
        elif el.strip().startswith("Decompilation"):
            matches = re.findall("compile_id = \d+", el)
            id = matches[0].replace("compile_id = ", "")
            if last_id == id:
                last_id = "interpreter"
        else:
            compile_id_to_changes[last_id].append(el)
    
    compile_id_to_inv = defaultdict(list)
    last_id = "interpreter"
    for el in callsite.inversions:
        if el.strip().startswith("Compilation"):
            matches = re.findall("id = \d+", el)
            id = matches[0].replace("id = ", "")
            last_id = id
        elif el.strip().startswith("Decompilation"):
            matches = re.findall("compile_id = \d+", el)
            id = matches[0].replace("compile_id = ", "")
            if last_id == id:
                last_id = "interpreter"
        else:
            compile_id_to_inv[last_id].append(el)
    c = [e for k, v in compile_id_to_changes.items() for e in v if k != "interpreter"]
    i = [e for k, v in compile_id_to_inv.items() for e in v if k != "interpreter"]
    return c, i    

if __name__ == "__main__":
    main()
