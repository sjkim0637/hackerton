import type { ConstructionObject } from "../types";

const COLORS: Record<string, string> = {
  "e-wire": "#56d6ff",
  "e-wire3s": "#ffb84d",
};

interface Props {
  objects: ConstructionObject[];
}

export function CablePlan2D({ objects }: Props) {
  const points = objects.flatMap((item) => item.geometry.points);
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
    >
      <g transform="scale(1 -1)">
        {objects.map((item) => (
          <polyline
            key={item.id}
            points={item.geometry.points.map((point) => `${point.x},${point.y}`).join(" ")}
            fill="none"
            stroke={COLORS[item.source.cad_layer] ?? "#d6f3ff"}
            strokeWidth={Math.max(item.properties.diameter_m * 3, 0.04)}
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
          />
        ))}
      </g>
    </svg>
  );
}
