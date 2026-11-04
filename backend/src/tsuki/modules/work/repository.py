from sqlalchemy import ColumnElement, RowMapping, func, literal_column, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from tsuki.api.pagination import DEFAULT_PAGE, DEFAULT_PAGE_SIZE
from tsuki.modules.work.enums import MediaType, ReleaseStatus
from tsuki.modules.work.models import Work


class WorkRepository:
    async def list_works(
        self,
        session: AsyncSession,
        *,
        keyword: str | None = None,
        media_type: MediaType | None = None,
        release_status: ReleaseStatus | None = None,
        page: int = DEFAULT_PAGE,
        page_size: int = DEFAULT_PAGE_SIZE,
    ) -> tuple[list[RowMapping], int]:
        """分页列表查询作品"""
        conditions: list[ColumnElement[bool]] = []

        # 关键词过滤
        if keyword is not None:
            alias_elem = (
                func.unnest(Work.aliases)
                .table_valued("value")
                .render_derived("work_aliases")
            )
            alias_exist = (
                (
                    select(literal_column("1"))
                    .select_from(alias_elem)
                    .where(alias_elem.c.value.icontains(keyword, autoescape=True))
                )
                .correlate(Work)
                .exists()
            )
            conditions.append(
                or_(
                    Work.name.icontains(keyword, autoescape=True),
                    Work.original_name.icontains(keyword, autoescape=True),
                    alias_exist,
                )
            )

        # 媒介类型与状态过滤
        if media_type is not None:
            conditions.append(Work.media_type == media_type.value)
        if release_status is not None:
            conditions.append(Work.release_status == release_status.value)

        # 分页偏移量
        offset = (page - 1) * page_size

        # 总数统计查询
        count_stmt = select(func.count()).select_from(Work).where(*conditions)
        total = (await session.execute(count_stmt)).scalar_one()

        # 列表数据查询
        list_stmt = (
            select(
                Work.id,
                Work.name,
                Work.original_name,
                Work.media_type,
                Work.release_status,
                Work.updated_at,
            )
            .where(*conditions)
            .order_by(Work.id.desc())
            .offset(offset)
            .limit(page_size)
        )
        result = await session.execute(list_stmt)
        rows = list(result.mappings().all())
        return (rows, total)

    async def get_by_id(self, session: AsyncSession, work_id: int) -> Work | None:
        """根据 id 查询作品"""
        result = await session.execute(select(Work).where(Work.id == work_id))
        return result.scalar_one_or_none()

    async def save(self, session: AsyncSession, work: Work) -> Work:
        """保存作品"""

        session.add(work)
        await session.flush()
        return work
