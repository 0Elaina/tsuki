from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query

from tsuki.api.dependencies import DbSession
from tsuki.api.pagination import Page
from tsuki.api.responses import ApiResponse
from tsuki.modules.auth.dependencies import get_current_admin, require_csrf
from tsuki.modules.work.schemas import (
    WorkCreateRequest,
    WorkDetail,
    WorkListItem,
    WorkListQuery,
)
from tsuki.modules.work.service import WorkService

admin_router = APIRouter(
    prefix="/api/v1/admin/works",
    tags=["作品管理"],
    dependencies=[Depends(require_csrf), Depends(get_current_admin)],
)

work_service = WorkService()


@admin_router.get("")
async def list_works(
    query: Annotated[WorkListQuery, Query()], session: DbSession
) -> ApiResponse[Page[WorkListItem]]:
    """获取作品列表"""

    page = await work_service.list_page(session, query)
    return ApiResponse.success(page)


@admin_router.get("/{work_id}")
async def get_work(
    work_id: Annotated[int, Path(ge=1, le=2**63 - 1, description="作品ID")],
    session: DbSession,
) -> ApiResponse[WorkDetail]:
    """获取作品详情"""

    detail = await work_service.get_detail(session, work_id)
    return ApiResponse.success(detail)


@admin_router.post("")
async def create_work(
    body: WorkCreateRequest, session: DbSession
) -> ApiResponse[WorkDetail]:
    """创建作品"""

    detail = await work_service.create(session, body)
    return ApiResponse.success(detail)
