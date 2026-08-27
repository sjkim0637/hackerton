import { useState } from "react";
import SiteMap from "./SiteMap";
import SpotTimeline from "./SpotTimeline";
import { CATEGORIES } from "../utils/category";

interface MapPageProps {
  onCreateAt: (spotId: string) => void;
  onOpenAR: () => void;
}

export default function MapPage({ onCreateAt, onOpenAR }: MapPageProps) {
  const [openSpotId, setOpenSpotId] = useState<string | null>(null);

  return (
    <div className="map-page">
      <div className="map-page__intro">
        <div className="map-page__intro-text">
          <h1>단지 배치도</h1>
          <p>핀을 눌러 그 장소에 쌓인 이웃들의 순간을 시간순으로 확인해보세요.</p>
        </div>
        <button className="btn-ar" onClick={onOpenAR}>
          🥽 AR로 보기
        </button>
      </div>

      <SiteMap mode="view" onSpotClick={setOpenSpotId} />

      <div className="map-legend">
        {CATEGORIES.map((c) => (
          <span key={c.id} className="map-legend__item">
            <span className="map-legend__dot" style={{ backgroundColor: c.color }} />
            {c.emoji} {c.label}
          </span>
        ))}
      </div>

      {openSpotId && (
        <SpotTimeline
          spotId={openSpotId}
          onClose={() => setOpenSpotId(null)}
          onCreateHere={(spotId) => {
            setOpenSpotId(null);
            onCreateAt(spotId);
          }}
        />
      )}
    </div>
  );
}
