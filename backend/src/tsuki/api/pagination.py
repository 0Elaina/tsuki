from pydantic import BaseModel, ConfigDict, Field

DEFAULT_PAGE: int = 1
DEFAULT_PAGE_SIZE: int = 20
MAX_PAGE_SIZE: int = 100


class PageQuery(BaseModel):
    model_config = ConfigDict(extra="forbid")

    page: int = Field(default=DEFAULT_PAGE, ge=1)
    page_size: int = Field(default=DEFAULT_PAGE_SIZE, ge=1, le=MAX_PAGE_SIZE)


class Page[T](BaseModel):
    items: list[T]
    total: int
    page: int
    page_size: int
