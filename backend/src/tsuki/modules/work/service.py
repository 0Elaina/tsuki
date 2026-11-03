from sqlalchemy.ext.asyncio import AsyncSession

from tsuki.api.pagination import Page
from tsuki.core.exceptions import BusinessException
from tsuki.modules.work.api_code import WorkApiCode
from tsuki.modules.work.enums import MediaType, ReleaseStatus
from tsuki.modules.work.repository import WorkRepository
from tsuki.modules.work.schemas import (
    WorkDetail,
    WorkListItem,
    WorkListQuery,
    WorkReleaseDate,
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
        release_date: WorkReleaseDate | None = None
        if work.release_year is not None:
            release_date = WorkReleaseDate(
                year=work.release_year, month=work.release_month, day=work.release_day
            )
        return WorkDetail.of(work=work, release_date=release_date)
