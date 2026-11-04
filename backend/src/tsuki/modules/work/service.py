from sqlalchemy.ext.asyncio import AsyncSession

from tsuki.api.pagination import Page
from tsuki.core.exceptions import BusinessException
from tsuki.modules.work.api_code import WorkApiCode
from tsuki.modules.work.enums import MediaType, ReleaseStatus
from tsuki.modules.work.models import Work
from tsuki.modules.work.repository import WorkRepository
from tsuki.modules.work.schemas import (
    WorkCreateRequest,
    WorkDetail,
    WorkListItem,
    WorkListQuery,
    WorkReleaseDate,
    WorkUpdateRequest,
    WorkWriteInput,
)


def _apply_work_input(work: Work, body: WorkWriteInput) -> None:
    """将写入入参映射到 orm"""

    work.name = body.name
    work.original_name = body.original_name
    work.aliases = list(body.aliases)
    work.summary = body.summary
    work.media_type = body.media_type.value
    work.release_status = body.release_status.value

    if body.release_date is None:
        work.release_year = None
        work.release_month = None
        work.release_day = None
    else:
        work.release_year = body.release_date.year
        work.release_month = body.release_date.month
        work.release_day = body.release_date.day


def _to_work_detail(work: Work) -> WorkDetail:
    """将 orm 转换成作品详情响应"""

    release_date: WorkReleaseDate | None = None
    if work.release_year is not None:
        release_date = WorkReleaseDate(
            year=work.release_year, month=work.release_month, day=work.release_day
        )
    return WorkDetail.of(work=work, release_date=release_date)


def _ensure_all_work_ids_exist(
    requested_ids: list[int], existing_ids: set[int]
) -> None:
    """按请求顺序核查所有目标 ID 均存在，存在缺失时抛出 404 异常并指明缺失 ID"""

    missing_ids = [work_id for work_id in requested_ids if work_id not in existing_ids]
    if missing_ids:
        formatted_ids = "、".join(str(i) for i in missing_ids)
        raise BusinessException(
            WorkApiCode.WORK_NOT_FOUND, f"作品不存在, ID: {formatted_ids}"
        )


class WorkService:
    def __init__(self, repository: WorkRepository | None = None) -> None:
        self.repository = repository or WorkRepository()

    async def list_page(
        self, session: AsyncSession, query: WorkListQuery
    ) -> Page[WorkListItem]:
        """分页查询作品"""

        rows, total = await self.repository.list_works(
            session,
            keyword=query.keyword,
            media_type=query.media_type,
            release_status=query.release_status,
            page=query.page,
            page_size=query.page_size,
        )
        items = [
            WorkListItem(
                id=row["id"],
                name=row["name"],
                original_name=row["original_name"],
                media_type=MediaType(row["media_type"]),
                release_status=ReleaseStatus(row["release_status"]),
                updated_at=row["updated_at"],
            )
            for row in rows
        ]
        return Page(
            items=items, total=total, page=query.page, page_size=query.page_size
        )

    async def get_detail(self, session: AsyncSession, work_id: int) -> WorkDetail:
        """获取作品详情"""

        work = await self.repository.get_by_id(session, work_id)
        if work is None:
            raise BusinessException(WorkApiCode.WORK_NOT_FOUND)
        return _to_work_detail(work)

    async def create(
        self, session: AsyncSession, body: WorkCreateRequest
    ) -> WorkDetail:
        """创建作品"""

        work = Work()
        _apply_work_input(work, body)
        saved_work = await self.repository.save(session, work)
        return _to_work_detail(saved_work)

    async def update(
        self, session: AsyncSession, work_id: int, body: WorkUpdateRequest
    ) -> WorkDetail:
        """更新作品"""

        work = await self.repository.get_by_id(session, work_id)
        if work is None:
            raise BusinessException(WorkApiCode.WORK_NOT_FOUND)

        _apply_work_input(work, body)
        saved_work = await self.repository.save(session, work)
        return _to_work_detail(saved_work)

    async def delete(self, session: AsyncSession, work_ids: list[int]) -> int:
        """删除作品"""

        deleted_ids = await self.repository.delete_by_ids(session, work_ids)
        _ensure_all_work_ids_exist(work_ids, set(deleted_ids))
        return len(deleted_ids)
