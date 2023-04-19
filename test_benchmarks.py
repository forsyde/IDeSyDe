import os
import unittest
import subprocess
import shutil


class BaseTest(unittest.TestCase):
    def setUp(self) -> None:
        self.test_cases = dict()
        for root, dirs, files in os.walk("examples_and_benchmarks"):
            if not dirs:
                self.test_cases[root] = files
        self.rust_built = subprocess.run(["cargo", "build"], shell=True)
        self.scala_built = subprocess.run(["sbt", "publishModules"], shell=True)
        if self.rust_built.returncode != 0:
            self.fail("Failed to build the rust parts")
        if self.scala_built.returncode != 0:
            self.fail("Failed to build the scala parts")

    def test_one_solution(self) -> None:
        bin_path = (
            "target" + os.path.sep + "debug" + os.path.sep + "idesyde-orchestration"
        )
        if os.name == "nt":
            bin_path += ".exe"
        for path, files in self.test_cases.items():
            with self.subTest(path):
                run_path = "run_" + path.replace(os.path.sep, "_")
                child = subprocess.run(
                    [
                        bin_path,
                        "--run-path",
                        run_path,
                        "--x-max-solutions",
                        "1",
                        "-v",
                        "debug",
                    ]
                    + [path + os.path.sep + f for f in files],
                    shell=True,
                )
                self.assertEqual(child.returncode, 0)
                self.assertTrue(
                    len(os.listdir(run_path + os.path.sep + "explored")) > 0
                )

    def tearDown(self) -> None:
        for path, _ in self.test_cases.items():
            run_path = "run_" + path.replace(os.path.sep, "_")
            if os.path.isdir(run_path):
                shutil.rmtree(run_path)


if __name__ == "__main__":
    unittest.main()
