import { useState } from "react";
import { AppProvider } from "./state/AppContext";
import TopNav, { type ViewName } from "./components/TopNav";
import MapPage from "./components/MapPage";
import PostForm from "./components/PostForm";
import AccountPage from "./components/AccountPage";
import ARView from "./components/ARView";
import "./App.css";

function AppShell() {
  const [view, setView] = useState<ViewName>("map");
  const [createAtSpotId, setCreateAtSpotId] = useState<string | null>(null);

  function goToCreate(spotId?: string) {
    setCreateAtSpotId(spotId ?? null);
    setView("create");
  }

  return (
    <div className="app-shell">
      {view !== "ar" && <TopNav view={view} onNavigate={setView} />}
      <main className={`app-main${view === "ar" ? " app-main--ar" : ""}`}>
        {view === "map" && (
          <MapPage onCreateAt={(spotId) => goToCreate(spotId)} onOpenAR={() => setView("ar")} />
        )}
        {view === "ar" && <ARView onClose={() => setView("map")} />}
        {view === "create" && (
          <PostForm
            initialSpotId={createAtSpotId}
            onDone={() => {
              setCreateAtSpotId(null);
              setView("map");
            }}
            onCancel={() => {
              setCreateAtSpotId(null);
              setView("map");
            }}
          />
        )}
        {view === "account" && <AccountPage />}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AppProvider>
      <AppShell />
    </AppProvider>
  );
}
