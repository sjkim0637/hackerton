import type { CategoryId, CategoryMeta } from "../types";

export const CATEGORIES: CategoryMeta[] = [
  { id: "season", label: "계절 풍경", emoji: "🌸", color: "#e8749c" },
  { id: "kids", label: "아이 성장", emoji: "👶", color: "#4f9dde" },
  { id: "garden", label: "텃밭/화단", emoji: "🌱", color: "#5aa469" },
  { id: "pet", label: "반려동물", emoji: "🐾", color: "#d99a3d" },
];

const CATEGORY_MAP: Record<CategoryId, CategoryMeta> = CATEGORIES.reduce(
  (acc, c) => {
    acc[c.id] = c;
    return acc;
  },
  {} as Record<CategoryId, CategoryMeta>,
);

export function getCategoryMeta(id: CategoryId): CategoryMeta {
  return CATEGORY_MAP[id];
}
