import re
from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List


def main():
    parser: ArgumentParser = ArgumentParser()
    parser.add_argument(
        "--input-folder", dest="input_folder", type=Path, default=Path("./hotness/")
    )
    parser.add_argument(
        "--output-folder", dest="output_folder", type=Path, default=Path("./hotness/")
    )
    parser.add_argument("--limit", dest="limit", type=int, default=10)
    args: Namespace = parser.parse_args()
    input_folder: Path = args.input_folder
    output_folder: Path = args.output_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    limit = args.limit
    for input_file in input_files:
        methods = get_hot_methods(input_file, limit)
        output_file = output_folder.joinpath(f"hot_methods_{input_file.stem}.txt")
        output_file.write_text("\n".join(methods))
    return


def get_hot_methods(input_file: Path, limit: int) -> List[str]:
    pattern = r"\s*(\d+\.\d*)\s*(\d+\.\d*)\s*((?:[_a-zA-Z][_a-zA-Z0-9]*)(?:(?:[\.\$]+)(?:(?:[_a-zA-Z0-9]+)|(?:<(?:cl)?init>)))*)(\(.*\)$)"
    methods = []
    found_methods = 0
    with open(input_file, "r") as f:
        for line in f.readlines():
            m = re.findall(pattern, line)
            if m and len(m[0]) >= 3:
                method = m[0][2]
                method = re.sub(r"\.0x\w+", "", method)
                methods.append(method)
                found_methods += 1
            if found_methods >= limit:
                break
            pass
    return methods


if __name__ == "__main__":
    main()
