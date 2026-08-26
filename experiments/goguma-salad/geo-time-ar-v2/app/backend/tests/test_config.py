from app.config import Settings


def test_database_url_selects_psycopg_v3_for_postgresql_url() -> None:
    settings = Settings(database_url="postgresql://user:password@database.example/app")

    assert settings.database_url == (
        "postgresql+psycopg://user:password@database.example/app"
    )


def test_database_url_selects_psycopg_v3_for_legacy_postgres_url() -> None:
    settings = Settings(database_url="postgres://user:password@database.example/app")

    assert settings.database_url == (
        "postgresql+psycopg://user:password@database.example/app"
    )
