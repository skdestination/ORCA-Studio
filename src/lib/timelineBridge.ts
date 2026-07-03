import { registerPlugin, Capacitor } from "@capacitor/core";
import { Clip, Layer, Project } from "../types";

export interface TimelineEnginePluginType {
  initTimeline(options: { project: string }): Promise<{ success: boolean }>;
  getTimelineState(): Promise<any>;
  createClip(options: { clip: any }): Promise<{ success: boolean }>;
  deleteClip(options: { clipIds: string[] }): Promise<{ success: boolean }>;
  splitClip(options: { clipId: string; splitTime: number; generatedId: string }): Promise<{ success: boolean }>;
  moveClip(options: { clipIds: string[]; deltaSeconds: number; targetLayerId: string; fallbackLayerId: string }): Promise<{ success: boolean }>;
  trimClip(options: { clipId: string; side: "left" | "right"; deltaSeconds: number; snappingEnabled: boolean; currentTime: number }): Promise<{ success: boolean }>;
  rippleDelete(options: { clipId: string }): Promise<{ success: boolean }>;
  updateClipSpeed(options: { clipId: string; speed: number }): Promise<{ success: boolean }>;
  snapDrag(options: { proposedSeconds: number; clipDuration: number; excludeClipIds: string[]; currentTime: number }): Promise<{ snappedSeconds: number }>;
  resolvePaste(options: { layerId: string; time: number; duration: number }): Promise<{ targetTime: number }>;
  snapPlayhead(options: { time: number }): Promise<{ snappedTime: number }>;
  setZoom(options: { zoom: number }): Promise<void>;
  scrollTimeline(options: { scroll: number }): Promise<void>;
  seek(options: { time: number }): Promise<void>;
  undo(): Promise<{ canUndo: boolean; canRedo: boolean }>;
  redo(): Promise<{ canUndo: boolean; canRedo: boolean }>;
  clearTimeline(): Promise<void>;
  timeToPixel(options: { time: number }): Promise<{ pixel: number }>;
  pixelToTime(options: { pixel: number }): Promise<{ time: number }>;
}

const NativeTimelineEngine = registerPlugin<TimelineEnginePluginType>("TimelineEngine");

// --- WEB COMPATIBILITY FALLBACK ENGINE (Matches Kotlin logic exactly) ---

class WebTimelineEngineFallback {
  private clips = new Map<string, Clip>();
  private layers: Layer[] = [];
  private selectedClipIds = new Set<string>();
  private currentTime = 0;
  private zoomLevel = 1.0;
  private scrollLeft = 0;
  private pixelsPerSecond = 100.0;

  // Simple Undo/Redo stacks for Web Fallback
  private undoStack: Array<string> = [];
  private redoStack: Array<string> = [];

  private saveStateToUndo() {
    this.undoStack.push(JSON.stringify(this.getStateSnapshot()));
    this.redoStack = []; // Clear redo
    if (this.undoStack.length > 50) {
      this.undoStack.shift();
    }
  }

  private getStateSnapshot() {
    return {
      clips: Array.from(this.clips.values()),
      layers: [...this.layers],
      selectedClipIds: Array.from(this.selectedClipIds),
      currentTime: this.currentTime,
      zoomLevel: this.zoomLevel,
      scrollLeft: this.scrollLeft
    };
  }

  init(project: Partial<Project> & { currentTime?: number; zoomLevel?: number }) {
    this.clips.clear();
    (project.clips || []).forEach(c => this.clips.set(c.id, { ...c }));
    this.layers = (project.layers || []).map(l => ({ ...l }));
    this.selectedClipIds.clear();
    this.currentTime = project.currentTime || 0;
    this.zoomLevel = project.zoomLevel || 1.0;
    this.undoStack = [];
    this.redoStack = [];
  }

  getState() {
    return this.getStateSnapshot();
  }

  createClip(clip: Clip) {
    this.saveStateToUndo();
    this.clips.set(clip.id, { ...clip });
    return { success: true };
  }

  deleteClip(clipIds: string[]) {
    this.saveStateToUndo();
    clipIds.forEach(id => {
      this.clips.delete(id);
      this.selectedClipIds.delete(id);
    });
    return { success: true };
  }

  splitClip(clipId: string, splitTime: number, generatedId: string) {
    const clip = this.clips.get(clipId);
    if (!clip || splitTime <= clip.leftSeconds || splitTime >= clip.leftSeconds + clip.durationSeconds) {
      return { success: false };
    }
    this.saveStateToUndo();

    const originalDuration = clip.durationSeconds;
    const leftDuration = splitTime - clip.leftSeconds;
    const rightDuration = originalDuration - leftDuration;

    // Left Clip updates
    clip.durationSeconds = leftDuration;

    // New Right Clip
    const speed = clip.speed || 1.0;
    const newTrimStart = clip.trimStartSeconds + leftDuration * speed;

    const newClip: Clip = {
      ...clip,
      id: generatedId,
      name: clip.name ? `${clip.name} (Split)` : "Split Clip",
      leftSeconds: splitTime,
      durationSeconds: rightDuration,
      trimStartSeconds: newTrimStart
    };

    this.clips.set(newClip.id, newClip);
    return { success: true };
  }

  moveClip(clipIds: string[], deltaSeconds: number, targetLayerId: string, fallbackLayerId: string) {
    if (clipIds.length === 0) return { success: false };
    this.saveStateToUndo();

    if (clipIds.length === 1) {
      const clipId = clipIds[0];
      const clip = this.clips.get(clipId);
      if (!clip) return { success: false };

      const potentialLeft = Math.max(0, clip.leftSeconds + deltaSeconds);

      // Check overlap on target layer
      const hasOverlapTarget = Array.from(this.clips.values()).some(other =>
        other.id !== clipId &&
        other.layerId === targetLayerId &&
        potentialLeft < other.leftSeconds + other.durationSeconds &&
        potentialLeft + clip.durationSeconds > other.leftSeconds
      );

      if (!hasOverlapTarget) {
        clip.leftSeconds = potentialLeft;
        clip.layerId = targetLayerId;
      } else {
        // Check overlap on fallback layer
        const hasOverlapFallback = Array.from(this.clips.values()).some(other =>
          other.id !== clipId &&
          other.layerId === fallbackLayerId &&
          potentialLeft < other.leftSeconds + other.durationSeconds &&
          potentialLeft + clip.durationSeconds > other.leftSeconds
        );
        if (!hasOverlapFallback) {
          clip.leftSeconds = potentialLeft;
          clip.layerId = fallbackLayerId;
        }
      }
    } else {
      // Multi drag calculations
      const pivotClip = this.clips.get(clipIds[0]);
      if (!pivotClip) return { success: false };

      const sortedLayers = [...this.layers].sort((a, b) => b.order - a.order);
      const originalPivotLayerIndex = sortedLayers.findIndex(l => l.id === pivotClip.layerId);
      const targetPivotLayerIndex = sortedLayers.findIndex(l => l.id === targetLayerId);
      const layerOffset = (originalPivotLayerIndex !== -1 && targetPivotLayerIndex !== -1)
        ? (targetPivotLayerIndex - originalPivotLayerIndex)
        : 0;

      // Overlap checks
      const wouldOverlap = Array.from(this.clips.values()).some(c => {
        if (clipIds.includes(c.id)) return false;

        return clipIds.some(selId => {
          const selClip = this.clips.get(selId);
          if (!selClip) return false;

          const proposedLeft = Math.max(0, selClip.leftSeconds + deltaSeconds);
          const initLayerIndex = sortedLayers.findIndex(l => l.id === selClip.layerId);
          let proposedLayerId = selClip.layerId;
          if (initLayerIndex !== -1 && layerOffset !== 0) {
            const targetIndex = Math.max(0, Math.min(sortedLayers.length - 1, initLayerIndex + layerOffset));
            proposedLayerId = sortedLayers[targetIndex].id;
          }

          return (
            c.layerId === proposedLayerId &&
            proposedLeft < c.leftSeconds + c.durationSeconds &&
            proposedLeft + selClip.durationSeconds > c.leftSeconds
          );
        });
      });

      if (!wouldOverlap) {
        clipIds.forEach(id => {
          const c = this.clips.get(id);
          if (!c) return;

          c.leftSeconds = Math.max(0, c.leftSeconds + deltaSeconds);
          const initLayerIndex = sortedLayers.findIndex(l => l.id === c.layerId);
          if (initLayerIndex !== -1 && layerOffset !== 0) {
            const targetIndex = Math.max(0, Math.min(sortedLayers.length - 1, initLayerIndex + layerOffset));
            c.layerId = sortedLayers[targetIndex].id;
          }
        });
      }
    }

    return { success: true };
  }

  trimClip(clipId: string, side: "left" | "right", deltaSeconds: number, snappingEnabled: boolean, currentTime: number) {
    const clip = this.clips.get(clipId);
    if (!clip) return { success: false };
    this.saveStateToUndo();

    const initialLeft = clip.leftSeconds;
    const initialDuration = clip.durationSeconds;
    const initialTrimStart = clip.trimStartSeconds;
    const speed = clip.speed || 1.0;

    if (side === "left") {
      let newLeft = Math.max(0, initialLeft + deltaSeconds);
      if (snappingEnabled) {
        newLeft = this.snapValue(newLeft, clipId, currentTime);
      }
      const change = newLeft - initialLeft;

      if (clip.type === "image" || clip.type === "text") {
        const newDuration = Math.max(0.5, initialDuration - change);
        if (newDuration >= 0.5) {
          const hasOverlap = Array.from(this.clips.values()).some(other =>
            other.id !== clipId &&
            other.layerId === clip.layerId &&
            newLeft < other.leftSeconds + other.durationSeconds &&
            newLeft + newDuration > other.leftSeconds
          );
          if (!hasOverlap) {
            clip.leftSeconds = newLeft;
            clip.durationSeconds = newDuration;
          }
        }
      } else {
        let newTrimStart = initialTrimStart + change * speed;
        let newDuration = initialDuration - change;
        let finalLeft = newLeft;

        if (newTrimStart < 0) {
          newTrimStart = 0;
          finalLeft = initialLeft - initialTrimStart / speed;
          newDuration = initialDuration + initialTrimStart / speed;
        }

        if (newDuration >= 0.5) {
          const hasOverlap = Array.from(this.clips.values()).some(other =>
            other.id !== clipId &&
            other.layerId === clip.layerId &&
            Math.max(0, finalLeft) < other.leftSeconds + other.durationSeconds &&
            Math.max(0, finalLeft) + newDuration > other.leftSeconds
          );
          if (!hasOverlap) {
            clip.leftSeconds = Math.max(0, finalLeft);
            clip.trimStartSeconds = newTrimStart;
            clip.durationSeconds = newDuration;
          }
        }
      }
    } else {
      // Right trim
      const maxAvailable = clip.originalDurationSeconds || Number.MAX_VALUE;
      let newDuration = Math.max(0.5, initialDuration + deltaSeconds);

      if (clip.type === "image" || clip.type === "text") {
        if (snappingEnabled) {
          const snappedRight = this.snapValue(initialLeft + newDuration, clipId, currentTime);
          newDuration = Math.max(0.5, snappedRight - initialLeft);
        }
        const hasOverlap = Array.from(this.clips.values()).some(other =>
          other.id !== clipId &&
          other.layerId === clip.layerId &&
          initialLeft < other.leftSeconds + other.durationSeconds &&
          initialLeft + newDuration > other.leftSeconds
        );
        if (!hasOverlap) {
          clip.durationSeconds = newDuration;
        }
      } else {
        if (initialTrimStart + newDuration * speed > maxAvailable) {
          newDuration = (maxAvailable - initialTrimStart) / speed;
        }
        if (snappingEnabled) {
          const snappedRight = this.snapValue(initialLeft + newDuration, clipId, currentTime);
          newDuration = Math.max(0.5, snappedRight - initialLeft);
          if (initialTrimStart + newDuration * speed > maxAvailable) {
            newDuration = (maxAvailable - initialTrimStart) / speed;
          }
        }
        const hasOverlap = Array.from(this.clips.values()).some(other =>
          other.id !== clipId &&
          other.layerId === clip.layerId &&
          initialLeft < other.leftSeconds + other.durationSeconds &&
          initialLeft + newDuration > other.leftSeconds
        );
        if (!hasOverlap) {
          clip.durationSeconds = newDuration;
        }
      }
    }

    return { success: true };
  }

  updateClipSpeed(clipId: string, speed: number) {
    const clip = this.clips.get(clipId);
    if (!clip) return { success: false };
    this.saveStateToUndo();

    const oldSpeed = clip.speed || 1;
    const originalDur = clip.durationSeconds * oldSpeed;
    const newDuration = originalDur / speed;
    const delta = newDuration - clip.durationSeconds;

    clip.speed = speed;
    clip.durationSeconds = newDuration;
    clip.opticalFlow = undefined;

    // Ripple shift subsequent clips
    for (const other of this.clips.values()) {
      if (other.layerId === clip.layerId && other.leftSeconds >= clip.leftSeconds + clip.durationSeconds - delta) {
        other.leftSeconds += delta;
      }
    }

    return { success: true };
  }

  undo() {
    if (this.undoStack.length > 0) {
      const popped = this.undoStack.pop()!;
      this.redoStack.push(JSON.stringify(this.getStateSnapshot()));
      
      const parsed = JSON.parse(popped);
      this.clips.clear();
      parsed.clips.forEach((c: Clip) => this.clips.set(c.id, c));
      this.layers = parsed.layers;
      this.selectedClipIds = new Set(parsed.selectedClipIds);
      this.currentTime = parsed.currentTime;
      this.zoomLevel = parsed.zoomLevel;
      this.scrollLeft = parsed.scrollLeft;
    }
    return { canUndo: this.undoStack.length > 0, canRedo: this.redoStack.length > 0 };
  }

  redo() {
    if (this.redoStack.length > 0) {
      const popped = this.redoStack.pop()!;
      this.undoStack.push(JSON.stringify(this.getStateSnapshot()));

      const parsed = JSON.parse(popped);
      this.clips.clear();
      parsed.clips.forEach((c: Clip) => this.clips.set(c.id, c));
      this.layers = parsed.layers;
      this.selectedClipIds = new Set(parsed.selectedClipIds);
      this.currentTime = parsed.currentTime;
      this.zoomLevel = parsed.zoomLevel;
      this.scrollLeft = parsed.scrollLeft;
    }
    return { canUndo: this.undoStack.length > 0, canRedo: this.redoStack.length > 0 };
  }

  snapDrag(proposedSeconds: number, clipDuration: number, excludeClipIds: string[], currentTime: number): number {
    const threshold = 15.0 / this.pixelsPerSecond;
    let minDistance = threshold;
    let snappedValue = proposedSeconds;

    const snapPoints: number[] = [0, currentTime];
    for (const c of this.clips.values()) {
      if (!excludeClipIds.includes(c.id)) {
        snapPoints.push(c.leftSeconds);
        snapPoints.push(c.leftSeconds + c.durationSeconds);
      }
    }

    for (const sp of snapPoints) {
      // Snap Left
      const distLeft = Math.abs(sp - proposedSeconds);
      if (distLeft < minDistance) {
        minDistance = distLeft;
        snappedValue = sp;
      }
      // Snap Right
      const newRight = proposedSeconds + clipDuration;
      const distRight = Math.abs(sp - newRight);
      if (distRight < minDistance) {
        minDistance = distRight;
        snappedValue = sp - clipDuration;
      }
    }

    return snappedValue;
  }

  resolvePaste(layerId: string, time: number, duration: number) {
    const layerClips = Array.from(this.clips.values()).filter(c => c.layerId === layerId);
    let targetTime = time;

    const overlappingClip = layerClips.find(
      (c) =>
        targetTime >= c.leftSeconds &&
        targetTime < c.leftSeconds + c.durationSeconds,
    );
    
    if (overlappingClip) {
      const midPoint =
        overlappingClip.leftSeconds + overlappingClip.durationSeconds / 2;
      if (targetTime < midPoint) {
        targetTime = overlappingClip.leftSeconds - duration;
        if (targetTime < 0) targetTime = 0;
      } else {
        targetTime =
          overlappingClip.leftSeconds + overlappingClip.durationSeconds;
      }
    }
    return targetTime;
  }

  snapPlayhead(time: number) {
    const threshold = 12.0 / this.pixelsPerSecond;
    let minDistance = threshold;
    let snappedTime = time;

    const snapPoints = [0];
    for (const c of this.clips.values()) {
      snapPoints.push(c.leftSeconds);
      snapPoints.push(c.leftSeconds + c.durationSeconds);
    }
    
    for (const sp of snapPoints) {
      const dist = Math.abs(sp - time);
      if (dist < minDistance) {
        minDistance = dist;
        snappedTime = sp;
      }
    }
    return snappedTime;
  }

  setZoom(zoom: number) { this.zoomLevel = zoom; }
  scrollTimeline(scroll: number) { this.scrollLeft = scroll; }
  seek(time: number) { this.currentTime = time; }

  snapValue(time: number, clipId: string, currentTime: number): number {
    return time;
  }
  
  rippleDelete(clipId: string): { success: boolean } {
    this.saveStateToUndo();
    this.clips.delete(clipId);
    return { success: true };
  }
}

const webFallback = new WebTimelineEngineFallback();

// --- CENTRALIZED TIMELINE BRIDGE API (The only object React imports) ---

export class TimelineBridge {
  static isNative = Capacitor.isNativePlatform();

  static async initTimeline(project: Partial<Project> & { currentTime?: number; zoomLevel?: number }): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.initTimeline({ project: JSON.stringify(project) });
    } else {
      webFallback.init(project);
      return { success: true };
    }
  }

  static async getTimelineState(): Promise<{
    clips: Clip[];
    layers: Layer[];
    selectedClipIds: string[];
    currentTime: number;
    zoomLevel: number;
    scrollLeft: number;
  }> {
    if (this.isNative) {
      const res = await NativeTimelineEngine.getTimelineState();
      return {
        clips: res.clips || [],
        layers: res.layers || [],
        selectedClipIds: res.selectedClipIds || [],
        currentTime: res.currentTime || 0,
        zoomLevel: res.zoomLevel || 1.0,
        scrollLeft: res.scrollLeft || 0
      };
    } else {
      return webFallback.getState();
    }
  }

  static async createClip(clip: Clip): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.createClip({ clip });
    } else {
      return webFallback.createClip(clip);
    }
  }

  static async deleteClip(clipIds: string[]): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.deleteClip({ clipIds });
    } else {
      return webFallback.deleteClip(clipIds);
    }
  }

  static async splitClip(clipId: string, splitTime: number, generatedId: string): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.splitClip({ clipId, splitTime, generatedId });
    } else {
      return webFallback.splitClip(clipId, splitTime, generatedId);
    }
  }

  static async moveClip(
    clipIds: string[],
    deltaSeconds: number,
    targetLayerId: string,
    fallbackLayerId: string
  ): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.moveClip({ clipIds, deltaSeconds, targetLayerId, fallbackLayerId });
    } else {
      return webFallback.moveClip(clipIds, deltaSeconds, targetLayerId, fallbackLayerId);
    }
  }

  static async trimClip(
    clipId: string,
    side: "left" | "right",
    deltaSeconds: number,
    snappingEnabled: boolean,
    currentTime: number
  ): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.trimClip({ clipId, side, deltaSeconds, snappingEnabled, currentTime });
    } else {
      return webFallback.trimClip(clipId, side, deltaSeconds, snappingEnabled, currentTime);
    }
  }

  static async rippleDelete(clipId: string): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.rippleDelete({ clipId });
    } else {
      return webFallback.rippleDelete(clipId);
    }
  }

  static async updateClipSpeed(clipId: string, speed: number): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.updateClipSpeed({ clipId, speed });
    } else {
      return webFallback.updateClipSpeed(clipId, speed);
    }
  }

  static async snapDrag(proposedSeconds: number, clipDuration: number, excludeClipIds: string[], currentTime: number): Promise<number> {
    if (this.isNative) {
      const res = await NativeTimelineEngine.snapDrag({ proposedSeconds, clipDuration, excludeClipIds, currentTime });
      return res.snappedSeconds;
    } else {
      return webFallback.snapDrag(proposedSeconds, clipDuration, excludeClipIds, currentTime);
    }
  }

  static async resolvePaste(layerId: string, time: number, duration: number): Promise<number> {
    if (this.isNative) {
      const res = await NativeTimelineEngine.resolvePaste({ layerId, time, duration });
      return res.targetTime;
    } else {
      return webFallback.resolvePaste(layerId, time, duration);
    }
  }

  static async snapPlayhead(time: number): Promise<number> {
    if (this.isNative) {
      const res = await NativeTimelineEngine.snapPlayhead({ time });
      return res.snappedTime;
    } else {
      return webFallback.snapPlayhead(time);
    }
  }

  static async setZoom(zoom: number): Promise<void> {
    if (this.isNative) {
      await NativeTimelineEngine.setZoom({ zoom });
    } else {
      webFallback.setZoom(zoom);
    }
  }

  static async scrollTimeline(scroll: number): Promise<void> {
    if (this.isNative) {
      await NativeTimelineEngine.scrollTimeline({ scroll });
    } else {
      webFallback.scrollTimeline(scroll);
    }
  }

  static async seek(time: number): Promise<void> {
    if (this.isNative) {
      await NativeTimelineEngine.seek({ time });
    } else {
      webFallback.seek(time);
    }
  }

  static async undo(): Promise<{ canUndo: boolean; canRedo: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.undo();
    } else {
      return webFallback.undo();
    }
  }

  static async redo(): Promise<{ canUndo: boolean; canRedo: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.redo();
    } else {
      return webFallback.redo();
    }
  }

  static async clearTimeline(): Promise<void> {
    if (this.isNative) {
      await NativeTimelineEngine.clearTimeline();
    }
  }

  // --- Core Conversions Exposed statically ---

  static timeToPixel(time: number, pixelsPerSecond: number): number {
    return time * pixelsPerSecond;
  }

  static pixelToTime(pixel: number, pixelsPerSecond: number): number {
    return pixel / pixelsPerSecond;
  }

  static frameToTime(frame: number, fps: number): number {
    return fps > 0 ? frame / fps : 0;
  }

  static timeToFrame(time: number, fps: number): number {
    return fps > 0 ? Math.round(time * fps) : 0;
  }

  static zoomToScale(zoom: number): number {
    return zoom;
  }
}
