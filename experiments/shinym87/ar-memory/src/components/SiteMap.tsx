import { SPOTS } from "../data/spots";
import { getCategoryMeta } from "../utils/category";
import { useApp } from "../state/AppContext";
import type { Post } from "../types";

interface SiteMapProps {
  /** view: 핀 클릭 시 타임라인 열기 / pick: 게시물 등록 시 위치 선택 */
  mode?: "view" | "pick";
  selectedSpotId?: string | null;
  onSpotClick: (spotId: string) => void;
}

function latestCategoryOf(posts: Post[]) {
  if (posts.length === 0) return null;
  const sorted = [...posts].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );
  return sorted[0].category;
}

export default function SiteMap({ mode = "view", selectedSpotId, onSpotClick }: SiteMapProps) {
  const { getSpotPosts } = useApp();

  return (
    <div className="site-map">
      <svg
        viewBox="0 0 100 100"
        className="site-map__svg"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-label="아파트 단지 배치도"
      >
        {/* 배경 잔디 */}
        <rect x="0" y="0" width="100" height="100" fill="var(--map-ground)" />

        {/* 동 건물들 (장식용) */}
        <g className="site-map__buildings">
          <rect x="6" y="6" width="14" height="22" rx="1.5" />
          <rect x="6" y="46" width="14" height="22" rx="1.5" />
          <rect x="82" y="6" width="12" height="22" rx="1.5" />
          <rect x="40" y="4" width="16" height="14" rx="1.5" />
          <rect x="4" y="82" width="16" height="12" rx="1.5" />
        </g>
        <g className="site-map__building-labels">
          <text x="13" y="18">101동</text>
          <text x="13" y="58">102동</text>
          <text x="88" y="18">103동</text>
          <text x="48" y="12">104동</text>
        </g>

        {/* 산책로 (장식용 곡선) */}
        <path
          className="site-map__path"
          d="M 15 34 C 30 20, 55 20, 70 24 C 85 28, 88 50, 78 68 C 70 82, 55 86, 50 82 C 40 74, 25 78, 20 72 C 10 66, 12 46, 15 34 Z"
        />

        {/* 지점 핀 */}
        {SPOTS.map((spot) => {
          const { neighborPosts, morePosts } = getSpotPosts(spot.id);
          const visiblePosts = [...neighborPosts, ...morePosts];
          const count = visiblePosts.length;
          const category = latestCategoryOf(visiblePosts);
          const meta = category ? getCategoryMeta(category) : null;
          const isSelected = selectedSpotId === spot.id;

          return (
            <g
              key={spot.id}
              transform={`translate(${spot.x}, ${spot.y})`}
              className={`site-map__pin${isSelected ? " site-map__pin--selected" : ""}${
                mode === "pick" ? " site-map__pin--pickable" : ""
              }`}
              onClick={() => onSpotClick(spot.id)}
              tabIndex={0}
              role="button"
              aria-label={`${spot.name}${count > 0 ? ` (게시물 ${count}개)` : " (게시물 없음)"}`}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") onSpotClick(spot.id);
              }}
            >
              {isSelected && <circle r="6.5" className="site-map__pin-ring" />}
              <circle
                r="4.2"
                fill={meta ? meta.color : "var(--pin-empty)"}
                className="site-map__pin-dot"
              />
              <text y="1.5" textAnchor="middle" fontSize="4">
                {meta ? meta.emoji : "📍"}
              </text>
              {count > 1 && (
                <g transform="translate(3.6, -3.6)">
                  <circle r="2.6" className="site-map__pin-badge" />
                  <text y="0.9" textAnchor="middle" fontSize="2.8" className="site-map__pin-badge-text">
                    {count}
                  </text>
                </g>
              )}
              <text y="8.5" textAnchor="middle" fontSize="2.6" className="site-map__pin-label">
                {spot.name}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
