import { useState } from "react";
import playgroundPhoto from "../../photo/playground.jpg";
import { AR_MARKERS, type ARMarker } from "../data/arMarkers";
import { getSpot } from "../data/spots";
import { getCategoryMeta } from "../utils/category";
import ARMarkerPopup from "./ARMarkerPopup";

// 현재는 놀이터 배경 사진만 준비되어 있어 AR 체험 뷰는 이 지점 고정으로 동작한다.
const AR_SPOT_ID = "playground";

interface ARViewProps {
  onClose: () => void;
}

export default function ARView({ onClose }: ARViewProps) {
  const spot = getSpot(AR_SPOT_ID);
  const [activeMarker, setActiveMarker] = useState<ARMarker | null>(null);

  if (!spot) return null;

  return (
    <div className="ar-view">
      <header className="ar-view__topbar">
        <button className="ar-view__back" onClick={onClose}>
          ← 지도로 돌아가기
        </button>
        <span className="ar-view__badge">🥽 AR 체험 뷰 (프로토타입)</span>
      </header>

      <p className="ar-view__spot-tag">📍 {spot.name} — 핀을 눌러 그 자리의 기록을 확인해보세요</p>

      <div className="ar-view__stage">
        {/* 배경 사진과 같은 비율(16:9)의 프레임 안에서만 핀 좌표(%)가 정확히 맞는다 */}
        <div className="ar-view__frame">
          <img className="ar-view__photo" src={playgroundPhoto} alt={spot.name} />
          <div className="ar-view__scrim" />

          {AR_MARKERS.map((marker) => {
            const meta = getCategoryMeta(marker.category);
            return (
              <button
                key={marker.id}
                type="button"
                className="ar-marker"
                style={{ left: `${marker.x}%`, top: `${marker.y}%` }}
                onClick={() => setActiveMarker(marker)}
                aria-label={marker.label}
              >
                <span className="ar-marker__ring" style={{ borderColor: meta.color }} />
                <span className="ar-marker__dot" style={{ backgroundColor: meta.color }}>
                  {meta.emoji}
                </span>
                <span className="ar-marker__label">{marker.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {activeMarker && (
        <ARMarkerPopup marker={activeMarker} onClose={() => setActiveMarker(null)} />
      )}
    </div>
  );
}
