import pytest
from data_processing.processor import InstrumentDataProcessor
from data_processing.db_config import DBConfig

@pytest.fixture(autouse=True)
def db_config():
    db_config = DBConfig(
        host="localhost",
        port=9341,
        database="test_db",
        user="test_user",
        password_env_key="test_db"
    )
    yield db_config

class TestInstrumentDataProcessor:
    def test_instrument_data_processor_exists(self):
        assert InstrumentDataProcessor is not None
    
    def test_instrument_data_processor_can_be_instantiated(self):
        processor = InstrumentDataProcessor(db_config=db_config)
        assert processor is not None
        
        assert isinstance(processor, InstrumentDataProcessor)
    
    def test_instrument_data_processor_extract_data(self):
        processor = InstrumentDataProcessor(db_config=db_config)
        data1 = processor._extract_raw_info("AAPL")
        
        assert data1 is not None
        assert data1["symbol"] == "AAPL"
    
    def test_instrument_data_processor_transform_instrument_data(self):
        processor = InstrumentDataProcessor(db_config=db_config)
        raw_data = processor._extract_raw_info("AAPL") 
        transformed_data = processor._transform_instrument("AAPL", raw_data)
        
        assert transformed_data is not None
        assert transformed_data["symbol"] == "AAPL"
        assert "asset_class" in transformed_data
        assert transformed_data["currency"] == "USD"
        
    def test_instrument_data_processor_build_insert_rows(self):
        processor = InstrumentDataProcessor(db_config=db_config)
        raw_data = processor._extract_raw_info("AAPL")
        transformed_data = processor._transform_instrument("AAPL", raw_data)
        insert_rows = processor._build_insert_rows([transformed_data], set([]))
        
        assert insert_rows is not None
        assert len(insert_rows) > 0 
        
    def test_instrument_data_processor_fetch_all_instruments(self):
        processor = InstrumentDataProcessor(db_config=db_config)
        instruments = processor.fetch_all_instruments([
            "AAPL",
            "MSFT",
            "GOOGL"
        ])
        
        assert instruments is not None
        assert len(instruments) == 3 
        assert all("asset_class" in instrument for instrument in instruments)
        
        
        
        
        
        