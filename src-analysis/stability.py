from argparse import ArgumentParser, Namespace
from typing import List

from pathlib import Path
import pandas as pd
from dataclasses import dataclass


@dataclass(frozen=True)
class Compilation:
    id: str
    kind: str
    time: int


@dataclass
class Decompilation:
    id: str
    kind: str
    time: int
    reason: str
    action: str



def main():
    parser: ArgumentParser = ArgumentParser(
        "Analysis",
        description="This script can be used to analyse the result of running the instrumentation and data digestion",
    )
    parser.add_argument(
        "--input-folder", dest="input_folder", type=Path, default=Path("./result/")
    )
    parser.add_argument("--name", dest="name", type=Path, required=True)
    parser.add_argument(
        "--output-folder", dest="output_folder", type=Path, default=Path("./result/")
    )

    args: Namespace = parser.parse_args()
    name = args.name
    input_folder = args.input_folder
    output_folder = args.output_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    stability_files:List[Path] = [f for f in input_files if f.name.startswith("stability")]

    stabilities = {"stable_point": [],
                   "max": [],
                   "callsite": [],
                   "compilation": [],
                   "has_decomp": []}
    

    for stability_file in stability_files:
        lines = stability_file.read_text().splitlines()
        for line in lines:
            split_line = line.split(" [")
            split_line = [el.replace("]", "").replace("[","") for el in split_line]
            start = int(split_line[0])
            m = int(split_line[1])
            callsite = split_line[2]
            offset = int(split_line[3])
            comps = extract_compilations(split_line[4])
            c2_comps = [c for c in comps if c.kind == "c2"]
            decomps = extract_decompilations(split_line[5])

            stability_point = offset+start
            subsequent_compilation, in_between_compilation, c_to_dec = find_compilation(stability_point, c2_comps, decomps)

            stabilities["stable_point"].append(start)
            stabilities["max"].append(m)
            stabilities["callsite"].append(callsite)
            stabilities["compilation"].append(subsequent_compilation.time if subsequent_compilation else None)
            stabilities["has_decomp"].append(c_to_dec.get(subsequent_compilation).time if c_to_dec.get(subsequent_compilation) else "")
        
    df: pd.DataFrame = pd.DataFrame(stabilities)
    result_file = output_folder.joinpath(f"stability_{name}.csv")
    df.sort_values("stable_point").to_csv(result_file)
    return


def find_compilation(stability_time, compilations: List[Compilation], decompilations: List[Decompilation]):
    # we consider two cases
    # the stability point happened after a compilation and before the corresponding the decompilation
    # the stability point happened before a compilation
    subsequent_compilation = None
    in_between_compilation = None
    compilation_to_decompilation = {}
    for comp in compilations:
        for decomp in decompilations:
            if decomp.id == comp.id:
                compilation_to_decompilation[comp] = decomp

    for comp in compilations:
        if stability_time <= comp.time and not comp.time <= compilation_to_decompilation[comp]:
            subsequent_compilation = comp
        if comp.time <= stability_time and not subsequent_compilation:
            in_between_compilation = comp
    return subsequent_compilation, in_between_compilation, compilation_to_decompilation





def extract_compilations(serialized):
    if serialized == "":
        return []
    s = serialized.split(",")
    comps = []
    for el in s:
        subel = el.split(" ")
        comp = Compilation(time=int(subel[0]), kind=subel[1], id=subel[2])
        comps.append(comp)
    return comps


def extract_decompilations(serialized):
    if serialized == "":
        return []
    s = serialized.split(",")
    decomps = []
    for el in s:
        subel = el.split(" ")
        decomp = Decompilation(time=int(subel[0]), kind=subel[1], id=subel[2], reason=subel[3], action=subel[4])
        decomps.append(decomp)
    return decomps
    


if __name__ == "__main__":
    main()
