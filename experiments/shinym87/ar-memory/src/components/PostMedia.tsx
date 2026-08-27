import type { Post } from "../types";
import { getCategoryMeta } from "../utils/category";

interface PostMediaProps {
  post: Pick<Post, "mediaUrl" | "mediaType" | "category">;
  className?: string;
}

/** 게시물 사진/영상을 렌더링한다. 시드 데이터는 무료 스톡 이미지(Picsum) URL을, 실제 게시물은 사용자가 업로드한 파일(Data URL)을 그대로 사용한다. */
export default function PostMedia({ post, className }: PostMediaProps) {
  const meta = getCategoryMeta(post.category);

  if (post.mediaType === "video") {
    return <video className={`media-real ${className ?? ""}`} src={post.mediaUrl} controls />;
  }

  return (
    <img
      className={`media-real ${className ?? ""}`}
      src={post.mediaUrl}
      alt={meta.label}
      loading="lazy"
    />
  );
}
