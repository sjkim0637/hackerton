import uuid
from datetime import datetime, timedelta

from geoalchemy2 import Geography
from sqlalchemy import cast, func, select
from sqlalchemy.orm import Session, joinedload

from app import models
from app.schemas import (
    CampaignRead,
    ContentCandidate,
    ContentRead,
    GeoZoneNearbyRead,
    MomentCreate,
    PlacementRead,
)


class PostgresRepository:
    def __init__(self, db: Session):
        self.db = db

    def nearby_geozones(
        self, latitude: float, longitude: float, radius_m: float, limit: int
    ) -> list[GeoZoneNearbyRead]:
        point = cast(func.ST_SetSRID(func.ST_MakePoint(longitude, latitude), 4326), Geography)
        distance = func.ST_Distance(models.GeoZone.center_point, point)
        statement = (
            select(models.GeoZone, distance.label("distance_m"))
            .where(func.ST_DWithin(models.GeoZone.center_point, point, radius_m))
            .order_by(distance, models.GeoZone.id)
            .limit(limit)
        )
        return [
            GeoZoneNearbyRead(
                id=zone.id,
                name=zone.name,
                description=zone.description,
                radius_m=zone.radius_m,
                distance_m=round(float(distance_m), 2),
            )
            for zone, distance_m in self.db.execute(statement).all()
        ]

    def timeline(
        self,
        geo_zone_id: uuid.UUID,
        from_at: datetime | None,
        to_at: datetime | None,
        limit: int,
    ) -> list[models.Moment]:
        statement = (
            select(models.Moment)
            .options(joinedload(models.Moment.content), joinedload(models.Moment.placement))
            .where(models.Moment.geo_zone_id == geo_zone_id)
        )
        if from_at is not None:
            statement = statement.where(models.Moment.recorded_at >= from_at)
        if to_at is not None:
            statement = statement.where(models.Moment.recorded_at <= to_at)
        statement = statement.order_by(models.Moment.recorded_at.desc(), models.Moment.id).limit(
            limit
        )
        return list(self.db.scalars(statement).unique())

    def get_moment(self, moment_id: uuid.UUID) -> models.Moment | None:
        statement = (
            select(models.Moment)
            .options(joinedload(models.Moment.content), joinedload(models.Moment.placement))
            .where(models.Moment.id == moment_id)
        )
        return self.db.scalar(statement)

    def create_moment(self, payload: MomentCreate) -> models.Moment:
        moment = models.Moment(**payload.model_dump())
        self.db.add(moment)
        self.db.commit()
        return self.get_moment(moment.id)  # type: ignore[return-value]

    def active_campaigns(
        self, geo_zone_id: uuid.UUID, at: datetime, limit: int
    ) -> list[CampaignRead]:
        statement = (
            select(models.Campaign, models.CampaignSchedule)
            .join(
                models.CampaignSchedule,
                models.CampaignSchedule.campaign_id == models.Campaign.id,
            )
            .options(joinedload(models.Campaign.content), joinedload(models.Campaign.placement))
            .where(
                models.Campaign.geo_zone_id == geo_zone_id,
                models.CampaignSchedule.geo_zone_id == geo_zone_id,
                models.CampaignSchedule.status == "active",
                models.CampaignSchedule.start_at <= at,
                models.CampaignSchedule.end_at > at,
            )
            .order_by(models.CampaignSchedule.priority.desc(), models.Campaign.id)
            .limit(limit)
        )
        return [
            CampaignRead(
                id=campaign.id,
                brand=campaign.brand,
                title=campaign.title,
                content=ContentRead.model_validate(campaign.content),
                placement=PlacementRead.model_validate(campaign.placement),
                priority=schedule.priority,
                start_at=schedule.start_at,
                end_at=schedule.end_at,
            )
            for campaign, schedule in self.db.execute(statement).unique().all()
        ]

    def content_candidates(
        self,
        geo_zone_id: uuid.UUID,
        at: datetime,
        moment_window_minutes: int,
        limit: int,
    ) -> list[ContentCandidate]:
        delta = timedelta(minutes=moment_window_minutes)
        moments = self.timeline(geo_zone_id, at - delta, at + delta, limit)
        campaigns = self.active_campaigns(geo_zone_id, at, limit)
        candidates = [
            ContentCandidate(
                source_type="moment",
                source_id=moment.id,
                content=ContentRead.model_validate(moment.content),
                placement=PlacementRead.model_validate(moment.placement),
                occurred_at=moment.recorded_at,
            )
            for moment in moments
        ]
        candidates.extend(
            ContentCandidate(
                source_type="campaign",
                source_id=campaign.id,
                content=campaign.content,
                placement=campaign.placement,
                priority=campaign.priority,
            )
            for campaign in campaigns
        )
        return candidates[:limit]
