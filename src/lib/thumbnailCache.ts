const DB_NAME = "OrcaThumbnailsDB";
const STORE_NAME = "thumbnails";

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function getCachedThumbnail(key: string): Promise<string | null> {
  try {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, "readonly");
      const store = transaction.objectStore(STORE_NAME);
      const request = store.get(key);
      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error);
    });
  } catch (e) {
    console.error("IndexedDB get error:", e);
    return null;
  }
}

async function setCachedThumbnail(key: string, dataUrl: string): Promise<void> {
  try {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, "readwrite");
      const store = transaction.objectStore(STORE_NAME);
      const request = store.put(dataUrl, key);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  } catch (e) {
    console.error("IndexedDB set error:", e);
  }
}

// -------------------------------------------------------------
// LRU Cache for memory optimization
// -------------------------------------------------------------
class LRUCache<K, V> {
  private max: number;
  private cache: Map<K, V>;

  constructor(max = 200) {
    this.max = max;
    this.cache = new Map<K, V>();
  }

  get(key: K): V | undefined {
    const item = this.cache.get(key);
    if (item !== undefined) {
      this.cache.delete(key);
      this.cache.set(key, item);
    }
    return item;
  }

  set(key: K, value: V): void {
    if (this.cache.has(key)) {
      this.cache.delete(key);
    } else if (this.cache.size >= this.max) {
      const oldestKey = this.cache.keys().next().value;
      if (oldestKey !== undefined) {
        this.cache.delete(oldestKey);
      }
    }
    this.cache.set(key, value);
  }

  has(key: K): boolean {
    return this.cache.has(key);
  }

  clear() {
    this.cache.clear();
  }
}

// -------------------------------------------------------------
// Thumbnail Pyramid Specifications
// -------------------------------------------------------------
export interface PyramidLevel {
  level: number;
  width: number;
  height: number;
  quality: number;
}

export const PYRAMID_LEVELS: Record<number, PyramidLevel> = {
  0: { level: 0, width: 40, height: 22, quality: 0.20 },
  1: { level: 1, width: 80, height: 45, quality: 0.25 },
  2: { level: 2, width: 160, height: 90, quality: 0.35 },
  3: { level: 3, width: 320, height: 180, quality: 0.50 },
  4: { level: 4, width: 640, height: 360, quality: 0.65 },
};

export function getLevelForPixelsPerSecond(pps: number): number {
  if (pps < 30) return 0;
  if (pps < 100) return 1;
  if (pps < 250) return 2;
  if (pps < 600) return 3;
  return 4;
}

export function makeCacheKey(sourceUrl: string, level: number, timeOffset: number): string {
  return `${sourceUrl}#level=${level}&t=${timeOffset.toFixed(2)}`;
}

// -------------------------------------------------------------
// Interfaces
// -------------------------------------------------------------
export interface ThumbnailRequest {
  clipId: string;
  sourceUrl: string;
  timeOffset: number;
  level: number;
}

export interface ThumbnailProvider {
  getOrGenerate(req: ThumbnailRequest): Promise<string>;
  cancelForClip(clipId: string): void;
  updateViewport(
    scrollLeft: number,
    viewportWidth: number,
    pixelsPerSecond: number,
    clips: Array<{ id: string; leftSeconds: number; trimStartSeconds: number }>
  ): void;
  getSync(sourceUrl: string, level: number, timeOffset: number): string | null;
  subscribe(
    sourceUrl: string,
    level: number,
    timeOffset: number,
    callback: (dataUrl: string) => void
  ): () => void;
}

interface ThumbnailTask {
  clipId: string;
  sourceUrl: string;
  timeOffset: number;
  level: number;
  priority: number;
  cacheKey: string;
  resolve: (dataUrl: string) => void;
  reject: (err: any) => void;
}

interface PendingPromise {
  resolve: (dataUrl: string) => void;
  reject: (err: any) => void;
  unsubscribe: () => void;
}

// -------------------------------------------------------------
// Web-Based HTML5 Video Decoder Implementation
// -------------------------------------------------------------
export class WebThumbnailProvider implements ThumbnailProvider {
  private queue: ThumbnailTask[] = [];
  private queueMap: Map<string, Promise<string>> = new Map();
  private isProcessing = false;

  private canvas: HTMLCanvasElement | null = null;
  private ctx: CanvasRenderingContext2D | null = null;

  private memoryCache = new LRUCache<string, string>(200);
  private listeners: Map<string, Array<(dataUrl: string) => void>> = new Map();
  private pendingPromises: Map<string, PendingPromise[]> = new Map();

  // Viewport tracking variables for dynamic priority calculations
  private scrollLeft = 0;
  private viewportWidth = 1000;
  private pixelsPerSecond = 100;
  private clipPositions: Map<string, { id: string; leftSeconds: number; trimStartSeconds: number }> = new Map();

  // Background video elements pool
  private videoPool: Map<string, HTMLVideoElement> = new Map();

  constructor() {
    if (typeof window !== "undefined") {
      this.canvas = document.createElement("canvas");
      this.ctx = this.canvas.getContext("2d");
    }
  }

  private getVideoElement(sourceUrl: string): HTMLVideoElement {
    if (this.videoPool.has(sourceUrl)) {
      return this.videoPool.get(sourceUrl)!;
    }

    if (this.videoPool.size >= 3) {
      const oldestUrl = this.videoPool.keys().next().value;
      if (oldestUrl !== undefined) {
        const vid = this.videoPool.get(oldestUrl);
        if (vid) {
          vid.src = "";
          vid.load();
        }
        this.videoPool.delete(oldestUrl);
      }
    }

    const video = document.createElement("video");
    video.muted = true;
    video.playsInline = true;
    video.crossOrigin = "anonymous";
    video.src = sourceUrl;
    video.load();

    this.videoPool.set(sourceUrl, video);
    return video;
  }

  getSync(sourceUrl: string, level: number, timeOffset: number): string | null {
    const key = makeCacheKey(sourceUrl, level, timeOffset);
    return this.memoryCache.get(key) || null;
  }

  subscribe(
    sourceUrl: string,
    level: number,
    timeOffset: number,
    callback: (dataUrl: string) => void
  ): () => void {
    const cacheKey = makeCacheKey(sourceUrl, level, timeOffset);
    if (!this.listeners.has(cacheKey)) {
      this.listeners.set(cacheKey, []);
    }
    this.listeners.get(cacheKey)!.push(callback);
    return () => {
      const list = this.listeners.get(cacheKey);
      if (list) {
        this.listeners.set(cacheKey, list.filter(cb => cb !== callback));
      }
    };
  }

  private notify(cacheKey: string, dataUrl: string) {
    const list = this.listeners.get(cacheKey);
    if (list) {
      list.forEach(cb => cb(dataUrl));
    }
  }

  async getOrGenerate(req: ThumbnailRequest): Promise<string> {
    const targetLevel = req.level;
    const cacheKey = makeCacheKey(req.sourceUrl, targetLevel, req.timeOffset);

    // 1. Check Memory Cache
    const cachedMem = this.memoryCache.get(cacheKey);
    if (cachedMem) return cachedMem;

    // 2. Check IndexedDB Cache
    const cachedIDB = await getCachedThumbnail(cacheKey);
    if (cachedIDB) {
      this.memoryCache.set(cacheKey, cachedIDB);
      this.notify(cacheKey, cachedIDB);
      return cachedIDB;
    }

    // 3. Initiate Progressive Generation starting from Level 0 up to targetLevel
    this.progressiveGenerationChain(req, targetLevel);

    // 4. Return a Promise that resolves when targetLevel is available
    return new Promise((resolve, reject) => {
      const checkAndResolve = () => {
        const val = this.memoryCache.get(cacheKey);
        if (val) {
          resolve(val);
          return true;
        }
        return false;
      };

      if (checkAndResolve()) return;

      const unsubscribe = this.subscribe(req.sourceUrl, targetLevel, req.timeOffset, (url) => {
        if (checkAndResolve()) {
          unsubscribe();
        }
      });

      if (!this.pendingPromises.has(cacheKey)) {
        this.pendingPromises.set(cacheKey, []);
      }
      this.pendingPromises.get(cacheKey)!.push({ resolve, reject, unsubscribe });
    });
  }

  private async progressiveGenerationChain(req: ThumbnailRequest, targetLevel: number) {
    // Generate Level 0 immediately, then step up sequentially (L1 -> L2 -> L3 -> L4)
    for (let l = 0; l <= targetLevel; l++) {
      const lKey = makeCacheKey(req.sourceUrl, l, req.timeOffset);
      if (this.memoryCache.has(lKey)) continue;

      const lIDB = await getCachedThumbnail(lKey);
      if (lIDB) {
        this.memoryCache.set(lKey, lIDB);
        this.notify(lKey, lIDB);
        continue;
      }

      try {
        await this.queueGeneration(req.clipId, req.sourceUrl, req.timeOffset, l);
      } catch (err) {
        // Stop progressive chain if a step fails or is cancelled
        break;
      }
    }
  }

  private queueGeneration(clipId: string, sourceUrl: string, timeOffset: number, level: number): Promise<string> {
    const cacheKey = makeCacheKey(sourceUrl, level, timeOffset);

    if (this.queueMap.has(cacheKey)) {
      return this.queueMap.get(cacheKey)!;
    }

    const promise = new Promise<string>((resolve, reject) => {
      const priority = this.getTaskPriority(clipId, timeOffset, level);
      if (priority === -1) {
        reject(new Error("Clip is not visible/buffered"));
        return;
      }

      this.queue.push({
        clipId,
        sourceUrl,
        timeOffset,
        level,
        priority,
        cacheKey,
        resolve: (val) => {
          this.queueMap.delete(cacheKey);
          resolve(val);
        },
        reject: (err) => {
          this.queueMap.delete(cacheKey);
          reject(err);
        }
      });

      this.queue.sort((a, b) => b.priority - a.priority);
      this.triggerProcessing();
    });

    this.queueMap.set(cacheKey, promise);
    return promise;
  }

  cancelForClip(clipId: string) {
    this.queue = this.queue.filter(task => {
      if (task.clipId === clipId) {
        task.reject(new Error("Cancelled"));
        const pending = this.pendingPromises.get(task.cacheKey);
        if (pending) {
          pending.forEach(p => {
            p.unsubscribe();
            p.reject(new Error("Cancelled"));
          });
          this.pendingPromises.delete(task.cacheKey);
        }
        return false;
      }
      return true;
    });
  }

  updateViewport(
    scrollLeft: number,
    viewportWidth: number,
    pixelsPerSecond: number,
    clips: Array<{ id: string; leftSeconds: number; trimStartSeconds: number }>
  ) {
    this.scrollLeft = scrollLeft;
    this.viewportWidth = viewportWidth;
    this.pixelsPerSecond = pixelsPerSecond;

    this.clipPositions.clear();
    clips.forEach(c => {
      this.clipPositions.set(c.id, c);
    });

    // Cancel / filter out tasks that scroll out of both visible range and buffer
    this.queue = this.queue.filter(task => {
      const prio = this.getTaskPriority(task.clipId, task.timeOffset, task.level);
      if (prio === -1) {
        task.reject(new Error("Cancelled: Scrolled out of view"));
        const pending = this.pendingPromises.get(task.cacheKey);
        if (pending) {
          pending.forEach(p => {
            p.unsubscribe();
            p.reject(new Error("Cancelled: Scrolled out of view"));
          });
          this.pendingPromises.delete(task.cacheKey);
        }
        return false;
      }
      task.priority = prio;
      return true;
    });

    this.queue.sort((a, b) => b.priority - a.priority);
  }

  private getTaskPriority(clipId: string, timeOffset: number, level: number): number {
    const pos = this.clipPositions.get(clipId);
    if (!pos) return 0; // Default low priority if position not loaded

    const timelineTime = pos.leftSeconds + (timeOffset - pos.trimStartSeconds);
    const timelinePx = timelineTime * this.pixelsPerSecond;

    const bufferPx = 300; // Small buffer surrounding viewport
    const isVisible = timelinePx >= this.scrollLeft && timelinePx <= this.scrollLeft + this.viewportWidth;
    const isBuffered = timelinePx >= this.scrollLeft - bufferPx && timelinePx <= this.scrollLeft + this.viewportWidth + bufferPx;

    if (isVisible) {
      return level === 0 ? 3 : 2; // Level 0 visible tasks are absolute priority (3), higher levels visible are next (2)
    } else if (isBuffered) {
      return 1; // Buffer tasks get medium priority (1)
    } else {
      return -1; // Out of bounds tasks get cancelled (-1)
    }
  }

  private triggerProcessing() {
    if (this.isProcessing) return;
    this.processNext();
  }

  private async processNext() {
    if (this.queue.length === 0) {
      this.isProcessing = false;
      return;
    }

    this.isProcessing = true;
    const task = this.queue.shift()!;

    try {
      const dataUrl = await this.generateThumbnail(task.sourceUrl, task.timeOffset, task.level);
      this.memoryCache.set(task.cacheKey, dataUrl);
      await setCachedThumbnail(task.cacheKey, dataUrl);
      this.notify(task.cacheKey, dataUrl);

      const pending = this.pendingPromises.get(task.cacheKey);
      if (pending) {
        pending.forEach(p => {
          p.unsubscribe();
          p.resolve(dataUrl);
        });
        this.pendingPromises.delete(task.cacheKey);
      }

      task.resolve(dataUrl);
    } catch (err) {
      task.reject(err);
      const pending = this.pendingPromises.get(task.cacheKey);
      if (pending) {
        pending.forEach(p => {
          p.unsubscribe();
          p.reject(err);
        });
        this.pendingPromises.delete(task.cacheKey);
      }
    } finally {
      setTimeout(() => this.processNext(), 16); // yield execution to avoid browser stutter
    }
  }

  private ensureVideoReady(video: HTMLVideoElement): Promise<void> {
    if (video.readyState >= 1) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      let timeoutId: any = null;

      const onLoaded = () => {
        clearTimeout(timeoutId);
        video.removeEventListener("loadedmetadata", onLoaded);
        video.removeEventListener("error", onError);
        resolve();
      };

      const onError = () => {
        clearTimeout(timeoutId);
        video.removeEventListener("loadedmetadata", onLoaded);
        video.removeEventListener("error", onError);
        reject(video.error || new Error("Video load failed"));
      };

      video.addEventListener("loadedmetadata", onLoaded);
      video.addEventListener("error", onError);

      timeoutId = setTimeout(() => {
        video.removeEventListener("loadedmetadata", onLoaded);
        video.removeEventListener("error", onError);
        reject(new Error("Video metadata load timeout"));
      }, 10000);
    });
  }

  private generateThumbnail(sourceUrl: string, timeOffset: number, level: number): Promise<string> {
    return new Promise((resolve, reject) => {
      if (typeof window === "undefined" || !this.canvas || !this.ctx) {
        return reject(new Error("Browser environment missing"));
      }

      let video: HTMLVideoElement;
      try {
        video = this.getVideoElement(sourceUrl);
      } catch (err) {
        return reject(err);
      }

      const levelConfig = PYRAMID_LEVELS[level] || PYRAMID_LEVELS[0];
      const canvas = this.canvas;
      const ctx = this.ctx;

      let hasCleanedUp = false;
      let timeoutId: any = null;

      const cleanUp = () => {
        if (hasCleanedUp) return;
        hasCleanedUp = true;
        clearTimeout(timeoutId);
        video.removeEventListener("seeked", onSeeked);
        video.removeEventListener("error", onError);
      };

      const onSeeked = () => {
        cleanUp();
        try {
          canvas.width = levelConfig.width;
          canvas.height = levelConfig.height;
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const dataUrl = canvas.toDataURL("image/webp", levelConfig.quality);
          resolve(dataUrl);
        } catch (err) {
          reject(err);
        }
      };

      const onError = () => {
        cleanUp();
        reject(video.error || new Error("Video seek error"));
      };

      this.ensureVideoReady(video)
        .then(() => {
          if (hasCleanedUp) return;
          video.addEventListener("seeked", onSeeked);
          video.addEventListener("error", onError);
          video.currentTime = Math.max(0.001, timeOffset);
        })
        .catch(err => {
          cleanUp();
          reject(err);
        });

      timeoutId = setTimeout(() => {
        cleanUp();
        reject(new Error(`Timeout generating thumbnail level ${level} for ${timeOffset}s`));
      }, 5000);
    });
  }
}

// -------------------------------------------------------------
// Future-Ready Native Codec Thumbnail Provider (Capacitor/Android)
// -------------------------------------------------------------
export class NativeThumbnailProvider implements ThumbnailProvider {
  private webFallback: WebThumbnailProvider;

  constructor() {
    this.webFallback = new WebThumbnailProvider();
  }

  async getOrGenerate(req: ThumbnailRequest): Promise<string> {
    // Highly customizable Native Bridge fallback:
    // If running in a true Android build with native plugins:
    // const Cap = (window as any).Capacitor;
    // if (Cap?.Plugins?.NativeThumbnailEngine) {
    //   try {
    //     return await Cap.Plugins.NativeThumbnailEngine.generate({ ...req });
    //   } catch (e) {
    //     console.warn("Native thumbnail engine failed, falling back to web decoder", e);
    //   }
    // }
    return this.webFallback.getOrGenerate(req);
  }

  cancelForClip(clipId: string): void {
    this.webFallback.cancelForClip(clipId);
  }

  updateViewport(
    scrollLeft: number,
    viewportWidth: number,
    pixelsPerSecond: number,
    clips: Array<{ id: string; leftSeconds: number; trimStartSeconds: number }>
  ): void {
    this.webFallback.updateViewport(scrollLeft, viewportWidth, pixelsPerSecond, clips);
  }

  getSync(sourceUrl: string, level: number, timeOffset: number): string | null {
    return this.webFallback.getSync(sourceUrl, level, timeOffset);
  }

  subscribe(
    sourceUrl: string,
    level: number,
    timeOffset: number,
    callback: (dataUrl: string) => void
  ): () => void {
    return this.webFallback.subscribe(sourceUrl, level, timeOffset, callback);
  }
}

// -------------------------------------------------------------
// Unified Provider Instance Export
// -------------------------------------------------------------
const isAndroid = typeof window !== "undefined" && (window as any).Capacitor?.getPlatform() === "android";
export const thumbnailProvider: ThumbnailProvider = isAndroid
  ? new NativeThumbnailProvider()
  : new WebThumbnailProvider();
