from fastapi import APIRouter, Depends, Response

from tsuki.api.dependencies import AppSettings, DbSession
from tsuki.api.responses import ApiResponse
from tsuki.modules.auth.dependencies import CurrentAdmin, require_csrf
from tsuki.modules.auth.schemas import AdminIdentity, LoginRequest, LoginResult
from tsuki.modules.auth.security import (
    create_admin_token,
    delete_admin_cookie,
    set_admin_cookie,
)
from tsuki.modules.auth.service import AuthService

router = APIRouter(
    prefix="/api/v1/admin/auth",
    tags=["管理员认证"],
    dependencies=[Depends(require_csrf)],
)

auth_service = AuthService()


@router.post("/login")
async def login(
    body: LoginRequest, response: Response, session: DbSession, settings: AppSettings
) -> ApiResponse[LoginResult]:
    """管理员登录"""

    admin = await auth_service.authenticate(
        session, body.username, body.password.get_secret_value()
    )
    token, expires_at = create_admin_token(admin, settings)
    set_admin_cookie(response, token=token, expires_at=expires_at, settings=settings)
    admin_identity = AdminIdentity(id=admin.id, username=admin.username)
    return ApiResponse.success(LoginResult(admin=admin_identity, expires_at=expires_at))


@router.get("/me")
async def get_me(admin: CurrentAdmin) -> ApiResponse[AdminIdentity]:
    """获取当前管理员信息"""

    return ApiResponse.success(AdminIdentity(id=admin.id, username=admin.username))


@router.post("/logout")
async def logout(response: Response, settings: AppSettings) -> ApiResponse[None]:
    """管理员退出"""

    delete_admin_cookie(response, settings=settings)
    return ApiResponse.success()
