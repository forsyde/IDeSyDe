import forsyde.io.python as forpyde
import idesyde.identification as ident

f = forpyde.ForSyDeModel.from_file('example.db')
sdfprob = ident.SDFExecution()
assert(sdfprob.identify(f, []) is True)
