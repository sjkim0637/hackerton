import re
from functools import lru_cache

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "development"
    database_url: str = "postgresql+psycopg://geotime:change-me@localhost:5432/geotime"
    postgis_schema: str = "public"
    minio_endpoint: str = "localhost:9000"
    minio_public_endpoint: str = "http://localhost:9000"
    minio_access_key: str = "geotime"
    minio_secret_key: str = "change-me-minio"
    minio_bucket: str = "geo-time-assets"
    cors_origins: str = "http://localhost:3000,http://10.0.2.2:8000"
    log_level: str = "INFO"

    @field_validator("database_url", mode="before")
    @classmethod
    def use_psycopg_v3_driver(cls, value: object) -> object:
        if not isinstance(value, str):
            return value
        if value.startswith("postgresql://"):
            return value.replace("postgresql://", "postgresql+psycopg://", 1)
        if value.startswith("postgres://"):
            return value.replace("postgres://", "postgresql+psycopg://", 1)
        return value

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def database_search_path_sql(self) -> str:
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", self.postgis_schema) is None:
            raise ValueError("POSTGIS_SCHEMA must be a PostgreSQL identifier")
        schemas = ["public"]
        if self.postgis_schema != "public":
            schemas.append(self.postgis_schema)
        return "SET search_path TO " + ", ".join(f'"{schema}"' for schema in schemas)


@lru_cache
def get_settings() -> Settings:
    return Settings()
