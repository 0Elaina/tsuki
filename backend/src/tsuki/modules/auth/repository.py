from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from tsuki.modules.auth.models import Admin


class AuthRepository:
    async def get_by_username(
        self, session: AsyncSession, username: str
    ) -> Admin | None:
        """获取管理员用户名"""

        return (
            await session.execute(select(Admin).where(Admin.username == username))
        ).scalar_one_or_none()

    async def get_by_id(self, session: AsyncSession, admin_id: int) -> Admin | None:
        """根据管理员ID获取管理员对象"""

        return (
            await session.execute(select(Admin).where(Admin.id == admin_id))
        ).scalar_one_or_none()

    async def create_admin(
        self, session: AsyncSession, username: str, password_hash: str
    ) -> Admin:
        """创建管理员对象"""

        admin = Admin(username=username, password_hash=password_hash)
        session.add(admin)
        await session.flush()
        return admin

    async def update_password(
        self, session: AsyncSession, admin: Admin, new_password_hash: str
    ) -> Admin:
        """更新管理员密码"""

        admin.password_hash = new_password_hash
        admin.auth_version += 1
        await session.flush()
        return admin
