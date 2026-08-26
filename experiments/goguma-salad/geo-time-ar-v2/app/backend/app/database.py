from collections.abc import Generator

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import NullPool

from app.config import get_settings


class Base(DeclarativeBase):
    pass


settings = get_settings()
engine_options = {"pool_pre_ping": True}
if settings.app_env.lower() in {"production", "preview"}:
    # Vercel Functions are ephemeral. Supabase's transaction pooler owns pooling.
    engine_options["poolclass"] = NullPool
engine = create_engine(settings.database_url, **engine_options)


@event.listens_for(engine, "connect")
def set_database_search_path(dbapi_connection, _connection_record) -> None:
    with dbapi_connection.cursor() as cursor:
        cursor.execute(settings.database_search_path_sql)


SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)


def get_db() -> Generator[Session, None, None]:
    with SessionLocal() as session:
        yield session
