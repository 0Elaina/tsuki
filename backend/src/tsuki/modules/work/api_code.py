from http import HTTPStatus

from tsuki.core.api_code import ApiCode


class WorkApiCode:
    WORK_NOT_FOUND = ApiCode(HTTPStatus.NOT_FOUND, "WORK_NOT_FOUND", "作品不存在")
