import { OrbitControls } from "@react-three/drei";
import { Canvas } from "@react-three/fiber";
import { useEffect, useMemo } from "react";
import { BufferGeometry, Float32BufferAttribute, Quaternion, Vector3 } from "three";

import type { ArchitectureSegment, ConstructionObject, Point3D } from "../types";

const COLORS: Record<string, string> = {
  "e-wire": "#22c7ff",
  "e-wire3s": "#ffad33",
};

function scenePoint(point: Point3D) {
  return new Vector3(point.x, point.z, -point.y);
}

function CableSegment({
  start,
  end,
  radius,
  color,
  selected,
  onSelect,
}: {
  start: Point3D;
  end: Point3D;
  radius: number;
  color: string;
  selected: boolean;
  onSelect: () => void;
}) {
  const geometry = useMemo(() => {
    const from = scenePoint(start);
    const to = scenePoint(end);
    const direction = to.clone().sub(from);
    const length = direction.length();
    const midpoint = from.clone().add(to).multiplyScalar(0.5);
    const quaternion = new Quaternion().setFromUnitVectors(
      new Vector3(0, 1, 0),
      direction.normalize(),
    );
    return { length, midpoint, quaternion };
  }, [start, end]);

  if (geometry.length < 0.001) return null;
  return (
    <mesh
      position={geometry.midpoint}
      quaternion={geometry.quaternion}
      onClick={(event) => {
        event.stopPropagation();
        onSelect();
      }}
    >
      <cylinderGeometry args={[radius, radius, geometry.length, 10]} />
      <meshStandardMaterial
        color={color}
        emissive={selected ? color : "#000000"}
        emissiveIntensity={selected ? 0.8 : 0}
        roughness={0.35}
        metalness={0.1}
      />
    </mesh>
  );
}

function CablePath({
  object,
  selected,
  onSelect,
}: {
  object: ConstructionObject;
  selected: boolean;
  onSelect: () => void;
}) {
  const color = COLORS[object.source.cad_layer] ?? "#e1f7ff";
  const radius = object.properties.diameter_m / 2;
  return object.geometry.points.slice(0, -1).map((point, index) => (
    <CableSegment
      key={`${object.id}-${index}`}
      start={point}
      end={object.geometry.points[index + 1]}
      radius={radius}
      color={color}
      selected={selected}
      onSelect={onSelect}
    />
  ));
}

function ArchitectureLines({ segments }: { segments: ArchitectureSegment[] }) {
  const geometry = useMemo(() => {
    const positions = new Float32Array(segments.length * 6);
    segments.forEach((segment, index) => {
      positions.set(
        [segment.start.x, 0.01, -segment.start.y, segment.end.x, 0.01, -segment.end.y],
        index * 6,
      );
    });
    const nextGeometry = new BufferGeometry();
    nextGeometry.setAttribute("position", new Float32BufferAttribute(positions, 3));
    return nextGeometry;
  }, [segments]);

  useEffect(() => () => geometry.dispose(), [geometry]);

  return (
    <lineSegments geometry={geometry}>
      <lineBasicMaterial color="#52758d" transparent opacity={0.68} />
    </lineSegments>
  );
}

export function CableScene3D({
  objects,
  architecture,
  showArchitecture,
  selectedId,
  onSelect,
}: {
  objects: ConstructionObject[];
  architecture: ArchitectureSegment[];
  showArchitecture: boolean;
  selectedId: string | null;
  onSelect: (object: ConstructionObject | null) => void;
}) {
  return (
    <Canvas
      className="scene-view"
      camera={{ position: [20, 18, 22], fov: 45, near: 0.1, far: 500 }}
      onPointerMissed={() => onSelect(null)}
    >
      <color attach="background" args={["#07111d"]} />
      <ambientLight intensity={0.8} />
      <directionalLight position={[12, 20, 8]} intensity={2.2} />
      <gridHelper args={[50, 50, "#33506a", "#16293b"]} position={[20, 0, -15]} />
      {showArchitecture && <ArchitectureLines segments={architecture} />}
      {objects.map((object) => (
        <CablePath
          key={object.id}
          object={object}
          selected={selectedId === object.id}
          onSelect={() => onSelect(object)}
        />
      ))}
      <OrbitControls makeDefault target={[15, 2, -14]} />
    </Canvas>
  );
}
