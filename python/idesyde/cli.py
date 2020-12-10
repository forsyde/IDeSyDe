import argparse
import asyncio
import logging

import networkx as nx
from forsyde.io.python import ForSyDeModel

from idesyde.identification import identify_decision_models
from idesyde.identification import choose_decision_models
from idesyde.exploration import choose_explorer

description = '''
DeSyDe - Analytical Design Space Exploration for ForSyDe
'''


def cli_entry():
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('model',
                        type=str,
                        help='Input ForSyDe-IO model to DeSyDe')
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
    parser.add_argument('-o', '--output',
                        type=str,
                        action='append',
                        nargs=1,
                        help='''
                        Output files, which can be another model or
                        graph visualization formats.
                        ''')
    args = parser.parse_args()
    logger = logging.getLogger('CLI')
    logger.setLevel(
        getattr(logging, args.verbosity.upper(), 'INFO')
    )
    consoleLogHandler = logging.StreamHandler()
    consoleLogHandler.setLevel(
        getattr(logging, args.verbosity.upper(), 'INFO')
    )
    consoleLogHandler.setFormatter(
        logging.Formatter('[%(levelname)s\t%(asctime)s] %(message)s')
    )
    logger.addHandler(consoleLogHandler)
    logger.debug('Arguments parsed')
    in_model = ForSyDeModel.from_file(args.model)
    logger.info('Model parsed')
    logger.debug('DeSyDeR API created')
    identified = identify_decision_models(in_model)
    logger.info(f'{len(identified)} Decision model(s) identified')
    logger.debug(f"Decision models identified: {identified}")
    models_chosen = choose_decision_models(identified)
    logger.info(f'{len(models_chosen)} Decision model(s) chosen')
    explorer_and_models = choose_explorer(models_chosen)
    logger.info(f'{len(explorer_and_models)} Explorer(s) and Model(s) chosen')
    model_decisions = None
    if len(explorer_and_models) == 0:
        print('No model or explorer could be chosen. Exiting')
    elif len(explorer_and_models) == 1:
        (e, m) = explorer_and_models[0]  # there is only one.
        logger.info('Initiating design space exploration')
        model_decisions = e.explore(m)
        logger.info('Design space explored')
    else:
        print('More than one chosen model and explorer. Exiting')
    if model_decisions:
        out_model = nx.compose(in_model, model_decisions)
        outputs = [i[0] for i in args.output]\
            if args.output else [f'out_{args.model}']
        for out_file in outputs:
            out_model.write(out_file)
            logger.info(f'Writting output model {out_file}')
    logging.info('Done')


if __name__ == "__main__":
    cli_entry()
