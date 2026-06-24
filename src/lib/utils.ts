import { Clip, Keyframe } from "../types";

export function solveCubicBezier(x1: number, y1: number, x2: number, y2: number, t: number): number {
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  // Binary search for u
  let low = 0;
  let high = 1;
  for (let i = 0; i < 8; i++) {
    const u = (low + high) / 2;
    // Calculate x of bezier
    const x = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u;
    if (x < t) {
      low = u;
    } else {
      high = u;
    }
  }
  const u = (low + high) / 2;
  // Calculate y
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
}

const sortedKeyframesCache = new WeakMap<Keyframe[], Keyframe[]>();
const filteredKeyframesCache = new WeakMap<Keyframe[], Map<string, Keyframe[]>>();

export function getInterpolatedProps(clip: Clip, timeInClip: number, activeMenu: string | null) {
  const normVolume = (v: number | undefined) => {
    if (v === undefined) return 100;
    if (v <= 1.0) return v * 100;
    return v;
  };

  const clipVolumeOriginal = normVolume(clip.volume);

  const baseProps = {
    volume: clipVolumeOriginal,
    translateX: clip.translateX ?? 0,
    translateY: clip.translateY ?? 0,
    scaleX: clip.scaleX ?? clip.scale ?? 1,
    scaleY: clip.scaleY ?? clip.scale ?? 1,
    scale: clip.scale ?? 1,
    rotation: clip.rotation ?? 0,
    anchorPointX: clip.anchorPointX ?? 0.5,
    anchorPointY: clip.anchorPointY ?? 0.5,
    opacity: clip.opacity ?? 1,
    blur: clip.blur ?? 0,
    brightness: clip.brightness ?? 100,
    contrast: clip.contrast ?? 100,
    saturation: clip.saturation ?? 100,
    exposure: clip.exposure ?? 0,
    sharpen: clip.sharpen ?? 0,
    sepia: clip.sepia ?? 0,
    grayscale: clip.grayscale ?? 0,
    hueRotate: clip.hueRotate ?? 0,
    invert: clip.invert ?? 0,
    maskPositionX: clip.maskPositionX ?? 0.5,
    maskPositionY: clip.maskPositionY ?? 0.5,
    maskScale: clip.maskScale ?? 1,
    maskRotation: clip.maskRotation ?? 0,
    maskFeather: clip.maskFeather ?? 0,
    maskExpansion: clip.maskExpansion ?? 0,
    maskWidth: clip.maskWidth ?? (clip.maskType === "half" ? 100 : 60),
    maskHeight: clip.maskHeight ?? (clip.maskType === "half" ? 50 : 60),
    maskRoundness: clip.maskRoundness ?? 15,
  };

  if (!clip.keyframes || clip.keyframes.length === 0) {
    return baseProps;
  }

  let kfs = sortedKeyframesCache.get(clip.keyframes);
  if (!kfs) {
    kfs = [...clip.keyframes].sort((a, b) => a.timeOffset - b.timeOffset);
    sortedKeyframesCache.set(clip.keyframes, kfs);
  }

  let propMap = filteredKeyframesCache.get(kfs);
  if (!propMap) {
    propMap = new Map();
    filteredKeyframesCache.set(kfs, propMap);
  }

  // We isolate keyframes by their provided animated property explicitly, allowing non-destructive engine design
  const getInterpolatedValue = (
    propName: keyof typeof baseProps,
    clipValue: number,
    fallbackDefault: number
  ) => {
    // Determine active keyframes per property specifically
    let activeKfs = propMap!.get(propName);
    if (!activeKfs) {
      activeKfs = kfs!.filter(k => k.properties[propName] !== undefined);
      propMap!.set(propName, activeKfs);
    }
    
    if (activeKfs.length === 0) {
      return clipValue;
    }

    if (timeInClip <= activeKfs[0].timeOffset) {
      const firstVal = activeKfs[0].properties[propName];
      return firstVal ?? clipValue;
    }
    
    if (timeInClip >= activeKfs[activeKfs.length - 1].timeOffset) {
      const lastVal = activeKfs[activeKfs.length - 1].properties[propName];
      return lastVal ?? clipValue;
    }

    // 1. Find previous keyframe
    // 2. Find next keyframe
    for (let i = 0; i < activeKfs.length - 1; i++) {
      const startKf = activeKfs[i];
      const endKf = activeKfs[i + 1];

      if (timeInClip >= startKf.timeOffset && timeInClip <= endKf.timeOffset) {
        const range = endKf.timeOffset - startKf.timeOffset;
        // 3. Calculate normalized progress
        let progress = range === 0 ? 0 : (timeInClip - startKf.timeOffset) / range;

        // 4. Apply interpolation curve (Easing)
        if (startKf.customEasePoints) {
          const [x1, y1, x2, y2] = startKf.customEasePoints;
          progress = solveCubicBezier(x1, y1, x2, y2, progress);
        } else {
          switch (startKf.curve) {
            case "easeIn": 
              progress = progress * progress; 
              break;
            case "easeOut": 
              progress = progress * (2 - progress); 
              break;
            case "easeInOut": 
              progress = progress < 0.5 ? 2 * progress * progress : -1 + (4 - 2 * progress) * progress; 
              break;
            case "hold": 
              progress = 0; 
              break;
            case "linear": 
            default: 
              break;
          }
        }

        const startRaw = startKf.properties[propName];
        const endRaw = endKf.properties[propName];

        const s = startRaw ?? clipValue;
        const e = endRaw ?? clipValue;
        
        // 5. Generate final value
        return s + (e - s) * progress;
      }
    }

    return clipValue;
  };

  // We generate final frame values for ALL properties safely through the core algorithm
  return {
    volume: Math.max(0, Math.min(100, getInterpolatedValue("volume", baseProps.volume, 100))),
    translateX: getInterpolatedValue("translateX", baseProps.translateX, 0),
    translateY: getInterpolatedValue("translateY", baseProps.translateY, 0),
    scaleX: getInterpolatedValue("scaleX", baseProps.scaleX, 1),
    scaleY: getInterpolatedValue("scaleY", baseProps.scaleY, 1),
    scale: Math.max(0.01, getInterpolatedValue("scale", baseProps.scale, 1)),
    rotation: getInterpolatedValue("rotation", baseProps.rotation, 0),
    anchorPointX: getInterpolatedValue("anchorPointX", baseProps.anchorPointX, 0.5),
    anchorPointY: getInterpolatedValue("anchorPointY", baseProps.anchorPointY, 0.5),
    opacity: Math.max(0, Math.min(1, getInterpolatedValue("opacity", baseProps.opacity, 1))),
    blur: Math.max(0, getInterpolatedValue("blur", baseProps.blur, 0)),
    brightness: getInterpolatedValue("brightness", baseProps.brightness, 100),
    contrast: getInterpolatedValue("contrast", baseProps.contrast, 100),
    saturation: getInterpolatedValue("saturation", baseProps.saturation, 100),
    exposure: getInterpolatedValue("exposure", baseProps.exposure, 0),
    sharpen: getInterpolatedValue("sharpen", baseProps.sharpen, 0),
    sepia: Math.max(0, Math.min(100, getInterpolatedValue("sepia", baseProps.sepia, 0))),
    grayscale: Math.max(0, Math.min(100, getInterpolatedValue("grayscale", baseProps.grayscale, 0))),
    hueRotate: getInterpolatedValue("hueRotate", baseProps.hueRotate, 0),
    invert: Math.max(0, Math.min(100, getInterpolatedValue("invert", baseProps.invert, 0))),
    maskPositionX: getInterpolatedValue("maskPositionX", baseProps.maskPositionX, 0.5),
    maskPositionY: getInterpolatedValue("maskPositionY", baseProps.maskPositionY, 0.5),
    maskScale: getInterpolatedValue("maskScale", baseProps.maskScale, 1),
    maskRotation: getInterpolatedValue("maskRotation", baseProps.maskRotation, 0),
    maskFeather: getInterpolatedValue("maskFeather", baseProps.maskFeather, 0),
    maskExpansion: getInterpolatedValue("maskExpansion", baseProps.maskExpansion, 0),
    maskWidth: getInterpolatedValue("maskWidth", baseProps.maskWidth, 60),
    maskHeight: getInterpolatedValue("maskHeight", baseProps.maskHeight, 60),
    maskRoundness: getInterpolatedValue("maskRoundness", baseProps.maskRoundness, 15),
  };
}
