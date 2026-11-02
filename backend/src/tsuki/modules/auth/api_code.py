from http import HTTPStatus

from tsuki.core.api_code import ApiCode


class AuthApiCode:
    INVALID_CREDENTIALS = ApiCode(
        HTTPStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误"
    )
    AUTHENTICATION_REQUIRED = ApiCode(
        HTTPStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录"
    )
    INVALID_TOKEN = ApiCode(
        HTTPStatus.UNAUTHORIZED, "INVALID_TOKEN", "登录已失效，请重新登录"
    )
    INVALID_CSRF_REQUEST = ApiCode(
        HTTPStatus.FORBIDDEN, "INVALID_CSRF_REQUEST", "请求校验失败"
    )
    ADMIN_ALREADY_EXISTS = ApiCode(
        HTTPStatus.CONFLICT, "ADMIN_ALREADY_EXISTS", "管理员已存在"
    )
    ADMIN_NOT_FOUND = ApiCode(HTTPStatus.NOT_FOUND, "ADMIN_NOT_FOUND", "管理员不存在")
