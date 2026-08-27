// photo/ 폴더에 있는 짧은 영상 파일을 파일명으로 찾을 수 있게 미리 매핑해둔다.
// 아직 넣지 않은 파일명을 조회하면 undefined를 반환하고, 호출부는 사진/텍스트 카드로 대체한다.
const videoModules = import.meta.glob("../../photo/*.{mp4,mov,webm,m4v}", {
  eager: true,
  query: "?url",
  import: "default",
}) as Record<string, string>;

const videoUrlByFilename: Record<string, string> = {};
for (const [modulePath, url] of Object.entries(videoModules)) {
  const filename = modulePath.split("/").pop();
  if (filename) videoUrlByFilename[filename] = url;
}

export function getVideoUrl(filename?: string): string | undefined {
  if (!filename) return undefined;
  return videoUrlByFilename[filename];
}
