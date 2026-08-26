import { useState } from "react";
import { getSpot } from "../data/spots";
import { useApp } from "../state/AppContext";
import PostCard from "./PostCard";

interface SpotTimelineProps {
  spotId: string;
  onClose: () => void;
  onCreateHere: (spotId: string) => void;
}

export default function SpotTimeline({ spotId, onClose, onCreateHere }: SpotTimelineProps) {
  const { getSpotPosts } = useApp();
  const [expanded, setExpanded] = useState(false);

  const spot = getSpot(spotId);
  const { neighborPosts, morePosts } = getSpotPosts(spotId);

  if (!spot) return null;

  return (
    <div className="timeline-overlay" onClick={onClose}>
      <aside className="timeline-panel" onClick={(e) => e.stopPropagation()}>
        <header className="timeline-panel__header">
          <div>
            <h2>{spot.name}</h2>
            <p className="timeline-panel__desc">{spot.description}</p>
          </div>
          <button className="btn-close" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </header>

        <button className="btn-create-here" onClick={() => onCreateHere(spotId)}>
          📍 이 지점에 순간 남기기
        </button>

        <div className="timeline-panel__body">
          {neighborPosts.length === 0 && morePosts.length === 0 && (
            <p className="timeline-panel__empty">
              아직 이 지점에 남겨진 기록이 없어요. 첫 순간을 기록해보세요!
            </p>
          )}

          {neighborPosts.length > 0 && (
            <div className="timeline-track">
              <div className="timeline-track__hint">시간 흐름에 따른 기록 (나 · 이웃)</div>
              {neighborPosts.map((post, i) => (
                <div key={post.id} className="timeline-item">
                  <div className="timeline-item__rail">
                    <span className="timeline-item__dot" />
                    {i < neighborPosts.length - 1 && <span className="timeline-item__line" />}
                  </div>
                  <div className="timeline-item__content">
                    <PostCard post={post} />
                  </div>
                </div>
              ))}
            </div>
          )}

          {morePosts.length > 0 && !expanded && (
            <button className="btn-more" onClick={() => setExpanded(true)}>
              단지 전체 공개 게시물 더보기 ({morePosts.length})
            </button>
          )}

          {expanded && morePosts.length > 0 && (
            <div className="timeline-track timeline-track--more">
              <div className="timeline-track__hint">단지 전체 공개</div>
              {morePosts.map((post) => (
                <div key={post.id} className="timeline-item">
                  <div className="timeline-item__content">
                    <PostCard post={post} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </aside>
    </div>
  );
}
