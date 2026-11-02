from sqlalchemy.ext.asyncio import AsyncSession

from tsuki.core.exceptions import BusinessException
from tsuki.modules.auth.api_code import AuthApiCode
from tsuki.modules.auth.models import Admin
from tsuki.modules.auth.repository import AuthRepository
from tsuki.modules.auth.security import (
    hash_password,
    verify_dummy_password,
    verify_password,
)


class AuthService:
    def __init__(self, repository: AuthRepository | None = None) -> None:
        self.repository = repository or AuthRepository()

    async def create_admin(
        self, session: AsyncSession, username: str, password: str
    ) -> Admin:
        """创建管理员"""

        async with session.begin():
            # 检查用户名是否已存在
            admin = await self.repository.get_by_username(session, username)
            if admin:
                raise BusinessException(AuthApiCode.ADMIN_ALREADY_EXISTS)

            # 创建管理员
            pwd_hash = await hash_password(password)
            admin = await self.repository.create_admin(
                session, username=username, password_hash=pwd_hash
            )
        return admin

    async def reset_password(
        self, session: AsyncSession, username: str, new_password: str
    ) -> Admin:
        """重置管理员密码"""

        async with session.begin():
            admin = await self.repository.get_by_username(session, username)
            if not admin:
                raise BusinessException(AuthApiCode.ADMIN_NOT_FOUND)

            pwd_hash = await hash_password(new_password)
            admin = await self.repository.update_password(
                session, admin=admin, new_password_hash=pwd_hash
            )
        return admin

    async def authenticate(
        self, session: AsyncSession, username: str, password: str
    ) -> Admin:
        """验证管理员身份"""
        async with session.begin():
            admin = await self.repository.get_by_username(session, username)
            if not admin:
                await verify_dummy_password(password)
                raise BusinessException(AuthApiCode.INVALID_CREDENTIALS)

            is_valid = await verify_password(password, admin.password_hash)
            if not is_valid:
                raise BusinessException(AuthApiCode.INVALID_CREDENTIALS)
        return admin
