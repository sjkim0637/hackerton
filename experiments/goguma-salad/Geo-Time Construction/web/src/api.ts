import type {
  ArchitectureBackgroundResponse,
  ConstructionObjectResponse,
  DrawingAnalysis,
} from "./types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8000";

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(payload?.detail ?? `API request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export async function analyzeDxf(file: File): Promise<DrawingAnalysis> {
  const body = new FormData();
  body.append("file", file);
  return parseResponse(
    await fetch(`${API_URL}/api/cad/analyze`, {
      method: "POST",
      body,
    }),
  );
}

export async function buildCableObjects(
  file: File,
  unitType: string,
  layers: string[],
  elevationM: number,
  diameterM: number,
): Promise<ConstructionObjectResponse> {
  const body = new FormData();
  body.append("file", file);
  const query = new URLSearchParams({
    unit_type: unitType,
    elevation_m: String(elevationM),
    diameter_m: String(diameterM),
  });
  layers.forEach((layer) => query.append("layers", layer));
  return parseResponse(
    await fetch(`${API_URL}/api/cad/construction-objects?${query}`, {
      method: "POST",
      body,
    }),
  );
}

export async function buildArchitectureBackground(
  file: File,
  unitType: string,
  minSegmentLengthMm = 100,
): Promise<ArchitectureBackgroundResponse> {
  const body = new FormData();
  body.append("file", file);
  const query = new URLSearchParams({
    unit_type: unitType,
    min_segment_length_mm: String(minSegmentLengthMm),
  });
  return parseResponse(
    await fetch(`${API_URL}/api/cad/architecture-background?${query}`, {
      method: "POST",
      body,
    }),
  );
}
