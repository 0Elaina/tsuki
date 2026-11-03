from typing import Self

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator

from tsuki.api.pagination import PageQuery
from tsuki.modules.work.enums import MediaType, ReleaseStatus
from tsuki.modules.work.models import Work

MAX_SEARCH_KEYWORD_LENGTH: int = 255


class WorkListQuery(PageQuery):
    """作品列表查询参数"""

    model_config = ConfigDict(extra="forbid")

    keyword: str | None = Field(default=None, max_length=MAX_SEARCH_KEYWORD_LENGTH)
    media_type: MediaType | None = None
    release_status: ReleaseStatus | None = None

    @field_validator("keyword", mode="before")
    def validate_keyword(value: object) -> object:
        if not value:
            return None
        if isinstance(value, str):
            return value.strip() if value.strip() else None
        return value


class WorkReleaseDate(BaseModel):
    """作品发布时间"""

    year: int
    month: int | None
    day: int | None


class WorkListItem(BaseModel):
    """作品列表项"""

    id: int
    name: str
    original_name: str | None
    media_type: MediaType
    release_status: ReleaseStatus
    updated_at: AwareDatetime


class WorkDetail(BaseModel):
    """作品详情"""

    id: int
    name: str
    original_name: str | None
    aliases: list[str]
    summary: str | None
    media_type: MediaType
    release_status: ReleaseStatus
    release_date: WorkReleaseDate | None
    created_at: AwareDatetime
    updated_at: AwareDatetime

    @classmethod
    def of(cls, work: Work, release_date: WorkReleaseDate | None) -> Self:
        return cls(
            id=work.id,
            name=work.name,
            original_name=work.original_name,
            aliases=work.aliases,
            summary=work.summary,
            media_type=MediaType(work.media_type),
            release_status=ReleaseStatus(work.release_status),
            release_date=release_date,
            created_at=work.created_at,
            updated_at=work.updated_at,
        )
