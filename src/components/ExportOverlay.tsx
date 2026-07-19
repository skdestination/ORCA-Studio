import React, { useRef, useState, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Capacitor } from "@capacitor/core";
import { Filesystem, Directory } from "@capacitor/filesystem";
import { Media } from "@capacitor-community/media";
import { VideoExport } from "../lib/videoExport";

interface ExportOverlayProps {
  isExporting: boolean;
  setIsExporting: (v: boolean) => void;
  exportedVideoUrl: string | null;
  setExportedVideoUrl: (v: string | null) => void;
  exportedVideoBlob: Blob | null;
  setExportedVideoBlob: (v: Blob | null) => void;
  exportProgress: number;
  setExportProgress: (v: number) => void;
  exportResolution: string;
  currentProjectRatio: string;
  clips: any[];
}

export function ExportOverlay({
  isExporting,
  setIsExporting,
  exportedVideoUrl,
  setExportedVideoUrl,
  exportedVideoBlob,
  setExportedVideoBlob,
  exportProgress,
  setExportProgress,
  exportResolution,
  currentProjectRatio,
  clips,
}: ExportOverlayProps) {
  const [exportToast, setExportToast] = useState<string | null>(null);
  const [isExportVideoPlaying, setIsExportVideoPlaying] = useState(false);
  const exportedVideoRef = useRef<HTMLVideoElement>(null);
  const savedBlobRef = useRef<Blob | null>(null);

  const showExportToast = (msg: string) => {
    setExportToast(msg);
    setTimeout(() => setExportToast(null), 3000);
  };

  const toggleExportVideoPlay = () => {
    if (!exportedVideoRef.current) return;
    if (isExportVideoPlaying) {
      exportedVideoRef.current.pause();
      setIsExportVideoPlaying(false);
    } else {
      exportedVideoRef.current.play()
        .then(() => {
          setIsExportVideoPlaying(true);
        })
        .catch((err) => {
          console.warn("Play failed", err);
        });
    }
  };

  useEffect(() => {
    if (!exportedVideoBlob || savedBlobRef.current === exportedVideoBlob) return;
    savedBlobRef.current = exportedVideoBlob;

    const autoSave = async () => {
      if (Capacitor.isNativePlatform()) {
        try {
          showExportToast("💾 Compressing and rendering...");
          const base64Data = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.readAsDataURL(exportedVideoBlob);
            reader.onloadend = () => {
              const base64String = reader.result as string;
              const base = base64String.substring(base64String.indexOf(",") + 1);
              resolve(base);
            };
            reader.onerror = (error) => reject(error);
          });

          const webmFilename = `orca-temp-${Date.now()}.webm`;
          const mp4Filename = `orca-final-${Date.now()}.mp4`;

          // 1. Write the raw WebM recording to the cache
          const writeResult = await Filesystem.writeFile({
            path: webmFilename,
            data: base64Data,
            directory: Directory.Cache,
          });

          const inputUri = writeResult.uri;

          // Resolve the destination path for the transcoded MP4 output
          const getUriResult = await Filesystem.getUri({
            path: mp4Filename,
            directory: Directory.Cache,
          });
          const outputUri = getUriResult.uri;

          // Compute proper dimensions based on current aspect ratio
          let resW = 1920;
          let resH = 1080;
          if (exportResolution === "4K") {
            resW = 3840;
            resH = 2160;
          } else if (exportResolution === "2K") {
            resW = 2560;
            resH = 1440;
          }

          const [rw, rh] = currentProjectRatio.split(":").map(Number);
          if (rw && rh) {
            if (rw < rh) {
              let tempH = resH;
              let tempW = Math.round(resH * (rw / rh));
              if (tempW % 2 !== 0) tempW += 1;
              if (tempH % 2 !== 0) tempH += 1;
              resW = tempW;
              resH = tempH;
            } else {
              let tempW = resW;
              let tempH = Math.round(resW * (rh / rw));
              if (tempW % 2 !== 0) tempW += 1;
              if (tempH % 2 !== 0) tempH += 1;
              resW = tempW;
              resH = tempH;
            }
          }

          // 2. Transcode the WebM to high-compatibility, hardware-accelerated H.264 MP4
          showExportToast("⚡ Encoding high-quality MP4...");
          await VideoExport.exportVideo(
            inputUri,
            outputUri,
            resW,
            resH,
            30,
            (pct) => {
              setExportProgress(pct);
            }
          );

          showExportToast("💾 Saving to device gallery...");

          try {
            const mediaAny = Media as any;
            if (typeof mediaAny.checkPermissions === "function") {
              const checkPerms = await mediaAny.checkPermissions();
              if (checkPerms.photos !== "granted" && checkPerms.publicPhotoLibrary !== "granted") {
                const reqPerms = await mediaAny.requestPermissions();
                if (reqPerms.photos !== "granted" && reqPerms.publicPhotoLibrary !== "granted") {
                  showExportToast("⚠️ Gallery permission denied");
                  return;
                }
              }
            }
          } catch (permErr) {
            console.warn("Permission API failed:", permErr);
          }

          let albumIdentifier: string | undefined;
          try {
            const albumsResult = await Media.getAlbums();
            const targetAlbum = albumsResult.albums.find(
              (a) =>
                a.name.toLowerCase() === "orca studio" ||
                a.name.toLowerCase() === "camera" ||
                a.name.toLowerCase() === "movies" ||
                a.name.toLowerCase() === "videos"
            );
            if (targetAlbum) {
              albumIdentifier = targetAlbum.identifier;
            } else {
              try {
                await Media.createAlbum({ name: "ORCA Studio" });
                const refreshed = await Media.getAlbums();
                const created = refreshed.albums.find((a) => a.name.toLowerCase() === "orca studio");
                if (created) {
                  albumIdentifier = created.identifier;
                } else if (refreshed.albums.length > 0) {
                  albumIdentifier = refreshed.albums[0].identifier;
                }
              } catch (createErr) {
                if (albumsResult.albums.length > 0) {
                  albumIdentifier = albumsResult.albums[0].identifier;
                }
              }
            }
          } catch (albumErr) {}

          await Media.saveVideo({
            path: outputUri,
            albumIdentifier,
          });

          try {
            await Filesystem.deleteFile({
              path: webmFilename,
              directory: Directory.Cache,
            });
            await Filesystem.deleteFile({
              path: mp4Filename,
              directory: Directory.Cache,
            });
          } catch (cleanErr) {}

          showExportToast("✅ Saved to Gallery!");
        } catch (err: any) {
          console.error(err);
          showExportToast(`❌ Save failed: ${err.message || err.toString()}`);
        }
      } else {
        // Web Download Auto-save
        try {
          showExportToast("💾 Saving to Downloads...");
          const a = document.createElement("a");
          a.href = exportedVideoUrl || "";
          a.download = `orca-project-${exportResolution}-${Date.now()}.mp4`;
          a.click();
          showExportToast("✅ Saved to device!");
        } catch (err: any) {
          showExportToast("❌ Auto-download failed");
        }
      }
    };

    autoSave();
  }, [exportedVideoBlob, exportedVideoUrl, exportResolution]);

  return (
    <AnimatePresence>
      {(isExporting || exportedVideoUrl) && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 bg-black z-[1000] flex flex-col items-center justify-between py-16 px-6 overflow-hidden select-none"
        >
          {/* Soft Ambient Halo behind the screen - visible only when complete to highlight edge glow */}
          {exportedVideoUrl && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none -z-10 animate-pulse duration-[4000ms]">
              <div className="w-[300px] h-[300px] sm:w-[460px] sm:h-[460px] bg-white/[0.04] blur-[80px] sm:blur-[100px] rounded-full" />
            </div>
          )}

          {/* Top Bar with Lines */}
          <div className="w-full flex items-center justify-center shrink-0 relative">
            <div className="flex items-center justify-center gap-6 w-full max-w-md">
              <div className="h-[1px] flex-1 bg-zinc-900" />
              <span className="text-zinc-400 text-xs font-semibold tracking-[0.25em] uppercase font-sans">
                {exportedVideoUrl ? "Export Complete" : "Exporting"}
              </span>
              <div className="h-[1px] flex-1 bg-zinc-900" />
            </div>

            {/* Minimal translucent close button */}
            <button
              onClick={() => {
                setIsExporting(false);
                setExportProgress(0);
                if (exportedVideoUrl) {
                  URL.revokeObjectURL(exportedVideoUrl);
                  setExportedVideoUrl(null);
                }
                setExportedVideoBlob(null);
                setIsExportVideoPlaying(false);
              }}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-zinc-900/60 hover:bg-zinc-800 border border-zinc-800/80 flex items-center justify-center text-zinc-400 hover:text-white transition-all duration-200 active:scale-90 cursor-pointer"
              title="Close"
            >
              ✕
            </button>
          </div>

          {/* Center Player Frame Container */}
          <div
            className="w-full flex-1 flex items-center justify-center my-8 cursor-pointer"
            onClick={() => {
              if (exportedVideoUrl) {
                toggleExportVideoPlay();
              }
            }}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: "spring", damping: 30, stiffness: 120 }}
              className={`relative bg-[#070708] border border-zinc-900/80 flex flex-col items-center justify-center overflow-hidden transition-all duration-500 ${
                currentProjectRatio === "9:16"
                  ? "w-[240px] sm:w-[280px] aspect-[9/16] rounded-[48px]"
                  : currentProjectRatio === "1:1"
                  ? "w-[260px] sm:w-[300px] aspect-square rounded-[36px]"
                  : "w-[340px] sm:w-[420px] aspect-[16/9] rounded-[24px]"
              } ${
                exportedVideoUrl
                  ? "shadow-[0_0_60px_rgba(255,255,255,0.08),_inset_0_1px_1px_rgba(255,255,255,0.02)] border-zinc-800"
                  : "shadow-[inset_0_4px_24px_rgba(255,255,255,0.01)]"
              }`}
            >
              {/* 1. Rendering First Frame thumbnail fading in same as percentage (when exporting) */}
              {!exportedVideoUrl && (() => {
                const firstVisualClip = clips.find((c) => c.type === "video" || c.type === "image");
                const firstFrameUrl =
                  firstVisualClip?.src ||
                  firstVisualClip?.thumbnail ||
                  "https://images.unsplash.com/photo-1620641788421-7a1c342ea42e?auto=format&fit=crop&q=80&w=400";
                return (
                  <div
                    className="absolute inset-0 w-full h-full bg-[#070708] pointer-events-none"
                    style={{ opacity: exportProgress / 100 }}
                  >
                    {firstVisualClip?.type === "video" ? (
                      <video
                        src={firstFrameUrl}
                        className="w-full h-full object-cover"
                        muted
                        playsInline
                      />
                    ) : (
                      <img
                        src={firstFrameUrl}
                        alt="First Frame Preview"
                        className="w-full h-full object-cover"
                        referrerPolicy="no-referrer"
                      />
                    )}
                  </div>
                );
              })()}

              {/* 2. Rendering finished video overlay with edge glow and play-in-place (when complete) */}
              {exportedVideoUrl && (
                <video
                  ref={exportedVideoRef}
                  src={exportedVideoUrl}
                  loop
                  muted
                  playsInline
                  onPlay={() => setIsExportVideoPlaying(true)}
                  onPause={() => setIsExportVideoPlaying(false)}
                  className="absolute inset-0 w-full h-full object-cover rounded-inherit pointer-events-none z-10"
                />
              )}

              {/* Subtle Dots Background to match the image premium texture - only visible during exporting */}
              {!exportedVideoUrl && (
                <div
                  className="absolute inset-0 opacity-[0.03] pointer-events-none z-10"
                  style={{
                    backgroundImage: "radial-gradient(circle, #ffffff 1px, transparent 1px)",
                    backgroundSize: "16px 16px",
                  }}
                />
              )}

              {/* Main Progress percentage text - Elegant light font */}
              {!exportedVideoUrl && (
                <span className="text-4xl sm:text-5xl font-light tracking-wide text-zinc-100 relative z-20 select-none font-sans">
                  {exportProgress}%
                </span>
              )}

              {/* Centered Play Button when Complete and Paused */}
              {exportedVideoUrl && !isExportVideoPlaying && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/20 backdrop-blur-[0.5px] z-20">
                  <div className="w-16 h-16 bg-white hover:bg-zinc-100 rounded-full flex items-center justify-center shadow-[0_12px_40px_rgba(0,0,0,0.5)] transition-transform transform hover:scale-105 active:scale-95 duration-200">
                    <span className="text-black text-2xl ml-1">▶</span>
                  </div>
                </div>
              )}

              {/* Ambient vignette gradient inside */}
              <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-black/20 pointer-events-none z-10" />

              {/* Live PREVIEW tag */}
              {exportedVideoUrl && (
                <div className="absolute top-4 left-5 z-20 pointer-events-none bg-black/60 backdrop-blur-md px-2.5 py-1 rounded-full border border-white/5 text-[8px] font-mono tracking-wider text-zinc-400">
                  <span className="w-1.5 h-1.5 rounded-full bg-white animate-ping inline-block mr-1.5 align-middle" />
                  PREVIEW
                </div>
              )}

              {/* Mini phase description at the bottom of the container */}
              {!exportedVideoUrl && (
                <div className="absolute bottom-6 left-6 right-6 text-center z-20">
                  <span className="text-[10px] text-zinc-600 uppercase tracking-widest block truncate">
                    {exportProgress < 25 && "Assembling Timeline Tracks..."}
                    {exportProgress >= 25 && exportProgress < 50 && "Blending Crossfades & Audio..."}
                    {exportProgress >= 50 && exportProgress < 75 && "Applying Color Grading..."}
                    {exportProgress >= 75 && exportProgress < 95 && "Compiling Spatial Video..."}
                    {exportProgress >= 95 && "Writing Video Codec..."}
                  </span>
                </div>
              )}
            </motion.div>
          </div>

          {/* Bottom Specs Pill */}
          <div className="w-full flex flex-col items-center gap-4 shrink-0">
            <div className="border border-zinc-900/85 rounded-full px-6 py-3 bg-[#08080a] text-[10px] sm:text-xs font-mono text-zinc-400 tracking-[0.2em] flex items-center justify-center gap-3.5 uppercase shadow-[inset_0_1px_1px_rgba(255,255,255,0.01)]">
              <span>{exportResolution || "4K"}</span>
              <span className="text-zinc-800">|</span>
              <span>30Fps</span>
              <span className="text-zinc-800">|</span>
              <span>Bitrate Max</span>
              <span className="text-zinc-800">|</span>
              <span>MP4</span>
            </div>
          </div>

          {/* Float elegant local toast popup */}
          <AnimatePresence>
            {exportToast && (
              <motion.div
                initial={{ opacity: 0, y: 15 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="fixed bottom-24 left-1/2 -translate-x-1/2 bg-[#18181b]/95 backdrop-blur-md border border-white/10 px-4 py-2 rounded-full shadow-2xl z-[1100] flex items-center gap-2 pointer-events-none select-none"
              >
                <span className="text-white text-xs font-medium tracking-wide">
                  {exportToast}
                </span>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
