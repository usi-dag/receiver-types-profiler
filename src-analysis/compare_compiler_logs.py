from argparse import ArgumentParser, Namespace
from pathlib import Path
from typing import List, Dict
from collections import defaultdict
import re


def main():
    parser: ArgumentParser = ArgumentParser("Compare compiler logs.")
    parser.add_argument(
        "--input-folder", dest="input_folder", type=Path, default=Path("./result/")
    )
    parser.add_argument(
        "--output-folder", dest="output_folder", type=Path, default=Path("./result/")
    )
    args: Namespace = parser.parse_args()
    input_folder: Path = args.input_folder
    output_folder: Path = args.output_folder
    input_files: List[Path] = [f for f in input_folder.iterdir()]
    input_files = [f for f in input_files if f.name.startswith("compiler_log")]
    # LOG_FILE=overhead_times/compiler_log_default_"$1"_"$entry"_"$i".xml
    bench_to_log_normal, bench_to_log_instrumented = organize_logs(input_files)
    bench_to_ids_normal = compute_compilation_ids(bench_to_log_normal)
    bench_to_ids_instrumented = compute_compilation_ids(bench_to_log_instrumented)

    save_result(bench_to_ids_normal, output_folder, "normal")
    save_result(bench_to_ids_instrumented, output_folder, "instrumented")

    pass


def save_result(bench_to_ids, output_folder, name):
    out_file = output_folder.joinpath(f"{name}.txt")
    out_file.touch()
    bench_to_count = {
        bench: [e for el in ids for e in el] for bench, ids in bench_to_ids.items()
    }
    text = "\n".join([f"{bench} - {len(ids)}" for bench, ids in bench_to_count.items()])
    out_file.write_text(text)


def compute_compilation_ids(bench_to_logs: Dict[str, List[Path]]):
    bench_to_c2_ids = defaultdict(list)
    for bench, logs in bench_to_logs.items():
        for log in logs:
            bench_to_c2_ids[bench].append(get_c2_compilation_ids(log))
    return bench_to_c2_ids


def get_c2_compilation_ids(log: Path):
    fh = open(log, "r")
    c2_compilation_ids = []
    for line in fh.readlines():
        if line.startswith("<task ") or line.startswith("<nmethod "):
            matches = re.search("compile_kind='(?P<kind>\w*)'", line)
            compile_kind = matches.group("kind") if matches else None
            matches = re.search("compile_id='(?P<id>\d*)'", line)
            id = matches.group("id")
            matches = re.search("compiler='(?P<kind>\w*)'", line)
            compiler = matches.group("kind") if matches else None
            if compiler == "c2" or compile_kind == "c2":
                c2_compilation_ids.append(id)
            pass
    return c2_compilation_ids


def organize_logs(input_files: List[Path]):
    benchmark_to_compiler_logs_normal = defaultdict(list)
    benchmark_to_compiler_logs_instrumented = defaultdict(list)
    for f in input_files:
        matches = re.search(
            "compiler_log_(?P<type>default|instrumented)_(?P<suite>ren|dacapo)_(?P<bench_name>.*)_\d.xml",
            f.name,
        )
        bench_name = matches.group("bench_name")
        run_type = matches.group("type")
        match run_type:
            case "default":
                benchmark_to_compiler_logs_normal[bench_name].append(f)
            case "instrumented":
                benchmark_to_compiler_logs_instrumented[bench_name].append(f)

    return benchmark_to_compiler_logs_normal, benchmark_to_compiler_logs_instrumented


if __name__ == "__main__":
    main()
