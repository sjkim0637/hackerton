import type { CategoryId } from "../types";

export interface ARMarker {
  id: string;
  /** 지도가 아닌, 사진 속 세부 위치를 가리키는 이름 (예: 미끄럼틀, 그네) */
  label: string;
  /**
   * 배경 사진(`photo/playground.jpg`) 기준 퍼센트 좌표(0~100).
   * 사진을 보면서 값을 직접 조정하면 된다 — 사진 좌상단이 (0, 0), 우하단이 (100, 100).
   */
  x: number;
  y: number;
  /** 지도 화면 핀과 동일한 카테고리 색상 규칙을 따르기 위한 카테고리 */
  category: CategoryId;
  /** 연동할 게시물 id. 영상이 없을 때 이 게시물의 사진/텍스트가 카드로 표시된다 */
  postId?: string;
  /**
   * photo/ 폴더에 넣을 짧은 영상 파일명(예: "playground-slide.mp4").
   * 해당 파일이 photo/ 폴더에 아직 없으면 자동으로 postId 카드로 대체된다.
   */
  videoFile?: string;
}

// 현재는 놀이터 배경 사진(photo/playground.jpg) 기준 좌표만 정의한다.
// 좌표는 눈대중으로 잡은 초기값이니, 실제 사진을 보면서 x/y를 조정해서 쓰면 된다.
export const AR_MARKERS: ARMarker[] = [
  {
    id: "playground-slide",
    label: "미끄럼틀",
    x: 27,
    y: 64,
    category: "kids",
    postId: "playground-slide",
    videoFile: "playground-slide.mp4",
  },
  {
    id: "playground-swing",
    label: "그네 (테스트)",
    x: 85,
    y: 53,
    category: "kids",
    postId: "playground-swing",
    videoFile: "playground-swing.mp4",
  },
];
