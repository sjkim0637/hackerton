"""Initial Geo-Time AR schema.

Revision ID: 0001
Revises:
"""

from collections.abc import Sequence

import geoalchemy2
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def timestamps() -> list[sa.Column]:
    return [
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
    ]


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis")

    op.create_table(
        "users",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("display_name", sa.String(length=120), nullable=False),
        sa.Column("email", sa.String(length=320), nullable=True),
        *timestamps(),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("email"),
    )
    op.create_table(
        "geo_zones",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column(
            "center_point",
            geoalchemy2.types.Geography(geometry_type="POINT", srid=4326, spatial_index=False),
            nullable=False,
        ),
        sa.Column("radius_m", sa.Float(), nullable=False),
        sa.Column(
            "geometry",
            geoalchemy2.types.Geometry(
                geometry_type="MULTIPOLYGON", srid=4326, spatial_index=False
            ),
            nullable=True,
        ),
        *timestamps(),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_geo_zones_center_point_gist",
        "geo_zones",
        ["center_point"],
        unique=False,
        postgresql_using="gist",
    )
    op.create_table(
        "pois",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("geo_zone_id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("poi_type", sa.String(length=50), nullable=False),
        sa.Column(
            "location",
            geoalchemy2.types.Geography(geometry_type="POINT", srid=4326, spatial_index=False),
            nullable=False,
        ),
        sa.Column(
            "metadata", postgresql.JSONB(astext_type=sa.Text()), nullable=False, server_default="{}"
        ),
        *timestamps(),
        sa.ForeignKeyConstraint(["geo_zone_id"], ["geo_zones.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_pois_geo_zone_id", "pois", ["geo_zone_id"])
    op.create_index("ix_pois_location_gist", "pois", ["location"], postgresql_using="gist")
    op.create_table(
        "contents",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("content_type", sa.String(length=30), nullable=False),
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("object_key", sa.String(length=500), nullable=False),
        sa.Column("public_url", sa.String(length=1000), nullable=True),
        sa.Column("mime_type", sa.String(length=120), nullable=True),
        sa.Column(
            "metadata", postgresql.JSONB(astext_type=sa.Text()), nullable=False, server_default="{}"
        ),
        *timestamps(),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_table(
        "spatial_placements",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("geo_zone_id", sa.Uuid(), nullable=False),
        sa.Column("poi_id", sa.Uuid(), nullable=True),
        sa.Column("anchor_type", sa.String(length=30), nullable=False),
        sa.Column("local_x", sa.Float(), nullable=False),
        sa.Column("local_y", sa.Float(), nullable=False),
        sa.Column("local_z", sa.Float(), nullable=False),
        sa.Column("qx", sa.Float(), nullable=False),
        sa.Column("qy", sa.Float(), nullable=False),
        sa.Column("qz", sa.Float(), nullable=False),
        sa.Column("qw", sa.Float(), nullable=False),
        sa.Column("scale", sa.Float(), nullable=False),
        sa.Column("min_visible_distance_m", sa.Float(), nullable=False),
        sa.Column("max_visible_distance_m", sa.Float(), nullable=False),
        sa.Column("view_cone_degrees", sa.Float(), nullable=False),
        *timestamps(),
        sa.ForeignKeyConstraint(["geo_zone_id"], ["geo_zones.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["poi_id"], ["pois.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_spatial_placements_geo_zone_id", "spatial_placements", ["geo_zone_id"])
    op.create_index("ix_spatial_placements_poi_id", "spatial_placements", ["poi_id"])
    op.create_table(
        "moments",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("geo_zone_id", sa.Uuid(), nullable=False),
        sa.Column("poi_id", sa.Uuid(), nullable=True),
        sa.Column("content_id", sa.Uuid(), nullable=False),
        sa.Column("placement_id", sa.Uuid(), nullable=False),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_by", sa.Uuid(), nullable=False),
        sa.Column(
            "rendering_metadata",
            postgresql.JSONB(astext_type=sa.Text()),
            nullable=False,
            server_default="{}",
        ),
        *timestamps(),
        sa.ForeignKeyConstraint(["content_id"], ["contents.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["created_by"], ["users.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["geo_zone_id"], ["geo_zones.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["placement_id"], ["spatial_placements.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["poi_id"], ["pois.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_moments_geo_zone_id", "moments", ["geo_zone_id"])
    op.create_index("ix_moments_poi_id", "moments", ["poi_id"])
    op.create_index("ix_moments_recorded_at", "moments", ["recorded_at"])
    op.create_index("ix_moments_zone_recorded", "moments", ["geo_zone_id", "recorded_at"])
    op.create_table(
        "campaigns",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("brand", sa.String(length=200), nullable=False),
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("content_id", sa.Uuid(), nullable=False),
        sa.Column("geo_zone_id", sa.Uuid(), nullable=False),
        sa.Column("placement_id", sa.Uuid(), nullable=False),
        *timestamps(),
        sa.ForeignKeyConstraint(["content_id"], ["contents.id"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["geo_zone_id"], ["geo_zones.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["placement_id"], ["spatial_placements.id"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_campaigns_geo_zone_id", "campaigns", ["geo_zone_id"])
    op.create_table(
        "campaign_schedules",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("campaign_id", sa.Uuid(), nullable=False),
        sa.Column("geo_zone_id", sa.Uuid(), nullable=False),
        sa.Column("start_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("end_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("priority", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(length=30), nullable=False),
        *timestamps(),
        sa.ForeignKeyConstraint(["campaign_id"], ["campaigns.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["geo_zone_id"], ["geo_zones.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.CheckConstraint("end_at > start_at", name="ck_campaign_schedule_window"),
    )
    op.create_index("ix_campaign_schedules_campaign_id", "campaign_schedules", ["campaign_id"])
    op.create_index("ix_campaign_schedules_geo_zone_id", "campaign_schedules", ["geo_zone_id"])
    op.create_index(
        "ix_campaign_schedules_window",
        "campaign_schedules",
        ["geo_zone_id", "status", "start_at", "end_at"],
    )
    op.create_table(
        "spatial_impressions",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("campaign_id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=True),
        sa.Column("geo_zone_id", sa.Uuid(), nullable=False),
        sa.Column("displayed_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("duration_ms", sa.Integer(), nullable=False),
        sa.Column("interaction_type", sa.String(length=50), nullable=True),
        sa.Column(
            "metadata", postgresql.JSONB(astext_type=sa.Text()), nullable=False, server_default="{}"
        ),
        *timestamps(),
        sa.ForeignKeyConstraint(["campaign_id"], ["campaigns.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["geo_zone_id"], ["geo_zones.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_spatial_impressions_campaign_id", "spatial_impressions", ["campaign_id"])
    op.create_index("ix_spatial_impressions_geo_zone_id", "spatial_impressions", ["geo_zone_id"])
    op.create_index("ix_spatial_impressions_user_id", "spatial_impressions", ["user_id"])


def downgrade() -> None:
    op.drop_table("spatial_impressions")
    op.drop_table("campaign_schedules")
    op.drop_table("campaigns")
    op.drop_table("moments")
    op.drop_table("spatial_placements")
    op.drop_table("contents")
    op.drop_table("pois")
    op.drop_table("geo_zones")
    op.drop_table("users")
