import type { AppData } from "../types";
import { createSeedData } from "../data/seed";

const STORAGE_KEY = "apt-ar-memory:data:v1";

export function loadAppData(): AppData {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createSeedData();
    const parsed = JSON.parse(raw) as AppData;
    if (!parsed.users || !parsed.posts || !parsed.currentUserId) {
      return createSeedData();
    }
    return parsed;
  } catch {
    return createSeedData();
  }
}

export function saveAppData(data: AppData) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    // localStorage 사용 불가(용량 초과 등) 시 조용히 무시 — 프로토타입 범위 밖
  }
}

export function resetAppData(): AppData {
  const fresh = createSeedData();
  saveAppData(fresh);
  return fresh;
}
