from setuptools import find_packages, setup

setup(name='idesyde',
      version='0.1.2',
      description='Analytical Design Space Exploration for ForSyDe',
      url='http://github.com/rojods/idesyde',
      author='Rodolfo Jordao',
      author_email='jordao@kth.se',
      license='MIT',
      python_requires='>=3.7',
      include_package_data=True,
      packages=find_packages(),
      install_requires=[
          'forsyde-io-python',
          'minizinc',
          'numpy'
      ],
      entry_points={
          "console_scripts": [
              "idesydecli = idesyde.cli:cli_entry"
          ]
      },
      zip_safe=True)
