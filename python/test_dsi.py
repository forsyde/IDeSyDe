from desyder.api import DeSyDeR
from forsyde.io.python import ForSyDeModel

dse = DeSyDeR()
f = ForSyDeModel.from_file('example.db')
dse.identify_problems(f)
