import type { ARMarker } from "../data/arMarkers";
import { useApp } from "../state/AppContext";
import { getCategoryMeta } from "../utils/category";
import { getVideoUrl } from "../utils/videos";
import { formatDate } from "../utils/format";
import PostMedia from "./PostMedia";

interface ARMarkerPopupProps {
  marker: ARMarker;
  onClose: () => void;
}

export default function ARMarkerPopup({ marker, onClose }: ARMarkerPopupProps) {
  const { posts, getUser } = useApp();
  const meta = getCategoryMeta(marker.category);
  const videoUrl = getVideoUrl(marker.videoFile);
  const post = marker.postId ? posts.find((p) => p.id === marker.postId) : undefined;
  const author = post ? getUser(post.authorId) : undefined;

  return (
    <div className="ar-popup-overlay" onClick={onClose}>
      <div className="ar-popup" onClick={(e) => e.stopPropagation()}>
        <header className="ar-popup__header">
          <span className="ar-popup__title" style={{ color: meta.color }}>
            {meta.emoji} {marker.label}
          </span>
          <button className="btn-close" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </header>

        {videoUrl ? (
          <video className="ar-popup__video" src={videoUrl} controls autoPlay playsInline />
        ) : post ? (
          <div className="ar-popup__card">
            <PostMedia post={post} className="ar-popup__thumb" />
            <div className="ar-popup__card-body">
              <div className="ar-popup__meta">
                <span>{author?.avatarEmoji ?? "👤"}</span>
                <span className="ar-popup__author">{author?.name ?? "알 수 없음"}</span>
              </div>
              <p className="ar-popup__desc">{post.description}</p>
              <time className="ar-popup__time">{formatDate(post.createdAt)}</time>
            </div>
          </div>
        ) : (
          <p className="ar-popup__empty">아직 이 위치에 남겨진 기록이 없어요.</p>
        )}
      </div>
    </div>
  );
}
