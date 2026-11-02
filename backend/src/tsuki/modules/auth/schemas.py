import re
from datetime import datetime

from pydantic import BaseModel, ConfigDict, SecretStr, field_validator

USERNAME_REGEX = re.compile(r"^[A-Za-z][A-Za-z0-9_]{2,31}$")
PASSWORD_REGEX = re.compile(r"^[\x21-\x7e]{6,128}$")


def validate_username(value: object) -> str:
    if not isinstance(value, str):
        raise ValueError("用户名必须为字符串")
    cleaned = value.strip()
    if not USERNAME_REGEX.fullmatch(cleaned):
        raise ValueError("用户名须为3–32位英文字母开头，且仅包含英文字母、数字和下划线")
    return cleaned


def validate_password(value: str) -> str:
    if not PASSWORD_REGEX.fullmatch(value):
        raise ValueError("密码须为6–128位ASCII可见字符，不可包含空格")
    return value


class LoginRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    username: str
    password: SecretStr

    @field_validator("username", mode="before")
    @classmethod
    def check_username(cls, value: object) -> str:
        return validate_username(value)

    @field_validator("password", mode="after")
    @classmethod
    def check_password(cls, value: SecretStr) -> SecretStr:
        validate_password(value.get_secret_value())
        return value


class AdminIdentity(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: int
    username: str


class LoginResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    admin: AdminIdentity
    expires_at: datetime
