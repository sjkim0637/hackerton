import { useState } from "react";
import type { Post } from "../types";
import { useApp } from "../state/AppContext";
import CategoryBadge from "./CategoryBadge";
import VisibilityBadge from "./VisibilityBadge";
import PostMedia from "./PostMedia";
import { formatDateTime } from "../utils/format";

export default function PostCard({ post }: { post: Post }) {
  const { getUser, currentUser, toggleLike, addComment, toggleNeighbor } = useApp();
  const [commentText, setCommentText] = useState("");
  const [showComments, setShowComments] = useState(false);

  const author = getUser(post.authorId);
  const isMe = post.authorId === currentUser.id;
  const isNeighbor = currentUser.neighbors.includes(post.authorId);
  const liked = post.likedBy.includes(currentUser.id);

  return (
    <article className="post-card">
      <header className="post-card__header">
        <div className="post-card__author">
          <span className="post-card__avatar">{author?.avatarEmoji ?? "👤"}</span>
          <div>
            <div className="post-card__author-name">
              {author?.name ?? "알 수 없음"}
              {isMe && <span className="post-card__me-tag">나</span>}
            </div>
            <div className="post-card__author-unit">{author?.unit}</div>
          </div>
        </div>
        {!isMe && (
          <button
            className={`btn-neighbor${isNeighbor ? " btn-neighbor--active" : ""}`}
            onClick={() => toggleNeighbor(post.authorId)}
          >
            {isNeighbor ? "이웃 ✓" : "+ 이웃 맺기"}
          </button>
        )}
      </header>

      <PostMedia post={post} className="post-card__media" />

      <div className="post-card__body">
        <div className="post-card__badges">
          <CategoryBadge category={post.category} />
          <VisibilityBadge visibility={post.visibility} />
        </div>
        <p className="post-card__description">{post.description}</p>
        <time className="post-card__time">{formatDateTime(post.createdAt)}</time>
      </div>

      <footer className="post-card__footer">
        <button
          className={`btn-like${liked ? " btn-like--active" : ""}`}
          onClick={() => toggleLike(post.id)}
        >
          {liked ? "❤️" : "🤍"} 좋아요 {post.likedBy.length > 0 && post.likedBy.length}
        </button>
        <button className="btn-comment-toggle" onClick={() => setShowComments((v) => !v)}>
          💬 댓글 {post.comments.length > 0 && post.comments.length}
        </button>
      </footer>

      {showComments && (
        <div className="post-card__comments">
          {post.comments.map((c) => {
            const commenter = getUser(c.authorId);
            return (
              <div key={c.id} className="comment">
                <span className="comment__avatar">{commenter?.avatarEmoji ?? "👤"}</span>
                <div className="comment__body">
                  <span className="comment__author">{commenter?.name}</span>
                  <span className="comment__text">{c.text}</span>
                </div>
              </div>
            );
          })}
          <form
            className="comment-form"
            onSubmit={(e) => {
              e.preventDefault();
              if (!commentText.trim()) return;
              addComment(post.id, commentText);
              setCommentText("");
            }}
          >
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="댓글을 남겨보세요"
            />
            <button type="submit">등록</button>
          </form>
        </div>
      )}
    </article>
  );
}
