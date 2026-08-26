import { useApp } from "../state/AppContext";

export default function AccountPage() {
  const { users, currentUser, setCurrentUserId, toggleNeighbor } = useApp();

  return (
    <div className="account-page">
      <section className="account-page__section">
        <h2>테스트 계정 전환</h2>
        <p className="account-page__hint">
          실제 로그인 없이, 아래 계정 중 하나를 선택해 다른 입주민 시점에서 서비스를 체험할 수 있어요.
        </p>
        <div className="account-list">
          {users.map((u) => (
            <button
              key={u.id}
              className={`account-item${u.id === currentUser.id ? " account-item--active" : ""}`}
              onClick={() => setCurrentUserId(u.id)}
            >
              <span className="account-item__avatar">{u.avatarEmoji}</span>
              <span className="account-item__info">
                <span className="account-item__name">{u.name}</span>
                <span className="account-item__unit">{u.unit}</span>
              </span>
              {u.id === currentUser.id && <span className="account-item__badge">현재 계정</span>}
            </button>
          ))}
        </div>
      </section>

      <section className="account-page__section">
        <h2>이웃 관리</h2>
        <p className="account-page__hint">
          현재 <strong>{currentUser.name}</strong>님의 이웃 목록이에요. 이웃으로 맺은 입주민의 게시물은 지도에서 우선 노출돼요.
        </p>
        <div className="account-list">
          {users
            .filter((u) => u.id !== currentUser.id)
            .map((u) => {
              const isNeighbor = currentUser.neighbors.includes(u.id);
              return (
                <div key={u.id} className="account-item account-item--static">
                  <span className="account-item__avatar">{u.avatarEmoji}</span>
                  <span className="account-item__info">
                    <span className="account-item__name">{u.name}</span>
                    <span className="account-item__unit">{u.unit}</span>
                  </span>
                  <button
                    className={`btn-neighbor${isNeighbor ? " btn-neighbor--active" : ""}`}
                    onClick={() => toggleNeighbor(u.id)}
                  >
                    {isNeighbor ? "이웃 ✓" : "+ 이웃 맺기"}
                  </button>
                </div>
              );
            })}
        </div>
      </section>
    </div>
  );
}
