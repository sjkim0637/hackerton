import { useState } from "react";
import { CATEGORIES } from "../utils/category";
import { SPOTS, getSpot } from "../data/spots";
import type { CategoryId, Visibility } from "../types";
import { useApp } from "../state/AppContext";
import SiteMap from "./SiteMap";

interface PostFormProps {
  initialSpotId?: string | null;
  onDone: (spotId: string) => void;
  onCancel: () => void;
}

export default function PostForm({ initialSpotId, onDone, onCancel }: PostFormProps) {
  const { addPost } = useApp();

  const [spotId, setSpotId] = useState<string | null>(initialSpotId ?? null);
  const [category, setCategory] = useState<CategoryId>("season");
  const [description, setDescription] = useState("");
  const [visibility, setVisibility] = useState<Visibility>("neighbors");
  const [mediaUrl, setMediaUrl] = useState<string | null>(null);
  const [mediaType, setMediaType] = useState<"image" | "video">("image");
  const [error, setError] = useState<string | null>(null);

  const selectedSpot = spotId ? getSpot(spotId) : null;

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const isVideo = file.type.startsWith("video/");
    setMediaType(isVideo ? "video" : "image");
    const reader = new FileReader();
    reader.onload = () => setMediaUrl(reader.result as string);
    reader.readAsDataURL(file);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!spotId) {
      setError("지도에서 위치를 선택해주세요.");
      return;
    }
    if (!mediaUrl) {
      setError("사진 또는 영상을 업로드해주세요.");
      return;
    }
    if (!description.trim()) {
      setError("짧은 설명을 남겨주세요.");
      return;
    }
    addPost({ spotId, category, description: description.trim(), mediaType, mediaUrl, visibility });
    onDone(spotId);
  }

  return (
    <div className="post-form-page">
      <header className="post-form-page__header">
        <h2>이 순간을 남기기</h2>
        <button className="btn-close" onClick={onCancel} aria-label="닫기">
          ✕
        </button>
      </header>

      <form className="post-form" onSubmit={handleSubmit}>
        <section className="post-form__section">
          <label className="post-form__label">1. 촬영한 장소를 지도에서 선택하세요</label>
          <p className="post-form__hint">촬영 장소와 게시 장소는 항상 같습니다. 지점을 클릭해 선택하세요.</p>
          <SiteMap mode="pick" selectedSpotId={spotId} onSpotClick={setSpotId} />
          <div className="post-form__spot-select">
            {SPOTS.map((s) => (
              <button
                type="button"
                key={s.id}
                className={`chip${spotId === s.id ? " chip--active" : ""}`}
                onClick={() => setSpotId(s.id)}
              >
                {s.name}
              </button>
            ))}
          </div>
          {selectedSpot && <p className="post-form__selected">선택됨: 📍 {selectedSpot.name}</p>}
        </section>

        <section className="post-form__section">
          <label className="post-form__label">2. 카테고리</label>
          <div className="post-form__categories">
            {CATEGORIES.map((c) => (
              <button
                type="button"
                key={c.id}
                className={`category-pick${category === c.id ? " category-pick--active" : ""}`}
                style={{
                  borderColor: category === c.id ? c.color : "var(--border)",
                  color: category === c.id ? c.color : "inherit",
                }}
                onClick={() => setCategory(c.id)}
              >
                {c.emoji} {c.label}
              </button>
            ))}
          </div>
        </section>

        <section className="post-form__section">
          <label className="post-form__label">3. 사진 또는 영상</label>
          <input type="file" accept="image/*,video/*" onChange={handleFileChange} />
          {mediaUrl && (
            <div className="post-form__preview">
              {mediaType === "video" ? (
                <video src={mediaUrl} controls />
              ) : (
                <img src={mediaUrl} alt="업로드 미리보기" />
              )}
            </div>
          )}
        </section>

        <section className="post-form__section">
          <label className="post-form__label" htmlFor="desc">
            4. 짧은 설명
          </label>
          <textarea
            id="desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="이 순간에 대해 남기고 싶은 이야기를 적어주세요"
            rows={3}
          />
        </section>

        <section className="post-form__section">
          <label className="post-form__label">5. 공개 범위</label>
          <div className="post-form__visibility">
            <label className={`radio-card${visibility === "neighbors" ? " radio-card--active" : ""}`}>
              <input
                type="radio"
                name="visibility"
                checked={visibility === "neighbors"}
                onChange={() => setVisibility("neighbors")}
              />
              🤝 이웃(친구) 공개
              <span className="radio-card__desc">이웃 맺은 입주민에게만 보여요 (기본값)</span>
            </label>
            <label className={`radio-card${visibility === "all" ? " radio-card--active" : ""}`}>
              <input
                type="radio"
                name="visibility"
                checked={visibility === "all"}
                onChange={() => setVisibility("all")}
              />
              🌐 단지 전체 공개
              <span className="radio-card__desc">모든 입주민이 볼 수 있어요</span>
            </label>
          </div>
        </section>

        {error && <p className="post-form__error">{error}</p>}

        <div className="post-form__actions">
          <button type="button" className="btn-secondary" onClick={onCancel}>
            취소
          </button>
          <button type="submit" className="btn-primary">
            게시하기
          </button>
        </div>
      </form>
    </div>
  );
}
