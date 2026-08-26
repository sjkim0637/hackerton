import type { AppData, Post, User } from "../types";

// 테스트 계정 3개. 실제 인증 없이 화면에서 전환하며 사용한다.
// 이웃(팔로우) 관계는 일방향으로 얽혀 있어, 계정에 따라 보이는 게시물이 달라지는 것을 확인할 수 있다.
//   김민준 → 이서연 (이서연은 김민준을 이웃으로 두지 않음)
//   이서연 → 김민준, 박도윤
//   박도윤 → 이서연
export const SEED_USERS: User[] = [
  {
    id: "u1",
    name: "김민준",
    unit: "102동 1503호",
    avatarEmoji: "🙋",
    neighbors: ["u2"],
  },
  {
    id: "u2",
    name: "이서연",
    unit: "103동 802호",
    avatarEmoji: "🙆",
    neighbors: ["u1", "u3"],
  },
  {
    id: "u3",
    name: "박도윤",
    unit: "104동 601호",
    avatarEmoji: "🐕",
    neighbors: ["u2"],
  },
];

let seq = 0;
function nextId(prefix: string) {
  seq += 1;
  return `${prefix}-${seq}`;
}

function post(input: Omit<Post, "id" | "likedBy" | "comments"> & {
  likedBy?: string[];
  comments?: Post["comments"];
}): Post {
  return {
    id: nextId("post"),
    likedBy: input.likedBy ?? [],
    comments: input.comments ?? [],
    ...input,
  };
}

export const SEED_POSTS: Post[] = [
  // 중앙 벚꽃길: 계절 풍경 시간순 기록
  post({
    authorId: "u1",
    spotId: "sakura-path",
    category: "season",
    description: "드디어 벚꽃이 만개했어요! 출근길이 행복합니다 🌸",
    mediaType: "image",
    mediaUrl: "seed:season:spring",
    visibility: "all",
    createdAt: "2025-04-05T09:12:00+09:00",
    likedBy: ["u2", "u3"],
    comments: [
      { id: nextId("cmt"), authorId: "u2", text: "저희 집 앞이 제일 예쁜 것 같아요!", createdAt: "2025-04-05T10:00:00+09:00" },
    ],
  }),
  post({
    authorId: "u2",
    spotId: "sakura-path",
    category: "season",
    description: "초록이 무성해진 여름 산책로, 그늘이 시원해요",
    mediaType: "image",
    mediaUrl: "seed:season:summer",
    visibility: "neighbors",
    createdAt: "2025-07-20T18:40:00+09:00",
    likedBy: ["u1"],
  }),
  post({
    authorId: "u3",
    spotId: "sakura-path",
    category: "season",
    description: "단풍이 정말 예쁘게 들었네요",
    mediaType: "image",
    mediaUrl: "seed:season:autumn",
    visibility: "all",
    createdAt: "2025-10-15T16:05:00+09:00",
  }),
  post({
    authorId: "u1",
    spotId: "sakura-path",
    category: "season",
    description: "눈 쌓인 벚꽃길, 겨울도 운치있네요 ❄️",
    mediaType: "image",
    mediaUrl: "seed:season:winter",
    visibility: "neighbors",
    createdAt: "2026-01-10T08:20:00+09:00",
    likedBy: ["u2"],
  }),

  // 어린이 놀이터: 아이 성장 기록
  post({
    authorId: "u1",
    spotId: "playground",
    category: "kids",
    description: "우리 아이 첫 걸음마 뗀 곳이에요",
    mediaType: "image",
    mediaUrl: "seed:kids:baby",
    visibility: "neighbors",
    createdAt: "2025-03-01T11:00:00+09:00",
    likedBy: ["u2"],
    comments: [
      { id: nextId("cmt"), authorId: "u2", text: "축하드려요!! 너무 귀여워요 ㅠㅠ", createdAt: "2025-03-01T12:30:00+09:00" },
    ],
  }),
  post({
    authorId: "u1",
    spotId: "playground",
    category: "kids",
    description: "이제 혼자 그네도 탈 수 있어요",
    mediaType: "image",
    mediaUrl: "seed:kids:toddler",
    visibility: "neighbors",
    createdAt: "2025-08-12T17:15:00+09:00",
  }),
  post({
    authorId: "u2",
    spotId: "playground",
    category: "kids",
    description: "미끄럼틀 정복 성공! 씩씩한 우리 딸",
    mediaType: "image",
    mediaUrl: "seed:kids:kid",
    visibility: "all",
    createdAt: "2025-11-02T15:50:00+09:00",
    likedBy: ["u1", "u3"],
  }),

  // 공동 텃밭: 심기 → 자라기 → 수확
  post({
    authorId: "u2",
    spotId: "garden",
    category: "garden",
    description: "상추, 방울토마토 모종을 심었어요",
    mediaType: "image",
    mediaUrl: "seed:garden:plant",
    visibility: "all",
    createdAt: "2025-04-10T10:00:00+09:00",
  }),
  post({
    authorId: "u2",
    spotId: "garden",
    category: "garden",
    description: "무럭무럭 자라는 중입니다",
    mediaType: "image",
    mediaUrl: "seed:garden:grow",
    visibility: "neighbors",
    createdAt: "2025-06-05T09:30:00+09:00",
    likedBy: ["u1"],
  }),
  post({
    authorId: "u3",
    spotId: "garden",
    category: "garden",
    description: "드디어 수확했습니다! 나눠 먹어요~",
    mediaType: "image",
    mediaUrl: "seed:garden:harvest",
    visibility: "all",
    createdAt: "2025-07-25T14:20:00+09:00",
    likedBy: ["u1", "u2"],
    comments: [
      { id: nextId("cmt"), authorId: "u1", text: "저도 하나 주세요 ㅎㅎ", createdAt: "2025-07-25T15:00:00+09:00" },
    ],
  }),

  // 반려동물 산책로
  post({
    authorId: "u3",
    spotId: "pet-trail",
    category: "pet",
    description: "댕댕이랑 상쾌한 아침 산책",
    mediaType: "image",
    mediaUrl: "seed:pet:morning",
    visibility: "neighbors",
    createdAt: "2025-05-18T07:10:00+09:00",
    likedBy: ["u2"],
  }),
  post({
    authorId: "u1",
    spotId: "pet-trail",
    category: "pet",
    description: "산책로에서 이웃 강아지랑 첫 인사했어요",
    mediaType: "image",
    mediaUrl: "seed:pet:greet",
    visibility: "all",
    createdAt: "2025-09-09T18:00:00+09:00",
  }),
  post({
    authorId: "u3",
    spotId: "pet-trail",
    category: "pet",
    description: "눈 오는 날의 산책, 발자국이 소복소복",
    mediaType: "image",
    mediaUrl: "seed:pet:snow",
    visibility: "neighbors",
    createdAt: "2025-12-24T09:40:00+09:00",
    likedBy: ["u2"],
  }),

  // 실개천 정자
  post({
    authorId: "u2",
    spotId: "pavilion",
    category: "season",
    description: "정자에서 이웃들과 봄나들이 티타임",
    mediaType: "image",
    mediaUrl: "seed:season:spring",
    visibility: "all",
    createdAt: "2025-05-01T16:30:00+09:00",
    likedBy: ["u1", "u3"],
  }),
  post({
    authorId: "u1",
    spotId: "pavilion",
    category: "season",
    description: "가을 정자 풍경, 낙엽이 운치있어요",
    mediaType: "image",
    mediaUrl: "seed:season:autumn",
    visibility: "neighbors",
    createdAt: "2025-10-01T17:45:00+09:00",
  }),
];

export function createSeedData(): AppData {
  return {
    users: SEED_USERS.map((u) => ({ ...u, neighbors: [...u.neighbors] })),
    posts: SEED_POSTS.map((p) => ({
      ...p,
      likedBy: [...p.likedBy],
      comments: p.comments.map((c) => ({ ...c })),
    })),
    currentUserId: "u1",
  };
}
