from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware

from tsuki.api.exception_handlers import register_exception_handlers
from tsuki.core.config import Settings
from tsuki.core.logging import configure_logging
from tsuki.lifespan import lifespan
from tsuki.modules.auth.router import router as auth_router


def create_app() -> CORSMiddleware:
    settings = Settings()
    configure_logging(settings.log_level)

    api = FastAPI(title=settings.app_name, debug=settings.debug, lifespan=lifespan)

    # 绑定路由
    api.include_router(auth_router)

    api.state.settings = settings
    register_exception_handlers(api)

    # Keep CORS outside ServerErrorMiddleware so error responses have CORS headers.
    return CORSMiddleware(
        app=api,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"],
        allow_headers=["Authorization", "Content-Type", "X-CSRF-Protection"],
    )
