from collections.abc import AsyncIterator
from contextlib import AsyncExitStack, asynccontextmanager
from typing import cast

from fastapi import FastAPI

from tsuki.core.config import Settings
from tsuki.db.session import create_database_engine, create_session_factory
from tsuki.integrations.redis import create_redis_client


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = cast(Settings, app.state.settings)

    async with AsyncExitStack() as resources:
        engine = create_database_engine(settings)
        resources.push_async_callback(engine.dispose)

        redis_client = create_redis_client(settings)
        resources.push_async_callback(redis_client.aclose)

        app.state.db_session_factory = create_session_factory(engine)
        app.state.redis_client = redis_client
        yield
