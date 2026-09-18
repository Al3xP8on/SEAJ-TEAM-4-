from app.backend.backend_python.data_processing.db_config import DBConfig

class TestDBConfig:
    def test_db_config_exists(self):
        assert DBConfig is not None
    
    def test_db_config_accepts_parameters(self):
        config = DBConfig(
            host="localhost",
            port=5432,
            user="test_user",
            password_env_key="test_password",
            database="test_db"
        )
        
        assert config.host == "localhost"
        assert config.port == 5432
        assert config.user == "test_user"
        assert config.password_env_key == "test_password"
        assert config.database == "test_db"
        
