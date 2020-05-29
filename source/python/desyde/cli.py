import argparse

from ForSyDe.Model.IO import ForSyDeIO

import desyde.preprocessing as pre
import desyde.identification as iden

description = '''
DeSyDe - Analytical Design Space Exploration for ForSyDe
'''

def cli_entry():
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('model', type=str,
                        help='Input ForSyDe-IO model to DeSyDe')
    parser.add_argument('--flat-out', type=str, metavar='flatout', dest='flatout',
                        help='Outputs to `flatout` the flattened model of the input.')
    args = parser.parse_args()
    if args.flatout:
        in_model = ForSyDeIO.parse(args.model)
        pre.ModelFlattener(in_model).flatten().dump(args.flatout)


if __name__ == "__main__":
    cli_entry()
