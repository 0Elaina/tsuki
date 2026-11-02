from logging.config import dictConfig


def configure_logging(level: str) -> None:
    dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "default": {
                    "format": "%(asctime)s %(levelname)s %(name)s %(message)s",
                },
            },
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "stream": "ext://sys.stdout",
                    "formatter": "default",
                },
            },
            "root": {"level": level, "handlers": ["console"]},
            "loggers": {
                "uvicorn": {
                    "level": level,
                    "handlers": ["console"],
                    "propagate": False,
                },
                "uvicorn.error": {
                    "level": level,
                    "handlers": [],
                    "propagate": True,
                },
                "uvicorn.access": {
                    "level": level,
                    "handlers": ["console"],
                    "propagate": False,
                },
                "sqlalchemy": {"level": "WARNING"},
            },
        }
    )
