import os
from typing import List
import subprocess
import shutil
import configparser
from robot.api import FatalError
from robot.api import logger
from robot.api.deco import library, keyword


@library(scope="GLOBAL", doc_format="reST")
class IDeSyDeLibrary:
    def __init__(self) -> None:
        self.parallel_lvl = os.cpu_count() or 1

    @keyword
    def set_up(self):
        if os.name == "nt":
            rust_built = subprocess.run(["cargo", "build"], shell=True)
            if rust_built.returncode != 0:
                raise FatalError("Failed to build the rust parts")
            # shutil.copyfile(
            #     "target" + os.path.sep + "debug" + os.path.sep + "idesyde-common.exe",
            #     "imodules" + os.path.sep + "idesyde-rust-common.exe",
            # )
            shutil.copyfile(
                "target"
                + os.path.sep
                + "debug"
                + os.path.sep
                + "idesyde-orchestration.exe",
                "idesyde-orchestrator.exe",
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
                "idesyde-orchestrator",
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
    def try_explore(
        self,
        path: str,
        log_level: str = "DEBUG",
        test_workdir: str = "test_runs",
    ) -> List[str]:
        # bin_path = next(f for f in os.listdir(".") if "idesyde" in f)
        # bin_path = "idesyde-orchestrator"
        bin_path = (
            (".\\" if os.name == "nt" else "./")
            + "idesyde"
            + (".exe" if os.name == "nt" else "")
        )
        files = os.listdir(path)
        run_path = test_workdir + os.path.sep + path
        os.makedirs(run_path, exist_ok=True)
        args = [
            bin_path,
            "--run-path",
            run_path,
            "--x-max-solutions",
            "1",
            "-p",
            str(self.parallel_lvl),
            "-v",
            log_level,
        ] + [path + os.path.sep + f for f in files]
        with subprocess.Popen(
            args, shell=(True if os.name == "nt" else False), stdout=subprocess.PIPE
        ) as child:
            for line in child.stdout:
                # logger.console(line)
                logger.debug(line)
        # if os.name == "nt":
        #     logger.console(subprocess.check_output(args, shell=True))
        # else:
        #     logger.console(subprocess.check_output(args))
        return os.listdir(run_path + os.path.sep + "explored") or []
        # assert child.returncode == 0
        # if has_solution:
        #     assert len(os.listdir(run_path + os.path.sep + "explored")) > 0
        # else:
        #     assert len(os.listdir(run_path + os.path.sep + "explored")) == 0

    @keyword
    def tear_down(self, test_workdir: str = "test_runs") -> None:
        if os.path.exists(test_workdir):
            shutil.rmtree(test_workdir)
