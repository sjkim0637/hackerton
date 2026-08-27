/**
 * 시드 데이터용 무료 스톡 이미지 URL을 만든다. Picsum Photos(https://picsum.photos)는
 * API Key 없이 seed 문자열로 항상 같은 실제 사진을 돌려주는 Placeholder 이미지 서비스다.
 * 실제 게시물 사진/영상은 사용자가 업로드한 파일(Base64 Data URL)을 그대로 사용한다.
 */
export function stockPhotoUrl(seed: string, width = 640, height = 480): string {
  return `https://picsum.photos/seed/${encodeURIComponent(seed)}/${width}/${height}`;
}
