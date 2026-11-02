from typing import Self

from pydantic import BaseModel

from tsuki.core.api_code import ApiCode, CommonApiCode


class ApiResponse[T](BaseModel):
    code: str
    message: str
    data: T | None

    @classmethod
    def success(cls, data: T | None = None) -> Self:
        return cls(
            code=CommonApiCode.SUCCESS.code,
            message=CommonApiCode.SUCCESS.message,
            data=data,
        )

    @classmethod
    def error(
        cls,
        api_code: ApiCode,
        message: str | None = None,
        *,
        data: T | None = None,
    ) -> Self:
        return cls(code=api_code.code, message=message or api_code.message, data=data)


class ValidationIssue(BaseModel):
    location: list[str | int]
    message: str
    type: str


class ValidationErrorData(BaseModel):
    errors: list[ValidationIssue]
