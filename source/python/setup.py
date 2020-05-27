from setuptools import setup, find_namespace_packages

setup(name='desyde',
      version='0.1.1',
      description='Analytical Design Space Exploration for ForSyDe',
      url='http://github.com/rojods/desyder',
      author='Rodolfo Jordao',
      author_email='jordao@kth.se',
      license='MIT',
      packages=['desyde'],
      #packages=find_namespace_packages(include=["desyde.*"]),
      install_requires=[
          'forsyde-python-model',
      ],
      entry_points = {
          "console_scripts": [
              "desyder = desyde.cli:cli_entry"
          ]
      },
      zip_safe=True)
