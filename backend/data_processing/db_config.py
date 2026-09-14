import os
from dataclasses import dataclass

@dataclass
class DBConfig:
    """Database configuration container. Passwords are NOT stored—fetched on-demand from environment."""
    host: str
    port: int
    database: str
    user: str
    password_env_key: str = 'DB_PASSWORD'
    
    @property
    def password(self) -> str:
        """Lazy-load password from environment at access time (never stored in memory)."""
        return os.getenv(self.password_env_key, '')
    
    @classmethod
    def from_env(cls) -> 'DBConfig':
        """Load non-sensitive config from environment variables."""
        return cls(
            host=os.getenv('DB_HOST', 'localhost'),
            port=int(os.getenv('DB_PORT', 5432)),
            database=os.getenv('POSTGRES_DB', 'seaj_dev'),
            user=os.getenv('POSTGRES_USER', 'postgres'),
            password_env_key=os.getenv('DB_PASSWORD_KEY', 'POSTGRES_PASSWORD')
        )