import uuid
from datetime import datetime
from typing import Any

from geoalchemy2 import Geography, Geometry
from sqlalchemy import DateTime, Float, ForeignKey, Index, Integer, String, Text, Uuid, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class TimestampMixin:
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class User(TimestampMixin, Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str | None] = mapped_column(String(320), unique=True)


class GeoZone(TimestampMixin, Base):
    __tablename__ = "geo_zones"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    center_point: Mapped[Any] = mapped_column(
        Geography(geometry_type="POINT", srid=4326), nullable=False
    )
    radius_m: Mapped[float] = mapped_column(Float, nullable=False)
    geometry: Mapped[Any | None] = mapped_column(
        Geometry(geometry_type="MULTIPOLYGON", srid=4326), nullable=True
    )

    pois: Mapped[list["POI"]] = relationship(back_populates="geo_zone")

    __table_args__ = (
        Index("ix_geo_zones_center_point_gist", "center_point", postgresql_using="gist"),
    )


class POI(TimestampMixin, Base):
    __tablename__ = "pois"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    geo_zone_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("geo_zones.id", ondelete="CASCADE"), nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    poi_type: Mapped[str] = mapped_column(String(50), nullable=False, default="generic")
    location: Mapped[Any] = mapped_column(
        Geography(geometry_type="POINT", srid=4326), nullable=False
    )
    ellipsoid_height_m: Mapped[float | None] = mapped_column(Float)
    orthometric_height_m: Mapped[float | None] = mapped_column(Float)
    metadata_: Mapped[dict[str, Any]] = mapped_column(
        "metadata", JSONB, default=dict, nullable=False
    )

    geo_zone: Mapped[GeoZone] = relationship(back_populates="pois")

    __table_args__ = (Index("ix_pois_location_gist", "location", postgresql_using="gist"),)


class SurveyControlPoint(TimestampMixin, Base):
    __tablename__ = "survey_control_points"

    id: Mapped[str] = mapped_column(String(80), primary_key=True)
    point_type: Mapped[str] = mapped_column(String(40), nullable=False)
    location: Mapped[Any] = mapped_column(
        Geography(geometry_type="POINT", srid=4326), nullable=False
    )
    ellipsoid_height_m: Mapped[float | None] = mapped_column(Float)
    orthometric_height_m: Mapped[float | None] = mapped_column(Float)
    geoid_height_m: Mapped[float | None] = mapped_column(Float)
    status: Mapped[str] = mapped_column(String(30), nullable=False, default="available")
    source_document: Mapped[str | None] = mapped_column(String(300))

    __table_args__ = (
        Index("ix_survey_control_points_location_gist", "location", postgresql_using="gist"),
    )


class Content(TimestampMixin, Base):
    __tablename__ = "contents"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    content_type: Mapped[str] = mapped_column(String(30), nullable=False)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    object_key: Mapped[str] = mapped_column(String(500), nullable=False)
    public_url: Mapped[str | None] = mapped_column(String(1000))
    mime_type: Mapped[str | None] = mapped_column(String(120))
    metadata_: Mapped[dict[str, Any]] = mapped_column(
        "metadata", JSONB, default=dict, nullable=False
    )


class SpatialPlacement(TimestampMixin, Base):
    """Stable placement in a Zone-local AR frame: +X east, +Y up, -Z north."""

    __tablename__ = "spatial_placements"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    geo_zone_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("geo_zones.id", ondelete="CASCADE"), nullable=False, index=True
    )
    poi_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("pois.id", ondelete="SET NULL"), index=True
    )
    anchor_type: Mapped[str] = mapped_column(String(30), default="zone_local", nullable=False)
    local_x: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    local_y: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    local_z: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    qx: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    qy: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    qz: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    qw: Mapped[float] = mapped_column(Float, nullable=False, default=1.0)
    scale: Mapped[float] = mapped_column(Float, nullable=False, default=1.0)
    min_visible_distance_m: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    max_visible_distance_m: Mapped[float] = mapped_column(Float, nullable=False, default=30.0)
    view_cone_degrees: Mapped[float] = mapped_column(Float, nullable=False, default=70.0)


class Moment(TimestampMixin, Base):
    __tablename__ = "moments"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    geo_zone_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("geo_zones.id", ondelete="CASCADE"), nullable=False, index=True
    )
    poi_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("pois.id", ondelete="SET NULL"), index=True
    )
    content_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("contents.id", ondelete="RESTRICT"), nullable=False
    )
    placement_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("spatial_placements.id", ondelete="RESTRICT"), nullable=False
    )
    recorded_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
    created_by: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    rendering_metadata: Mapped[dict[str, Any]] = mapped_column(JSONB, default=dict, nullable=False)

    content: Mapped[Content] = relationship()
    placement: Mapped[SpatialPlacement] = relationship()

    __table_args__ = (Index("ix_moments_zone_recorded", "geo_zone_id", "recorded_at"),)


class Campaign(TimestampMixin, Base):
    __tablename__ = "campaigns"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    brand: Mapped[str] = mapped_column(String(200), nullable=False)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    content_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("contents.id", ondelete="RESTRICT"), nullable=False
    )
    geo_zone_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("geo_zones.id", ondelete="CASCADE"), nullable=False, index=True
    )
    placement_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("spatial_placements.id", ondelete="RESTRICT"), nullable=False
    )

    content: Mapped[Content] = relationship()
    placement: Mapped[SpatialPlacement] = relationship()
    schedules: Mapped[list["CampaignSchedule"]] = relationship(back_populates="campaign")


class CampaignSchedule(TimestampMixin, Base):
    __tablename__ = "campaign_schedules"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    campaign_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("campaigns.id", ondelete="CASCADE"), nullable=False, index=True
    )
    geo_zone_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("geo_zones.id", ondelete="CASCADE"), nullable=False, index=True
    )
    start_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    end_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    priority: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    status: Mapped[str] = mapped_column(String(30), nullable=False, default="active")

    campaign: Mapped[Campaign] = relationship(back_populates="schedules")

    __table_args__ = (
        Index("ix_campaign_schedules_window", "geo_zone_id", "status", "start_at", "end_at"),
    )


class SpatialImpression(TimestampMixin, Base):
    __tablename__ = "spatial_impressions"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    campaign_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("campaigns.id", ondelete="CASCADE"), nullable=False, index=True
    )
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), index=True
    )
    geo_zone_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("geo_zones.id", ondelete="CASCADE"), nullable=False, index=True
    )
    displayed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    duration_ms: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    interaction_type: Mapped[str | None] = mapped_column(String(50))
    metadata_: Mapped[dict[str, Any]] = mapped_column(
        "metadata", JSONB, default=dict, nullable=False
    )
