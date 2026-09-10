"""
routers/reports.py — Google Play AI Policy Compliance Content Reporting API
POST /api/v1/report
GET  /api/v1/admin/reports
"""

import logging
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from database.postgres import get_db
from database.models import ContentReport, User
from routers.auth import get_optional_user

log = logging.getLogger(__name__)
router = APIRouter()


class ReportRequest(BaseModel):
    content_type: str = Field(..., description="tryon_result, catalog_item, inspiration_look")
    content_id: Optional[str] = Field(None, description="ID of item or session")
    image_url: Optional[str] = Field(None, description="URL of image being flagged")
    reason: str = Field(..., description="nudity_sexual, sexually_suggestive, inappropriate_ai, offensive, other")
    details: Optional[str] = Field(None, description="Additional context from user")


@router.post("/report")
@router.post("/report/", include_in_schema=False)
async def submit_content_report(
    payload: ReportRequest,
    request: Request,
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    """
    In-App User Reporting Endpoint (Mandatory Google Play AI Policy Requirement)
    Allows users to flag inappropriate AI generated images, catalog items, or looks.
    """
    cf_connecting_ip = request.headers.get("CF-Connecting-IP")
    forwarded_for = request.headers.get("X-Forwarded-For")
    if cf_connecting_ip:
        real_ip = cf_connecting_ip.strip()
    elif forwarded_for:
        real_ip = forwarded_for.split(",")[0].strip()
    else:
        real_ip = request.headers.get("X-Real-IP", request.client.host if request.client else "unknown")

    report = ContentReport(
        user_id=current_user.id if current_user else None,
        reporter_ip=real_ip,
        content_type=payload.content_type,
        content_id=payload.content_id,
        image_url=payload.image_url,
        reason=payload.reason,
        details=payload.details,
        status="pending"
    )

    db.add(report)
    await db.commit()
    await db.refresh(report)

    log.warning(
        f"🚨 IN-APP CONTENT REPORT SUBMITTED: id={report.id} type={report.content_type} "
        f"reason={report.reason} user={report.user_id} IP={real_ip}"
    )

    return {
        "status": "success",
        "message": "Thank you for reporting. Our content safety system has received your report and flagged the item for review.",
        "report_id": report.id
    }


@router.get("/admin/reports")
async def list_content_reports(
    db: AsyncSession = Depends(get_db)
):
    """List pending content reports for admin review."""
    result = await db.execute(
        select(ContentReport).order_by(ContentReport.created_at.desc()).limit(100)
    )
    reports = result.scalars().all()
    return [
        {
            "id": r.id,
            "user_id": r.user_id,
            "reporter_ip": r.reporter_ip,
            "content_type": r.content_type,
            "content_id": r.content_id,
            "image_url": r.image_url,
            "reason": r.reason,
            "details": r.details,
            "status": r.status,
            "created_at": r.created_at.isoformat() if r.created_at else None,
        }
        for r in reports
    ]
