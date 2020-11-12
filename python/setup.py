from setuptools import setup

setup(name='desyder',
      version='0.1.2',
      description='Analytical Design Space Exploration for ForSyDe',
      url='http://github.com/rojods/desyder',
      author='Rodolfo Jordao',
      author_email='jordao@kth.se',
      license='MIT',
      python_requires='>=3.7',
      packages=['desyder'],
      include_package_data=True,
      #packages=find_namespace_packages(include=["desyde.*"]),
      install_requires=[
          'forsyde-io-python',
          'minizinc',
          'numpy'
      ],
      entry_points={
          "console_scripts": [
              "desyder = desyder.cli:cli_entry"
          ]
      },
      zip_safe=True)
