import argparse
import logging
import random

import forsyde.io.python.api as forsyde_io
import networkx as nx

from idesyde.identification.api import identify_decision_models
from idesyde.identification.api import choose_decision_models
from idesyde.exploration import choose_explorer
from idesyde.exploration import MinizincExplorer

description = '''
  ___  ___        ___        ___
 |_ _||   \  ___ / __| _  _ |   \  ___ 
  | | | |) |/ -_)\__ \| || || |) |/ -_)
 |___||___/ \___||___/ \_, ||___/ \___|
                       |__/

Automated Identification and Exploration of Design Spaces in ForSyDe
'''


def cli_entry():
    parser = argparse.ArgumentParser(prog="idesyde",
                                     description=description,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('model', type=str, help='Input ForSyDe-IO model to DeSyDe')
    parser.add_argument('--verbosity',
                        type=str,
                        default="INFO",
                        help='''
                        CLI verbosity level, from most silent to most verbose:
                        CRITICAL, ERROR, INFO (default), DEBUG.

                        Default is INFO.

                        Note that capitalization is done internally, so
                        info and INFO are equally valid.
                        ''')
    parser.add_argument('-o',
                        '--output',
                        type=str,
                        action='append',
                        nargs=1,
                        help='''
                        Output files, which can be another model or
                        graph visualization formats.
                        ''')
    parser.add_argument('--decision-model',
                        action='append',
                        nargs=1,
                        help='''
                        Filter decision model to match these short names.
                        ''')
    parser.add_argument('--mzn-solver',
                        type=str,
                        default='gecode',
                        help='''
                        Minizinc solver to be used for decision models
                        that are solved by them.
                        ''')
    args = parser.parse_args()
    logger = logging.getLogger('CLI')
    logger.setLevel(getattr(logging, args.verbosity.upper(), 'INFO'))
    consoleLogHandler = logging.StreamHandler()
    consoleLogHandler.setLevel(getattr(logging, args.verbosity.upper(), 'INFO'))
    consoleLogHandler.setFormatter(logging.Formatter('[{levelname:<8}{asctime}] {message}', style='{'))
    logger.addHandler(consoleLogHandler)
    logger.debug('Arguments parsed')
    in_model = forsyde_io.load_model(args.model)
    logger.info('Model parsed')
    logger.debug('DeSyDeR API created')
    identified = identify_decision_models(in_model)
    logger.info(f'{len(identified)} Decision model(s) identified')
    logger.debug(f"Decision models identified: {identified}")
    desired_names = [i[0] for i in args.decision_model] if args.decision_model else []
    models_chosen = choose_decision_models(identified, desired_names=desired_names)
    logger.info(f'{len(models_chosen)} Decision model(s) chosen')
    explorer_and_models = choose_explorer(models_chosen)
    logger.info(f'{len(explorer_and_models)} Explorer(s) and Model(s) chosen')
    resulting_model = None
    if len(explorer_and_models) > 0:
        if len(explorer_and_models) > 1:
            logger.warning("More than one explorer and model chosen. Picking one randomly")
        (explorer, model) = random.choice(explorer_and_models)
        logger.info(f'Exploring {model.short_name()} with {explorer.short_name()}')
        if isinstance(explorer, MinizincExplorer):
            resulting_model = explorer.explore(model, backend_solver_name=args.mzn_solver)
        else:
            resulting_model = explorer.explore(model)
        logger.info('Exploration complete')
    if resulting_model:
        out_model = nx.compose(in_model, resulting_model)
        outputs = [i[0] for i in args.output]\
            if args.output else [f'out_{args.model}']
        for out_file in outputs:
            forsyde_io.write_model(out_model, out_file)
            logger.info(f'Writting output model {out_file}')
    logging.info('Done')


if __name__ == "__main__":
    cli_entry()
