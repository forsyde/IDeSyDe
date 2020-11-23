import argparse
import asyncio
import logging

from forsyde.io.python import ForSyDeModel

from desyder.api import DeSyDeR


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
    api = DeSyDeR()
    loop = asyncio.get_event_loop()
    logger.debug('DeSyDeR API created')
    identified = loop.run_until_complete(
        api.identify_problems(in_model)
    )
    logger.info(f'{len(identified)} Problems identified')
    logger.debug(f"Decision models identified: {identified}")


if __name__ == "__main__":
    cli_entry()
