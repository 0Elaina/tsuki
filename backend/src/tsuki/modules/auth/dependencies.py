from typing import Annotated

from fastapi import Depends, Header, Request

from tsuki.api.dependencies import AppSettings, DbSession
from tsuki.core.exceptions import BusinessException
from tsuki.modules.auth.api_code import AuthApiCode
from tsuki.modules.auth.models import Admin
from tsuki.modules.auth.repository import AuthRepository
from tsuki.modules.auth.security import ADMIN_COOKIE_NAME, decode_admin_token


async def require_csrf(
    request: Request,
    x_csrf_protection: Annotated[
        str | None,
        Header(alias="X-CSRF-Protection", description="防护头, 非安全方法固定为 1"),
    ] = None,
) -> None:
    if request.method in ["GET", "HEAD", "OPTIONS"]:
        return

    if x_csrf_protection != "1":
        raise BusinessException(AuthApiCode.INVALID_CSRF_REQUEST)


async def get_current_admin(
    request: Request, session: DbSession, settings: AppSettings
) -> Admin:
    """获取当前管理员"""
    token = request.cookies.get(ADMIN_COOKIE_NAME)
    if not token:
        raise BusinessException(AuthApiCode.AUTHENTICATION_REQUIRED)

    payload = decode_admin_token(token, settings)
    admin_id = int(payload["sub"])
    repo = AuthRepository()
    admin = await repo.get_by_id(session, admin_id)

    if admin is None:
        raise BusinessException(AuthApiCode.INVALID_TOKEN)

    if admin.auth_version != payload["ver"]:
        raise BusinessException(AuthApiCode.INVALID_TOKEN)
    return admin


CurrentAdmin = Annotated[Admin, Depends(get_current_admin)]
