// 추억 공유 소셜 AR 프로토타입 — 핵심 도메인 타입 정의

export type CategoryId = "season" | "kids" | "garden" | "pet";

export type Visibility = "neighbors" | "all";

export interface CategoryMeta {
  id: CategoryId;
  label: string;
  emoji: string;
  color: string; // 핀/배지에 쓰는 대표 색상
}

export interface Spot {
  id: string;
  name: string;
  description: string;
  /** 배치도 SVG 상의 위치 (0~100 사이의 퍼센트 좌표) */
  x: number;
  y: number;
}

export interface User {
  id: string;
  name: string;
  unit: string; // 동/호수 (예: "102동 1503호")
  avatarEmoji: string;
  /** 이 사용자가 이웃 맺기(팔로우)한 사용자 id 목록 */
  neighbors: string[];
}

export interface Comment {
  id: string;
  authorId: string;
  text: string;
  createdAt: string; // ISO 8601
}

export interface Post {
  id: string;
  authorId: string;
  spotId: string;
  category: CategoryId;
  description: string;
  mediaType: "image" | "video";
  /** 실제 업로드본은 data URL, 시드 데이터는 "seed:" 프리픽스로 표시되는 placeholder 식별자 */
  mediaUrl: string;
  visibility: Visibility;
  createdAt: string; // ISO 8601
  likedBy: string[];
  comments: Comment[];
}

export interface AppData {
  users: User[];
  posts: Post[];
  currentUserId: string;
}
