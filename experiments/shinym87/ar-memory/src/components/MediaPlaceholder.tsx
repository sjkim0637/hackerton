import type { CategoryId, Post } from "../types";
import { getCategoryMeta } from "../utils/category";

const LABELS: Record<string, string> = {
  spring: "봄",
  summer: "여름",
  autumn: "가을",
  winter: "겨울",
  baby: "첫 걸음마",
  toddler: "아장아장",
  kid: "씩씩하게",
  plant: "심기",
  grow: "자라는 중",
  harvest: "수확",
  morning: "아침 산책",
  greet: "첫 인사",
  snow: "눈 오는 날",
};

interface MediaPlaceholderProps {
  post: Pick<Post, "mediaUrl" | "mediaType" | "category">;
  className?: string;
}

/**
 * 시드 데이터는 실제 파일이 없으므로 "seed:category:label" 식별자를 받아
 * 카테고리 색상의 그라디언트 카드로 대체 표시한다. 실제 업로드본(data URL)은 그대로 렌더링한다.
 */
export default function MediaPlaceholder({ post, className }: MediaPlaceholderProps) {
  const meta = getCategoryMeta(post.category);

  if (post.mediaUrl.startsWith("seed:")) {
    const [, , label] = post.mediaUrl.split(":");
    return (
      <div
        className={`media-placeholder ${className ?? ""}`}
        style={{
          background: `linear-gradient(135deg, ${meta.color}55, ${meta.color}22)`,
        }}
      >
        <span className="media-placeholder__emoji">{meta.emoji}</span>
        <span className="media-placeholder__label">{LABELS[label] ?? meta.label}</span>
      </div>
    );
  }

  if (post.mediaType === "video") {
    return (
      <video className={`media-real ${className ?? ""}`} src={post.mediaUrl} controls />
    );
  }

  return <img className={`media-real ${className ?? ""}`} src={post.mediaUrl} alt={meta.label} />;
}

export function categoryColor(id: CategoryId): string {
  return getCategoryMeta(id).color;
}
