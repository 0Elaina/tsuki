from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException
from starlette.responses import JSONResponse

from tsuki.api.responses import ApiResponse, ValidationErrorData, ValidationIssue
from tsuki.core.api_code import CommonApiCode
from tsuki.core.exceptions import BusinessException


async def business_exception_handler(
    request: Request, exc: BusinessException
) -> JSONResponse:
    response = ApiResponse[None].error(exc.api_code, exc.message)
    return JSONResponse(
        status_code=exc.api_code.http_status,
        content=response.model_dump(mode="json"),
    )


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    data = ValidationErrorData(
        errors=[
            ValidationIssue(
                location=list(error["loc"]),
                message=error["msg"],
                type=error["type"],
            )
            for error in exc.errors()
        ]
    )
    response = ApiResponse[ValidationErrorData].error(
        CommonApiCode.INVALID_REQUEST, data=data
    )
    return JSONResponse(
        status_code=CommonApiCode.INVALID_REQUEST.http_status,
        content=response.model_dump(mode="json"),
    )


async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    api_code = {
        404: CommonApiCode.RESOURCE_NOT_FOUND,
        405: CommonApiCode.METHOD_NOT_ALLOWED,
        422: CommonApiCode.INVALID_REQUEST,
    }.get(exc.status_code, CommonApiCode.HTTP_ERROR)
    # The generic fallback code does not replace the exception's HTTP status.
    response = ApiResponse[None].error(api_code)
    return JSONResponse(
        status_code=exc.status_code,
        content=response.model_dump(mode="json"),
        headers=exc.headers,
    )


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    # Starlette re-raises server errors after responding; Uvicorn logs the traceback.
    response = ApiResponse[None].error(CommonApiCode.INTERNAL_SERVER_ERROR)
    return JSONResponse(
        status_code=CommonApiCode.INTERNAL_SERVER_ERROR.http_status,
        content=response.model_dump(mode="json"),
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.exception_handler(BusinessException)(business_exception_handler)
    app.exception_handler(RequestValidationError)(validation_exception_handler)
    app.exception_handler(HTTPException)(http_exception_handler)
    app.exception_handler(Exception)(unhandled_exception_handler)
