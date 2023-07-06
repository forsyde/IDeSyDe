import os
from typing import List
import subprocess
import shutil
import configparser
from robot.api import FatalError
from robot.api.deco import library, keyword


@library(scope="GLOBAL", doc_format="reST")
class TestRobot:
    def __init__(self) -> None:
        self.parallel_lvl = os.cpu_count() or 1

    @keyword
    def set_up(self):
        if os.name == "nt":
            rust_built = subprocess.run(["cargo", "build"], shell=True)
            if rust_built.returncode != 0:
                raise FatalError("Failed to build the rust parts")
            shutil.copyfile(
                "target" + os.path.sep + "debug" + os.path.sep + "idesyde-common.exe",
                "imodules" + os.path.sep + "idesyde-rust-common.exe",
            )
            shutil.copyfile(
                "target"
                + os.path.sep
                + "debug"
                + os.path.sep
                + "idesyde-orchestration.exe",
                "idesyde-orchestration.exe",
            )
        else:
            rust_built = subprocess.run(["cargo", "build"])
            if rust_built.returncode != 0:
                raise FatalError("Failed to build the rust parts")
            shutil.copyfile(
                "target" + os.path.sep + "debug" + os.path.sep + "idesyde-common",
                "imodules" + os.path.sep + "idesyde-rust-common",
            )
            shutil.copyfile(
                "target"
                + os.path.sep
                + "debug"
                + os.path.sep
                + "idesyde-orchestration",
                "idesyde-orchestration",
            )
        if os.name == "nt":
            scala_built = subprocess.run(["sbt", "publishModules"], shell=True)
        else:
            scala_built = subprocess.run(["sbt", "publishModules"])
        if scala_built.returncode != 0:
            raise FatalError("Failed to build the scala parts")

    @keyword
    def get_test_cases(
        self, expected_extensions: List[str] = [".fiodl", ".dts", ".slx", ".xml"]
    ) -> List[str]:
        test_cases = list()
        for root, dirs, files in os.walk("examples_and_benchmarks"):
            if not dirs and any(
                any(f.endswith(ext) for ext in expected_extensions) for f in files
            ):
                test_cases.append(root)
        return test_cases

    @keyword
    def test_solution(self, path: str, test_slow: bool = False):
        bin_path = "idesyde-orchestration"
        if os.name == "nt":
            bin_path += ".exe"
        files = os.listdir(path)
        config = configparser.ConfigParser()
        config.read(path + os.path.sep + "testcase.cfg")
        has_solution = (
            (config["solutions"]["has-solution"] or "true").lower() == "true"
            if "testcase.cfg" in files
            else True
        )
        is_slow = (
            (
                "slow" in config["solutions"] and config["solutions"]["slow"] or "false"
            ).lower()
            == "true"
            if "testcase.cfg" in files
            else False
        )
        if not is_slow or is_slow == test_slow:
            run_path = "testruns" + os.path.sep + path
            os.makedirs(run_path)
            args = [
                bin_path,
                "--run-path",
                run_path,
                "--x-max-solutions",
                "1",
                "-p",
                str(self.parallel_lvl),
                "-v",
                "debug",
            ] + [path + os.path.sep + f for f in files]
            if os.name == "nt":
                child = subprocess.run(args, shell=True)
            else:
                child = subprocess.run(args)
            assert child.returncode == 0
            if has_solution:
                assert len(os.listdir(run_path + os.path.sep + "explored")) > 0
            else:
                assert len(os.listdir(run_path + os.path.sep + "explored")) == 0

    @keyword
    def tear_down(self) -> None:
        shutil.rmtree("testruns")
