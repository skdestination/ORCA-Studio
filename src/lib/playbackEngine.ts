export class PlaybackEngine {
  private animationFrameRef: number | null = null;
  private lastTime: number | null = null;
  public isPlaying = false;
  private speed = 1.0;
  public currentTimeRef: { current: number } = { current: 0 };
  
  // Store dependencies as a config object to keep it simple
  private deps: {
    isPlayingRef: { current: boolean },
    isRecordingRef: { current: boolean },
    previewEndTargetTimeRef: { current: number | null },
    setIsPlaying: (playing: boolean) => void,
    timelineScrollRef: { current: HTMLDivElement | null },
    pixelsPerSecond: number
  } = {
    isPlayingRef: { current: false },
    isRecordingRef: { current: false },
    previewEndTargetTimeRef: { current: null },
    setIsPlaying: () => {},
    timelineScrollRef: { current: null },
    pixelsPerSecond: 100.0
  };

  constructor(
    private onTimeUpdate: (time: number) => void,
    private onPlayStateChange: (isPlaying: boolean) => void
  ) {}

  setDependencies(deps: Partial<typeof this.deps>) {
    this.deps = { ...this.deps, ...deps };
  }

  play() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.lastTime = null;
    this.animationFrameRef = requestAnimationFrame(this.playLoop);
    this.onPlayStateChange(true);
  }

  pause() {
    if (!this.isPlaying) return;
    this.isPlaying = false;
    if (this.animationFrameRef) cancelAnimationFrame(this.animationFrameRef);
    // Sync React state on pause
    this.onTimeUpdate(this.currentTimeRef.current);
    this.onPlayStateChange(false);
  }

  private playLoop = (time: number) => {
    if (!this.isPlaying) return;
    if (this.lastTime === null) this.lastTime = time;
    const deltaTime = (time - this.lastTime) / 1000;
    this.lastTime = time;

    if (this.deps.isPlayingRef.current || this.deps.isRecordingRef.current) {
        this.currentTimeRef.current += deltaTime * this.speed;

        // Handle previewEndTargetTime logic
        if (this.deps.previewEndTargetTimeRef.current !== null && this.currentTimeRef.current >= this.deps.previewEndTargetTimeRef.current) {
            this.currentTimeRef.current = this.deps.previewEndTargetTimeRef.current;
            this.deps.previewEndTargetTimeRef.current = null;
            this.pause();
            this.deps.setIsPlaying(false);
        }

        // Directly update DOM to avoid continuous React re-renders
        if (this.deps.timelineScrollRef.current) {
            this.deps.timelineScrollRef.current.scrollLeft = Math.max(0, this.currentTimeRef.current * this.deps.pixelsPerSecond);
        }

        const timeEl = document.getElementById("playback-current-time");
        if (timeEl) {
            const s = Math.max(0, this.currentTimeRef.current);
            const mins = Math.floor((s % 3600) / 60);
            const secs = Math.floor(s % 60);
            timeEl.textContent = `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
        }
    }
    
    this.animationFrameRef = requestAnimationFrame(this.playLoop);
  };
}
