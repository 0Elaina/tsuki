from tsuki.core.api_code import ApiCode


class BusinessException(Exception):
    def __init__(self, api_code: ApiCode, message: str | None = None) -> None:
        self.api_code = api_code
        self.message = message or api_code.message
        super().__init__(self.message)
