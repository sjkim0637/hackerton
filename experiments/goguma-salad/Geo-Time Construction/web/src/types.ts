export interface Point3D {
  x: number;
  y: number;
  z: number;
}

export interface UnitRegion {
  unit_type: string;
  title: string;
  origin_x_mm: number;
  origin_y_mm: number;
  width_mm: number;
  height_mm: number;
}

export interface LayerSummary {
  name: string;
  entity_count: number;
}

export interface DrawingAnalysis {
  filename: string;
  source_units: string;
  source_unit_code: number;
  layer_count: number;
  entity_count: number;
  entity_types: Record<string, number>;
  layers: LayerSummary[];
  unit_regions: UnitRegion[];
}

export interface ConstructionObject {
  id: string;
  category: "communication";
  type: "cable_path";
  system: "home_network";
  source: {
    type: "dxf";
    drawing_name: string;
    entity_handle: string;
    cad_layer: string;
    unit_type: string;
  };
  geometry: {
    type: "polyline";
    points: Point3D[];
  };
  properties: {
    diameter_m: number;
    elevation_m: number;
    source_entity_type: string;
  };
}

export interface ConstructionObjectResponse {
  drawing: DrawingAnalysis;
  unit_region: UnitRegion;
  selected_layers: string[];
  object_count: number;
  objects: ConstructionObject[];
  device_count: number;
  devices: CommunicationDevice[];
}

export interface CommunicationDevice {
  id: string;
  category: "communication";
  type: "communication_device";
  system: "home_network";
  source: {
    type: "dxf";
    drawing_name: string;
    entity_handle: string;
    cad_layer: string;
    unit_type: string;
  };
  geometry: {
    type: "point";
    position: Point3D;
  };
  properties: {
    subtype: string;
    display_name: string;
    block_name: string;
    elevation_m: number;
    size_m: number;
    rotation_deg: number;
  };
}

export type SelectableObject = ConstructionObject | CommunicationDevice;

export interface ArchitectureSegment {
  id: string;
  cad_layer: string;
  entity_handle: string;
  source_entity_type: string;
  start: Point3D;
  end: Point3D;
}

export interface ArchitectureBackgroundResponse {
  filename: string;
  unit_type: string;
  source_units: string;
  source_path_entity_count: number;
  rendered_segment_count: number;
  min_segment_length_mm: number;
  segments: ArchitectureSegment[];
}
