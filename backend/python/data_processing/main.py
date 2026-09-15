import sys

from dotenv import load_dotenv

from data_processing.processor import InstrumentDataProcessor
from data_processing.db_config import DBConfig
from logger.logger import logger


def main() -> None:
    """Populate instruments table with default tickers from yfinance."""
    load_dotenv()
    db_config = DBConfig.from_env()
    processor = InstrumentDataProcessor(db_config)
    processor.populate_instruments()


if __name__ == '__main__':
    try:
        main()
    except Exception:
        logger.critical("[FAIL] Instrument population aborted", exc_info=True)
        sys.exit(1)

