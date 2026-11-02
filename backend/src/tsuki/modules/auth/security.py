import asyncio
from datetime import datetime, timedelta
from typing import Any

import jwt
from fastapi import Response
from pwdlib import PasswordHash
from pwdlib.hashers.argon2 import Argon2Hasher

from tsuki.core.config import Settings
from tsuki.core.exceptions import BusinessException
from tsuki.core.time_util import utc_now
from tsuki.modules.auth.api_code import AuthApiCode
from tsuki.modules.auth.models import Admin

password_hash = PasswordHash((Argon2Hasher(),))

DUMMY_PASSWORD_HASH = (
    "$argon2id$v=19$m=65536,t=3,p=4$c29tZXNhbHQAAAAAAAAAAA$Q7re1Zl9x0ne11m8V5W6Gg"
)

JWT_ALGORITHM = "HS256"
ADMIN_COOKIE_NAME = "tsuki_admin_token"
ADMIN_COOKIE_PATH = "/api/v1/admin"


async def hash_password(password: str) -> str:
    return await asyncio.to_thread(password_hash.hash, password)


async def verify_password(plain_password: str, hashed_password: str) -> bool:
    return await asyncio.to_thread(
        password_hash.verify, plain_password, hashed_password
    )


async def verify_dummy_password(plain_password: str) -> bool:
    await verify_password(plain_password, DUMMY_PASSWORD_HASH)
    return False


def create_admin_token(admin: Admin, settings: Settings) -> tuple[str, datetime]:
    """创建管理员令牌"""

    expires_at = utc_now() + timedelta(seconds=settings.admin_access_token_ttl_seconds)
    payload = {
        "sub": str(admin.id),
        "exp": int(expires_at.timestamp()),
        "ver": admin.auth_version,
    }

    # 使用 HS256 算法加密令牌
    access_token = jwt.encode(
        payload, settings.admin_jwt_secret.get_secret_value(), algorithm=JWT_ALGORITHM
    )
    return (access_token, expires_at)


def decode_admin_token(token: str, settings: Settings) -> dict[str, Any]:
    """解码管理员令牌"""

    try:
        payload = jwt.decode(
            token,
            settings.admin_jwt_secret.get_secret_value(),
            algorithms=[JWT_ALGORITHM],
            options={"require": ["sub", "exp", "ver"]},
        )

        # 校验 sub: 必须为纯数字字符串且大于 0
        raw_sub = payload["sub"]
        if not isinstance(raw_sub, str) or not raw_sub.isdigit() or int(raw_sub) <= 0:
            raise ValueError("非法的管理员 ID")
        # 校验 ver: 必须为正整型（排除 bool 类型）
        raw_ver = payload["ver"]
        if isinstance(raw_ver, bool) or not isinstance(raw_ver, int) or raw_ver <= 0:
            raise ValueError("非法的认证版本")

        return payload

    except (jwt.PyJWTError, ValueError, TypeError):
        raise BusinessException(AuthApiCode.INVALID_TOKEN)


def set_admin_cookie(
    response: Response, token: str, expires_at: datetime, settings: Settings
) -> None:
    """设置管理员 Cookie"""

    response.set_cookie(
        key=ADMIN_COOKIE_NAME,
        value=token,
        max_age=settings.admin_access_token_ttl_seconds,
        expires=expires_at,
        path=ADMIN_COOKIE_PATH,
        httponly=True,
        samesite="lax",
        secure=settings.admin_cookie_secure,
    )


def delete_admin_cookie(response: Response, settings: Settings) -> None:
    """删除管理员 Cookie"""

    response.delete_cookie(
        key=ADMIN_COOKIE_NAME,
        path=ADMIN_COOKIE_PATH,
        httponly=True,
        samesite="lax",
        secure=settings.admin_cookie_secure,
    )
