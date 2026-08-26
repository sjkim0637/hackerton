"""Add POI elevation references and public survey control points.

Revision ID: 0002
Revises: 0001
"""

from collections.abc import Sequence

import geoalchemy2
import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("pois", sa.Column("ellipsoid_height_m", sa.Float(), nullable=True))
    op.add_column("pois", sa.Column("orthometric_height_m", sa.Float(), nullable=True))
    op.create_table(
        "survey_control_points",
        sa.Column("id", sa.String(length=80), nullable=False),
        sa.Column("point_type", sa.String(length=40), nullable=False),
        sa.Column(
            "location",
            geoalchemy2.types.Geography(
                geometry_type="POINT", srid=4326, spatial_index=False
            ),
            nullable=False,
        ),
        sa.Column("ellipsoid_height_m", sa.Float(), nullable=True),
        sa.Column("orthometric_height_m", sa.Float(), nullable=True),
        sa.Column("geoid_height_m", sa.Float(), nullable=True),
        sa.Column("status", sa.String(length=30), nullable=False),
        sa.Column("source_document", sa.String(length=300), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_survey_control_points_location_gist",
        "survey_control_points",
        ["location"],
        postgresql_using="gist",
    )


def downgrade() -> None:
    op.drop_table("survey_control_points")
    op.drop_column("pois", "orthometric_height_m")
    op.drop_column("pois", "ellipsoid_height_m")
