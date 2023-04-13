import os
import unittest
import subprocess


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

    def atLeastOneSolution(self) -> None:
        print(self.test_cases)
        # print(self.scala_built)

    def tearDown(self) -> None:
        return super().tearDown()
