import { useMemo, useState } from "react";

import { analyzeDxf, buildArchitectureBackground, buildCableObjects } from "./api";
import { CablePlan2D } from "./components/CablePlan2D";
import { CableScene3D } from "./components/CableScene3D";
import type {
  ArchitectureBackgroundResponse,
  ConstructionObjectResponse,
  DrawingAnalysis,
  SelectableObject,
} from "./types";

const CABLE_LAYERS = ["e-wire", "e-wire3s"];

export default function App() {
  const [file, setFile] = useState<File | null>(null);
  const [architectureFile, setArchitectureFile] = useState<File | null>(null);
  const [analysis, setAnalysis] = useState<DrawingAnalysis | null>(null);
  const [result, setResult] = useState<ConstructionObjectResponse | null>(null);
  const [architecture, setArchitecture] = useState<ArchitectureBackgroundResponse | null>(null);
  const [showArchitecture, setShowArchitecture] = useState(true);
  const [showDevices, setShowDevices] = useState(true);
  const [selectedObject, setSelectedObject] = useState<SelectableObject | null>(null);
  const [unitType, setUnitType] = useState("84A");
  const [layers, setLayers] = useState(CABLE_LAYERS);
  const [elevation, setElevation] = useState(2.3);
  const [diameter, setDiameter] = useState(0.03);
  const [mode, setMode] = useState<"2d" | "3d">("3d");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const layerCounts = useMemo(
    () => Object.fromEntries((analysis?.layers ?? []).map((layer) => [layer.name, layer.entity_count])),
    [analysis],
  );

  async function selectFile(nextFile: File | null) {
    setFile(nextFile);
    setResult(null);
    setArchitecture(null);
    setSelectedObject(null);
    setError(null);
    if (!nextFile) {
      setAnalysis(null);
      return;
    }
    setLoading(true);
    try {
      const nextAnalysis = await analyzeDxf(nextFile);
      setAnalysis(nextAnalysis);
      if (nextAnalysis.unit_regions.length) {
        setUnitType(nextAnalysis.unit_regions[0].unit_type);
      }
    } catch (caught) {
      setAnalysis(null);
      setError(caught instanceof Error ? caught.message : "DXF 분석에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function selectArchitectureFile(nextFile: File | null) {
    setArchitectureFile(nextFile);
    setArchitecture(null);
    setSelectedObject(null);
    setError(null);
  }

  async function generateObjects() {
    if (!file || !layers.length) return;
    setLoading(true);
    setError(null);
    try {
      const [nextResult, nextArchitecture] = await Promise.all([
        buildCableObjects(file, unitType, layers, elevation, diameter),
        architectureFile
          ? buildArchitectureBackground(architectureFile, unitType)
          : Promise.resolve(null),
      ]);
      setResult(nextResult);
      setArchitecture(nextArchitecture);
      setSelectedObject(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "3D 객체 생성에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function toggleLayer(layer: string) {
    setLayers((current) =>
      current.includes(layer) ? current.filter((item) => item !== layer) : [...current, layer],
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">GEO × TIME × LAYER</p>
          <h1>Communication Cable Path</h1>
        </div>
        <span className="phase-badge">PHASE 1 · DXF PoC</span>
      </header>

      <section className="workspace">
        <aside className="control-panel">
          <label className="upload-box">
            <span>DXF 도면 선택</span>
            <strong>{file?.name ?? "ET-1101 DXF를 선택하세요"}</strong>
            <input type="file" accept=".dxf" onChange={(event) => void selectFile(event.target.files?.[0] ?? null)} />
          </label>

          <label className="upload-box secondary">
            <span>건축 배경 DXF (선택)</span>
            <strong>{architectureFile?.name ?? "XR_단위 DXF를 선택하세요"}</strong>
            <input type="file" accept=".dxf" onChange={(event) => selectArchitectureFile(event.target.files?.[0] ?? null)} />
          </label>

          {analysis && (
            <>
              <div className="metrics">
                <div><span>Layer</span><strong>{analysis.layer_count}</strong></div>
                <div><span>Entity</span><strong>{analysis.entity_count.toLocaleString()}</strong></div>
                <div><span>Unit</span><strong>{analysis.source_units}</strong></div>
              </div>

              <div className="field-group">
                <label htmlFor="unit-type">평형</label>
                <select id="unit-type" value={unitType} onChange={(event) => {
                  setUnitType(event.target.value);
                  setResult(null);
                  setArchitecture(null);
                  setSelectedObject(null);
                }}>
                  {analysis.unit_regions.map((region) => (
                    <option key={region.unit_type} value={region.unit_type}>{region.unit_type}</option>
                  ))}
                </select>
              </div>

              <fieldset className="field-group">
                <legend>통신 Layer</legend>
                {CABLE_LAYERS.map((layer) => (
                  <label className="check-row" key={layer}>
                    <input type="checkbox" checked={layers.includes(layer)} onChange={() => toggleLayer(layer)} />
                    <span className={`layer-dot ${layer === "e-wire3s" ? "orange" : "blue"}`} />
                    <code>{layer}</code>
                    <small>{layerCounts[layer] ?? 0}</small>
                  </label>
                ))}
              </fieldset>

              <label className="check-row background-toggle">
                <input
                  type="checkbox"
                  checked={showArchitecture}
                  onChange={(event) => setShowArchitecture(event.target.checked)}
                />
                <span className="layer-dot gray" />
                <span>건축 배경</span>
                <small>{architecture?.rendered_segment_count.toLocaleString() ?? "-"}</small>
              </label>

              <label className="check-row background-toggle">
                <input
                  type="checkbox"
                  checked={showDevices}
                  onChange={(event) => setShowDevices(event.target.checked)}
                />
                <span className="layer-dot green" />
                <span>홈넷 설비</span>
                <small>{result?.device_count.toLocaleString() ?? "-"}</small>
              </label>

              <div className="parameter-grid">
                <label>표시 높이 (m)<input type="number" min="0" max="20" step="0.1" value={elevation} onChange={(event) => setElevation(Number(event.target.value))} /></label>
                <label>배선 지름 (m)<input type="number" min="0.005" max="1" step="0.005" value={diameter} onChange={(event) => setDiameter(Number(event.target.value))} /></label>
              </div>

              <button className="primary-button" disabled={loading || !layers.length} onClick={() => void generateObjects()}>
                {loading ? "변환 중…" : "Construction Object 생성"}
              </button>

              {selectedObject && (
                <section className="object-card">
                  <div><span>선택 객체</span><strong>{selectedObject.id}</strong></div>
                  <dl>
                    <dt>Layer</dt><dd>{selectedObject.source.cad_layer}</dd>
                    <dt>Handle</dt><dd>{selectedObject.source.entity_handle || "-"}</dd>
                    <dt>Elevation</dt><dd>{selectedObject.properties.elevation_m} m</dd>
                    {selectedObject.type === "cable_path" ? (
                      <>
                        <dt>Entity</dt><dd>{selectedObject.properties.source_entity_type}</dd>
                        <dt>Diameter</dt><dd>{selectedObject.properties.diameter_m} m</dd>
                        <dt>Points</dt><dd>{selectedObject.geometry.points.length}</dd>
                      </>
                    ) : (
                      <>
                        <dt>Device</dt><dd>{selectedObject.properties.display_name}</dd>
                        <dt>Subtype</dt><dd>{selectedObject.properties.subtype}</dd>
                        <dt>Block</dt><dd>{selectedObject.properties.block_name}</dd>
                        <dt>Rotation</dt><dd>{selectedObject.properties.rotation_deg}°</dd>
                      </>
                    )}
                  </dl>
                </section>
              )}
            </>
          )}
          {error && <p className="error-message">{error}</p>}
        </aside>

        <section className="viewer-panel">
          <div className="viewer-toolbar">
            <div>
              <strong>{result ? `${result.unit_region.unit_type} · ${result.object_count} paths · ${result.device_count} devices` : "Viewer"}</strong>
              <span>{result ? `${result.objects.reduce((sum, item) => sum + item.geometry.points.length, 0)} points · ${architecture?.rendered_segment_count.toLocaleString() ?? 0} background segments` : "DXF 분석 후 객체를 생성하세요"}</span>
            </div>
            <div className="mode-switch">
              <button className={mode === "2d" ? "active" : ""} onClick={() => setMode("2d")}>2D</button>
              <button className={mode === "3d" ? "active" : ""} onClick={() => setMode("3d")}>3D</button>
            </div>
          </div>
          <div className="viewer-stage">
            {!result && <div className="empty-view"><span>⌁</span><p>통신 배선 경로가 여기에 표시됩니다.</p></div>}
            {result && mode === "2d" && (
              <CablePlan2D
                objects={result.objects}
                devices={showDevices ? result.devices : []}
                architecture={architecture?.segments ?? []}
                showArchitecture={showArchitecture}
                selectedId={selectedObject?.id ?? null}
                onSelect={setSelectedObject}
              />
            )}
            {result && mode === "3d" && (
              <CableScene3D
                objects={result.objects}
                devices={showDevices ? result.devices : []}
                architecture={architecture?.segments ?? []}
                showArchitecture={showArchitecture}
                selectedId={selectedObject?.id ?? null}
                onSelect={setSelectedObject}
              />
            )}
          </div>
          <footer className="viewer-legend">
            <span><i className="blue" />e-wire</span>
            <span><i className="orange" />e-wire3s</span>
            <span><i className="gray" />건축 배경</span>
            <span><i className="purple" />통신단자함</span>
            <span><i className="green" />홈넷 설비</span>
            <span className="coordinate-note">원본 mm → 평형 Local m</span>
          </footer>
        </section>
      </section>
    </main>
  );
}
