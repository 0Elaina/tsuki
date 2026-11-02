from dataclasses import dataclass
from http import HTTPStatus


@dataclass(frozen=True)
class ApiCode:
    http_status: HTTPStatus
    code: str
    message: str


class CommonApiCode:
    SUCCESS = ApiCode(HTTPStatus.OK, "SUCCESS", "响应成功")
    INVALID_REQUEST = ApiCode(
        HTTPStatus.UNPROCESSABLE_ENTITY, "INVALID_REQUEST", "请求参数校验失败"
    )
    RESOURCE_NOT_FOUND = ApiCode(
        HTTPStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求的资源不存在"
    )
    METHOD_NOT_ALLOWED = ApiCode(
        HTTPStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不允许"
    )
    HTTP_ERROR = ApiCode(HTTPStatus.BAD_REQUEST, "HTTP_ERROR", "请求失败")
    INTERNAL_SERVER_ERROR = ApiCode(
        HTTPStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "服务器内部错误"
    )
