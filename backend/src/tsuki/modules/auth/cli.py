import argparse
import asyncio
import getpass
import sys

from tsuki.core.config import Settings
from tsuki.core.exceptions import BusinessException
from tsuki.db.session import create_database_engine, create_session_factory
from tsuki.modules.auth.schemas import validate_password, validate_username
from tsuki.modules.auth.service import AuthService


def prompt_password(prompt_text: str = "请输入管理员密码: ") -> str:
    password = getpass.getpass(prompt_text)
    validate_password(password)
    confirmation = getpass.getpass("请再次输入密码以确认: ")
    if password != confirmation:
        raise ValueError("两次输入的密码不一致")
    return password


async def run_create_admin(username: str, password: str) -> None:
    settings = Settings()
    engine = create_database_engine(settings)
    session_factory = create_session_factory(engine)
    try:
        service = AuthService()
        async with session_factory() as session:
            admin = await service.create_admin(
                session, username=username, password=password
            )
            print(f"管理员 '{admin.username}' 创建成功 (ID: {admin.id})")
    finally:
        await engine.dispose()


async def run_reset_password(username: str, new_password: str) -> None:
    settings = Settings()
    engine = create_database_engine(settings)
    session_factory = create_session_factory(engine)
    try:
        service = AuthService()
        async with session_factory() as session:
            admin = await service.reset_password(
                session, username=username, new_password=new_password
            )
            print(
                f"管理员 '{admin.username}' 密码重置成功 (认证版本: {admin.auth_version})"
            )
    finally:
        await engine.dispose()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="python -m tsuki.modules.auth.cli",
        description="Tsuki 管理员账号管理命令行工具",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    create_parser = subparsers.add_parser("create-admin", help="创建新管理员账号")
    create_parser.add_argument(
        "--username", required=True, type=str, help="管理员用户名（3-32位字符）"
    )

    reset_parser = subparsers.add_parser("reset-password", help="重置管理员密码")
    reset_parser.add_argument(
        "--username", required=True, type=str, help="管理员用户名（3-32位字符）"
    )

    return parser.parse_args(argv)


async def main() -> int:
    args = parse_args()

    try:
        username = validate_username(args.username)
        password = prompt_password()
    except (ValueError, TypeError) as err:
        print(f"参数或输入校验失败: {err}", file=sys.stderr)
        return 1

    try:
        if args.command == "create-admin":
            await run_create_admin(username=username, password=password)
        elif args.command == "reset-password":
            await run_reset_password(username=username, new_password=password)
        return 0
    except BusinessException as err:
        print(f"操作失败: {err.message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
