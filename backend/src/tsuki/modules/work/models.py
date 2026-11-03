from datetime import datetime

from sqlalchemy import (
    ARRAY,
    BigInteger,
    CheckConstraint,
    DateTime,
    Identity,
    SmallInteger,
    String,
    Text,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from tsuki.core.time_util import utc_now
from tsuki.db.base import Base
from tsuki.modules.work.enums import ReleaseStatus


class Work(Base):
    __tablename__ = "works"
    __table_args__ = (
        CheckConstraint("char_length(name) > 0", name="name_not_empty"),
        CheckConstraint(
            "media_type IN ('anime', 'manga', 'novel', 'game')",
            name="media_type",
        ),
        CheckConstraint(
            "release_status IN ('unknown', 'not_released', 'releasing', 'finished', 'hiatus', 'canceled')",
            name="release_status",
        ),
        CheckConstraint(
            "release_year IS NULL OR (release_year >= 1 AND release_year <= 9999)",
            name="release_year_range",
        ),
        CheckConstraint(
            "release_month IS NULL OR (release_month >= 1 AND release_month <= 12)",
            name="release_month_range",
        ),
        CheckConstraint(
            "release_day IS NULL OR (release_day >= 1 AND release_day <= 31)",
            name="release_day_range",
        ),
        CheckConstraint(
            "(release_year IS NULL AND release_month IS NULL AND release_day IS NULL) "
            "OR (release_year IS NOT NULL AND (release_day IS NULL OR release_month IS NOT NULL))",
            name="release_date_shape",
        ),
    )

    id: Mapped[int] = mapped_column(BigInteger, Identity(always=True), primary_key=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    original_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    aliases: Mapped[list[str]] = mapped_column(
        ARRAY(Text()), nullable=False, default=list, server_default=text("'{}'::text[]")
    )
    summary: Mapped[str | None] = mapped_column(Text, nullable=True)
    media_type: Mapped[str] = mapped_column(String(32), nullable=False)
    release_status: Mapped[str] = mapped_column(
        String(32),
        nullable=False,
        default=ReleaseStatus.UNKNOWN,
        server_default=text("'unknown'"),
    )
    release_year: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    release_month: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    release_day: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utc_now
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utc_now, onupdate=utc_now
    )
