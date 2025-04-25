import re
from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List
import pandas as pd


def main():
    parser: ArgumentParser = ArgumentParser()
    parser.add_argument(
        "--input-folder", dest="input_folder", type=Path, default=Path("./hotness/")
    )
    parser.add_argument(
        "--output-folder", dest="output_folder", type=Path, default=Path("./hotness/")
    )
    parser.add_argument("--limit", dest="limit", type=int)
    args: Namespace = parser.parse_args()
    input_folder: Path = args.input_folder
    output_folder: Path = args.output_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    limit = args.limit
    for input_file in input_files:
        methods = get_hot_methods(input_file, limit)
        methods = method_names_to_descriptor(methods)
        output_file = output_folder.joinpath(f"hot_methods_{input_file.stem}.csv")
        to_write = [";".join(e) for e in methods]
        df = pd.DataFrame(
            methods,
            columns=["exclusive cpu sec", "cpu cycles", "method_descriptor"],
        )
        df.to_csv(output_file, index=False)
    return


def method_names_to_descriptor(methods: List[List[str]]):
    # net.jpountz.lz4.LZ4JavaUnsafeCompressor.compress64k(byte[], int, int, byte[], int, int)
    #
    for m in methods:
        name = m[2]
        if name in ["<Total>", "<JVM-System>", "<no Java callstack recorded>"]:
            continue
        if name.startswith("<static>"):
            continue
        s = name.split("(")
        if len(s) == 1:
            continue
        if "::" in name:
            continue
        method_name = s[0]
        method_name = method_name.replace(".", "/", method_name.count(".") - 1)
        args = s[1].replace(")", "").split(", ")

        new_args = translate_arguments(args)
        name = f"{method_name}({''.join(new_args)})"
        m[2] = name
    return methods


def translate_arguments(args: List[str]) -> List[str]:
    primitive_to_descriptor = {
        "int": "I",
        "byte": "B",
        "char": "C",
        "double": "D",
        "float": "F",
        "long": "J",
        "short": "S",
        "boolean": "Z",
    }
    new_args = []
    for arg in args:
        n_arrays = 0
        while "[]" in arg:
            arg = arg.replace("[]", "", 1)
            n_arrays += 1
        if arg in primitive_to_descriptor.keys():
            new_arg = primitive_to_descriptor[arg]
        else:
            new_arg = f"L{arg.replace('.', '/')};"
        new_args.append(f"{'[' * n_arrays}{new_arg}")
    return new_args


def get_hot_methods(input_file: Path, limit: int) -> List[List[str]]:
    found_methods = 0
    methods = []
    with open(input_file, "r") as f:
        for line in f.readlines()[7:-1]:
            line = line.strip()
            res = line.split(maxsplit=2)
            res[2] = re.sub(r"\.0x\w+", "", res[2])
            methods.append(res)
            found_methods += 1
            if limit and found_methods >= limit:
                break
    return methods


if __name__ == "__main__":
    main()
