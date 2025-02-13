from argparse import ArgumentParser, Namespace
from pathlib import Path
import json
from typing import TextIO, Dict, AnyStr, List
from functools import reduce
from dataclasses import dataclass


@dataclass
class ReceiverRatioChange:
    window: int
    class_name: str
    diff: float


@dataclass
class Inversion:
    window1: int
    window2: int
    class_name1: int
    class_name2: int


def main() -> None:
    parser: ArgumentParser = ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("/Users/jacobsalvi/Master/MasterThesis/receiver-types-profiler/output/output_virtual_07-02-25-09-49-12.json"))
    parser.add_argument("--delta", type=int, default=1000, help="Timestamp in microseconds")
    args: Namespace = parser.parse_args()
    input_file: TextIO = open(args.input, "r")
    content: Dict[str, int | Dict[str, List[int]]] = json.load(input_file)
    beginning: int = content.get("beginning")
    callsite_to_info: Dict[str, Dict[str, List[int]]] = {k: v for k, v in content.items() if k != "beginning"}
    for cs, info in callsite_to_info.items():
        percentage_windows = analyse_callsite(cs, info, beginning, args.delta)
        changes = check_percentage_changes(percentage_windows)
        inversions = check_inversions(percentage_windows)
        output_results(cs, changes, inversions)
    return    


def get_output_folder() -> Path:
    return Path(__file__).parents[1].joinpath("result")


def output_results(cs: str, changes: List[ReceiverRatioChange], inversions: List[Inversion]):
    result: Path = get_output_folder()
    result.mkdir(exist_ok=True)
    to_print: List[str] = [f"Callsite is: {cs}"]
    indent: str = "    "
    to_print.append("Changes:")
    for change in changes:
        to_print.append(f"{indent}{change.class_name} - {change.window} - {change.diff}")
    to_print.append("Inversions:")
    for inv in inversions:
        to_print.append(f"{indent}{inv.class_name1} - {inv.class_name2} - {inv.window1} - {inv.window2}")
    result_file: Path = result.joinpath("result.txt")
    result_file.touch(exist_ok=True)
    with result_file.open("a") as f:
        for line in to_print:
            f.write(f"{line}\n")
            print(line)
    return


def analyse_callsite(_call_site: str, info: Dict[AnyStr, List[int]], _beginning: int, time_frame: int) -> List[Dict[AnyStr, float]]:
    window_start: int = min(reduce(lambda a, b: a+b, info.values(), []))
    end: int = max(reduce(lambda a, b: a+b, info.values(), []))
    windows: List[Dict[AnyStr, List[int]]] = []
    while True:
        current_window: Dict[AnyStr, List[int]] = {}
        for class_name, timestamps in info.items():
            current_window[class_name] = [e for e in timestamps if window_start <= e < window_start + time_frame]
        windows.append(current_window)
        window_start += time_frame
        if window_start > end:
            break

    percentage_windows: List[Dict[AnyStr, float]] = [{k: len(v) / sum(map(len, e.values())) for k, v in e.items()} for e in windows]
    print(percentage_windows)
    return percentage_windows


def check_percentage_changes(p_windows: List[Dict[AnyStr, float]]) -> List[ReceiverRatioChange]:
    threshold = 0.1
    changes: List[ReceiverRatioChange] = []
    for i, pair in enumerate(zip(p_windows, p_windows[1:])):
        w1, w2 = pair
        for k in w1.keys():
            el1 = w1.get(k, 0)
            el2 = w2.get(k, 0)
            diff: float = abs(el1-el2)
            if diff > threshold:
                change = ReceiverRatioChange(window=i, class_name=k, diff=diff)
                changes.append(change)
    return changes


def sign(e):
    if e == 0:
        return e
    return e/abs(e)


def check_inversions(p_windows: List[Dict[AnyStr, float]]) -> List[Inversion]:
    inversions = []
    for i, pair in enumerate(zip(p_windows, p_windows[1:])):
        w1, w2 = pair
        keys = list(w1.keys())
        for k1, k2 in zip(keys, keys[1:]):
            val_key1_window1 = w1.get(k1, 0)
            val_key2_window1 = w1.get(k2, 0)
            val_key1_window2 = w2.get(k1, 0)
            val_key2_window2 = w2.get(k2, 0)
            if sign(val_key1_window1-val_key2_window1) != sign(val_key1_window2-val_key2_window2):
                inversions.append(Inversion(window1=i, window2=i+1, class_name1=k1, class_name2=k2))
    return inversions


if __name__ == "__main__":
    main()
