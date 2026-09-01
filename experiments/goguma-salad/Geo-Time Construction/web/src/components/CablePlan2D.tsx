import type {
  ArchitectureSegment,
  CommunicationDevice,
  ConstructionObject,
  SelectableObject,
} from "../types";

const COLORS: Record<string, string> = {
  "e-wire": "#56d6ff",
  "e-wire3s": "#ffb84d",
};

interface Props {
  objects: ConstructionObject[];
  devices: CommunicationDevice[];
  architecture: ArchitectureSegment[];
  showArchitecture: boolean;
  selectedId: string | null;
  onSelect: (object: SelectableObject | null) => void;
}

export function CablePlan2D({
  objects,
  devices,
  architecture,
  showArchitecture,
  selectedId,
  onSelect,
}: Props) {
  const points = [
    ...objects.flatMap((item) => item.geometry.points),
    ...devices.map((item) => item.geometry.position),
    ...(showArchitecture ? architecture.flatMap((item) => [item.start, item.end]) : []),
  ];
  if (!points.length) {
    return <div className="empty-view">표시할 통신 배선이 없습니다.</div>;
  }
  const minX = Math.min(...points.map((point) => point.x));
  const maxX = Math.max(...points.map((point) => point.x));
  const minY = Math.min(...points.map((point) => point.y));
  const maxY = Math.max(...points.map((point) => point.y));
  const padding = 1;

  return (
    <svg
      className="plan-view"
      viewBox={`${minX - padding} ${-(maxY + padding)} ${maxX - minX + padding * 2} ${maxY - minY + padding * 2}`}
      aria-label="통신 배선 2D 평면"
      onClick={() => onSelect(null)}
    >
      <g transform="scale(1 -1)">
        {showArchitecture && architecture.map((segment) => (
          <line
            key={segment.id}
            x1={segment.start.x}
            y1={segment.start.y}
            x2={segment.end.x}
            y2={segment.end.y}
            stroke="#55758b"
            strokeOpacity="0.62"
            strokeWidth="0.025"
            vectorEffect="non-scaling-stroke"
          />
        ))}
        {objects.map((item) => (
          <polyline
            key={item.id}
            points={item.geometry.points.map((point) => `${point.x},${point.y}`).join(" ")}
            fill="none"
            stroke={COLORS[item.source.cad_layer] ?? "#d6f3ff"}
            strokeWidth={selectedId === item.id ? 0.16 : Math.max(item.properties.diameter_m * 3, 0.06)}
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
            className="selectable-path"
            onClick={(event) => {
              event.stopPropagation();
              onSelect(item);
            }}
          />
        ))}
        {devices.map((device) => {
          const position = device.geometry.position;
          const selected = selectedId === device.id;
          const isPanel = device.properties.subtype === "communication_panel";
          return isPanel ? (
            <rect
              key={device.id}
              x={position.x - 0.22}
              y={position.y - 0.22}
              width="0.44"
              height="0.44"
              rx="0.05"
              fill="#b45cff"
              stroke={selected ? "#ffffff" : "#e3baff"}
              strokeWidth={selected ? 0.12 : 0.05}
              vectorEffect="non-scaling-stroke"
              className="selectable-path"
              onClick={(event) => {
                event.stopPropagation();
                onSelect(device);
              }}
            />
          ) : (
            <circle
              key={device.id}
              cx={position.x}
              cy={position.y}
              r={selected ? 0.24 : 0.18}
              fill="#6fe3a2"
              stroke={selected ? "#ffffff" : "#b9ffd5"}
              strokeWidth={selected ? 0.12 : 0.05}
              vectorEffect="non-scaling-stroke"
              className="selectable-path"
              onClick={(event) => {
                event.stopPropagation();
                onSelect(device);
              }}
            />
          );
        })}
      </g>
    </svg>
  );
}
