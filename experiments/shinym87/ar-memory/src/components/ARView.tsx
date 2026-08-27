import { useMemo } from "react";
import playgroundPhoto from "../../photo/playground.jpg";
import { getSpot } from "../data/spots";
import { useApp } from "../state/AppContext";
import { getCategoryMeta } from "../utils/category";
import { formatDate } from "../utils/format";
import PostMedia from "./PostMedia";

// 현재는 놀이터 배경 사진만 준비되어 있어 AR 체험 뷰는 이 지점 고정으로 동작한다.
const AR_SPOT_ID = "playground";

interface ARViewProps {
  onClose: () => void;
}

export default function ARView({ onClose }: ARViewProps) {
  const { getSpotPosts, getUser } = useApp();
  const spot = getSpot(AR_SPOT_ID);
  const { neighborPosts, morePosts } = getSpotPosts(AR_SPOT_ID);
  const posts = useMemo(() => [...neighborPosts, ...morePosts], [neighborPosts, morePosts]);
  const featured = posts[posts.length - 1];

  if (!spot) return null;

  return (
    <div className="ar-view">
      <div className="ar-view__background" style={{ backgroundImage: `url(${playgroundPhoto})` }}>
        <div className="ar-view__scrim" />

        <header className="ar-view__topbar">
          <button className="ar-view__back" onClick={onClose}>
            ← 지도로 돌아가기
          </button>
          <span className="ar-view__badge">🥽 AR 체험 뷰 (프로토타입)</span>
        </header>

        <div className="ar-view__reticle" aria-hidden="true">
          <span className="ar-view__corner ar-view__corner--tl" />
          <span className="ar-view__corner ar-view__corner--tr" />
          <span className="ar-view__corner ar-view__corner--bl" />
          <span className="ar-view__corner ar-view__corner--br" />
        </div>

        <p className="ar-view__spot-tag">
          📍 {spot.name} — 실제 AR Glass라면 이 장면 위에 아래 기록들이 떠 보였을 거예요
        </p>

        {featured && (
          <article className="ar-floating-card">
            <PostMedia post={featured} className="ar-floating-card__thumb" />
            <div className="ar-floating-card__body">
              <span className="ar-floating-card__hint">가장 최근 기록</span>
              <p className="ar-floating-card__desc">{featured.description}</p>
              <time className="ar-floating-card__time">{formatDate(featured.createdAt)}</time>
            </div>
          </article>
        )}

        <div className="ar-view__tray">
          {posts.length === 0 && (
            <p className="ar-view__empty">아직 이 장소에 남겨진 기록이 없어요.</p>
          )}
          {posts.map((post) => {
            const author = getUser(post.authorId);
            const meta = getCategoryMeta(post.category);
            return (
              <article key={post.id} className="ar-card">
                <PostMedia post={post} className="ar-card__thumb" />
                <div className="ar-card__body">
                  <div className="ar-card__meta">
                    <span>{author?.avatarEmoji ?? "👤"}</span>
                    <span className="ar-card__author">{author?.name}</span>
                    <span className="ar-card__category" style={{ color: meta.color }}>
                      {meta.emoji} {meta.label}
                    </span>
                  </div>
                  <p className="ar-card__desc">{post.description}</p>
                  <time className="ar-card__time">{formatDate(post.createdAt)}</time>
                </div>
              </article>
            );
          })}
        </div>
      </div>
    </div>
  );
}
