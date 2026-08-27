import { useApp } from "../state/AppContext";

export type ViewName = "map" | "create" | "account" | "ar";

interface TopNavProps {
  view: ViewName;
  onNavigate: (view: ViewName) => void;
}

export default function TopNav({ view, onNavigate }: TopNavProps) {
  const { currentUser } = useApp();

  return (
    <header className="top-nav">
      <div className="top-nav__brand">
        <span className="top-nav__logo">🏡</span>
        <span>단지 추억 공유 AR</span>
      </div>

      <nav className="top-nav__tabs">
        <button
          className={`top-nav__tab${view === "map" ? " top-nav__tab--active" : ""}`}
          onClick={() => onNavigate("map")}
        >
          🗺️ 지도
        </button>
        <button
          className={`top-nav__tab${view === "create" ? " top-nav__tab--active" : ""}`}
          onClick={() => onNavigate("create")}
        >
          ➕ 순간 남기기
        </button>
        <button
          className={`top-nav__tab${view === "account" ? " top-nav__tab--active" : ""}`}
          onClick={() => onNavigate("account")}
        >
          👤 계정
        </button>
      </nav>

      <button className="top-nav__account" onClick={() => onNavigate("account")}>
        <span>{currentUser.avatarEmoji}</span>
        <span className="top-nav__account-name">{currentUser.name}</span>
      </button>
    </header>
  );
}
