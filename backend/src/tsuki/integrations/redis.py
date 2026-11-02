from redis.asyncio import Redis

from tsuki.core.config import Settings


def create_redis_client(settings: Settings) -> Redis:
    return Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password.get_secret_value(),
        db=settings.redis_db,
        decode_responses=True,
    )
