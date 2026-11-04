from datetime import date
from typing import Annotated, Self

from pydantic import (
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
    model_validator,
)

from tsuki.api.pagination import PageQuery
from tsuki.modules.work.enums import MediaType, ReleaseStatus
from tsuki.modules.work.models import Work

MAX_SEARCH_KEYWORD_LENGTH: int = 255
MAX_NAME_LENGTH: int = 255
MAX_ORIGINAL_NAME_LENGTH: int = 255
MAX_ALIASES_COUNT: int = 50
MAX_ALIAS_LENGTH: int = 255
MAX_SUMMARY_LENGTH: int = 10000
MAX_BATCH_IDS_COUNT: int = 100
MAX_WORK_ID: int = 2**63 - 1

AliasName = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_ALIAS_LENGTH),
]
WorkId = Annotated[int, Field(strict=True, ge=1, le=MAX_WORK_ID)]


class WorkListQuery(PageQuery):
    """作品列表查询参数"""

    model_config = ConfigDict(extra="forbid")

    keyword: str | None = Field(default=None, max_length=MAX_SEARCH_KEYWORD_LENGTH)
    media_type: MediaType | None = None
    release_status: ReleaseStatus | None = None

    @field_validator("keyword", mode="before")
    @classmethod
    def validate_keyword(cls, value: object) -> object:
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


class WorkReleaseDateInput(BaseModel):
    """发行日期入参"""

    model_config = ConfigDict(extra="forbid")
    year: int = Field(strict=True, ge=1, le=9999)
    month: int | None = Field(default=None, strict=True, ge=1, le=12)
    day: int | None = Field(default=None, strict=True, ge=1, le=31)

    @model_validator(mode="after")
    def validate_model(self) -> Self:
        if self.day is not None and self.month is None:
            raise ValueError("填写发行日时必须填写月份")
        if self.month is not None and self.day is not None:
            try:
                date(self.year, self.month, self.day)
            except ValueError:
                raise ValueError("发行日期不合法, 不是有效的日历日期")
        return self


class WorkWriteInput(BaseModel):
    """作品写入公共基础模型"""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(max_length=MAX_NAME_LENGTH)
    original_name: str | None = Field(max_length=MAX_ORIGINAL_NAME_LENGTH)
    aliases: list[AliasName] = Field(max_length=MAX_ALIASES_COUNT)
    summary: str | None = Field(max_length=MAX_SUMMARY_LENGTH)
    media_type: MediaType
    release_status: ReleaseStatus
    release_date: WorkReleaseDateInput | None

    @field_validator("name", mode="before")
    @classmethod
    def validate_name(cls, value: object) -> object:
        if isinstance(value, str):
            stripped = value.strip()
            if not stripped:
                raise ValueError("作品名称不能为空")
            return stripped
        return value

    @field_validator("original_name", "summary", mode="before")
    @classmethod
    def validate_original_name_and_summary(cls, value: object) -> object:
        if value is None:
            return None
        if isinstance(value, str):
            return value.strip() if value.strip() else None
        return value

    @field_validator("aliases", mode="after")
    @classmethod
    def validate_aliases(cls, value: list[str]) -> list[str]:
        return list(dict.fromkeys(value))


class WorkUpdateRequest(WorkWriteInput):
    """作品更新入参"""

    pass


class WorkCreateRequest(WorkWriteInput):
    """作品创建入参"""

    original_name: str | None = Field(default=None, max_length=MAX_ORIGINAL_NAME_LENGTH)
    aliases: list[AliasName] = Field(default_factory=list, max_length=MAX_ALIASES_COUNT)
    summary: str | None = Field(default=None, max_length=MAX_SUMMARY_LENGTH)
    release_status: ReleaseStatus = Field(default=ReleaseStatus.UNKNOWN)
    release_date: WorkReleaseDateInput | None = Field(default=None)


class WorkBatchIdsRequest(BaseModel):
    """批量操作作品 ID 列表入参"""

    model_config = ConfigDict(extra="forbid")

    work_ids: list[WorkId] = Field(min_length=1, max_length=MAX_BATCH_IDS_COUNT)

    @field_validator("work_ids", mode="after")
    @classmethod
    def validate_work_ids(cls, value: list[int]) -> list[int]:
        return list(dict.fromkeys(value))


class WorkBatchStatusUpdateRequest(WorkBatchIdsRequest):
    """批量更新作品状态入参"""

    release_status: ReleaseStatus


class WorkBatchDeleteResult(BaseModel):
    """批量删除作品结果"""

    deleted_count: int = Field(ge=0)


class WorkBatchStatusUpdateResult(BaseModel):
    """批量更新作品状态结果"""

    updated_count: int = Field(ge=0)
