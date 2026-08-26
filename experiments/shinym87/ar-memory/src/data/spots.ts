import type { Spot } from "../types";

// 단지 배치도(SVG, viewBox 0 0 100 100) 위의 고정 지점들.
// 게시물은 이 지점들 중 하나에 소속되며, 같은 지점에 쌓인 게시물이 시간순 타임라인을 이룬다.
export const SPOTS: Spot[] = [
  {
    id: "sakura-path",
    name: "중앙 벚꽃길",
    description: "단지를 가로지르는 산책로. 계절마다 풍경이 크게 바뀐다.",
    x: 32,
    y: 34,
  },
  {
    id: "playground",
    name: "어린이 놀이터",
    description: "그네, 미끄럼틀이 있는 놀이터.",
    x: 70,
    y: 24,
  },
  {
    id: "garden",
    name: "공동 텃밭",
    description: "입주민들이 함께 가꾸는 텃밭.",
    x: 20,
    y: 72,
  },
  {
    id: "pet-trail",
    name: "반려동물 산책로",
    description: "단지 둘레길. 반려동물과 산책하기 좋은 코스.",
    x: 78,
    y: 68,
  },
  {
    id: "pavilion",
    name: "실개천 정자",
    description: "작은 개천 옆 정자. 이웃들이 모이는 쉼터.",
    x: 50,
    y: 82,
  },
];

export function getSpot(id: string): Spot | undefined {
  return SPOTS.find((s) => s.id === id);
}
