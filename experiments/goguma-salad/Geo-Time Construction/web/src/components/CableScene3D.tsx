import { OrbitControls } from "@react-three/drei";
import { Canvas } from "@react-three/fiber";
import { useMemo } from "react";
import { Quaternion, Vector3 } from "three";

import type { ConstructionObject, Point3D } from "../types";

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
}: {
  start: Point3D;
  end: Point3D;
  radius: number;
  color: string;
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
    <mesh position={geometry.midpoint} quaternion={geometry.quaternion}>
      <cylinderGeometry args={[radius, radius, geometry.length, 10]} />
      <meshStandardMaterial color={color} roughness={0.35} metalness={0.1} />
    </mesh>
  );
}

function CablePath({ object }: { object: ConstructionObject }) {
  const color = COLORS[object.source.cad_layer] ?? "#e1f7ff";
  const radius = object.properties.diameter_m / 2;
  return object.geometry.points.slice(0, -1).map((point, index) => (
    <CableSegment
      key={`${object.id}-${index}`}
      start={point}
      end={object.geometry.points[index + 1]}
      radius={radius}
      color={color}
    />
  ));
}

export function CableScene3D({ objects }: { objects: ConstructionObject[] }) {
  return (
    <Canvas className="scene-view" camera={{ position: [20, 18, 22], fov: 45, near: 0.1, far: 500 }}>
      <color attach="background" args={["#07111d"]} />
      <ambientLight intensity={0.8} />
      <directionalLight position={[12, 20, 8]} intensity={2.2} />
      <gridHelper args={[50, 50, "#33506a", "#16293b"]} position={[20, 0, -15]} />
      {objects.map((object) => (
        <CablePath key={object.id} object={object} />
      ))}
      <OrbitControls makeDefault target={[15, 2, -14]} />
    </Canvas>
  );
}
