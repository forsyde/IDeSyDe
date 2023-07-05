import os
import unittest
import subprocess
import shutil
import configparser


class BaseTest(unittest.TestCase):
    parallel_lvl = min(5, os.cpu_count() or 1)

    def setUp(self) -> None:
        self.test_cases = dict()
        for root, dirs, files in os.walk("examples_and_benchmarks"):
            if not dirs:
                self.test_cases[root] = files
        if os.name == "nt":
            self.rust_built = subprocess.run(["cargo", "build"], shell=True)
        else:
            self.rust_built = subprocess.run(["cargo", "build"])
        if os.name == "nt":
            self.scala_built = subprocess.run(["sbt", "publishModules"], shell=True)
        else:
            self.scala_built = subprocess.run(["sbt", "publishModules"])
        if self.rust_built.returncode != 0:
            self.fail("Failed to build the rust parts")
        if self.scala_built.returncode != 0:
            self.fail("Failed to build the scala parts")
        self.test_slow = os.environ.get("TEST_SLOW", "no").lower() == "yes"

    def test_solutions(self) -> None:
        bin_path = (
            "target" + os.path.sep + "debug" + os.path.sep + "idesyde-orchestration"
        )
        if os.name == "nt":
            bin_path += ".exe"
        for path, files in self.test_cases.items():
            config = configparser.ConfigParser()
            config.read(path + os.path.sep + "testcase.cfg")
            has_solution = (
                (config["solutions"]["has-solution"] or "true").lower() == "true"
                if "testcase.cfg" in files
                else True
            )
            is_slow = (
                (
                    "slow" in config["solutions"]
                    and config["solutions"]["slow"]
                    or "false"
                ).lower()
                == "true"
                if "testcase.cfg" in files
                else False
            )
            if not is_slow or is_slow == self.test_slow:
                with self.subTest(path):
                    run_path = "testruns" + os.path.sep + path
                    os.makedirs(run_path)
                    args = [ bin_path, "--run-path", run_path, "--x-max-solutions", "1", "-p", str(self.parallel_lvl), "-v", "debug" ] + [path + os.path.sep + f for f in files]
                    if os.name == "nt":
                        child = subprocess.run(args, shell=True)
                    else:
                        child = subprocess.run(args)
                    self.assertEqual(child.returncode, 0)
                    if has_solution:
                        self.assertTrue(
                            len(os.listdir(run_path + os.path.sep + "explored")) > 0
                        )
                    else:
                        self.assertTrue(
                            len(os.listdir(run_path + os.path.sep + "explored")) == 0
                        )

    # def test_no_solution(self) -> None:
    #     bin_path = (
    #         "target" + os.path.sep + "debug" + os.path.sep + "idesyde-orchestration"
    #     )
    #     if os.name == "nt":
    #         bin_path += ".exe"
    #     for path, files in self.test_cases.items():
    #         config = configparser.ConfigParser()
    #         config.read(path + os.path.sep + "testcase.cfg")
    #         has_solution = (
    #             (config["solutions"]["has-solution"] or "true").lower() == "true"
    #             if "testcase.cfg" in files
    #             else True
    #         )
    #         is_slow = (
    #             (
    #                 "slow" in config["solutions"]
    #                 and config["solutions"]["slow"]
    #                 or "false"
    #             ).lower()
    #             == "true"
    #             if "testcase.cfg" in files
    #             else False
    #         )
    #         if not has_solution and not is_slow:
    #             with self.subTest(path):
    #                 run_path = "testruns" + os.path.sep + path
    #                 os.makedirs(run_path)
    #                 child = subprocess.run(
    #                     [
    #                         bin_path,
    #                         "--run-path",
    #                         run_path,
    #                         "--x-max-solutions",
    #                         "1",
    #                         "-p",
    #                         str(self.parallel_lvl),
    #                         "-v",
    #                         "debug",
    #                     ]
    #                     + [path + os.path.sep + f for f in files],
    #                     shell=True,
    #                 )
    #                 self.assertEqual(child.returncode, 0)
    #                 self.assertTrue(
    #                     len(os.listdir(run_path + os.path.sep + "explored")) == 0
    #                 )

    def tearDown(self) -> None:
        shutil.rmtree("testruns")
        # for path, _ in self.test_cases.items():
        #     run_path = "testruns" + os.path.sep + path
        #     if os.path.isdir(run_path):
        #         shutil.rmtree(run_path)


if __name__ == "__main__":
    unittest.main()
