import type { CategoryId } from "../types";
import { getCategoryMeta } from "../utils/category";

export default function CategoryBadge({ category }: { category: CategoryId }) {
  const meta = getCategoryMeta(category);
  return (
    <span className="badge badge--category" style={{ backgroundColor: `${meta.color}22`, color: meta.color }}>
      {meta.emoji} {meta.label}
    </span>
  );
}
