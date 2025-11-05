from argparse import ArgumentParser, Namespace
from typing import List

from pathlib import Path
import re
import pandas as pd

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

    stabilities = {"stable_point": [], "max": [], "callsite": []}
    

    pattern = "\[(?P<stable_point>\d+)\] \[(?P<max>\d+)\] \[(?P<callsite>.+)\]"
    for stability_file in stability_files:
        lines = stability_file.read_text().splitlines()
        for line in lines:
            matches = re.search(pattern, line)
            start = int(matches.group("stable_point"))
            m = int(matches.group("max"))
            callsite = matches.group("callsite")
            stabilities["stable_point"].append(start)
            stabilities["max"].append(m)
            stabilities["callsite"].append(callsite)
        
    df: pd.DataFrame = pd.DataFrame(stabilities)
    result_file = output_folder.joinpath(f"stability_{name}.csv")
    df.sort_values("stable_point").to_csv(result_file)
    pass
    


if __name__ == "__main__":
    main()
