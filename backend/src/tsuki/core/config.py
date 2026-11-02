from typing import Literal

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="TSUKI_",
        env_file=".env",
        env_file_encoding="utf-8",
    )

    app_name: str = "Tsuki"
    debug: bool = False
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"
    cors_origins: list[str] = []

    postgres_host: str = "127.0.0.1"
    postgres_port: int = 5432
    postgres_db: str = "tsuki"
    postgres_user: str
    postgres_password: SecretStr

    redis_host: str = "127.0.0.1"
    redis_port: int = 6379
    redis_password: SecretStr
    redis_db: int = 0
    redis_key_prefix: str = "tsuki:"

    admin_jwt_secret: SecretStr
    admin_access_token_ttl_seconds: int = 28800
    admin_cookie_secure: bool = True
