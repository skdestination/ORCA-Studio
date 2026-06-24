export type Screen = "home" | "editor" | "settings";

export type Layer = {
  id: string; // unique ID
  order: number; // For Up/Down sorting. Lower = deeper layer, Higher = top layer.
  isMuted: boolean;
  isHidden: boolean;
  isLocked?: boolean;
  name?: string;
  isManuallyCreated?: boolean;
};

export type Keyframe = {
  id: string;
  timeOffset: number; // Offset from clip's leftSeconds
  properties: {
    // Transform
    volume?: number;
    translateX?: number;
    translateY?: number;
    scaleX?: number;
    scaleY?: number;
    scale?: number; // Uniform scale
    rotation?: number;
    anchorPointX?: number;
    anchorPointY?: number;
    
    // Appearance
    opacity?: number;

    // Effects
    blur?: number;
    brightness?: number;
    contrast?: number;
    saturation?: number;
    exposure?: number;
    sharpen?: number;
    sepia?: number;
    grayscale?: number;
    hueRotate?: number;
    invert?: number;

    // Masks
    maskPositionX?: number;
    maskPositionY?: number;
    maskScale?: number;
    maskRotation?: number;
    maskFeather?: number;
    maskExpansion?: number;
    maskWidth?: number;
    maskHeight?: number;
    maskRoundness?: number;

    [key: string]: number | undefined;
  };
  curve?: "linear" | "easeIn" | "easeOut" | "easeInOut" | "hold";
  customEasePoints?: [number, number, number, number];
};

export type Clip = {
  id: string;
  layerId: string;
  type: "video" | "image" | "audio" | "text";
  src: string;
  name?: string;
  originalSrc?: string;
  fileId?: string;
  text?: string;
  color?: string;
  fontFamily?: string;
  fontSize?: number;
  textAnimation?: string;
  leftSeconds: number; // Start time on timeline
  durationSeconds: number; // Length on timeline
  originalDurationSeconds?: number; // Original source duration
  trimStartSeconds: number; // Offset within the source media
  volume?: number; // 0 to 100
  speed?: number; // playback speed modifier
  opticalFlow?: boolean; // smooth slow-motion
  width?: number; // resolution width
  height?: number; // resolution height
  fps?: number; // frames per second
  
  // Transform Base
  translateX?: number;
  translateY?: number;
  scaleX?: number;
  scaleY?: number;
  scale?: number;
  rotation?: number;
  anchorPointX?: number;
  anchorPointY?: number;

  // Appearance Base
  opacity?: number;

  // Effects Base
  blur?: number;
  brightness?: number;
  contrast?: number;
  saturation?: number;
  exposure?: number;
  sharpen?: number;
  sepia?: number;
  grayscale?: number;
  hueRotate?: number;
  invert?: number;

  // Masks
  maskType?: "none" | "circle" | "square" | "half";
  maskPositionX?: number;
  maskPositionY?: number;
  maskScale?: number;
  maskRotation?: number;
  maskFeather?: number;
  maskExpansion?: number;
  maskWidth?: number;
  maskHeight?: number;
  maskRoundness?: number;

  cropRatio?: "1:1" | "16:9" | "9:16" | "4:3" | "free" | null;
  cropRect?: { top: number, right: number, bottom: number, left: number };
  mixBlendMode?: "normal" | "multiply" | "screen" | "overlay" | "darken" | "lighten" | "color-dodge" | "color-burn" | "hard-light" | "soft-light" | "difference" | "exclusion" | "hue" | "saturation" | "color" | "luminosity";
  keyframes?: Keyframe[];
  transition?: {
    type: string;
    duration: number;
  };
  isStabilized?: boolean;
  stabilizationMode?: "standard" | "active" | "locked" | "off";
  stabilizationStrength?: number;
  compareStabilization?: boolean;
  thumbnail?: string;
};

export type Project = {
  id: string;
  name: string;
  ratio: string;
  updatedAt: string;
  duration: string;
  size: string;
  thumbnail: string;
  thumbnailFileId?: string;
  layers: Layer[];
  clips: Clip[];
};
