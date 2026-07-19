import { Clip, Layer } from "../types";

export interface Command {
  execute(): void;
  undo(): void;
}

export interface ClipState {
  id: string;
  leftSeconds: number;
  layerId: string;
  durationSeconds: number;
  trimStartSeconds: number;
  speed: number;
}

export class MoveClipCommand implements Command {
  constructor(
    private clips: Map<string, Clip>,
    private clipIds: string[],
    private oldStates: ClipState[],
    private newStates: ClipState[]
  ) {}

  execute() {
    this.newStates.forEach(state => {
      const clip = this.clips.get(state.id);
      if (clip) {
        clip.leftSeconds = state.leftSeconds;
        clip.layerId = state.layerId;
      }
    });
  }

  undo() {
    this.oldStates.forEach(state => {
      const clip = this.clips.get(state.id);
      if (clip) {
        clip.leftSeconds = state.leftSeconds;
        clip.layerId = state.layerId;
      }
    });
  }
}

export class DeleteClipCommand implements Command {
  private deletedClips: Clip[];

  constructor(
    private clips: Map<string, Clip>,
    private selectedClipIds: Set<string>,
    private clipIds: string[]
  ) {
    this.deletedClips = clipIds.map(id => ({ ...this.clips.get(id)! }));
  }

  execute() {
    this.clipIds.forEach(id => {
      this.clips.delete(id);
      this.selectedClipIds.delete(id);
    });
  }

  undo() {
    this.deletedClips.forEach(clip => {
      this.clips.set(clip.id, clip);
    });
  }
}

export class AddClipCommand implements Command {
  constructor(
    private clips: Map<string, Clip>,
    private clip: Clip
  ) {}

  execute() {
    this.clips.set(this.clip.id, this.clip);
  }

  undo() {
    this.clips.delete(this.clip.id);
  }
}
