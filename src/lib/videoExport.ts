import { registerPlugin, Capacitor } from "@capacitor/core";

export interface VideoExportPluginType {
  exportVideo(options: {
    inputPath: string;
    outputPath: string;
    width?: number;
    height?: number;
    fps?: number;
  }): Promise<{ status: string }>;
  addListener(
    eventName: "exportProgress",
    listenerFunc: (data: { progress: number }) => void
  ): Promise<any>;
}

const NativeVideoExport = registerPlugin<VideoExportPluginType>("VideoExport");

export class VideoExport {
  static isNative = Capacitor.isNativePlatform();

  /**
   * Transcodes an input video path (usually a WebM file recorded by the browser) 
   * into a standard, gallery-ready MP4 H.264 video.
   */
  static async exportVideo(
    inputPath: string,
    outputPath: string,
    width = 1920,
    height = 1080,
    fps = 30,
    onProgress?: (progress: number) => void
  ): Promise<void> {
    if (this.isNative) {
      let listener: any = null;
      try {
        if (onProgress) {
          listener = await NativeVideoExport.addListener("exportProgress", (data) => {
            // Native sends progress as a fraction from 0.0 to 1.0, convert to 0-100 percentage
            onProgress(Math.round(data.progress * 100));
          });
        }
        await NativeVideoExport.exportVideo({
          inputPath,
          outputPath,
          width,
          height,
          fps,
        });
      } catch (e: any) {
        console.error("[VideoExport Native Error] Transcode failed:", e);
        throw e;
      } finally {
        if (listener) {
          listener.remove();
        }
      }
    } else {
      console.log(`%c[VideoExport Web Fallback] Exporting ${inputPath} -> ${outputPath}`, "color: #9c27b0; font-weight: bold;");
      if (onProgress) {
        for (let i = 0; i <= 100; i += 10) {
          await new Promise((r) => setTimeout(r, 100));
          onProgress(i);
        }
      }
    }
  }
}
