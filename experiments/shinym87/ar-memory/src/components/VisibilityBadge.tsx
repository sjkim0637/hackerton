import type { Visibility } from "../types";

export default function VisibilityBadge({ visibility }: { visibility: Visibility }) {
  const isAll = visibility === "all";
  return (
    <span className={`badge badge--visibility${isAll ? " badge--visibility-all" : ""}`}>
      {isAll ? "🌐 단지 전체 공개" : "🤝 이웃 공개"}
    </span>
  );
}
