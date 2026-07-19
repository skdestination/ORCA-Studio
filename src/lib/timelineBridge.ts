import { registerPlugin, Capacitor } from "@capacitor/core";
import { Clip, Layer, Project } from "../types";
import { Command, MoveClipCommand } from "./commands";

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

class WebTimelineEngineFallback {
  private clips = new Map<string, Clip>();
  private layers: Layer[] = [];
  private selectedClipIds = new Set<string>();
  private currentTime = 0;
  private zoomLevel = 1.0;
  private scrollLeft = 0;
  private pixelsPerSecond = 100.0;

  private undoStack: Array<Command> = [];
  private redoStack: Array<Command> = [];

  commitCommand(command: Command) {
    command.execute();
    this.undoStack.push(command);
    this.redoStack = []; 
    if (this.undoStack.length > 50) {
      this.undoStack.shift();
    }
  }

  undo() {
    if (this.undoStack.length > 0) {
      const command = this.undoStack.pop()!;
      command.undo();
      this.redoStack.push(command);
    }
    return { canUndo: this.undoStack.length > 0, canRedo: this.redoStack.length > 0 };
  }

  redo() {
    if (this.redoStack.length > 0) {
      const command = this.redoStack.pop()!;
      command.execute();
      this.undoStack.push(command);
    }
    return { canUndo: this.undoStack.length > 0, canRedo: this.redoStack.length > 0 };
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
    this.clips.set(clip.id, { ...clip });
    return { success: true };
  }

  moveClip(clipIds: string[], deltaSeconds: number, targetLayerId: string, fallbackLayerId: string) {
    const oldStates = clipIds.map(id => {
      const clip = this.clips.get(id)!;
      return { id, leftSeconds: clip.leftSeconds, layerId: clip.layerId, durationSeconds: clip.durationSeconds, trimStartSeconds: clip.trimStartSeconds, speed: clip.speed };
    });
    const newStates = clipIds.map(id => {
      const clip = this.clips.get(id)!;
      return { id, leftSeconds: clip.leftSeconds + deltaSeconds, layerId: targetLayerId, durationSeconds: clip.durationSeconds, trimStartSeconds: clip.trimStartSeconds, speed: clip.speed };
    });
    
    this.commitCommand(new MoveClipCommand(this.clips, clipIds, oldStates, newStates));
    return { success: true };
  }

  deleteClip(clipIds: string[]) {
    clipIds.forEach(id => {
      this.clips.delete(id);
      this.selectedClipIds.delete(id);
    });
    return { success: true };
  }

  splitClip(clipId: string, splitTime: number, generatedId: string) {
    const clip = this.clips.get(clipId);
    if (!clip) return { success: false };
    const splitOffset = splitTime - clip.leftSeconds;
    if (splitOffset <= 0 || splitOffset >= clip.durationSeconds) {
      return { success: false };
    }
    const newClip: Clip = {
      ...clip,
      id: generatedId,
      leftSeconds: splitTime,
      durationSeconds: clip.durationSeconds - splitOffset,
      trimStartSeconds: clip.trimStartSeconds + splitOffset * (clip.speed || 1)
    };
    clip.durationSeconds = splitOffset;
    this.clips.set(newClip.id, newClip);
    return { success: true };
  }

  trimClip(clipId: string, side: "left" | "right", deltaSeconds: number, snappingEnabled: boolean, currentTime: number) {
    const clip = this.clips.get(clipId);
    if (!clip) return { success: false };
    if (side === "left") {
      const newLeft = clip.leftSeconds + deltaSeconds;
      const newDuration = clip.durationSeconds - deltaSeconds;
      if (newDuration > 0.1) {
        clip.leftSeconds = newLeft;
        clip.durationSeconds = newDuration;
        clip.trimStartSeconds += deltaSeconds * (clip.speed || 1);
      }
    } else {
      const newDuration = clip.durationSeconds + deltaSeconds;
      if (newDuration > 0.1) {
        clip.durationSeconds = newDuration;
      }
    }
    return { success: true };
  }

  updateClipSpeed(clipId: string, speed: number) {
    const clip = this.clips.get(clipId);
    if (clip) {
      clip.speed = speed;
    }
    return { success: true };
  }

  snapDrag(proposedSeconds: number, clipDuration: number, excludeClipIds: string[], currentTime: number) {
    if (Math.abs(proposedSeconds - currentTime) < 0.2) {
      return currentTime;
    }
    if (Math.abs(proposedSeconds) < 0.2) {
      return 0;
    }
    return proposedSeconds;
  }

  resolvePaste(layerId: string, time: number, duration: number) {
    return time;
  }

  snapPlayhead(time: number) {
    return time;
  }
}

export class TimelineBridge {
  private static isNative = Capacitor.isNativePlatform();
  private static webFallback = new WebTimelineEngineFallback();

  static async initTimeline(project: Partial<Project> & { currentTime?: number; zoomLevel?: number }): Promise<{ success: boolean }> {
    if (this.isNative) {
      return await NativeTimelineEngine.initTimeline({ project: JSON.stringify(project) });
    } else {
      this.webFallback.init(project);
      return { success: true };
    }
  }

  static async getTimelineState() {
    if (this.isNative) {
      return await NativeTimelineEngine.getTimelineState();
    } else {
      return this.webFallback.getState();
    }
  }

  static async undo() {
    if (this.isNative) {
        return await NativeTimelineEngine.undo();
    } else {
        return this.webFallback.undo();
    }
  }

  static async redo() {
    if (this.isNative) {
        return await NativeTimelineEngine.redo();
    } else {
        return this.webFallback.redo();
    }
  }
  
  static async moveClip(clipIds: string[], deltaSeconds: number, targetLayerId: string, fallbackLayerId: string) {
      if (this.isNative) {
          return await NativeTimelineEngine.moveClip({ clipIds, deltaSeconds, targetLayerId, fallbackLayerId });
      } else {
          return this.webFallback.moveClip(clipIds, deltaSeconds, targetLayerId, fallbackLayerId);
      }
  }

  static async deleteClip(clipIds: string[]) {
      if (this.isNative) {
          return await NativeTimelineEngine.deleteClip({ clipIds });
      } else {
          return this.webFallback.deleteClip(clipIds);
      }
  }

  static async splitClip(clipId: string, splitTime: number, generatedId: string) {
      if (this.isNative) {
          return await NativeTimelineEngine.splitClip({ clipId, splitTime, generatedId });
      } else {
          return this.webFallback.splitClip(clipId, splitTime, generatedId);
      }
  }

  static async resolvePaste(layerId: string, time: number, duration: number): Promise<number> {
      if (this.isNative) {
          const res = await NativeTimelineEngine.resolvePaste({ layerId, time, duration });
          return res.targetTime;
      } else {
          return this.webFallback.resolvePaste(layerId, time, duration);
      }
  }

  static async snapDrag(proposedSeconds: number, clipDuration: number, excludeClipIds: string[], currentTime: number): Promise<number> {
      if (this.isNative) {
          const res = await NativeTimelineEngine.snapDrag({ proposedSeconds, clipDuration, excludeClipIds, currentTime });
          return res.snappedSeconds;
      } else {
          return this.webFallback.snapDrag(proposedSeconds, clipDuration, excludeClipIds, currentTime);
      }
  }

  static async trimClip(clipId: string, side: "left" | "right", deltaSeconds: number, snappingEnabled: boolean, currentTime: number) {
      if (this.isNative) {
          return await NativeTimelineEngine.trimClip({ clipId, side, deltaSeconds, snappingEnabled, currentTime });
      } else {
          return this.webFallback.trimClip(clipId, side, deltaSeconds, snappingEnabled, currentTime);
      }
  }

  static async snapPlayhead(time: number): Promise<number> {
      if (this.isNative) {
          const res = await NativeTimelineEngine.snapPlayhead({ time });
          return res.snappedTime;
      } else {
          return this.webFallback.snapPlayhead(time);
      }
  }

  static async updateClipSpeed(clipId: string, speed: number) {
      if (this.isNative) {
          return await NativeTimelineEngine.updateClipSpeed({ clipId, speed });
      } else {
          return this.webFallback.updateClipSpeed(clipId, speed);
      }
  }
}

