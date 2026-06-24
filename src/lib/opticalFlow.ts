import { Capacitor, registerPlugin } from "@capacitor/core";
import { Filesystem, Directory } from "@capacitor/filesystem";

export interface SmoothSlowMotionPlugin {
  receiveVideoPath(options: { inputPath: string }): Promise<{
    success: boolean;
    receivedPath: string;
    durationMs?: number;
    width?: number;
    height?: number;
    fps?: number;
  }>;
  getVideoMetadata(options: { inputPath: string }): Promise<{
    durationMs: number;
    width: number;
    height: number;
    fps: number;
  }>;
  decodeAllFrames(options: { inputPath: string; speed?: number }): Promise<{
    success: boolean;
    decodedFramesCount: number;
    flowComputedCount?: number;
    timestampsVerified: boolean;
    verificationError?: string;
    sampledTimestampsUs: number[];
    firstTimestampUs?: number;
    lastTimestampUs?: number;
    avgFlowMagnitude?: number;
    maxFlowMagnitude?: number;
    flowVisualization?: string;
    isFlowCorrect?: boolean;
    interpolatedFramesCount?: number;
    averagePsnr?: number;
    averageWarpError?: number;
    interpolationVisualization?: string;
    outputPath?: string;
    totalExportTimeMs?: number;
    decoderTimeMs?: number;
    opticalFlowTimeMs?: number;
    interpolationTimeMs?: number;
    encoderTimeMs?: number;
    decoderPct?: number;
    opticalFlowPct?: number;
    interpolationPct?: number;
    encoderPct?: number;
    decoderMsPerFrame?: number;
    opticalFlowMsPerFrame?: number;
    interpolationMsPerFrame?: number;
    encoderMsPerFrame?: number;
  }>;
}

const SmoothSlowMotionNative = registerPlugin<SmoothSlowMotionPlugin>("SmoothSlowMotion");

export async function getVideoMetadataNative(inputPath: string): Promise<{
  durationMs: number;
  width: number;
  height: number;
  fps: number;
}> {
  if (!Capacitor.isNativePlatform()) {
    throw new Error("getVideoMetadataNative is only supported on native Android platform.");
  }
  return await SmoothSlowMotionNative.getVideoMetadata({ inputPath });
}

export async function loadVideoMetadata(
  videoBlobUrlOrBlob: string | Blob
): Promise<{ durationMs: number; width: number; height: number; fps: number }> {
  // If native platform, try filesystem-based native plugin metadata loading first
  if (Capacitor.isNativePlatform()) {
    try {
      const inputFileName = `meta_temp_${Date.now()}.mp4`;
      let base64Data: string;
      if (videoBlobUrlOrBlob instanceof Blob) {
        base64Data = await convertBlobToBase64(videoBlobUrlOrBlob);
      } else {
        const response = await fetch(videoBlobUrlOrBlob);
        const blob = await response.blob();
        base64Data = await convertBlobToBase64(blob);
      }

      await Filesystem.writeFile({
        path: inputFileName,
        data: base64Data,
        directory: Directory.Cache,
      });

      const inputUri = await Filesystem.getUri({
        directory: Directory.Cache,
        path: inputFileName
      });

      const rawInput = inputUri.uri.replace("file://", "");
      const metadata = await getVideoMetadataNative(rawInput);
      
      // Clean up temp file
      try {
        await Filesystem.deleteFile({
          path: inputFileName,
          directory: Directory.Cache,
        });
      } catch (e) {
        console.warn("Could not delete temp meta file", e);
      }

      return metadata;
    } catch (err) {
      console.error("Native metadata extraction failed, falling back to Web element source", err);
    }
  }

  // Web or Fallback mechanism: use HTML5 Video element properties
  return new Promise((resolve, reject) => {
    const url = typeof videoBlobUrlOrBlob === "string" 
      ? videoBlobUrlOrBlob 
      : URL.createObjectURL(videoBlobUrlOrBlob);
    
    const video = document.createElement("video");
    video.preload = "metadata";
    video.src = url;
    video.crossOrigin = "anonymous";
    
    video.onloadedmetadata = () => {
      resolve({
        durationMs: Math.round(video.duration * 1000),
        width: video.videoWidth,
        height: video.videoHeight,
        fps: 30, // Standard video framerate default
      });
    };
    
    video.onerror = () => {
      reject(new Error("Failed to load video metadata from source: " + url));
    };
  });
}

export async function processSmoothSlowMoBrowser(
  videoBlobUrlOrBlob: string | Blob,
  speedFactor: number,
  onProgress: (progress: number) => void
): Promise<{
  url: string;
  fileId: string;
  decodedFramesCount?: number;
  flowComputedCount?: number;
  timestampsVerified?: boolean;
  sampledTimestampsUs?: number[];
  firstTimestampUs?: number;
  lastTimestampUs?: number;
  avgFlowMagnitude?: number;
  maxFlowMagnitude?: number;
  flowVisualization?: string;
  isFlowCorrect?: boolean;
  interpolatedFramesCount?: number;
  averagePsnr?: number;
  averageWarpError?: number;
  interpolationVisualization?: string;
  totalExportTimeMs?: number;
  decoderTimeMs?: number;
  opticalFlowTimeMs?: number;
  interpolationTimeMs?: number;
  colorConversionTimeMs?: number;
  encoderTimeMs?: number;
  decoderPct?: number;
  opticalFlowPct?: number;
  interpolationPct?: number;
  colorConversionPct?: number;
  encoderPct?: number;
  decoderMsPerFrame?: number;
  opticalFlowMsPerFrame?: number;
  interpolationMsPerFrame?: number;
  colorConversionMsPerFrame?: number;
  encoderMsPerFrame?: number;
  matAllocations?: number;
  byteBufferAllocations?: number;
  frameCopies?: number;
}> {
  console.log("[AUDIT] Stage 1: Export/Processing button pressed / initiated in processSmoothSlowMoBrowser");

  if (!Capacitor.isNativePlatform()) {
    console.log("Web platform detected. Native plugin not used.");
    const url = typeof videoBlobUrlOrBlob === "string" 
      ? videoBlobUrlOrBlob 
      : URL.createObjectURL(videoBlobUrlOrBlob);
    return { url, fileId: Date.now().toString() };
  }

  // Focus on Native processing
  console.log("Native platform detected. Verifying plugin receive path step.");
  const inputFileName = `input_${Date.now()}.mp4`;
  
  let base64Data: string;
  if (videoBlobUrlOrBlob instanceof Blob) {
    base64Data = await convertBlobToBase64(videoBlobUrlOrBlob);
  } else {
    const response = await fetch(videoBlobUrlOrBlob);
    const blob = await response.blob();
    base64Data = await convertBlobToBase64(blob);
  }

  await Filesystem.writeFile({
    path: inputFileName,
    data: base64Data,
    directory: Directory.Cache,
  });

  const inputUri = await Filesystem.getUri({
    directory: Directory.Cache,
    path: inputFileName
  });

  const rawInput = inputUri.uri.replace("file://", "");
  
  try {
    // 1. Send path to verify receive video path
    const result = await SmoothSlowMotionNative.receiveVideoPath({
      inputPath: rawInput
    });
    console.log("Plugin verified receiveVideoPath returning:", result);
    onProgress(15);

    // Setup dynamic listener for precise native progress callbacks
    let progressListener: any = null;
    try {
      progressListener = await (SmoothSlowMotionNative as any).addListener("progress", (data: { progress: number }) => {
        onProgress(data.progress);
      });
      console.log("Successfully attached progress listener to SmoothSlowMotion plugin");
    } catch (listenerError) {
      console.warn("Could not register progress callback listener:", listenerError);
    }

    // 2. Decode all frames and verify timestamps
    console.log("Invoking native decodeAllFrames pipeline for timestamp verification...");
    const decodeResult = await SmoothSlowMotionNative.decodeAllFrames({
      inputPath: rawInput,
      speed: speedFactor
    });
    console.log("Native decoding and timestamp verification completed successfully:", decodeResult);

    // Clean up active listener
    if (progressListener) {
      try {
        await progressListener.remove();
      } catch (remError) {
        console.warn("Could not remove progress listener:", remError);
      }
    }
    onProgress(100);

    const resolvedPath = decodeResult.outputPath;
    const url = resolvedPath
      ? Capacitor.convertFileSrc("file://" + resolvedPath)
      : (typeof videoBlobUrlOrBlob === "string" ? videoBlobUrlOrBlob : URL.createObjectURL(videoBlobUrlOrBlob));

    return {
      url,
      fileId: "native-processed-" + Date.now().toString(),
      decodedFramesCount: decodeResult.decodedFramesCount,
      flowComputedCount: decodeResult.flowComputedCount,
      timestampsVerified: decodeResult.timestampsVerified,
      sampledTimestampsUs: decodeResult.sampledTimestampsUs,
      firstTimestampUs: decodeResult.firstTimestampUs,
      lastTimestampUs: decodeResult.lastTimestampUs,
      avgFlowMagnitude: decodeResult.avgFlowMagnitude,
      maxFlowMagnitude: decodeResult.maxFlowMagnitude,
      flowVisualization: decodeResult.flowVisualization,
      isFlowCorrect: decodeResult.isFlowCorrect,
      interpolatedFramesCount: decodeResult.interpolatedFramesCount,
      averagePsnr: decodeResult.averagePsnr,
      averageWarpError: decodeResult.averageWarpError,
      interpolationVisualization: decodeResult.interpolationVisualization,
      totalExportTimeMs: decodeResult.totalExportTimeMs,
      decoderTimeMs: decodeResult.decoderTimeMs,
      opticalFlowTimeMs: decodeResult.opticalFlowTimeMs,
      interpolationTimeMs: decodeResult.interpolationTimeMs,
      colorConversionTimeMs: 0,
      encoderTimeMs: decodeResult.encoderTimeMs,
      decoderPct: decodeResult.decoderPct,
      opticalFlowPct: decodeResult.opticalFlowPct,
      interpolationPct: decodeResult.interpolationPct,
      colorConversionPct: 0,
      encoderPct: decodeResult.encoderPct,
      decoderMsPerFrame: decodeResult.decoderMsPerFrame,
      opticalFlowMsPerFrame: decodeResult.opticalFlowMsPerFrame,
      interpolationMsPerFrame: decodeResult.interpolationMsPerFrame,
      colorConversionMsPerFrame: 0,
      encoderMsPerFrame: decodeResult.encoderMsPerFrame,
      matAllocations: 0,
      byteBufferAllocations: 0,
      frameCopies: 0,
    };

  } catch (err: any) {
    console.error("Plugin failed in native processing", err);
    throw err;
  }
}

function convertBlobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = reject;
    reader.onload = () => {
        const result = reader.result as string;
        const base64Str = result.split(',')[1];
        resolve(base64Str);
    };
    reader.readAsDataURL(blob);
  });
}

