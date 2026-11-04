from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query

from tsuki.api.dependencies import DbSession
from tsuki.api.pagination import Page
from tsuki.api.responses import ApiResponse
from tsuki.modules.auth.dependencies import get_current_admin, require_csrf
from tsuki.modules.work.schemas import (
    WorkBatchIdsRequest,
    WorkBatchStatusUpdateRequest,
    WorkCreateRequest,
    WorkDetail,
    WorkListItem,
    WorkListQuery,
    WorkUpdateRequest,
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


@admin_router.put("/{work_id}")
async def update_work(
    work_id: Annotated[int, Path(ge=1, le=2**63 - 1, description="作品ID")],
    body: WorkUpdateRequest,
    session: DbSession,
) -> ApiResponse[WorkDetail]:
    """更新作品"""

    detail = await work_service.update(session, work_id, body)
    return ApiResponse.success(detail)


@admin_router.post("/batch-delete")
async def batch_delete_works(
    body: WorkBatchIdsRequest, session: DbSession
) -> ApiResponse[int]:
    """批量删除作品"""

    result_count = await work_service.delete(session, body.work_ids)
    return ApiResponse.success(result_count)


@admin_router.delete("/{work_id}")
async def delete_work(
    work_id: Annotated[int, Path(ge=1, le=2**63 - 1, description="作品ID")],
    session: DbSession,
) -> ApiResponse[None]:
    """删除作品"""

    await work_service.delete(session, [work_id])
    return ApiResponse.success()


@admin_router.post("/batch-update-status")
async def batch_update_work_status(
    body: WorkBatchStatusUpdateRequest, session: DbSession
) -> ApiResponse[int]:
    """批量更新作品状态"""

    result_count = await work_service.update_status(
        session, body.work_ids, body.release_status
    )
    return ApiResponse.success(result_count)
