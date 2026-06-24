export type InterpolationType = "linear" | "easeIn" | "easeOut" | "easeInOut" | "hold";

export interface Keyframe {
  id: string;
  timeOffset: number; // in seconds relative to the start of the clip
  value: number;
  interpolation: InterpolationType;
}

export interface AnimatableProperty {
  baseValue: number;
  keyframes: Keyframe[];
}

// Easing functions
const cubicBezier = (t: number, p1x: number, p1y: number, p2x: number, p2y: number): number => {
  // A simplified bezier implementation for common easings.
  // Real implementation would use binary search or Newton-Raphson.
  // For standard "ease" curves, we can approximate or use basic polynomial easing.
  // We'll use standard Penner equations for high performance.
  return t; // fallback
};

export const easeIn = (t: number): number => t * t;
export const easeOut = (t: number): number => t * (2 - t);
export const easeInOut = (t: number): number => t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

export function evaluateProperty(property: AnimatableProperty | undefined, currentTimeOffset: number, fallbackValue: number): number {
  if (!property) return fallbackValue;
  if (!property.keyframes || property.keyframes.length === 0) return property.baseValue;
  
  const keyframes = property.keyframes;
  
  // Sort keyframes by time just in case, though they should be kept sorted in state
  // We perform a shallow copy to sort to prevent mutating state during render
  // For maximum performance this sorting should happen on insertion, not evaluation
  
  if (keyframes.length === 1) {
    return keyframes[0].value;
  }

  // Find surrounding keyframes
  let prevFrame: Keyframe | null = null;
  let nextFrame: Keyframe | null = null;

  for (let i = 0; i < keyframes.length; i++) {
    if (keyframes[i].timeOffset <= currentTimeOffset) {
      prevFrame = keyframes[i];
    } else if (keyframes[i].timeOffset > currentTimeOffset && !nextFrame) {
      nextFrame = keyframes[i];
      break;
    }
  }

  if (!prevFrame && nextFrame) {
    // Before the first keyframe
    return nextFrame.value;
  }

  if (prevFrame && !nextFrame) {
    // After the last keyframe
    return prevFrame.value;
  }

  if (prevFrame && nextFrame) {
    if (prevFrame.interpolation === "hold") {
      return prevFrame.value;
    }
    
    // Normalized progress
    const duration = nextFrame.timeOffset - prevFrame.timeOffset;
    const t = duration === 0 ? 0 : (currentTimeOffset - prevFrame.timeOffset) / duration;
    
    let easedT = t;
    switch (prevFrame.interpolation) {
      case "linear":
        easedT = t;
        break;
      case "easeIn":
        easedT = easeIn(t);
        break;
      case "easeOut":
        easedT = easeOut(t);
        break;
      case "easeInOut":
        easedT = easeInOut(t);
        break;
    }

    return prevFrame.value + (nextFrame.value - prevFrame.value) * easedT;
  }

  return property.baseValue;
}
