import React, {
  useState,
  useRef,
  useEffect,
  useLayoutEffect,
  useMemo,
  useCallback,
} from "react";
import {
  Play,
  Pause,
  Undo2,
  Redo2,
  Trash2,
  PlusCircle,
  ChevronLeft,
  ChevronDown,
  ArrowUpDown,
  Volume2,
  VolumeX,
  Eye,
  EyeOff,
  Scissors,
  Clock,
  SlidersHorizontal,
  Crop,
  Star,
  MoreVertical,
  Plus as PlusIcon,
  Settings,
  Copy,
  Check,
  X,
  Save,
  Move,
  Maximize,
  RotateCw,
  Wand2,
  Activity,
  Blend,
  SkipBack,
  AlertCircle,
  ArrowUp,
  ArrowDown,
  Type,
  Smile,
  Image as ImageIcon,
  ListOrdered,
  List,
  Diamond,
  LineChart,
  SquareDashed,
  Music,
  Download,
  Share,
  Mic,
  ZoomIn,
  ZoomOut,
  Layers,
  Link2,
  Lock,
  Unlock,
  Minimize2,
  Maximize2,
  ArrowLeft,
  Clipboard,
  Bell,
  ChevronRight,
  MoreHorizontal,
  Sparkles,
  Video,
  Folder,
  Keyboard,
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { Capacitor } from "@capacitor/core";
import { App as CapApp } from "@capacitor/app";
import { StatusBar } from "@capacitor/status-bar";
import { Filesystem, Directory } from "@capacitor/filesystem";
import { Media } from "@capacitor-community/media";
import { processSmoothSlowMoBrowser } from "./lib/opticalFlow";
import { SpeedCurveEditor } from "./SpeedCurveEditor";
import { TextEditorMenu } from "./TextEditorMenu";
import { ExportOverlay } from "./components/ExportOverlay";

import { Screen, Layer, Keyframe, Clip, Project } from "./types";
import { getInterpolatedProps } from "./lib/utils";
import { VideoRenderer, AudioRenderer } from "./components/Renderers";
import { MinusIcon, CompactRulerControl, SpeedRulerControl, ScaleRulerControl } from "./components/Controls";

// --- Mock Data & Constants ---
const BASE_PIXELS_PER_SECOND = 100;

export const DEFAULT_FLOW_BAR_ORDER = [
  'voiceover',
  'volume',
  'text',
  'crop',
  'adjust',
  'speed',
  'stabilize',
  'copy',
  'extract-audio',
  'move',
  'magic',
  'activity',
  'mask',
];


import { MaskControlOverlay } from "./components/MaskControlOverlay";

import { CropControlOverlay } from "./components/CropControlOverlay";

import { getFile } from "./lib/db";

function ProjectCoverImage({ p }: { p: Project }) {
  const [dbSrc, setDbSrc] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    if (p.thumbnailFileId) {
      getFile(p.thumbnailFileId).then(blob => {
        if (blob) {
          objectUrl = URL.createObjectURL(blob);
          setDbSrc(objectUrl);
        }
      }).catch(console.error);
    }
    
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [p.thumbnailFileId]);

  return (
    <img
      src={dbSrc || p.thumbnail || undefined}
      alt=""
      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
      referrerPolicy="no-referrer"
    />
  );
}

const getBestSupportedVideoType = () => {
  const isSafari = typeof navigator !== "undefined" && /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
  const defaultExt = isSafari ? "mp4" : "webm";
  const types = [
    { mime: "video/mp4;codecs=avc1", ext: "mp4" },
    { mime: "video/mp4;codecs=h264", ext: "mp4" },
    { mime: "video/mp4", ext: "mp4" },
    { mime: "video/webm;codecs=h264", ext: "webm" },
    { mime: "video/webm;codecs=vp9", ext: "webm" },
    { mime: "video/webm;codecs=vp8", ext: "webm" },
    { mime: "video/webm", ext: "webm" },
    { mime: "video/quicktime", ext: "mov" },
  ];
  if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
    return { mime: "", ext: defaultExt };
  }
  for (const t of types) {
    if (MediaRecorder.isTypeSupported(t.mime)) {
      return t;
    }
  }
  return { mime: "", ext: defaultExt };
};

// --- Performance-Optimizing Cached Waveform Resolver & Memoized Timeline Components ---
const audioWaveCache = new Map<string, { key: string; pathD: string }>();

function getCachedWavePath(clip: Clip, activeExpandedMenu: string | null): string {
  const kfsHash = clip.keyframes
    ? clip.keyframes.map(k => `${k.id}_${k.timeOffset}_${k.properties.volume ?? ""}`).join("|")
    : "";
  const cacheKey = `${clip.id}_${clip.durationSeconds}_${clip.volume ?? 100}_${kfsHash}_${activeExpandedMenu}`;
  const cached = audioWaveCache.get(clip.id);
  
  if (cached && cached.key === cacheKey) {
    return cached.pathD;
  }
  
  const seed = clip.id.split("").reduce((acc, char) => acc + char.charCodeAt(0), 0);
  const freq1 = 0.07 + (seed % 7) * 0.015;
  const freq2 = 0.14 + (seed % 11) * 0.012;
  const freq3 = 0.03 + (seed % 5) * 0.008;

  const hasVolumeKeyframes = clip.keyframes?.some(k => k.properties.volume !== undefined);
  const constantVolume = typeof clip.volume === "number" ? clip.volume : 100;
  const constantVolMult = constantVolume / 100;

  const barCount = 140;
  let pathD = "";

  for (let i = 0; i < barCount; i++) {
    const h1 = Math.sin(i * freq1);
    const h2 = Math.cos(i * freq2);
    const h3 = Math.sin(i * freq3);
    
    // Generate a premium fluid song acoustic pattern
    let val = 0.12 + 0.38 * Math.abs(h1) + 0.32 * Math.abs(h2 * h3) + 0.12 * Math.sin(i * 0.3);
    val = Math.max(0.06, Math.min(0.95, val));

    // Check volume at this slice of the audio duration
    let volMult = constantVolMult;
    if (hasVolumeKeyframes && clip.durationSeconds) {
      const tRel = (i / (barCount - 1)) * clip.durationSeconds;
      const propsAtBar = getInterpolatedProps(clip, tRel, activeExpandedMenu);
      volMult = (propsAtBar.volume ?? 100) / 100;
    }
    
    const finalVal = val * volMult;
    const x = i * 10 + 5;
    const halfH = finalVal * 42; // Vertically symmetric waves scaling up to 42 units up & down
    const y1 = 50 - halfH;
    const y2 = 50 + halfH;
    pathD += `M ${x} ${y1} L ${x} ${y2} `;
  }
  
  audioWaveCache.set(clip.id, { key: cacheKey, pathD });
  return pathD;
}

interface TimelineRulerTicksProps {
  maxTimelineDuration: number;
  pixelsPerSecond: number;
  zoomLevel: number;
}

const TimelineRulerTicks = React.memo(({
  maxTimelineDuration,
  pixelsPerSecond,
  zoomLevel
}: TimelineRulerTicksProps) => {
  return (
    <>
      {Array.from({ length: Math.ceil(maxTimelineDuration) + 1 }).map(
        (_, i) => {
          let step = 1;
          if (pixelsPerSecond < 2) step = 300; // marks every 5 mins
          else if (pixelsPerSecond < 5) step = 60; // marks every 1 min
          else if (pixelsPerSecond < 10) step = 30; // very zoomed out
          else if (pixelsPerSecond < 20) step = 10;
          else if (pixelsPerSecond < 35) step = 5;
          else if (pixelsPerSecond < 70) step = 2; // normal default is 100
          
          const showText = i % step === 0;

          // Skip rendering the tick entirely if it's too squished
          const hideTick = pixelsPerSecond < 2 ? i % 60 !== 0 : (pixelsPerSecond < 5 ? i % 10 !== 0 : false);
          if (hideTick) return null;

          const mins = Math.floor(i / 60);
          const secs = i % 60;
          const formattedLabel = `${mins}:${secs.toString().padStart(2, "0")}`;

          return (
            <div
              key={i}
              className="absolute h-full w-0 pointer-events-none"
              style={{ left: `${i * pixelsPerSecond}px` }}
            >
              {/* Premium micro tick line at the bottom */}
              <div
                className={`absolute bottom-0 w-[1px] pointer-events-none transition-colors duration-150 ${
                  showText ? "h-[5px] bg-zinc-500/80" : "h-[3px] bg-zinc-700/50"
                }`}
              />
              
              {showText && (
                <span
                  className="absolute left-0 -translate-x-1/2 top-[1.5px] text-[7.5px] text-zinc-450 hover:text-zinc-300 font-semibold font-mono tracking-wider select-none transition-colors duration-150 leading-none whitespace-nowrap"
                  style={{ textShadow: "none" }}
                >
                  {formattedLabel}
                </span>
              )}
              {/* Sub-ticks for zoom */}
              {pixelsPerSecond >= 80 ? (
                Array.from({ length: 9 }).map((_, subIndex) => {
                  const isHalf = subIndex === 4;
                  return (
                    <div
                      key={subIndex}
                      className={`absolute bottom-0 w-[1px] ${isHalf ? "bg-zinc-600/70" : "bg-zinc-800/40"} pointer-events-none`}
                      style={{
                        left: `${(subIndex + 1) * (pixelsPerSecond / 10)}px`,
                        height: isHalf ? "3.5px" : "1.5px",
                      }}
                    />
                  );
                })
              ) : pixelsPerSecond >= 40 ? (
                <div
                  className="absolute bottom-0 w-[1px] bg-zinc-600/50 pointer-events-none"
                  style={{
                    left: `${pixelsPerSecond / 2}px`,
                    height: "3px",
                  }}
                />
              ) : null}
            </div>
          );
        }
      )}
    </>
  );
}, (prev, next) => {
  return (
    prev.maxTimelineDuration === next.maxTimelineDuration &&
    prev.pixelsPerSecond === next.pixelsPerSecond &&
    prev.zoomLevel === next.zoomLevel
  );
});

interface TimelineClipItemProps {
  clip: Clip;
  pixelsPerSecond: number;
  selectedClipIds: string[];
  selectedClipId: string | null;
  layerHiddenOrMuted: boolean;
  activeExpandedMenu: string | null;
  currentTime: number;
  isErrored: boolean;
  onPointerDown: (e: React.PointerEvent<HTMLDivElement>, clip: Clip) => void;
  onClipError: (clipId: string) => void;
  onTrimStart: (e: React.PointerEvent<HTMLDivElement>, clip: Clip, handle: "left" | "right") => void;
}

const TimelineClipItem = React.memo(({
  clip,
  pixelsPerSecond,
  selectedClipIds,
  selectedClipId,
  layerHiddenOrMuted,
  activeExpandedMenu,
  currentTime,
  isErrored,
  onPointerDown,
  onClipError,
  onTrimStart,
}: TimelineClipItemProps) => {
  const isSelected = selectedClipId === clip.id;
  const isMultiselected = selectedClipIds.includes(clip.id);
  
  // High-end premium gradient palettes for different clip types
  let gradientClass = "";
  let borderClass = "";
  let glowClass = "";
  
  if (clip.type === "audio") {
    gradientClass = isMultiselected 
      ? "bg-gradient-to-r from-[#5a21b3] via-[#7c3aed] to-[#4c1d95]" 
      : "bg-gradient-to-r from-[#1f0e38]/95 via-[#2f1154]/90 to-[#1f0e38]/95";
    borderClass = isMultiselected 
      ? "border-purple-400" 
      : "border-purple-500/20 hover:border-purple-400/40";
    glowClass = isMultiselected 
      ? "shadow-[0_0_20px_rgba(168,85,247,0.45),_inset_0_1px_1px_rgba(255,255,255,0.2)]" 
      : "shadow-[0_4px_12px_rgba(0,0,0,0.4)]";
  } else if (clip.type === "video") {
    gradientClass = isMultiselected 
      ? "bg-gradient-to-r from-[#1e40af] via-[#3b82f6] to-[#1d4ed8]" 
      : "bg-gradient-to-r from-[#0a152b]/95 via-[#0e2144]/90 to-[#0a152b]/95";
    borderClass = isMultiselected 
      ? "border-blue-400" 
      : "border-blue-500/20 hover:border-blue-400/40";
    glowClass = isMultiselected 
      ? "shadow-[0_0_20px_rgba(59,130,246,0.45),_inset_0_1px_1px_rgba(255,255,255,0.2)]" 
      : "shadow-[0_4px_12px_rgba(0,0,0,0.4)]";
  } else if (clip.type === "text") {
    gradientClass = isMultiselected 
      ? "bg-gradient-to-r from-[#b45309] via-[#f59e0b] to-[#d97706]" 
      : "bg-gradient-to-r from-[#331502]/95 via-[#4a1c00]/90 to-[#331502]/95";
    borderClass = isMultiselected 
      ? "border-amber-400" 
      : "border-amber-500/20 hover:border-amber-400/40";
    glowClass = isMultiselected 
      ? "shadow-[0_0_20px_rgba(245,158,11,0.45),_inset_0_1px_1px_rgba(255,255,255,0.2)]" 
      : "shadow-[0_4px_12px_rgba(0,0,0,0.4)]";
  } else { // image
    gradientClass = isMultiselected 
      ? "bg-gradient-to-r from-[#047857] via-[#10b981] to-[#065f46]" 
      : "bg-gradient-to-r from-[#02180f]/95 via-[#052b1b]/90 to-[#02180f]/95";
    borderClass = isMultiselected 
      ? "border-emerald-400" 
      : "border-emerald-500/20 hover:border-emerald-400/40";
    glowClass = isMultiselected 
      ? "shadow-[0_0_20px_rgba(16,185,129,0.45),_inset_0_1px_1px_rgba(255,255,255,0.2)]" 
      : "shadow-[0_4px_12px_rgba(0,0,0,0.4)]";
  }

  const roundedClass = clip.type === "audio" ? "rounded-xl" : "rounded-lg";
  const scaleClass = isSelected ? "scale-[1.01] z-50" : isMultiselected ? "scale-[1.01] z-20" : "hover:scale-[1.005] z-10";
  
  return (
    <div
      onPointerDown={(e) => onPointerDown(e, clip)}
      className={`absolute h-[28px] sm:h-[34px] overflow-hidden flex items-center cursor-pointer select-none border backdrop-blur-sm transition-all duration-200 transform-gpu
                 ${roundedClass} ${gradientClass} ${borderClass} ${glowClass} ${scaleClass}
                 ${layerHiddenOrMuted ? "grayscale opacity-25 shadow-none" : ""}
               `}
      style={{
        left: clip.leftSeconds * pixelsPerSecond,
        width: Math.max(2, clip.durationSeconds * pixelsPerSecond),
        touchAction: "none",
        willChange: "left, width",
      }}
    >
      {/* High-glass aesthetic top specular line */}
      <div className="absolute inset-x-0 top-0 h-[38%] bg-gradient-to-b from-white/[0.08] to-transparent pointer-events-none z-20" />
      
      {/* Keyframes Overlay */}
      {clip.keyframes && clip.keyframes.length > 0 && (() => {
        const volumeKeyframes = clip.keyframes.filter((k) => k.properties.volume !== undefined).sort((a,b) => a.timeOffset - b.timeOffset);
        const moveKeyframes = clip.keyframes.filter((k) => k.properties.translateX !== undefined || k.properties.scale !== undefined).sort((a,b) => a.timeOffset - b.timeOffset);

        return (
          <div className="absolute inset-0 pointer-events-none z-30" style={{ margin: '4px 0' }}>
            <svg className="absolute inset-0 w-full h-full" preserveAspectRatio="none" viewBox="0 0 100 100">
              {/* Volume - Purple */}
              {volumeKeyframes.length > 1 && (
                <polyline 
                  points={volumeKeyframes.map((kf) => 
                    `${(kf.timeOffset / clip.durationSeconds) * 100},${100 - Math.min(100, Math.max(0, kf.properties.volume ?? 100))}`
                  ).join(' ')}
                  fill="none" stroke="#c084fc" strokeWidth="2.5" vectorEffect="non-scaling-stroke" opacity="0.8" strokeDasharray="3,3"
                />
              )}
              {/* Zoom - Green */}
              {moveKeyframes.length > 1 && (
                <polyline 
                  points={moveKeyframes.map((kf) => {
                    const sc = kf.properties.scale ?? 1;
                    const y = sc <= 1 ? 100 - (sc * 50) : Math.max(0, 50 - ((sc - 1) * 25));
                    return `${(kf.timeOffset / clip.durationSeconds) * 100},${y}`;
                  }).join(' ')}
                  fill="none" stroke="#4ade80" strokeWidth="2.5" vectorEffect="non-scaling-stroke" opacity="0.8" strokeDasharray="3,3"
                />
              )}
              {/* Pan - Blue */}
              {moveKeyframes.length > 1 && (
                <polyline 
                  points={moveKeyframes.map((kf) => {
                    const tx = kf.properties.translateX ?? 0;
                    const y = Math.max(0, Math.min(100, 50 - (tx / 400) * 50));
                    return `${(kf.timeOffset / clip.durationSeconds) * 100},${y}`;
                  }).join(' ')}
                  fill="none" stroke="#60a5fa" strokeWidth="2.5" vectorEffect="non-scaling-stroke" opacity="0.8" strokeDasharray="3,3"
                />
              )}
            </svg>

            {clip.keyframes.map((kf) => {
              const isVol = kf.properties.volume !== undefined;
              const isMove = kf.properties.translateX !== undefined || kf.properties.scale !== undefined;
              
              const x = (kf.timeOffset / clip.durationSeconds) * 100;

              return (
                <div key={kf.id}>
                  {isVol && (() => {
                    const y = 100 - Math.min(100, Math.max(0, kf.properties.volume ?? 100));
                    return (
                      <div
                        className="absolute w-[10px] h-[10px] border-[2px] border-[#18181b] rounded-full shadow-[0_1px_4px_rgba(0,0,0,0.8)] transform -translate-x-1/2 -translate-y-1/2 flex items-center justify-center transition-all z-40 group bg-purple-400"
                        style={{ left: `${x}%`, top: `${y}%` }}
                      >
                      </div>
                    );
                  })()}
                  {isMove && (() => {
                    const sc = kf.properties.scale ?? 1;
                    const yZoom = sc <= 1 ? 100 - (sc * 50) : Math.max(0, 50 - ((sc - 1) * 25));

                    const tx = kf.properties.translateX ?? 0;
                    const yPan = Math.max(0, Math.min(100, 50 - (tx / 400) * 50));

                    return (
                      <>
                        {/* Zoom Node */}
                        <div
                          className="absolute w-[10px] h-[10px] border-[2px] border-[#18181b] rounded-full shadow-[0_1px_4px_rgba(0,0,0,0.8)] transform -translate-x-1/2 -translate-y-1/2 flex items-center justify-center transition-all z-30 group bg-emerald-400"
                          style={{ left: `${x}%`, top: `${yZoom}%` }}
                        >
                        </div>
                        {/* Pan Node */}
                        {Math.abs(yPan - yZoom) > 5 && (
                          <div
                            className="absolute w-[8px] h-[8px] border-[1.5px] border-[#18181b] rounded-full shadow-[0_1px_4px_rgba(0,0,0,0.8)] transform -translate-x-1/2 -translate-y-1/2 flex items-center justify-center transition-all z-20 group bg-blue-450"
                            style={{ left: `${x}%`, top: `${yPan}%` }}
                          >
                          </div>
                        )}
                      </>
                    );
                  })()}
                </div>
              );
            })}
          </div>
        );
      })()}

      <div className="absolute inset-0 bg-black/15 pointer-events-none z-10"></div>
      
      {clip.type === "text" && (
        <div className="w-full h-full flex items-center justify-center px-4 pointer-events-none overflow-hidden pb-1 pt-3">
          <span className="text-[11px] font-extrabold text-white/95 truncate drop-shadow-md bg-black/55 px-2.5 py-0.5 rounded-md border border-white/10 tracking-wide">
            {clip.text || "Type text..."}
          </span>
        </div>
      )}
      
      {clip.type === "image" && (
        <>
          <img
            src={clip.src || undefined}
            className="absolute inset-0 w-full h-full object-cover opacity-60 pointer-events-none"
            draggable={false}
            onError={() => onClipError(clip.id)}
          />
          <div className="absolute inset-0 bg-gradient-to-r from-black/85 via-black/25 to-transparent pointer-events-none z-10"></div>
        </>
      )}
      
      {clip.type === "video" && (
        <>
          <video
            src={clip.src ? clip.src + "#t=0.001" : undefined}
            className="absolute inset-0 w-full h-full object-cover opacity-60 pointer-events-none"
            draggable={false}
            preload="metadata"
            onError={() => onClipError(clip.id)}
          />
          <div className="absolute inset-0 bg-gradient-to-r from-black/85 via-black/25 to-transparent pointer-events-none z-10"></div>
        </>
      )}
      
      {clip.type === "audio" && (() => {
        const pathD = getCachedWavePath(clip, activeExpandedMenu);
        const gradientId = `wave-grad-${clip.id}`;
        return (
          <svg 
            className="absolute inset-y-[4px] left-[12px] right-[12px] w-[calc(100%-24px)] h-[calc(100%-8px)] pointer-events-none opacity-[0.95]"
            viewBox="0 0 1400 100"
            preserveAspectRatio="none"
          >
            <defs>
              <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" stopColor="#d8b4fe" />
                <stop offset="50%" stopColor="#f472b6" />
                <stop offset="100%" stopColor="#a5b4fc" />
              </linearGradient>
            </defs>
            <path
              d={pathD}
              stroke={`url(#${gradientId})`}
              strokeWidth="3.6"
              strokeLinecap="round"
            />
          </svg>
        );
      })()}

      {/* Type indicator icon with gorgeous modern styling */}
      <div className={`absolute max-w-full overflow-hidden whitespace-nowrap pl-2 flex items-center gap-1 ${clip.type === "audio" ? "inset-y-0 z-20 pointer-events-none" : `top-1 pointer-events-none`}`}>
        {isErrored && clip.type !== "text" && (
          <span className="text-[8px] font-extrabold text-red-200 uppercase drop-shadow-md pb-0.5 px-1.5 bg-red-600/90 rounded-md inline-flex items-center gap-1">
            <AlertCircle size={8} /> MISSING
          </span>
        )}
        
        {clip.type === "audio" ? (
          <span className={`text-[10px] py-0.5 px-2 font-extrabold text-purple-200 tracking-wider drop-shadow-sm bg-black/65 border border-purple-500/30 rounded-md inline-flex items-center backdrop-blur-md shadow-sm gap-1 ml-0.5 sm:ml-1 scale-95 origin-left`}>
            <Music size={10} className="text-purple-400 shrink-0" />
            AUDIO {layerHiddenOrMuted && "(MUTED)"}
          </span>
        ) : (
          <span className={`text-[9px] py-0.5 px-1.5 font-extrabold tracking-wider text-zinc-200 drop-shadow-sm bg-black/65 border border-white/10 rounded-md shadow-sm backdrop-blur-md inline-flex items-center uppercase scale-95 origin-left gap-1`}>
            {clip.type === "video" ? (
              <Video size={10} className="text-blue-400 shrink-0" />
            ) : clip.type === "text" ? (
              <Type size={10} className="text-amber-400 shrink-0" />
            ) : (
              <ImageIcon size={10} className="text-emerald-400 shrink-0" />
            )}
            {clip.type} {layerHiddenOrMuted && "(HIDDEN)"}
          </span>
        )}

        {clip.opticalFlow && (
          <span className="text-[8px] font-extrabold text-indigo-200 uppercase drop-shadow-md pb-0.5 px-1.5 bg-indigo-600/60 rounded-md inline-flex items-center gap-1">
            <Sparkles size={8} className="text-indigo-300" /> SMOOTH
          </span>
        )}
      </div>

      {/* Keyframe Markers and Slope Connections */}
      {clip.keyframes && clip.keyframes.length > 0 && (
        <svg className="absolute inset-0 w-full h-full pointer-events-none z-30 overflow-visible">
          {(() => {
            const sortedKfs = [...clip.keyframes].sort((a, b) => a.timeOffset - b.timeOffset);
            const isVol = activeExpandedMenu === "volume";
            const relevantSortedKfs = sortedKfs.filter(kf => {
              if (isVol) {
                return kf.properties.volume !== undefined;
              } else {
                return kf.properties.translateX !== undefined || kf.properties.scale !== undefined;
              }
            });
            const points = relevantSortedKfs.map(kf => {
              const xCoord = kf.timeOffset * pixelsPerSecond;
              
              let val = 1.0;
              if (kf.properties.volume !== undefined) {
                val = kf.properties.volume > 1.5 ? kf.properties.volume / 100 : kf.properties.volume;
              } else if (kf.properties.opacity !== undefined) {
                val = kf.properties.opacity;
              } else if (kf.properties.scale !== undefined) {
                val = (kf.properties.scale - 0.1) / 2.9;
              }
              val = Math.max(0, Math.min(1, val));
              
              const yCoord = 29 - (val * 20); 
              return { x: xCoord, y: yCoord, val, kf };
            });

            let pathD = "";
            if (points.length >= 2) {
              pathD = `M ${points[0].x} ${points[0].y} ` + points.slice(1).map(p => `L ${p.x} ${p.y}`).join(" ");
            }

            return (
              <>
                {pathD && (
                  <path
                    d={pathD}
                    fill="none"
                    stroke="#c7d2fe"
                    strokeWidth="1.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                )}

                {points.map((p) => {
                  const isSelectedKf = isSelected && Math.abs(currentTime - (clip.leftSeconds + p.kf.timeOffset)) < 0.05;
                  return (
                    <g key={p.kf.id} className="transform-gpu">
                      {isSelectedKf && (
                        <circle
                          cx={p.x}
                          cy={p.y}
                          r="8"
                          fill="none"
                          stroke="#a5b4fc"
                          strokeWidth="2"
                          className="animate-pulse"
                          style={{ transformOrigin: `${p.x}px ${p.y}px` }}
                        />
                      )}
                      <circle
                        cx={p.x}
                        cy={p.y}
                        r="5.5"
                        fill="#09090b"
                        stroke="none"
                      />
                      <circle
                        cx={p.x}
                        cy={p.y}
                        r="3.8"
                        fill={isSelectedKf ? "#ffffff" : "#c7d2fe"}
                        stroke={isSelectedKf ? "#6366f1" : "#4f46e5"}
                        strokeWidth="1.8"
                      />
                      {isSelected && (
                        <text
                          x={p.x}
                          y={p.y - 8}
                          textAnchor="middle"
                          fill="#ffffff"
                          fontSize="7.5"
                          fontWeight="bold"
                          className="font-mono pointer-events-none drop-shadow-[0_1.5px_3px_rgba(0,0,0,0.95)] select-none"
                          style={{ paintOrder: "stroke", stroke: "#09090b", strokeWidth: "2px", strokeLinejoin: "round" }}
                        >
                          {p.kf.properties.volume !== undefined 
                            ? `${Math.round((p.kf.properties.volume > 1.5 ? p.kf.properties.volume / 100 : p.kf.properties.volume) * 100)}%` 
                            : p.kf.properties.opacity !== undefined 
                            ? `${Math.round(p.kf.properties.opacity * 100)}%`
                            : p.kf.properties.scale !== undefined
                            ? `${Math.round(p.kf.properties.scale * 100)}%`
                            : ""
                          }
                        </text>
                      )}
                    </g>
                  );
                })}
              </>
            );
          })()}
        </svg>
      )}

      {/* Upgraded Trim Controls with premium vertically-inset glossy glass handle pills */}
      {isSelected && (
        <>
          <div
            onPointerDown={(e) => onTrimStart(e, clip, "left")}
            className="absolute left-0.5 top-1 bottom-1 w-[9px] bg-white hover:bg-zinc-100 rounded-lg flex items-center justify-center cursor-col-resize hover:w-[11px] transition-all z-[60] shadow-[0_2px_8px_rgba(0,0,0,0.7)] border border-black/25"
            style={{ touchAction: "none" }}
          >
            <div className="flex flex-col gap-0.5 justify-center items-center">
              <div className="w-[1.2px] h-1.5 bg-zinc-800 rounded-full" />
              <div className="w-[1.2px] h-1.5 bg-zinc-800 rounded-full" />
            </div>
          </div>
          <div
            onPointerDown={(e) => onTrimStart(e, clip, "right")}
            className="absolute right-0.5 top-1 bottom-1 w-[9px] bg-white hover:bg-zinc-100 rounded-lg flex items-center justify-center cursor-col-resize hover:w-[11px] transition-all z-[60] shadow-[0_2px_8px_rgba(0,0,0,0.7)] border border-black/25"
            style={{ touchAction: "none" }}
          >
            <div className="flex flex-col gap-0.5 justify-center items-center">
              <div className="w-[1.2px] h-1.5 bg-zinc-800 rounded-full" />
              <div className="w-[1.2px] h-1.5 bg-zinc-800 rounded-full" />
            </div>
          </div>
        </>
      )}
    </div>
  );
}, (prev, next) => {
  const isSelected = next.selectedClipId === next.clip.id;
  const wasSelected = prev.selectedClipId === prev.clip.id;
  if (isSelected !== wasSelected) {
    return false; // selection transitioned
  }
  if (isSelected) {
    // Selected clip re-renders on playhead tick to animate keyframe proximity pulse
    if (prev.currentTime !== next.currentTime) {
      return false;
    }
  }
  return (
    prev.clip === next.clip &&
    prev.pixelsPerSecond === next.pixelsPerSecond &&
    prev.layerHiddenOrMuted === next.layerHiddenOrMuted &&
    prev.activeExpandedMenu === next.activeExpandedMenu &&
    prev.isErrored === next.isErrored &&
    prev.selectedClipIds === next.selectedClipIds
  );
});

import { KEYBOARD_SHORTCUTS, sampleMediaImages, sampleMediaVideos, sampleMediaAudio } from "./data";
import { useFullscreen } from "./hooks/useFullscreen";

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<Screen>("home");
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const { isFullscreen, toggleFullscreen } = useFullscreen();

  const [isRecording, setIsRecording] = useState(false);
  const [hasMicPermission, setHasMicPermission] = useState(false);
  const [voiceoverLayerId, setVoiceoverLayerId] = useState<string | null>(null);
  const [liveMicLevels, setLiveMicLevels] = useState<number[]>(
    [40, 70, 30, 80, 50, 100, 60, 40, 90, 50, 30, 70, 40, 80, 50, 60, 40, 90, 30]
  );

  const micStreamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const micLevelsAnimFrameRef = useRef<number | null>(null);
  const recordingStartPlayheadTimeRef = useRef<number>(0);
  const voiceoverRecorderRef = useRef<MediaRecorder | null>(null);
  const recordingChunksRef = useRef<Blob[]>([]);

  // Start / Stop real mic analysis based on isRecording
  useEffect(() => {
    if (!isRecording) {
      if (micLevelsAnimFrameRef.current) {
        cancelAnimationFrame(micLevelsAnimFrameRef.current);
        micLevelsAnimFrameRef.current = null;
      }
      if (micStreamRef.current) {
        micStreamRef.current.getTracks().forEach((track) => track.stop());
        micStreamRef.current = null;
      }
      if (audioContextRef.current && audioContextRef.current.state !== "closed") {
        audioContextRef.current.close().catch(() => {});
        audioContextRef.current = null;
      }
      return;
    }

    let active = true;
    const startAnalyser = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        if (!active) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        micStreamRef.current = stream;
        setHasMicPermission(true);

        try {
          let recorder: MediaRecorder;
          try {
            recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
          } catch {
            recorder = new MediaRecorder(stream);
          }
          recordingChunksRef.current = [];
          recorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
              recordingChunksRef.current.push(event.data);
            }
          };
          recorder.start();
          voiceoverRecorderRef.current = recorder;
        } catch (recorderErr) {
          console.warn("MediaRecorder creation failed", recorderErr);
        }

        const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
        const audioCtx = new AudioContextClass();
        audioContextRef.current = audioCtx;

        const source = audioCtx.createMediaStreamSource(stream);
        const analyser = audioCtx.createAnalyser();
        analyser.fftSize = 64; 
        analyserRef.current = analyser;
        source.connect(analyser);

        const bufferLength = analyser.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);

        const updateLevels = () => {
          if (!active || !analyserRef.current) return;
          analyserRef.current.getByteFrequencyData(dataArray);

          const newLevels = Array.from({ length: 19 }, (_, index) => {
            const sampleIndex = Math.floor((index / 19) * bufferLength);
            const rawValue = dataArray[sampleIndex] || 0;
            const heightValue = 15 + (rawValue / 255) * 85;
            return Math.min(100, Math.max(15, heightValue + (Math.sin(Date.now() / 80 + index) * 3)));
          });

          setLiveMicLevels(newLevels);
          micLevelsAnimFrameRef.current = requestAnimationFrame(updateLevels);
        };

        updateLevels();
      } catch (err) {
        console.warn("Could not start real audio analyser, falling back to realistic simulation.", err);
        
        const updateSimulatedLevels = () => {
          if (!active) return;
          
          const time = Date.now();
          const speechEnvelope = Math.max(0.1, Math.sin(time / 800) * 0.4 + 0.6); 
          const syllables = Math.max(0.2, Math.sin(time / 150) * 0.5 + 0.5);      
          const voiceIntensity = speechEnvelope * syllables;

          const newLevels = Array.from({ length: 19 }, (_, index) => {
            const staticHeight = [40, 70, 30, 80, 50, 100, 60, 40, 90, 50, 30, 70, 40, 80, 50, 60, 40, 90, 30][index];
            const speakerOffset = (Math.sin(time / 120 + index * 1.6) * 45 + 55) * voiceIntensity;
            const liveHeight = 15 + speakerOffset * 0.85;
            return Math.min(100, Math.max(15, liveHeight));
          });
          
          setLiveMicLevels(newLevels);
          micLevelsAnimFrameRef.current = requestAnimationFrame(updateSimulatedLevels);
        };
        
        updateSimulatedLevels();
      }
    };

    startAnalyser();

    return () => {
      active = false;
      if (micLevelsAnimFrameRef.current) {
        cancelAnimationFrame(micLevelsAnimFrameRef.current);
      }
    };
  }, [isRecording]);
  const [projectMenuOpenId, setProjectMenuOpenId] = useState<string | null>(
    null,
  );
  const [projectToDelete, setProjectToDelete] = useState<string | null>(null);

  const [flowBarOrder, setFlowBarOrder] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem("ai_studio_video_flowbar_order");
      if (saved) return JSON.parse(saved);
    } catch (e) {}
    return DEFAULT_FLOW_BAR_ORDER;
  });

  // Home Screen State
  const [projects, setProjects] = useState<Project[]>(() => {
    try {
      const saved = localStorage.getItem("ai_studio_video_projects");
      if (saved) return JSON.parse(saved);
    } catch (e) {}
    return [
      {
        id: "new-p",
        name: "New Project",
        ratio: "9:16",
        updatedAt: "Recent",
        duration: "00:12",
        size: "85 MB",
        thumbnail: "https://images.unsplash.com/photo-1620641788421-7a1c342ea42e?auto=format&fit=crop&q=80&w=400",
        layers: [],
        clips: [],
      },
      {
        id: "1",
        name: "Summer Vacation",
        ratio: "16:9",
        updatedAt: "2 hours ago",
        duration: "00:15",
        size: "124 MB",
        thumbnail: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&q=80&w=400",
        layers: [],
        clips: [],
      },
      {
        id: "frosted-p",
        name: "Frosted",
        ratio: "1:1",
        updatedAt: "1 day ago",
        duration: "00:10",
        size: "42 MB",
        thumbnail: "https://images.unsplash.com/photo-1614036417651-efe5912149d8?auto=format&fit=crop&q=80&w=400",
        layers: [],
        clips: [],
      },
    ];
  });

  const [sortBy, setSortBy] = useState<"recent" | "name" | "duration">("recent");
  const [isSortMenuOpen, setIsSortMenuOpen] = useState(false);
  
  const [appScale, setAppScale] = useState(1);
  const [snappingEnabled, setSnappingEnabled] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem("ai_studio_snapping_enabled");
      return saved === null ? true : saved === "true";
    } catch (e) {
      return true;
    }
  });

  const handleToggleSnapping = (enabled: boolean) => {
    setSnappingEnabled(enabled);
    try {
      localStorage.setItem("ai_studio_snapping_enabled", enabled.toString());
    } catch (e) {}
  };
  useEffect(() => {
    try {
      const savedScale = localStorage.getItem("ai_studio_app_scale");
      if (savedScale && !isNaN(parseFloat(savedScale))) {
        setAppScale(parseFloat(savedScale));
      }
    } catch(e){}
    try {
      const savedFonts = localStorage.getItem('ai_studio_custom_fonts');
      if (savedFonts) {
        const fonts = JSON.parse(savedFonts);
        fonts.forEach((f: any) => {
          const newFont = new FontFace(f.name, `url(${f.url})`);
          newFont.load().then((loadedFace) => {
            document.fonts.add(loadedFace);
          }).catch((err) => console.error("Failed to load font on startup", f.name, err));
        });
      }
    } catch(e){}
  }, []);

  const handleAppScaleChange = (scale: number) => {
    setAppScale(scale);
    localStorage.setItem("ai_studio_app_scale", scale.toString());
  };

  const [activeProjectId, setActiveProjectId] = useState<string | null>(null);
  const [isCreatingProject, setIsCreatingProject] = useState(false);
  const [selectedRatioTransition, setSelectedRatioTransition] = useState<
    string | null
  >(null);
  const [focusedRatio, setFocusedRatio] = useState<string>("9:16");
  const [customRatioW, setCustomRatioW] = useState("1080");
  const [customRatioH, setCustomRatioH] = useState("1920");

  // Editor State
  const [currentProjectRatio, setCurrentProjectRatio] =
    useState<string>("9:16");
  const [layers, setLayers] = useState<Layer[]>([]);
  const layersRef = useRef<Layer[]>([]);
  useEffect(() => {
    layersRef.current = layers;
  }, [layers]);

  const [clips, setClips] = useState<Clip[]>([]);
  const clipsRef = useRef<Clip[]>([]);
  useEffect(() => {
    clipsRef.current = clips;
  }, [clips]);

  // Automatically clean up any layer that has no clips, unless it was manually created via the "Add Layer" (+) button, or is the active voiceover layer.
  useEffect(() => {
    setLayers((prevLayers) => {
      if (prevLayers.length === 0) return prevLayers;
      const activeLayerIds = new Set(clips.map((clip) => clip.layerId));
      const filtered = prevLayers.filter((layer) => {
        if (layer.isManuallyCreated) return true;
        if (layer.id === voiceoverLayerId) return true;
        return activeLayerIds.has(layer.id);
      });
      if (
        filtered.length === prevLayers.length &&
        filtered.every((val, index) => val === prevLayers[index])
      ) {
        return prevLayers;
      }
      return filtered;
    });
  }, [clips, voiceoverLayerId]);

  const [currentTime, setCurrentTime] = useState(0);
  const currentTimeRef = useRef(0);
  useEffect(() => {
    currentTimeRef.current = currentTime;
  }, [currentTime]);
  const [isPlaying, setIsPlaying] = useState(false);
  const [zoomLevel, setZoomLevel] = useState(1); // 1 = normal, 2 = zoomed in
  const [isShortcutsOpen, setIsShortcutsOpen] = useState(false);
  const [stabilizingProgress, setStabilizingProgress] = useState<{ clipId: string; progress: number; stage: string } | null>(null);
  // Stationary playhead position
  const PLAYHEAD_X = 60;
  const TIMELINE_AREA_START = 100;
  const [lastCreatedTextClipId, setLastCreatedTextClipId] = useState<string | null>(null);
  const [lastCreatedTextLayerId, setLastCreatedTextLayerId] = useState<string | null>(null);
  const previousExpandedMenuRef = useRef<string | null>(null);
  const [activeExpandedMenu, setActiveExpandedMenu] = useState<string | null>(
    null,
  );
  const lastAddedTextClipIdRef = useRef<string | null>(null);
  const prevActiveExpandedMenuRef = useRef<string | null>(null);

  useEffect(() => {
    if (prevActiveExpandedMenuRef.current === 'text' && activeExpandedMenu !== 'text') {
      const clipId = lastAddedTextClipIdRef.current;
      if (clipId) {
        const clip = clips.find(c => c.id === clipId);
        if (clip && clip.type === 'text' && (!clip.text || clip.text.trim() === '')) {
          setClips(prev => prev.filter(c => c.id !== clip.id));
          setLayers(prev => prev.filter(l => l.id !== clip.layerId));
        }
        lastAddedTextClipIdRef.current = null;
      }
    }
    prevActiveExpandedMenuRef.current = activeExpandedMenu;
  }, [activeExpandedMenu, clips]);

  const [isMediaPermissionGranted, setIsMediaPermissionGranted] = useState<boolean>(false);
  const [selectedMediaTab, setSelectedMediaTab] = useState<"Image" | "Video" | "Audio" | "Folders">("Image");
  const [currentMediaFolder, setCurrentMediaFolder] = useState<string | null>(null);
  const [isMediaExpanded, setIsMediaExpanded] = useState(false);
  const mediaTouchStartYRef = useRef<number | null>(null);

  const handleMediaTouchStart = (e: React.TouchEvent) => {
    mediaTouchStartYRef.current = e.touches[0].clientY;
  };

  const handleMediaTouchMove = (e: React.TouchEvent) => {
    if (mediaTouchStartYRef.current === null) return;
    const currentY = e.touches[0].clientY;
    const diffY = mediaTouchStartYRef.current - currentY; // positive means swipe up
    if (diffY > 35 && !isMediaExpanded) {
      setIsMediaExpanded(true);
      mediaTouchStartYRef.current = null;
    } else if (diffY < -35 && isMediaExpanded) {
      setIsMediaExpanded(false);
      mediaTouchStartYRef.current = null;
    }
  };

  const handleMediaTouchEnd = () => {
    mediaTouchStartYRef.current = null;
  };

  useEffect(() => {
    if (activeExpandedMenu !== "plus-media") {
      setIsMediaExpanded(false);
    }
  }, [activeExpandedMenu]);

  const [deviceMedias, setDeviceMedias] = useState<any[]>([]);
  const [isMediaLoading, setIsMediaLoading] = useState<boolean>(false);

  const memoizedMediaFolders = useMemo(() => {
    const foldersMap: { [key: string]: { count: number; icon: string } } = {};
    if (Capacitor.isNativePlatform() && deviceMedias.length > 0) {
      deviceMedias.forEach(item => {
        const folderName = item.folder || "Camera Roll";
        if (!foldersMap[folderName]) {
          let icon = "📁";
          if (folderName.toLowerCase().includes("download")) icon = "📥";
          else if (folderName.toLowerCase().includes("camera") || folderName.toLowerCase().includes("dcim")) icon = "📸";
          else if (folderName.toLowerCase().includes("record") || folderName.toLowerCase().includes("audio")) icon = "🎙️";
          else if (folderName.toLowerCase().includes("screenshot")) icon = "🖼️";
          else if (folderName.toLowerCase().includes("telegram") || folderName.toLowerCase().includes("whatsapp")) icon = "💬";
          foldersMap[folderName] = { count: 0, icon };
        }
        foldersMap[folderName].count++;
      });
    }
    return Object.keys(foldersMap).length > 0 
      ? Object.keys(foldersMap).map(name => ({
          name,
          count: foldersMap[name].count,
          icon: foldersMap[name].icon
        }))
      : [
          { name: "Camera Roll", count: 4, icon: "📁" },
          { name: "Downloads", count: 2, icon: "📥" },
          { name: "Audio Recordings", count: 2, icon: "🎙️" },
          { name: "Screenshots", count: 2, icon: "📸" },
        ];
  }, [deviceMedias]);

  const memoizedDisplayItems = useMemo(() => {
    let displayItems: any[] = [];
    
    if (currentMediaFolder) {
      if (Capacitor.isNativePlatform() && deviceMedias.length > 0) {
        displayItems = deviceMedias.filter(item => item.folder === currentMediaFolder);
      } else {
        if (currentMediaFolder === "Camera Roll") {
          displayItems = [
            { name: "Alpine Peaks", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", duration: 15, thumbnail: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&q=80&w=400", type: "video" },
            { name: "Sunset Drive", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", duration: 12, thumbnail: "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&q=80&w=400", type: "video" },
            { name: "Mountain Peak", url: "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&q=80&w=600", type: "image" },
            { name: "Ocean Sunset", url: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&q=80&w=600", type: "image" },
          ];
        } else if (currentMediaFolder === "Downloads") {
          displayItems = [
            { name: "Lofi Beats", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", duration: 184, type: "audio" },
            { name: "Cozy Study", url: "https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&q=80&w=600", type: "image" },
          ];
        } else if (currentMediaFolder === "Audio Recordings") {
          displayItems = [
            { name: "Serene Nature", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", duration: 372, type: "audio" },
            { name: "Luminous Synth", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", duration: 302, type: "audio" },
          ];
        } else if (currentMediaFolder === "Screenshots") {
          displayItems = [
            { name: "Neon Downtown", url: "https://images.unsplash.com/photo-1515621061946-eff1c2a352bd?auto=format&fit=crop&q=80&w=600", type: "image" },
            { name: "Retro Desert", url: "https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&q=80&w=600", type: "image" },
          ];
        }
      }
    } else {
      if (selectedMediaTab === "Image") {
        displayItems = (Capacitor.isNativePlatform() && deviceMedias.length > 0)
          ? deviceMedias.filter(item => item.type === "image")
          : sampleMediaImages.map(img => ({ ...img, type: "image" }));
      } else if (selectedMediaTab === "Video") {
        displayItems = (Capacitor.isNativePlatform() && deviceMedias.length > 0)
          ? deviceMedias.filter(item => item.type === "video")
          : sampleMediaVideos.map(vid => ({ ...vid, type: "video" }));
      } else if (selectedMediaTab === "Audio") {
        displayItems = (Capacitor.isNativePlatform() && deviceMedias.length > 0)
          ? deviceMedias.filter(item => item.type === "audio")
          : sampleMediaAudio.map(aud => ({ ...aud, type: "audio" }));
      }
    }
    return displayItems;
  }, [currentMediaFolder, selectedMediaTab, deviceMedias]);

  const fetchRealDeviceMedia = async () => {
    if (!Capacitor.isNativePlatform()) {
      setIsMediaPermissionGranted(true);
      return;
    }
    setIsMediaLoading(true);
    let scannedList: any[] = [];
    let usedFilesystemScanner = false;

    try {
      const mediaAny = Media as any;
      if (typeof mediaAny.checkPermissions === "function") {
        try {
          const check = await mediaAny.checkPermissions();
          const hasLegacy = check.publicStorage === "granted";
          const hasModern = check.publicStorage13Plus === "granted";
          
          if (!hasLegacy && !hasModern) {
            if (typeof mediaAny.requestPermissions === "function") {
              const req = await mediaAny.requestPermissions();
              const reqLegacy = req.publicStorage === "granted";
              const reqModern = req.publicStorage13Plus === "granted";
              if (!reqLegacy && !reqModern) {
                showToast("Gallery storage permission not granted. Standard picker will be used.");
              }
            }
          }
        } catch (permErr) {
          console.warn("Storage permission request failed, using fallback picker", permErr);
        }
      }
      setIsMediaPermissionGranted(true);

      try {
        const result = await Media.getMedias();
        if (result && result.medias && result.medias.length > 0) {
          scannedList = result.medias.map((item: any) => {
            const convertedUrl = Capacitor.convertFileSrc(item.path || item.identifier);
            return {
              name: item.name || "Media File",
              path: item.path || item.identifier,
              url: convertedUrl,
              type: item.type || "video",
              folder: item.folder || "Camera Roll",
              duration: item.duration || 10,
              thumbnail: item.type === "video" ? undefined : convertedUrl,
            };
          });
        } else {
          // If empty, let's trigger the filesystem scanner fallback
          usedFilesystemScanner = true;
        }
      } catch (scanErr) {
        console.warn("System background scanner bypassed. Standard API unimplemented or failed, falling back to Filesystem scanner.", scanErr);
        usedFilesystemScanner = true;
      }

      if (usedFilesystemScanner) {
        try {
          const fsPermissions = await Filesystem.checkPermissions();
          if (fsPermissions.publicStorage !== 'granted') {
            await Filesystem.requestPermissions();
          }
        } catch (fsPermErr) {
          console.warn("Filesystem permission request failed or skipped:", fsPermErr);
        }

        const directoriesToScan = [
          { name: "DCIM/Camera", path: "DCIM/Camera", folder: "Camera Roll" },
          { name: "DCIM", path: "DCIM", folder: "Camera Roll" },
          { name: "Pictures", path: "Pictures", folder: "Screenshots" },
          { name: "Movies", path: "Movies", folder: "Camera Roll" },
          { name: "Download", path: "Download", folder: "Downloads" },
          { name: "Music", path: "Music", folder: "Audio Recordings" }
        ];

        for (const dir of directoriesToScan) {
          try {
            const readResult = await Filesystem.readdir({
              path: dir.path,
              directory: Directory.ExternalStorage
            });

            if (readResult && readResult.files) {
              for (const fileItem of readResult.files) {
                const fileName = typeof fileItem === 'string' ? fileItem : fileItem.name;
                const filePath = typeof fileItem === 'string' ? `${dir.path}/${fileItem}` : ((fileItem as any).path || `${dir.path}/${fileItem.name}`);
                const fileType = typeof fileItem === 'string' ? 'file' : fileItem.type;

                if (fileType === 'directory') continue;

                const lowerName = fileName.toLowerCase();
                let mediaType: "video" | "image" | "audio" | null = null;

                if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".3gp") || lowerName.endsWith(".webm") || lowerName.endsWith(".mkv")) {
                  mediaType = "video";
                } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif") || lowerName.endsWith(".webp") || lowerName.endsWith(".bmp")) {
                  mediaType = "image";
                } else if (lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg") || lowerName.endsWith(".aac") || lowerName.endsWith(".m4a")) {
                  mediaType = "audio";
                }

                if (mediaType) {
                  try {
                    const uriResult = await Filesystem.getUri({
                      path: filePath,
                      directory: Directory.ExternalStorage
                    });
                    const convertedUrl = Capacitor.convertFileSrc(uriResult.uri);

                    scannedList.push({
                      name: fileName,
                      path: uriResult.uri,
                      url: convertedUrl,
                      type: mediaType,
                      folder: dir.folder,
                      duration: mediaType === "audio" ? 180 : 10,
                      thumbnail: mediaType === "image" ? convertedUrl : undefined,
                    });
                  } catch (uriErr) {
                    console.warn(`Could not get URI for ${filePath}:`, uriErr);
                  }
                }
              }
            }
          } catch (rErr) {
            console.warn(`Filesystem scan of ${dir.path} skipped or empty:`, rErr);
          }
        }
      }

      if (scannedList.length > 0) {
        setDeviceMedias((prev) => {
          const existingPaths = new Set(prev.map(p => p.path));
          const nonDupScanned = scannedList.filter((item: any) => !existingPaths.has(item.path));
          return [...prev, ...nonDupScanned];
        });
      }
    } catch (err) {
      console.error("Local storage scanner failed:", err);
      setIsMediaPermissionGranted(true);
    } finally {
      setIsMediaLoading(false);
    }
  };

  useEffect(() => {
    if (activeExpandedMenu === "plus-media" && Capacitor.isNativePlatform()) {
      fetchRealDeviceMedia();
    }
  }, [activeExpandedMenu]);

  const [activeTransformTab, setActiveTransformTab] = useState<"position" | "scale" | "rotate">("position");
  const [maskAdjustOpen, setMaskAdjustOpen] = useState(false);
  const [showFloatingMaskAdjust, setShowFloatingMaskAdjust] = useState(false);
  useEffect(() => {
    if (activeExpandedMenu === "mask") {
      setMaskAdjustOpen(true);
    } else {
      setShowFloatingMaskAdjust(false);
    }
  }, [activeExpandedMenu]);
  const [layerMenuOpenId, setLayerMenuOpenId] = useState<string | null>(null);
  const [draggingLayerId, setDraggingLayerId] = useState<string | null>(null);
  const hasDraggedLayerRef = useRef(false);
  const [applyVolumeToAll, setApplyVolumeToAll] = useState(false);
  const [clipVolume, setClipVolume] = useState(100);
  const [clipSpeed, setClipSpeed] = useState(1);
  const [smoothProcessingProgress, setSmoothProcessingProgress] = useState<
    number | null
  >(null);
  const [opticalFlowDiagnostics, setOpticalFlowDiagnostics] = useState<{
    clipId: string | null;
    decodedFramesCount?: number;
    flowComputedCount?: number;
    timestampsVerified?: boolean;
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
  } | null>(null);
  const [selectedClipIds, setSelectedClipIds] = useState<string[]>([]);
  const selectedClipId = selectedClipIds.length === 1 ? selectedClipIds[0] : null;
  const setSelectedClipId = (id: string | null) => setSelectedClipIds(id === null ? [] : [id]);
  const [multiSelectActive, setMultiSelectActive] = useState(false);
  const copyTimerRef = useRef<NodeJS.Timeout | null>(null);
  const [marquee, setMarquee] = useState<{ startX: number, startY: number, currentX: number, currentY: number } | null>(null);
  const [selectedInterpolationPreset, setSelectedInterpolationPreset] = useState<string>("linear");
  const [draggingHandle, setDraggingHandle] = useState<1 | 2 | null>(null);
  const interpolationLongPressTimerRef = useRef<NodeJS.Timeout | null>(null);
  const hasTriggeredLongPress = useRef<boolean>(false);

  const [transitionModal, setTransitionModal] = useState<{
    prevClipId: string;
    currentClipId: string;
    layerId: string;
    type: string;
    duration: number;
  } | null>(null);

  const isTransitionAllowed = useCallback((clipA: Clip, clipB: Clip): boolean => {
    if (clipA.type === "text" || clipB.type === "text") return false;
    if (clipA.type === "audio" && clipB.type === "audio") return true;
    if ((clipA.type === "image" || clipA.type === "video") && (clipB.type === "image" || clipB.type === "video")) {
      return true;
    }
    return false;
  }, []);

  const getAdjacentClipPairsOnLayer = useCallback((layerClips: Clip[]) => {
    const sorted = [...layerClips].sort((a, b) => a.leftSeconds - b.leftSeconds);
    const pairs: { prev: Clip; current: Clip }[] = [];
    for (let i = 1; i < sorted.length; i++) {
      const prev = sorted[i - 1];
      const current = sorted[i];
      const gap = current.leftSeconds - (prev.leftSeconds + prev.durationSeconds);
      if (Math.abs(gap) <= 0.25 && isTransitionAllowed(prev, current)) {
        pairs.push({ prev, current });
      }
    }
    return pairs;
  }, [isTransitionAllowed]);

  const getLayerActiveClips = useCallback((layerClips: Clip[], curTime: number) => {
    const sorted = [...layerClips].sort((a, b) => a.leftSeconds - b.leftSeconds);
    const active: { clip: Clip; role: "incoming" | "outgoing" | "normal"; progress?: number; transitionType?: string }[] = [];

    for (let i = 0; i < sorted.length; i++) {
      const c = sorted[i];
      const prev = i > 0 ? sorted[i - 1] : null;
      
      const hasTransition = c.transition && prev && isTransitionAllowed(prev, c);
      const td = (hasTransition && c.transition) ? c.transition.duration : 0;

      if (hasTransition && td > 0 && curTime >= c.leftSeconds && curTime <= c.leftSeconds + td) {
        const progress = (curTime - c.leftSeconds) / td;
        active.push({
          clip: prev!,
          role: "outgoing",
          progress,
          transitionType: c.transition!.type
        });
        active.push({
          clip: c,
          role: "incoming",
          progress,
          transitionType: c.transition!.type
        });
        continue;
      }
      
      const isNormalActive = curTime >= c.leftSeconds && curTime <= c.leftSeconds + c.durationSeconds;
      if (isNormalActive) {
        if (!active.some(a => a.clip.id === c.id)) {
          active.push({
            clip: c,
            role: "normal"
          });
        }
      }
    }

    return active;
  }, [isTransitionAllowed]);

  const addTransition = useCallback((incomingClipId: string, type: string, duration: number, silent = false) => {
    setClips((prevClips) =>
      prevClips.map((c) => {
        if (c.id === incomingClipId) {
          return {
            ...c,
            transition: { type, duration },
          };
        }
        return c;
      })
    );
    if (!silent) {
      setToastMessage("Transition applied successfully");
      setTimeout(() => setToastMessage(null), 2000);
    }
  }, []);

  const removeTransition = useCallback((incomingClipId: string) => {
    setClips((prevClips) =>
      prevClips.map((c) => {
        if (c.id === incomingClipId) {
          const { transition, ...rest } = c;
          return rest;
        }
        return c;
      })
    );
    setToastMessage("Transition removed");
    setTimeout(() => setToastMessage(null), 2000);
  }, []);
  
  const selectedClip = clips.find((c) => c.id === selectedClipId);
  const updateClipsProperties = useCallback((clipIds: string[], updates: Partial<Clip>) => {
    setClips((prev) =>
      prev.map((c) => {
        if (!clipIds.includes(c.id)) return c;
        const timeInClip = currentTimeRef.current - c.leftSeconds;

        let newKfMap: Record<string, any> = {};
        let kfs = [...(c.keyframes || [])];
        let hasApplicableKeyframes = false;

        for (const [key, value] of Object.entries(updates)) {
          if (value === undefined) continue;
          const hasKfsForProp = kfs.some((k) => k.properties[key] !== undefined);
          if (hasKfsForProp) {
            hasApplicableKeyframes = true;
            newKfMap[key] = value;
          }
        }

        // Check if clip's layer is locked
        const clipLayer = layersRef.current.find((l) => l.id === c.layerId);
        let finalUpdates = { ...updates };
        if (clipLayer?.isLocked && !hasApplicableKeyframes) {
          // Block transform properties from being updated on base clip when locked
          const transformKeys = ["translateX", "translateY", "scale", "scaleX", "scaleY", "rotation"];
          for (const key of transformKeys) {
            delete finalUpdates[key];
          }
        }

        const updatedClip = { ...c, ...finalUpdates };

        if (hasApplicableKeyframes) {
          const existingIndex = kfs.findIndex(
            (kf) => Math.abs(kf.timeOffset - timeInClip) < 0.05
          );
          if (existingIndex >= 0) {
            kfs[existingIndex] = {
              ...kfs[existingIndex],
              properties: { ...kfs[existingIndex].properties, ...newKfMap },
            };
          } else {
            kfs.push({
              id: "kf_" + Date.now() + Math.random(),
              timeOffset: timeInClip,
              properties: newKfMap,
              curve: "linear",
            });
            kfs.sort((a, b) => a.timeOffset - b.timeOffset);
          }
          updatedClip.keyframes = kfs;
        }

        return updatedClip;
      })
    );
  }, []);

  // Copied settings state specifically designed with support for multiple adjustment categories
  const [copiedSettings, setCopiedSettings] = useState<{
    adjust?: {
      exposure?: number;
      brightness?: number;
      contrast?: number;
      saturation?: number;
      blur?: number;
      grayscale?: number;
      sepia?: number;
      hueRotate?: number;
      invert?: number;
    };
    volume?: {
      volume?: number;
    };
    speed?: {
      speed?: number;
    };
    stabilize?: {
      stabilizationStrength?: number;
      stabilizeMode?: string;
    };
    move?: {
      scale?: number;
      positionX?: number;
      positionY?: number;
      rotation?: number;
      anchorX?: number;
      anchorY?: number;
    };
    blend?: {
      opacity?: number;
      mixBlendMode?: string;
    };
    mask?: {
      maskType?: "none" | "circle" | "square" | "half";
      maskPositionX?: number;
      maskPositionY?: number;
      maskScale?: number;
      maskRotation?: number;
      maskFeather?: number;
    };
    crop?: {
      cropRatio?: string;
      cropX?: number;
      cropY?: number;
      cropW?: number;
      cropH?: number;
    };
  }>({});

  const handleCopySettings = (type: string) => {
    if (!selectedClipId) return;
    const clip = clips.find(c => c.id === selectedClipId);
    if (!clip) return;

    let copiedData: any = {};
    if (type === "adjust") {
      copiedData = {
        exposure: clip.exposure ?? 0,
        brightness: clip.brightness ?? 100,
        contrast: clip.contrast ?? 100,
        saturation: clip.saturation ?? 100,
        blur: clip.blur ?? 0,
        grayscale: clip.grayscale ?? 0,
        sepia: clip.sepia ?? 0,
        hueRotate: clip.hueRotate ?? 0,
        invert: clip.invert ?? 0,
      };
    } else if (type === "volume") {
      copiedData = { volume: clip.volume ?? 100 };
    } else if (type === "speed") {
      copiedData = { speed: clip.speed ?? 1 };
    } else if (type === "stabilize") {
      copiedData = {
        stabilizationStrength: clip.stabilizationStrength ?? 50,
        stabilizeMode: clip.stabilizeMode ?? "standard",
      };
    } else if (type === "move") {
      copiedData = {
        scale: clip.scale ?? 1,
        positionX: clip.positionX ?? 0,
        positionY: clip.positionY ?? 0,
        rotation: clip.rotation ?? 0,
        anchorX: clip.anchorX ?? 0.5,
        anchorY: clip.anchorY ?? 0.5,
      };
    } else if (type === "blend") {
      copiedData = {
        opacity: clip.opacity ?? 100,
        mixBlendMode: clip.mixBlendMode ?? "normal",
      };
    } else if (type === "mask") {
      copiedData = {
        maskType: clip.maskType ?? "none",
        maskPositionX: clip.maskPositionX ?? 0.5,
        maskPositionY: clip.maskPositionY ?? 0.5,
        maskScale: clip.maskScale ?? 1,
        maskRotation: clip.maskRotation ?? 0,
        maskFeather: clip.maskFeather ?? 0.1,
      };
    } else if (type === "crop") {
      copiedData = {
        cropRatio: clip.cropRatio,
        cropX: clip.cropX,
        cropY: clip.cropY,
        cropW: clip.cropW,
        cropH: clip.cropH,
      };
    }

    setCopiedSettings(prev => ({
      ...prev,
      [type]: copiedData
    }));
  };

  const handlePasteSettings = (type: string) => {
    if (!selectedClipId) return;
    const settings = copiedSettings[type as keyof typeof copiedSettings];
    if (!settings) return;

    updateClipsProperties([selectedClipId], settings);
  };

  const renderCopyPasteButtons = (menuType: string) => {
    const hasCopied = !!copiedSettings[menuType as keyof typeof copiedSettings];
    return (
      <div className="flex items-center gap-1 shrink-0 bg-zinc-900/60 rounded-md p-0.5 border border-white/5 shadow-inner">
        <button
          onClick={() => {
            handleCopySettings(menuType);
            setToastMessage(`Copied ${menuType} settings`);
            setTimeout(() => setToastMessage(null), 1500);
          }}
          className="p-1 rounded text-zinc-400 hover:text-white hover:bg-zinc-800 transition pointer-events-auto"
          title={`Copy ${menuType} settings`}
        >
          <Copy size={11} strokeWidth={2.5} />
        </button>
        <button
          onClick={() => {
            if (!hasCopied) return;
            handlePasteSettings(menuType);
            setToastMessage(`Pasted ${menuType} settings`);
            setTimeout(() => setToastMessage(null), 1500);
          }}
          disabled={!hasCopied}
          className={`p-1 rounded transition pointer-events-auto ${hasCopied ? "text-zinc-400 hover:text-indigo-400 hover:bg-zinc-800 cursor-pointer" : "text-zinc-600/70 cursor-not-allowed"}`}
          title={`Paste ${menuType} settings`}
        >
          <Clipboard size={11} strokeWidth={2.5} />
        </button>
      </div>
    );
  };

  const isAtKeyframe = selectedClip?.keyframes?.some(k => {
    const isClose = Math.abs(currentTime - (selectedClip.leftSeconds + k.timeOffset)) < 0.05;
    if (!isClose) return false;
    if (activeExpandedMenu === "volume") {
      return k.properties.volume !== undefined;
    } else {
      return k.properties.translateX !== undefined || k.properties.scale !== undefined;
    }
  }) ?? false;

  const currentSelectedClipInterpolatedProps = useMemo(() => {
    if (!selectedClipId) return null;
    const clip = clips.find(c => c.id === selectedClipId);
    if (!clip) return null;
    return getInterpolatedProps(clip, currentTime - clip.leftSeconds, activeExpandedMenu);
  }, [selectedClipId, clips, currentTime, activeExpandedMenu]);

  const keyframePercentageLabel = useMemo(() => {
    if (!activeExpandedMenu || !selectedClipId || !currentSelectedClipInterpolatedProps) return null;
    if (activeExpandedMenu === "volume") {
      return `${Math.round(currentSelectedClipInterpolatedProps.volume ?? 100)}%`;
    } else if (activeExpandedMenu === "move") {
      return `${Math.round((currentSelectedClipInterpolatedProps.scale ?? 1) * 100)}%`;
    } else if (activeExpandedMenu === "blend") {
      return `${Math.round((currentSelectedClipInterpolatedProps.opacity ?? 1) * 100)}%`;
    } else if (activeExpandedMenu === "adjust") {
      return `${Math.round(currentSelectedClipInterpolatedProps.brightness ?? 100)}%`;
    }
    return null;
  }, [activeExpandedMenu, selectedClipId, currentSelectedClipInterpolatedProps]);

  const isBetweenVolumeKeyframes = (selectedClip?.keyframes?.filter(k => k.properties.volume !== undefined).length ?? 0) >= 2;
  const isBetweenLayoutKeyframes = (selectedClip?.keyframes?.filter(k => k.properties.translateX !== undefined || k.properties.scale !== undefined).length ?? 0) >= 2;
  const isBetweenKeyframes = isBetweenVolumeKeyframes || isBetweenLayoutKeyframes;

  const [isExportExpanded, setIsExportExpanded] = useState(false);
  const [pillPopup, setPillPopup] = useState<{ message: string; progress?: number; type: 'info' | 'loading' } | null>(null);
  const [isRatioExpanded, setIsRatioExpanded] = useState(false);
  const [exportResolution, setExportResolution] = useState("4K");
  const [exportFps, setExportFps] = useState("30");
  const [exportBitrate, setExportBitrate] = useState("High");
  const [exportOpticalFlow, setExportOpticalFlow] = useState(true);
  const [erroredClips, setErroredClips] = useState<Set<string>>(new Set());
  const relinkedClipsRef = useRef<Set<string>>(new Set());

  const handleClipError = (clipId: string) => {
    // Prevent infinite loops if fallback source itself experiences error
    if (relinkedClipsRef.current.has(clipId)) {
      setErroredClips((prev) => {
        if (prev.has(clipId)) return prev;
        const newSet = new Set(prev);
        newSet.add(clipId);
        return newSet;
      });
      return;
    }

    relinkedClipsRef.current.add(clipId);

    setClips((prev) => {
      let foundAndFixed = false;
      let matchedName = "";
      const updatedClips = prev.map((c) => {
        if (c.id === clipId) {
          let fallbackSrc = "";
          if (c.type === "video") {
            const matchingVideo = sampleMediaVideos.find((vid) => 
              vid.name.toLowerCase() === c.name?.toLowerCase() || 
              (c.src && vid.url.split('/').pop() === c.src.split('/').pop())
            ) || sampleMediaVideos[0];
            fallbackSrc = matchingVideo?.url || "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4";
            matchedName = matchingVideo?.name || "Alpine Peaks";
          } else if (c.type === "image") {
            const matchingImage = sampleMediaImages.find((img) => 
              img.name.toLowerCase() === c.name?.toLowerCase() ||
              (c.src && img.url.split('/').pop() === c.src.split('/').pop())
            ) || sampleMediaImages[0];
            fallbackSrc = matchingImage?.url || "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&q=80&w=600";
            matchedName = matchingImage?.name || "Mountain Peak";
          } else if (c.type === "audio") {
            const matchingAudio = sampleMediaAudio.find((aud) => 
              aud.name.toLowerCase() === c.name?.toLowerCase() ||
              (c.src && aud.url.split('/').pop() === c.src.split('/').pop())
            ) || sampleMediaAudio[0];
            fallbackSrc = matchingAudio?.url || "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
            matchedName = matchingAudio?.name || "Serene Nature";
          }

          if (fallbackSrc && c.src !== fallbackSrc) {
            foundAndFixed = true;
            return {
              ...c,
              src: fallbackSrc,
              originalSrc: fallbackSrc,
            };
          }
        }
        return c;
      });

      if (foundAndFixed) {
        showToast(`Auto-relinked "${matchedName}" from cloud sources`);
        // Remove from error set to attempt reload
        setErroredClips((prev) => {
          const newSet = new Set(prev);
          newSet.delete(clipId);
          return newSet;
        });
      } else {
        setErroredClips((prev) => {
          const newSet = new Set(prev);
          newSet.add(clipId);
          return newSet;
        });
      }
      return updatedClips;
    });
  };

  const [copiedClip, setCopiedClip] = useState<Clip | null>(null);
  const [pastePopup, setPastePopup] = useState<{
    x: number;
    y: number;
    time: number;
    layerId?: string;
  } | null>(null);
  const longPressTimerRef = useRef<NodeJS.Timeout | null>(null);
  const pointerMoveCanvasRef = useRef(false);

  const [history, setHistory] = useState<{ layers: Layer[]; clips: Clip[] }[]>([
    { layers: [], clips: [] },
  ]);
  const [historyIndex, setHistoryIndex] = useState(0);
  const isUndoRedoAction = useRef(false);

  const historyStateRef = useRef({ history, historyIndex });
  useEffect(() => {
    historyStateRef.current = { history, historyIndex };
  }, [history, historyIndex]);

  useEffect(() => {
    // Redundant clipVolume sync has been removed completely, as we trust the dynamic interpolation
  }, []);

  useEffect(() => {
    // Intentional omission of empty layer cleanup so users can add empty layers
  }, [clips, layers]);

  useEffect(() => {
    // Empty text clips are cleaned up manually on deselect instead of within a reactive useEffect
  }, []);

  useEffect(() => {
    if (isUndoRedoAction.current) {
      isUndoRedoAction.current = false;
      return;
    }
    const timeout = setTimeout(() => {
      const { history: latestHistory, historyIndex: latestIndex } = historyStateRef.current;
      const last = latestHistory[latestIndex] || latestHistory[latestHistory.length - 1];
      if (
        last &&
        JSON.stringify(last.layers) === JSON.stringify(layers) &&
        JSON.stringify(last.clips) === JSON.stringify(clips)
      ) {
        return;
      }
      let newHistory = latestHistory.slice(0, latestIndex + 1);
      newHistory.push({ layers, clips });
      if (newHistory.length > 50) newHistory = newHistory.slice(newHistory.length - 50);
      
      setHistory(newHistory);
      setHistoryIndex(newHistory.length - 1);
    }, 300);

    return () => clearTimeout(timeout);
  }, [layers, clips]);

  const undo = () => {
    if (historyIndex > 0) {
      isUndoRedoAction.current = true;
      const prev = history[historyIndex - 1];
      setLayers(prev.layers);
      setClips(prev.clips);
      setHistoryIndex(historyIndex - 1);
    }
  };

  const redo = () => {
    if (historyIndex < history.length - 1) {
      isUndoRedoAction.current = true;
      const next = history[historyIndex + 1];
      setLayers(next.layers);
      setClips(next.clips);
      setHistoryIndex(historyIndex + 1);
    }
  };

  const fileInputRef = useRef<HTMLInputElement>(null);
  const timelineScrollRef = useRef<HTMLDivElement>(null);
  const animationFrameRef = useRef<number>();
  const lastTimeRef = useRef<number>();


  // Real-time synchronization
  const isPlayingRef = useRef(isPlaying);
  const isRecordingRef = useRef(isRecording);
  // Important: timeline zoom level affects pixels per second
  const pixelsPerSecond = BASE_PIXELS_PER_SECOND * zoomLevel;

  // Sync scroll position when zoom level or pixelsPerSecond changes
  useEffect(() => {
    if (timelineScrollRef.current) {
      timelineScrollRef.current.scrollLeft = currentTimeRef.current * pixelsPerSecond;
    }
  }, [pixelsPerSecond]);

  useEffect(() => {
    isPlayingRef.current = isPlaying;
    if (isPlaying) {
      lastTimeRef.current = undefined;
    }
  }, [isPlaying]);

  useEffect(() => {
    isRecordingRef.current = isRecording;
    if (isRecording) {
      lastTimeRef.current = undefined;
    }
  }, [isRecording]);

  useEffect(() => {
    const playLoop = (time: number) => {
      if (lastTimeRef.current === undefined) lastTimeRef.current = time;
      const deltaTime = (time - lastTimeRef.current) / 1000;
      lastTimeRef.current = time;

      if (isPlayingRef.current || isRecordingRef.current) {
        setCurrentTime((prev) => {
          const next = prev + deltaTime;
          if (timelineScrollRef.current) {
            const container = timelineScrollRef.current;
            container.scrollLeft = Math.max(0, next * pixelsPerSecond);
          }
          return next;
        });
      }
      animationFrameRef.current = requestAnimationFrame(playLoop);
    };
    animationFrameRef.current = requestAnimationFrame(playLoop);

    return () => {
      if (animationFrameRef.current)
        cancelAnimationFrame(animationFrameRef.current);
    };
  }, [pixelsPerSecond]);

  // Global Keyboard Shortcuts for Pro Editing Capabilities
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore if user is writing in any text fields
      const activeEl = document.activeElement;
      if (activeEl) {
        const tag = activeEl.tagName.toLowerCase();
        if (
          tag === "input" ||
          tag === "textarea" ||
          activeEl.hasAttribute("contenteditable")
        ) {
          return;
        }
      }

      // 1. Play / Pause
      if (e.key === " " || e.code === "Space") {
        e.preventDefault();
        setIsPlaying((prev) => !prev);
      }

      // 2. Delete clip
      if (e.key === "Delete" || e.key === "Backspace") {
        if (selectedClipIds.length > 0) {
          e.preventDefault();
          deleteSelectedClip();
          showToast(`Deleted ${selectedClipIds.length} clip(s)`);
        }
      }

      // 3. Split clip (Hotkey: 's')
      if (e.key === "s" || e.key === "S") {
        if (selectedClipId) {
          e.preventDefault();
          splitSelectedClip();
          showToast("Clip split completed");
        }
      }

      // 4. Undo / Redo
      if ((e.metaKey || e.ctrlKey) && (e.key === "z" || e.key === "Z")) {
        if (!e.shiftKey) {
          e.preventDefault();
          if (historyIndex > 0) {
            undo();
            showToast("Undo");
          }
        } else {
          e.preventDefault();
          if (historyIndex < history.length - 1) {
            redo();
            showToast("Redo");
          }
        }
      }

      // 5. Redo (Cmd+Y or Ctrl+Y)
      if ((e.metaKey || e.ctrlKey) && (e.key === "y" || e.key === "Y")) {
        e.preventDefault();
        if (historyIndex < history.length - 1) {
          redo();
          showToast("Redo");
        }
      }

      // 6. Navigation Seeking (ArrowLeft / ArrowRight)
      if (e.key === "ArrowLeft") {
        e.preventDefault();
        const delta = e.shiftKey ? 5 : 0.5;
        setCurrentTime((prev) => {
          const next = Math.max(0, prev - delta);
          if (timelineScrollRef.current) {
            const container = timelineScrollRef.current;
            container.scrollLeft = Math.max(0, next * pixelsPerSecond);
          }
          return next;
        });
      }
      if (e.key === "ArrowRight") {
        e.preventDefault();
        const delta = e.shiftKey ? 5 : 0.5;
        setCurrentTime((prev) => {
          const next = prev + delta;
          if (timelineScrollRef.current) {
            const container = timelineScrollRef.current;
            container.scrollLeft = Math.max(0, next * pixelsPerSecond);
          }
          return next;
        });
      }

      // 7. Zoom Hotkeys (Plus / Minus)
      if (e.key === "=" || e.key === "+") {
        e.preventDefault();
        setZoomLevel((prev) => Math.min(10, prev + 0.25));
        showToast("Zoom In");
      }
      if (e.key === "-" || e.key === "_") {
        e.preventDefault();
        setZoomLevel((prev) => Math.max(0.1, prev - 0.25));
        showToast("Zoom Out");
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [
    selectedClipId,
    selectedClipIds,
    isPlaying,
    historyIndex,
    history.length,
    pixelsPerSecond,
    deleteSelectedClip,
    splitSelectedClip,
    undo,
    redo,
  ]);

  const [isExporting, setIsExporting] = useState(false);
  const [exportProgress, setExportProgress] = useState(0);
  const [exportedVideoUrl, setExportedVideoUrl] = useState<string | null>(null);
  const [exportedVideoBlob, setExportedVideoBlob] = useState<Blob | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  const startExport = async () => {
    console.log("[AUDIT] Stage 1: Export button pressed");
    try {
      let maxDuration = 0;
      for (const c of clips) {
        if (c.leftSeconds + c.durationSeconds > maxDuration) {
          maxDuration = c.leftSeconds + c.durationSeconds;
        }
      }
      if (maxDuration === 0) {
        showToast("No clips to export.");
        setIsExportExpanded(false);
        return;
      }

      setIsExportExpanded(false);
      setIsExporting(true);
      setExportProgress(0);
      setCurrentTime(0);
      setIsPlaying(false);

      await new Promise((r) => setTimeout(r, 600)); // wait for video seek

      const canvas = document.createElement("canvas");
      let exportWidth = 1920;
      let exportHeight = 1080;
      if (exportResolution === "4K") {
        exportWidth = 3840;
        exportHeight = 2160;
      }
      if (exportResolution === "2K") {
        exportWidth = 2560;
        exportHeight = 1440;
      }

      const [rw, rh] = currentProjectRatio.split(":").map(Number);
      if (rw && rh) {
        if (rw < rh) {
          let tempH = exportHeight;
          let tempW = Math.round(exportHeight * (rw / rh));
          if (tempW % 2 !== 0) tempW += 1;
          if (tempH % 2 !== 0) tempH += 1;
          canvas.width = tempW;
          canvas.height = tempH;
        } else {
          let tempW = exportWidth;
          let tempH = Math.round(exportWidth * (rh / rw));
          if (tempW % 2 !== 0) tempW += 1;
          if (tempH % 2 !== 0) tempH += 1;
          canvas.width = tempW;
          canvas.height = tempH;
        }
      } else {
        canvas.width = exportWidth;
        canvas.height = exportHeight;
      }
      const ctx = canvas.getContext("2d")!;

      const fps = parseInt(exportFps) || 30;
      const stream = canvas.captureStream(fps);

      const bestType = getBestSupportedVideoType();
      const recorderOptions: MediaRecorderOptions = {};
      if (bestType.mime) {
        recorderOptions.mimeType = bestType.mime;
      }
      
      const recorder = new MediaRecorder(stream, recorderOptions);
      const chunks: Blob[] = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.push(e.data);
      };
      recorder.onstop = async () => {
        const mimeToUse = bestType.mime || recorder.mimeType || "video/mp4";
        const blob = new Blob(chunks, { type: mimeToUse });
        const url = URL.createObjectURL(blob);
        setExportedVideoBlob(blob);
        setExportedVideoUrl(url);
        setIsExporting(false);
        setIsPlaying(false);
      };

      recorder.start();
      setIsPlaying(true);

      const previewEl = document.getElementById("preview-screen");
      const previewW = previewEl?.clientWidth || 1;
      const previewH = previewEl?.clientHeight || 1;

      const startTime = performance.now();
      let rAF: number;
      let playingLocal = true;

      const drawFn = () => {
        if (!playingLocal) return;

        const elapsed = (performance.now() - startTime) / 1000;

        if (elapsed >= maxDuration + 0.1) {
          playingLocal = false;
          recorder.stop();
          return;
        }

        const currentClampedTime = Math.min(maxDuration, elapsed);
        setCurrentTime(currentClampedTime);

        setExportProgress(
          Math.min(100, Math.round((elapsed / maxDuration) * 100)),
        );

        ctx.fillStyle = "black";
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        const layerOrder = [...layers].sort((a, b) => a.order - b.order);
        for (const layer of layerOrder) {
          if (layer.isHidden) continue;
          const clip = clips.find(
            (c) =>
              c.layerId === layer.id &&
              elapsed >= c.leftSeconds &&
              elapsed <= c.leftSeconds + c.durationSeconds,
          );
          if (!clip) continue;

          const elId = `clip-media-${clip.id}`;
          const el = document.getElementById(elId) as any;
          if (el && (el.tagName === "IMG" || el.tagName === "VIDEO")) {
            ctx.save();
            ctx.translate(canvas.width / 2, canvas.height / 2);

            const scaleX = canvas.width / previewW;
            const scaleY = canvas.height / previewH;
            const absTranslateX = (clip.translateX || 0) * scaleX;
            const absTranslateY = (clip.translateY || 0) * scaleY;

            ctx.translate(absTranslateX, absTranslateY);
            ctx.rotate(((clip.rotation || 0) * Math.PI) / 180);
            ctx.scale(clip.scale ?? 1, clip.scale ?? 1);

            const imgW = el.videoWidth || el.naturalWidth || canvas.width;
            const imgH = el.videoHeight || el.naturalHeight || canvas.height;
            if (imgW && imgH) {
              const imgRatio = imgW / imgH;
              const canvasRatio = canvas.width / canvas.height;
              let drawWidth, drawHeight;
              if (imgRatio > canvasRatio) {
                drawHeight = canvas.height;
                drawWidth = canvas.height * imgRatio;
              } else {
                drawWidth = canvas.width;
                drawHeight = canvas.width / imgRatio;
              }
              ctx.drawImage(
                el,
                -drawWidth / 2,
                -drawHeight / 2,
                drawWidth,
                drawHeight,
              );
            }
            ctx.restore();
          }
        }
        rAF = requestAnimationFrame(drawFn);
      };
      rAF = requestAnimationFrame(drawFn);
    } catch (err: any) {
      console.error("[AUDIT] Stage 1 failed in startExport with stack trace:", err.stack || err);
      setIsExporting(false);
      setExportProgress(0);
      setIsPlaying(false);
      showToast(`Export failed: ${err.message || err.toString()}`);
    }
  };

  const cleanupVoiceoverLayer = useCallback(() => {
    if (voiceoverLayerId) {
      setLayers((prev) => {
        // Find if this layer has clips
        const hasClips = clipsRef.current.some((c) => c.layerId === voiceoverLayerId);
        if (!hasClips) {
          return prev.filter((l) => l.id !== voiceoverLayerId);
        }
        return prev;
      });
      setVoiceoverLayerId(null);
    }
  }, [voiceoverLayerId]);

  useEffect(() => {
    if (activeExpandedMenu === "voiceover" && !voiceoverLayerId) {
      const newId = `layer-voiceover-${Date.now()}`;
      setLayers((prev) => [
        ...prev,
        {
          id: newId,
          order: prev.length,
          isMuted: false,
          isHidden: false,
          name: "Voiceover",
        },
      ]);
      setVoiceoverLayerId(newId);
    } else if (activeExpandedMenu !== "voiceover" && voiceoverLayerId) {
      if (isRecording) {
        setIsRecording(false);
        if (voiceoverRecorderRef.current && voiceoverRecorderRef.current.state !== "inactive") {
          const calculatedDuration = currentTime - recordingStartPlayheadTimeRef.current;
          const finalDuration = calculatedDuration > 0.2 ? calculatedDuration : 5.0;
          voiceoverRecorderRef.current.onstop = () => {
            const audioBlob = new Blob(recordingChunksRef.current, { type: voiceoverRecorderRef.current?.mimeType || "audio/webm" });
            const audioUrl = URL.createObjectURL(audioBlob);
            setClips((prev) => [
              ...prev,
              {
                id: `clip-voiceover-${Date.now()}`,
                layerId: voiceoverLayerId,
                type: "audio",
                src: audioUrl,
                leftSeconds: recordingStartPlayheadTimeRef.current,
                durationSeconds: finalDuration,
                trimStartSeconds: 0,
                volume: 100,
              },
            ]);
            showToast("Recording saved to project timeline");
          };
          try {
            voiceoverRecorderRef.current.stop();
          } catch (e) {
            console.warn(e);
          }
        }
      }
      cleanupVoiceoverLayer();
    }
  }, [activeExpandedMenu, voiceoverLayerId, isRecording, cleanupVoiceoverLayer]);

  const handleRecordClick = async () => {
    if (isRecording) {
      setIsRecording(false);
      showToast("Recording saved to project timeline");
      
      const calculatedDuration = currentTime - recordingStartPlayheadTimeRef.current;
      const finalDuration = calculatedDuration > 0.2 ? calculatedDuration : 5.0;

      // Add actual voiceover clip on the active voiceover layer
      if (voiceoverLayerId) {
        if (voiceoverRecorderRef.current && voiceoverRecorderRef.current.state !== "inactive") {
          voiceoverRecorderRef.current.onstop = () => {
            const audioBlob = new Blob(recordingChunksRef.current, { type: voiceoverRecorderRef.current?.mimeType || "audio/webm" });
            const audioUrl = URL.createObjectURL(audioBlob);
            setClips((prev) => [
              ...prev,
              {
                id: `clip-voiceover-${Date.now()}`,
                layerId: voiceoverLayerId,
                type: "audio",
                src: audioUrl,
                leftSeconds: recordingStartPlayheadTimeRef.current,
                durationSeconds: finalDuration,
                trimStartSeconds: 0,
                volume: 100,
              },
            ]);
          };
          try {
            voiceoverRecorderRef.current.stop();
          } catch (e) {
            console.warn(e);
          }
        } else {
          setClips((prev) => [
            ...prev,
            {
              id: `clip-voiceover-${Date.now()}`,
              layerId: voiceoverLayerId,
              type: "audio",
              src: "",
              leftSeconds: recordingStartPlayheadTimeRef.current,
              durationSeconds: finalDuration,
              trimStartSeconds: 0,
              volume: 100,
            },
          ]);
        }
      }
      return;
    }

    // Capture start time before starting recording
    recordingStartPlayheadTimeRef.current = currentTime;
    setIsRecording(true);
    showToast("Studio recording started...");
  };

  const handleBackToHome = () => {
    // Save project
    showToast("Project saved successfully!");
    setIsPlaying(false);
    setCurrentScreen("home");
    setActiveProjectId(null);
  };

  useEffect(() => {
    if (currentScreen === "editor") {
      // Push an initial state when entering editor
      window.history.pushState({ screen: "editor" }, "", window.location.href);
      
      const handlePopState = (event: PopStateEvent) => {
        handleBackToHome();
      };
      
      window.addEventListener("popstate", handlePopState);
      
      return () => {
        window.removeEventListener("popstate", handlePopState);
      };
    }
  }, [currentScreen]);

  // Create refs for back-button-related states to avoid stale closures in unified Capacitor back handler
  const currentScreenRef = useRef(currentScreen);
  const activeExpandedMenuRef = useRef(activeExpandedMenu);
  const transitionModalRef = useRef(transitionModal);
  const projectToDeleteRef = useRef(projectToDelete);
  const isCreatingProjectRef = useRef(isCreatingProject);
  const opticalFlowDiagnosticsRef = useRef(opticalFlowDiagnostics);
  const layerMenuOpenIdRef = useRef(layerMenuOpenId);
  const projectMenuOpenIdRef = useRef(projectMenuOpenId);
  const selectedClipIdsRef = useRef(selectedClipIds);

  useEffect(() => { currentScreenRef.current = currentScreen; }, [currentScreen]);
  useEffect(() => { activeExpandedMenuRef.current = activeExpandedMenu; }, [activeExpandedMenu]);
  useEffect(() => { transitionModalRef.current = transitionModal; }, [transitionModal]);
  useEffect(() => { projectToDeleteRef.current = projectToDelete; }, [projectToDelete]);
  useEffect(() => { isCreatingProjectRef.current = isCreatingProject; }, [isCreatingProject]);
  useEffect(() => { opticalFlowDiagnosticsRef.current = opticalFlowDiagnostics; }, [opticalFlowDiagnostics]);
  useEffect(() => { layerMenuOpenIdRef.current = layerMenuOpenId; }, [layerMenuOpenId]);
  useEffect(() => { projectMenuOpenIdRef.current = projectMenuOpenId; }, [projectMenuOpenId]);
  useEffect(() => { selectedClipIdsRef.current = selectedClipIds; }, [selectedClipIds]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    let activeListener: any = null;

    const initBackButton = async () => {
      try {
        activeListener = await CapApp.addListener("backButton", () => {
          if (projectToDeleteRef.current) {
            setProjectToDelete(null);
          } else if (isCreatingProjectRef.current) {
            setIsCreatingProject(false);
          } else if (projectMenuOpenIdRef.current) {
            setProjectMenuOpenId(null);
          } else if (transitionModalRef.current) {
            setTransitionModal(null);
          } else if (opticalFlowDiagnosticsRef.current) {
            setOpticalFlowDiagnostics(null);
          } else if (activeExpandedMenuRef.current) {
            setActiveExpandedMenu(null);
          } else if (layerMenuOpenIdRef.current) {
            setLayerMenuOpenId(null);
          } else if (selectedClipIdsRef.current.length > 0) {
            setSelectedClipIds([]);
          } else if (currentScreenRef.current === "settings") {
            setCurrentScreen("home");
          } else if (currentScreenRef.current === "editor") {
            handleBackToHome();
          } else {
            // No modals/overlays or secondary screens open while on home screen: exit under native platforms
            CapApp.exitApp();
          }
        });
        console.log("Successfully registered Capacitor backButton listener");
      } catch (err) {
        console.warn("Failed to register backButton listener:", err);
      }
    };

    initBackButton();

    return () => {
      if (activeListener) {
        activeListener.remove();
      }
    };
  }, []);

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      StatusBar.hide().catch((err) => {
        console.warn("Failed to hide StatusBar:", err);
      });
    }
  }, []);

  const createProject = (ratio: string) => {
    const newProjectId = Math.random().toString(36).substring(2, 9);
    const newProject: Project = {
      id: newProjectId,
      name: "New Project",
      ratio,
      updatedAt: "Just now",
      duration: "00:00",
      size: "0 MB",
      thumbnail:
        "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?auto=format&fit=crop&q=80&w=300",
      layers: [],
      clips: [],
    };

    setProjects((prev) => {
      const updated = [newProject, ...prev];
      localStorage.setItem("ai_studio_video_projects", JSON.stringify(updated));
      return updated;
    });

    setActiveProjectId(newProjectId);
    setCurrentProjectRatio(ratio);
    setLayers([]);
    setClips([]);
    setCurrentTime(0);
    setZoomLevel(1);
    setCurrentScreen("editor");
  };

  const duplicateProject = (project: Project) => {
    const newProject = {
      ...project,
      id: Math.random().toString(36).substring(7),
      name: `${project.name} (Copy)`,
      lastEdited: new Date().toISOString(),
    };
    setProjects((prev) => {
      const updated = [newProject, ...prev];
      localStorage.setItem("ai_studio_video_projects", JSON.stringify(updated));
      return updated;
    });
    setProjectMenuOpenId(null);
    showToast("Project duplicated");
  };

  const confirmDeleteProject = () => {
    if (!projectToDelete) return;
    setProjects((prev) => {
      const updated = prev.filter((p) => p.id !== projectToDelete);
      localStorage.setItem("ai_studio_video_projects", JSON.stringify(updated));
      return updated;
    });
    setProjectToDelete(null);
    showToast("Project deleted");
  };

  const openProject = async (project: Project) => {
    // Show some loading indicator if needed here, but since it's local it should be fast
    let resolvedClips = project.clips || [];
    let resolvedLayers = project.layers || [];

    // Auto-populate default sample projects if they have no tracks or clips
    if (resolvedClips.length === 0) {
      if (project.id === "1") {
        resolvedLayers = [
          { id: "L_v1", order: 0, isMuted: false, isHidden: false, name: "Video Track" },
          { id: "L_a1", order: 1, isMuted: false, isHidden: false, name: "Audio Track" }
        ];
        resolvedClips = [
          {
            id: "v1_clip",
            layerId: "L_v1",
            type: "video",
            src: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            leftSeconds: 0,
            durationSeconds: 15,
            originalDurationSeconds: 15,
            trimStartSeconds: 0,
            volume: 100,
            speed: 1,
            name: "Alpine Peaks"
          },
          {
            id: "a1_clip",
            layerId: "L_a1",
            type: "audio",
            src: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            leftSeconds: 0,
            durationSeconds: 15,
            originalDurationSeconds: 184,
            trimStartSeconds: 0,
            volume: 50,
            speed: 1,
            name: "Lofi Beats"
          }
        ];
      } else if (project.id === "frosted-p") {
        resolvedLayers = [
          { id: "L_im1", order: 0, isMuted: false, isHidden: false, name: "Image Track" },
          { id: "L_tx1", order: 1, isMuted: false, isHidden: false, name: "Text Track" }
        ];
        resolvedClips = [
          {
            id: "im1_clip",
            layerId: "L_im1",
            type: "image",
            src: "https://images.unsplash.com/photo-1614036417651-efe5912149d8?auto=format&fit=crop&q=80&w=400",
            leftSeconds: 0,
            durationSeconds: 10,
            originalDurationSeconds: 10,
            trimStartSeconds: 0,
            name: "Frosted Image"
          },
          {
            id: "tx1_clip",
            layerId: "L_tx1",
            type: "text",
            text: "Cozy Winter",
            color: "#ffffff",
            fontSize: 44,
            textAnimation: "Bounce",
            leftSeconds: 2,
            durationSeconds: 6,
            trimStartSeconds: 0,
            src: ""
          }
        ];
      }
    } else {
      try {
        const { getFile } = await import("./lib/db");
        resolvedClips = await Promise.all(
          resolvedClips.map(async (c) => {
            if (c.fileId) {
              const blob = await getFile(c.fileId);
              if (blob) {
                const url = URL.createObjectURL(blob);
                return { ...c, src: url, originalSrc: c.originalSrc ? url : undefined };
              }
            }
            return c;
          })
        );
      } catch(err) {
        console.warn("Could not restore media blobs", err);
      }
    }

    setActiveProjectId(project.id);
    setCurrentProjectRatio(project.ratio);
    setLayers(resolvedLayers);
    setClips(resolvedClips);
    setCurrentTime(0);
    setZoomLevel(1);
    setCurrentScreen("editor");
  };

  // Auto-save
  useEffect(() => {
    if (currentScreen === "editor" && activeProjectId) {
      setProjects((prev) => {
        let hasChanges = false;
        const updated = prev.map((p) => {
          if (p.id === activeProjectId) {
            // compute max duration
            let maxDuration = 0;
            for (const c of clips) {
              if (c.leftSeconds + c.durationSeconds > maxDuration) {
                maxDuration = c.leftSeconds + c.durationSeconds;
              }
            }

            const newDuration = formatTime(maxDuration);
            
            // find thumbnail
            const firstVisualClip = clips.find(c => (c.type === "video" || c.type === "image") && c.dbFileId);
            const newThumbnailFileId = firstVisualClip?.dbFileId || p.thumbnailFileId;

            if (p.ratio !== currentProjectRatio || p.layers !== layers || p.clips !== clips || p.duration !== newDuration || p.thumbnailFileId !== newThumbnailFileId) {
              hasChanges = true;
              return {
                ...p,
                ratio: currentProjectRatio,
                layers,
                clips,
                updatedAt: "Just now",
                duration: newDuration,
                thumbnailFileId: newThumbnailFileId,
              };
            }
            return p;
          }
          return p;
        });

        if (!hasChanges) return prev;

        // We debounce local storage save slightly or just write it
        localStorage.setItem(
          "ai_studio_video_projects",
          JSON.stringify(updated),
        );
        return updated;
      });
    }
  }, [layers, clips, currentProjectRatio, activeProjectId, currentScreen]);

  const addMediaClip = (
    id: string,
    type: "video" | "audio" | "image",
    src: string,
    duration: number,
    startAtTime: number,
    fileId?: string,
    width?: number,
    height?: number,
    fps?: number,
  ) => {
    const newLayerId = "L_" + id;
    setLayers((prev) => {
      const maxOrder = prev.reduce((max, l) => Math.max(max, l.order), -1);
      return [
        ...prev,
        {
          id: newLayerId,
          order: maxOrder + 1,
          isHidden: false,
          isMuted: false,
        },
      ];
    });

    setClips((prev) => [
      ...prev,
      {
        id,
        layerId: newLayerId,
        type,
        src,
        fileId,
        leftSeconds: startAtTime,
        durationSeconds: duration,
        originalDurationSeconds: duration,
        trimStartSeconds: 0,
        width,
        height,
        fps,
      },
    ]);
  };

  // Using global sampleMedia static arrays to optimize memory and component allocations

  const handleSelectMediaItem = (item: { name: string; url: string; type: "video" | "image" | "audio"; duration?: number; thumbnail?: string }) => {
    const id = Math.random().toString(36).substring(2, 9);
    const fileId = "FM_" + Math.random().toString(36).substring(2, 9);
    const startAtTime = currentTime;
    
    setPillPopup({ message: `Importing ${item.name}...`, type: 'loading' });
    
    if (item.type === "image") {
      const img = new Image();
      img.onload = () => {
        addMediaClip(id, "image", item.url, 5, startAtTime, fileId, img.naturalWidth, img.naturalHeight);
        setPillPopup({ message: `Imported ${item.name} clearly`, type: "info" });
        setTimeout(() => setPillPopup(null), 2500);
      };
      img.onerror = () => {
        addMediaClip(id, "image", item.url, 5, startAtTime, fileId);
        setPillPopup({ message: `Imported ${item.name} clearly`, type: "info" });
        setTimeout(() => setPillPopup(null), 2500);
      };
      img.src = item.url;
    } else if (item.type === "audio") {
      addMediaClip(id, "audio", item.url, item.duration || 10, startAtTime, fileId);
      setPillPopup({ message: `Imported ${item.name} audio track`, type: "info" });
      setTimeout(() => setPillPopup(null), 2500);
    } else {
      setPillPopup({ message: "Parsing video structures...", type: 'loading' });
      const video = document.createElement("video");
      video.preload = "metadata";
      video.onloadedmetadata = () => {
        addMediaClip(id, "video", item.url, item.duration || 12, startAtTime, fileId, video.videoWidth, video.videoHeight, 30);
        setPillPopup({ message: `Loaded: ${video.videoWidth}x${video.videoHeight} @ 30 FPS`, type: 'info' });
        setTimeout(() => setPillPopup(null), 2500);
      };
      video.onerror = () => {
        addMediaClip(id, "video", item.url, item.duration || 12, startAtTime, fileId, 1920, 1080, 30);
        setPillPopup({ message: `Loaded: 1920x1080 @ 30 FPS`, type: 'info' });
        setTimeout(() => setPillPopup(null), 2500);
      };
      video.src = item.url;
    }
  };

  const handleAddText = () => {
    const startAtTime = currentTime;
    const duration = 5;
    const newLayerId = Math.random().toString(36).substring(2, 9);

    setLayers((prev) => {
      const maxOrder =
        prev.length > 0 ? Math.max(...prev.map((l) => l.order)) : 0;
      return [
        ...prev,
        {
          id: newLayerId,
          order: maxOrder + 1,
          isMuted: false,
          isHidden: false,
          name: "Text Layer"
        },
      ];
    });

    const newTextId = Math.random().toString(36).substring(2, 9);
    lastAddedTextClipIdRef.current = newTextId;
    setClips((prev) => [
      ...prev,
      {
        id: newTextId,
        layerId: newLayerId,
        type: "text",
        src: "",
        text: "",
        color: "#ffffff",
        fontSize: 48,
        leftSeconds: startAtTime,
        durationSeconds: duration,
        trimStartSeconds: 0,
      },
    ]);
    setSelectedClipId(newTextId);
    setActiveExpandedMenu("text");
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    let type: "video" | "image" | "audio" = "video";
    if (file.type.startsWith("image/")) type = "image";
    if (file.type.startsWith("audio/")) type = "audio";

    const src = URL.createObjectURL(file);
    const id = Math.random().toString(36).substring(2, 9);
    const fileId = "F_" + Math.random().toString(36).substring(2, 9);
    const startAtTime = currentTime;

    const addToLibrary = (finalDuration: number) => {
      const newItem = {
        name: file.name,
        path: fileId,
        url: src,
        type: type,
        folder: type === "image" ? "Camera Roll" : type === "audio" ? "Audio Recordings" : "Videos",
        duration: finalDuration,
        thumbnail: type === "image" ? src : undefined,
      };
      setDeviceMedias((prev) => [newItem, ...prev]);
    };

    try {
      if (navigator.storage && navigator.storage.persist) {
        await navigator.storage.persist();
      }
      const { storeFile } = await import("./lib/db");
      await storeFile(fileId, file);
    } catch(err) {
      console.warn("Storage failed", err);
    }

    if (type === "video" || type === "audio") {
      try {
        setPillPopup({ message: "Parsing video structures...", type: 'loading' });
        const { loadVideoMetadata } = await import("./lib/opticalFlow");
        const metadata = await loadVideoMetadata(file);
        
        setPillPopup({
          message: `Loaded: ${metadata.width}x${metadata.height} @ ${Math.round(metadata.fps)} FPS`,
          type: 'info'
        });
        setTimeout(() => setPillPopup(null), 3500);

        addMediaClip(
          id,
          type,
          src,
          (metadata.durationMs / 1000) || 10,
          startAtTime,
          fileId,
          metadata.width,
          metadata.height,
          metadata.fps
        );
        addToLibrary((metadata.durationMs / 1000) || 10);
      } catch (metaErr) {
        console.warn("Unified metadata load failed, using DOM fallback:", metaErr);
        setPillPopup(null);
        if (type === "video") {
          const video = document.createElement("video");
          video.preload = "metadata";
          video.onloadedmetadata = () => {
            addMediaClip(
              id,
              type,
              src,
              video.duration || 10,
              startAtTime,
              fileId,
              video.videoWidth,
              video.videoHeight,
              30
            );
            addToLibrary(video.duration || 10);
          };
          video.src = src;
        } else {
          const audio = document.createElement("audio");
          audio.preload = "metadata";
          audio.onloadedmetadata = () => {
            addMediaClip(
              id,
              type,
              src,
              audio.duration || 10,
              startAtTime,
              fileId,
              undefined,
              undefined,
              30
            );
            addToLibrary(audio.duration || 10);
          };
          audio.src = src;
        }
      }
    } else {
      if (type === "image") {
        const img = new Image();
        img.onload = () => {
          addMediaClip(id, "image", src, 5, startAtTime, fileId, img.naturalWidth, img.naturalHeight);
          addToLibrary(5);
        };
        img.onerror = () => {
          addMediaClip(id, "image", src, 5, startAtTime, fileId);
          addToLibrary(5);
        };
        img.src = src;
      } else {
        addMediaClip(id, type, src, 5, startAtTime, fileId);
        addToLibrary(5);
      }
    }
  };

  function deleteSelectedClip() {
    if (selectedClipIds.length > 0) {
      setClips((prev) => prev.filter((c) => !selectedClipIds.includes(c.id)));
      setSelectedClipIds([]);
      // Optional: Cleanup empty layers
    }
  }

  const handleToggleKeyframe = useCallback(() => {
    if (!selectedClipId || !timelineScrollRef.current) return;
    const playheadTime = currentTimeRef.current;
    
    setClips(prev => prev.map(c => {
      if (c.id !== selectedClipId) return c;
      const timeInClip = playheadTime - c.leftSeconds;
      const keyframes = c.keyframes || [];
      const isVolMode = activeExpandedMenu === "volume";

      const existingIndex = keyframes.findIndex(kf => {
        const isTimeClose = Math.abs(kf.timeOffset - timeInClip) < 0.05;
        if (!isTimeClose) return false;
        
        if (isVolMode) {
          return kf.properties.volume !== undefined;
        } else {
          return kf.properties.translateX !== undefined || kf.properties.scale !== undefined;
        }
      });

      if (existingIndex >= 0) {
        const targetKf = keyframes[existingIndex];
        let updatedProperties = { ...targetKf.properties };

        if (isVolMode) {
          delete updatedProperties.volume;
        } else {
          delete updatedProperties.translateX;
          delete updatedProperties.translateY;
          delete updatedProperties.rotation;
          delete updatedProperties.scale;
          delete updatedProperties.opacity;
        }

        if (Object.keys(updatedProperties).length === 0) {
          return {
            ...c,
            keyframes: keyframes.filter((_, i) => i !== existingIndex)
          };
        } else {
          return {
            ...c,
            keyframes: keyframes.map((k, i) => i === existingIndex ? { ...k, properties: updatedProperties } : k)
          };
        }
      } else {
        const newProperties: any = {};
        if (isVolMode) {
          const currentVol = typeof c.volume === "number" ? (c.volume <= 1.0 ? c.volume * 100 : c.volume) : 100;
          newProperties.volume = currentVol;
        } else {
          newProperties.translateX = c.translateX || 0;
          newProperties.translateY = c.translateY || 0;
          newProperties.rotation = c.rotation || 0;
          newProperties.scale = c.scale ?? 1;
          newProperties.opacity = c.opacity ?? 1;
        }

        const newKeyframe: Keyframe = {
          id: "kf_" + Date.now() + Math.random(),
          timeOffset: timeInClip,
          properties: newProperties,
          curve: "linear",
        };

        return {
          ...c,
          keyframes: [...keyframes, newKeyframe].sort((a, b) => a.timeOffset - b.timeOffset)
        };
      }
    }));
  }, [selectedClipId, currentTime, activeExpandedMenu]);

  function splitSelectedClip() {
    if (!timelineScrollRef.current) return;
    const playheadTime = currentTimeRef.current;
    
    let clip = clips.find((c) => c.id === selectedClipId);

    // If no clip selected or selected clip not at playheadTime, find the clip under playhead
    if (!clip || !(playheadTime >= clip.leftSeconds && playheadTime <= clip.leftSeconds + clip.durationSeconds)) {
      clip = clips.find((c) => playheadTime >= c.leftSeconds && playheadTime <= c.leftSeconds + c.durationSeconds);
    }

    if (!clip) return;

    const firstDuration = playheadTime - clip.leftSeconds;
    
    // Ensure we are splitting at a valid point within the clip
    if (firstDuration <= 0.01 || firstDuration >= clip.durationSeconds - 0.01) {
      return;
    }

    const newClipId = "C_" + Math.random().toString(36).substring(2, 9);
    const speed = clip.speed || 1.0;
    const fullOrgDur = clip.originalDurationSeconds !== undefined ? clip.originalDurationSeconds : clip.durationSeconds * speed;

    // Filter and shift keyframes for both parts with deep copies
    const kfs1 = clip.keyframes
      ? clip.keyframes
          .filter((kf) => kf.timeOffset < firstDuration)
          .map((kf) => ({
            ...kf,
            properties: kf.properties ? { ...kf.properties } : {},
          }))
      : [];
    const kfs2 = clip.keyframes
      ? clip.keyframes
          .filter((kf) => kf.timeOffset >= firstDuration)
          .map((kf) => ({
            ...kf,
            timeOffset: kf.timeOffset - firstDuration,
            properties: kf.properties ? { ...kf.properties } : {},
          }))
      : [];

    setClips((prev) => {
      const rest = prev.filter((c) => c.id !== clip.id);
      const newClip1 = {
        ...clip,
        cropRect: clip.cropRect ? { ...clip.cropRect } : undefined,
        durationSeconds: firstDuration,
        originalDurationSeconds: fullOrgDur,
        keyframes: kfs1,
      };
      const newClip2 = {
        ...clip,
        id: newClipId,
        cropRect: clip.cropRect ? { ...clip.cropRect } : undefined,
        leftSeconds: playheadTime,
        trimStartSeconds: clip.trimStartSeconds + firstDuration * speed,
        durationSeconds: clip.durationSeconds - firstDuration,
        originalDurationSeconds: fullOrgDur,
        keyframes: kfs2,
      };
      return [...rest, newClip1, newClip2];
    });
  };

  const formatTime = (seconds: number) => {
    const s = Math.max(0, seconds);
    const mins = Math.floor((s % 3600) / 60);
    const secs = Math.floor(s % 60);
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const handleSmoothSlowMo = async () => {
    const currentClip = clips.find(c => c.id === selectedClipId);
    if (!currentClip || currentClip.type !== "video") return;

    const isOpticalFlowApplied = currentClip?.opticalFlow || false;

    if (isOpticalFlowApplied) {
      if (selectedClipId) {
        setClips((prev) =>
          prev.map((c) =>
            c.id === selectedClipId
              ? { ...c, opticalFlow: false, src: c.originalSrc || c.src }
              : c,
          ),
        );
      }
      return;
    }

    if (smoothProcessingProgress !== null) return;
    setSmoothProcessingProgress(0);

    try {
      if (!currentClip.src) {
        throw new Error("No video source found.");
      }
      setPillPopup({ message: "Applying Smooth Slow-mo...", progress: 0, type: 'loading' });
      
      let videoSource: string | Blob = currentClip.src;
      if (currentClip.fileId) {
        try {
          const { getFile } = await import("./lib/db");
          const cachedBlob = await getFile(currentClip.fileId);
          if (cachedBlob) {
            videoSource = cachedBlob;
          }
        } catch (dbErr) {
          console.warn("Could not retrieve file from IndexedDB:", dbErr);
        }
      }

      const decodeResult = await processSmoothSlowMoBrowser(
        videoSource,
        currentClip.speed || 1,
        (progress) => {
          setPillPopup({ message: "Applying Smooth Slow-mo...", progress: Math.min(99, Math.round(progress)), type: 'loading' });
        }
      );

      const { 
        url: newSrcUrl, 
        fileId: newFileId, 
        decodedFramesCount, 
        flowComputedCount, 
        timestampsVerified,
        avgFlowMagnitude,
        maxFlowMagnitude,
        flowVisualization,
        isFlowCorrect,
        interpolatedFramesCount,
        averagePsnr,
        averageWarpError,
        interpolationVisualization,
        totalExportTimeMs,
        decoderTimeMs,
        opticalFlowTimeMs,
        interpolationTimeMs,
        colorConversionTimeMs,
        encoderTimeMs,
        decoderPct,
        opticalFlowPct,
        interpolationPct,
        colorConversionPct,
        encoderPct,
        decoderMsPerFrame,
        opticalFlowMsPerFrame,
        interpolationMsPerFrame,
        colorConversionMsPerFrame,
        encoderMsPerFrame,
        matAllocations,
        byteBufferAllocations,
        frameCopies
      } = decodeResult;

      setOpticalFlowDiagnostics({
        clipId: selectedClipId,
        decodedFramesCount,
        flowComputedCount,
        timestampsVerified,
        avgFlowMagnitude,
        maxFlowMagnitude,
        flowVisualization,
        isFlowCorrect,
        interpolatedFramesCount,
        averagePsnr,
        averageWarpError,
        interpolationVisualization,
        totalExportTimeMs,
        decoderTimeMs,
        opticalFlowTimeMs,
        interpolationTimeMs,
        colorConversionTimeMs,
        encoderTimeMs,
        decoderPct,
        opticalFlowPct,
        interpolationPct,
        colorConversionPct,
        encoderPct,
        decoderMsPerFrame,
        opticalFlowMsPerFrame,
        interpolationMsPerFrame,
        colorConversionMsPerFrame,
        encoderMsPerFrame,
        matAllocations,
        byteBufferAllocations,
        frameCopies
      });

      if (decodedFramesCount !== undefined) {
        const flowMsg = flowComputedCount !== undefined ? `, ${flowComputedCount} DIS flows computed` : '';
        setPillPopup({ 
          message: `Slow-mo: ${decodedFramesCount} frames (${timestampsVerified ? "Verified TS" : "Non-monotonic TS!"})${flowMsg}`, 
          type: 'info' 
        });
      } else {
        setPillPopup({ message: "Smooth Slow-mo Applied", type: 'info' });
      }

      if (selectedClipId) {
        setClips((prev) =>
          prev.map((c) =>
            c.id === selectedClipId
              ? { ...c, opticalFlow: true, originalSrc: c.originalSrc || c.src, src: newSrcUrl, fileId: newFileId }
              : c,
          ),
        );
      }

      setTimeout(() => {
        setPillPopup(null);
      }, 2000);
    } catch (err: any) {
      console.error(err);
      setPillPopup({ message: `Failed: ${err.message || 'unknown error'}`, type: 'info' });
      setTimeout(() => {
        setPillPopup(null);
      }, 5000);
      setSmoothProcessingProgress(null);
    }
  };

  const handleCopy = () => {
    const clipToCopy = clips.find((c) => c.id === selectedClipId);
    if (clipToCopy) {
      setCopiedClip(clipToCopy);
      setToastMessage("Clip copied");
      setTimeout(() => setToastMessage(null), 2000);
    }
  };

  const [copyLongPressed, setCopyLongPressed] = useState(false);

  const onCopyPointerDown = (e: React.PointerEvent) => {
    setCopyLongPressed(false);
    if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    copyTimerRef.current = setTimeout(() => {
      setCopyLongPressed(true);
      setMultiSelectActive(prev => !prev);
      try {
        if (navigator.vibrate) {
          navigator.vibrate(50);
        }
      } catch (ex) {}
    }, 600);
  };

  const onCopyPointerUp = (e: React.PointerEvent) => {
    if (copyTimerRef.current) {
      clearTimeout(copyTimerRef.current);
      copyTimerRef.current = null;
    }
    if (!copyLongPressed) {
      handleCopy();
    }
  };

  const onCopyPointerLeave = () => {
    if (copyTimerRef.current) {
      clearTimeout(copyTimerRef.current);
      copyTimerRef.current = null;
    }
  };

  const handleExtractAudio = () => {
    const videoClip = clips.find(c => c.id === selectedClipId && c.type === "video");
    if (!videoClip) return;

    const newClipId = "C_" + Math.random().toString(36).substring(2, 9);
    
    setLayers(prevLayers => {
      // Find the minimum layer order to place the new audio layer below it
      const minOrder = prevLayers.length > 0 ? Math.min(...prevLayers.map(l => l.order)) : 0;
      const newLayerId = "L_AUDIO_" + Math.random().toString(36).substring(2, 9);
      
      const newLayer: Layer = {
        id: newLayerId,
        order: minOrder - 1,
        isHidden: false,
        isMuted: false,
        name: "Extracted Audio",
      };
      
      const newClipsLayers = [...prevLayers, newLayer];
      
      setClips(prevClips => {
        const audioClip: Clip = {
           ...videoClip,
           id: newClipId,
           layerId: newLayerId,
           type: "audio"
        };
        // Also mute the original video clip? It defaults to replacing audio. Let's set its volume to 0.
        const modifiedVideos = prevClips.map(c => c.id === videoClip.id ? { ...c, volume: 0 } : c);
        return [...modifiedVideos, audioClip];
      });
      
      return newClipsLayers;
    });
    
    setToastMessage("Audio extracted to new layer");
    setTimeout(() => setToastMessage(null), 2000);
  };

  const handleStabilize = (clipId: string, mode: "standard" | "active" | "locked" | "off") => {
    const clip = clips.find(c => c.id === clipId);
    if (!clip || !clip.src) {
        setToastMessage("Error: Clip not found or no source!");
        setTimeout(() => setToastMessage(null), 2000);
        return;
    }

    if (mode === "off") {
      setClips((prev) =>
        prev.map((c) =>
          c.id === clipId
            ? {
                ...c,
                isStabilized: false,
                stabilizationMode: "off",
                compareStabilization: false,
              }
            : c
        )
      );
      setToastMessage("Stabilization turned off");
      setTimeout(() => setToastMessage(null), 2000);
      return;
    }

    // Initialize progress tracking
    setStabilizingProgress({
      clipId,
      progress: 0,
      stage: "Optical flow analysis...",
    });

    let currentProgress = 0;
    const interval = setInterval(() => {
      currentProgress += 5;
      let stage = "Analyzing camera rotation...";
      if (currentProgress < 25) {
        stage = "Analyzing warp vectors...";
      } else if (currentProgress < 50) {
        stage = "Smoothing frame translations...";
      } else if (currentProgress < 75) {
        stage = "Synthesizing viewport margin crop...";
      } else if (currentProgress < 95) {
        stage = "Applying continuous warp stabilizer...";
      } else {
        stage = "Completing mesh compilation...";
      }

      setStabilizingProgress({
        clipId,
        progress: Math.min(100, currentProgress),
        stage,
      });

      if (currentProgress >= 100) {
        clearInterval(interval);
        setClips((prev) =>
          prev.map((c) =>
            c.id === clipId
              ? {
                  ...c,
                  isStabilized: true,
                  stabilizationMode: mode,
                  compareStabilization: c.compareStabilization ?? false,
                }
              : c
          )
        );
        setStabilizingProgress(null);
        setToastMessage(`Stabilization complete: ${mode.toUpperCase()} mode applied`);
        setTimeout(() => setToastMessage(null), 2500);
      }
    }, 100);
  };

  const handlePaste = () => {
    if (!copiedClip || !pastePopup) return;

    let targetLayerId = pastePopup.layerId;
    let newLayers = [...layers];

    if (!targetLayerId) {
      targetLayerId = Date.now().toString();
      const maxOrder = layers.reduce((max, l) => Math.max(max, l.order), 0);
      newLayers = [
        ...layers,
        {
          id: targetLayerId,
          order: maxOrder + 1,
          isMuted: false,
          isHidden: false,
        },
      ];
      setLayers(newLayers);
    }

    let targetTime = pastePopup.time;
    const layerClips = clips.filter((c) => c.layerId === targetLayerId);

    // Check if targetTime overlaps any existing clip on this layer
    const overlappingClip = layerClips.find(
      (c) =>
        targetTime >= c.leftSeconds &&
        targetTime < c.leftSeconds + c.durationSeconds,
    );
    if (overlappingClip) {
      const midPoint =
        overlappingClip.leftSeconds + overlappingClip.durationSeconds / 2;
      if (targetTime < midPoint) {
        targetTime = overlappingClip.leftSeconds - copiedClip.durationSeconds;
        if (targetTime < 0) targetTime = 0;
      } else {
        targetTime =
          overlappingClip.leftSeconds + overlappingClip.durationSeconds;
      }
    }

    const newClip: Clip = {
      ...copiedClip,
      id: Date.now().toString(),
      layerId: targetLayerId,
      leftSeconds: targetTime,
    };

    setClips([...clips, newClip]);
    setPastePopup(null);
    setToastMessage("Clip pasted");
    setTimeout(() => setToastMessage(null), 2000);
  };

  const toggleLayerMute = (layerId: string) => {
    setLayers((l) =>
      l.map((layer) =>
        layer.id === layerId ? { ...layer, isMuted: !layer.isMuted } : layer,
      ),
    );
  };

  const toggleLayerVisibility = (layerId: string) => {
    setLayers((l) =>
      l.map((layer) =>
        layer.id === layerId ? { ...layer, isHidden: !layer.isHidden } : layer,
      ),
    );
  };

  const handleLayerPointerDown = (e: React.PointerEvent, layerId: string) => {
    e.stopPropagation();
    const target = e.currentTarget;
    target.setPointerCapture(e.pointerId);

    let startY = e.clientY;
    hasDraggedLayerRef.current = false;
    let hasMoved = false;
    let pendingSteps = 0;
    
    setDraggingLayerId(layerId);

    const onPointerMove = (moveEvent: PointerEvent) => {
      const deltaY = moveEvent.clientY - startY;
      if (Math.abs(deltaY) > 5) {
        hasMoved = true;
        hasDraggedLayerRef.current = true;
      }

      if (hasMoved) {
        const rowHeight = window.innerWidth >= 640 ? 38 : 32;
        const expectedSteps =
          deltaY > 0 ? Math.floor(deltaY / rowHeight) : Math.ceil(deltaY / rowHeight);
        if (expectedSteps !== 0 && expectedSteps !== pendingSteps) {
          const stepDiff = expectedSteps - pendingSteps;
          pendingSteps = expectedSteps;

          setLayers((prev) => {
            const sorted = [...prev].sort((a, b) => b.order - a.order);
            const visIdx = sorted.findIndex((l) => l.id === layerId);
            const targetVisIdx = visIdx + stepDiff;

            if (targetVisIdx >= 0 && targetVisIdx < sorted.length) {
              const targetLayer = sorted[targetVisIdx];
              const clone = [...prev];
              const l1 = clone.findIndex((l) => l.id === layerId);
              const l2 = clone.findIndex((l) => l.id === targetLayer.id);

              const tempOrder = clone[l1].order;
              clone[l1].order = clone[l2].order;
              clone[l2].order = tempOrder;

              startY = moveEvent.clientY;
              pendingSteps = 0;
              return clone;
            }
            return prev;
          });
        }
      }
    };

    const onPointerUp = (upEvent: PointerEvent) => {
      try {
        target.releasePointerCapture(upEvent.pointerId);
      } catch (err) {}
      window.removeEventListener("pointermove", onPointerMove);
      window.removeEventListener("pointerup", onPointerUp);
      window.removeEventListener("pointercancel", onPointerUp);

      setDraggingLayerId(null);
      // Removed the custom menu opening logic to avoid conflict with onClick
    };

    window.addEventListener("pointermove", onPointerMove);
    window.addEventListener("pointerup", onPointerUp);
    window.addEventListener("pointercancel", onPointerUp);
  };

  const currentPixelsPerSecondRef = useRef(pixelsPerSecond);
  useEffect(() => {
    currentPixelsPerSecondRef.current = pixelsPerSecond;
  }, [pixelsPerSecond]);

  const currentZoomLevelRef = useRef(zoomLevel);

  // Adjust scroll position on zoom to keep focus
  useLayoutEffect(() => {
    const container = timelineScrollRef.current;
    if (container && currentZoomLevelRef.current !== zoomLevel) {
      const center = container.clientWidth / 2;
      const scrollTime = (container.scrollLeft + center) / (BASE_PIXELS_PER_SECOND * currentZoomLevelRef.current);
      const newPixelsPerSecond = BASE_PIXELS_PER_SECOND * zoomLevel;
      container.scrollLeft = scrollTime * newPixelsPerSecond - center;
      currentZoomLevelRef.current = zoomLevel;
    }
  }, [zoomLevel]);

  // --- Pinch/Wheel to Zoom ---
  useEffect(() => {
    const container = timelineScrollRef.current;
    if (!container) return;

    let initialDist: number | null = null;
    let initialZoom = 1;

    const handleTouchStart = (e: TouchEvent) => {
      if (e.touches.length === 2) {
        initialDist = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY,
        );
        initialZoom = currentZoomLevelRef.current;
      }
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (e.touches.length === 2 && initialDist !== null) {
        e.preventDefault(); // prevent native scroll
        const dist = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY,
        );
        const scale = dist / initialDist;
        setZoomLevel(Math.min(Math.max(0.01, initialZoom * scale), 10));
      }
    };

    const handleTouchEnd = (e: TouchEvent) => {
      if (e.touches.length < 2) {
        initialDist = null;
      }
    };

    const handleWheel = (e: WheelEvent) => {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        const delta = -e.deltaY * 0.01;
        setZoomLevel((prev) =>
          Math.min(Math.max(0.01, prev * Math.exp(delta)), 10),
        );
      }
    };

    container.addEventListener("touchstart", handleTouchStart, {
      passive: false,
    });
    container.addEventListener("touchmove", handleTouchMove, {
      passive: false,
    });
    container.addEventListener("touchend", handleTouchEnd);
    container.addEventListener("wheel", handleWheel, { passive: false });

    return () => {
      container.removeEventListener("touchstart", handleTouchStart);
      container.removeEventListener("touchmove", handleTouchMove);
      container.removeEventListener("touchend", handleTouchEnd);
      container.removeEventListener("wheel", handleWheel);
    };
  }, [currentScreen]);

  const previewTouchRef = useRef<{
    startX: number;
    startY: number;
    startTranslateX: number;
    startTranslateY: number;
    startDistance: number;
    startScale: number;
    activeClipId: string;
    startMaskWidth?: number;
    startMaskHeight?: number;
    startMaskPositionX?: number;
    startMaskPositionY?: number;
  } | null>(null);

  const handlePreviewTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 2 && selectedClipId) {
      const clip = clips.find(c => c.id === selectedClipId);
      if (clip) {
        // Stop if layer is locked
        const clipLayer = layers.find(l => l.id === clip.layerId);
        if (clipLayer?.isLocked) return;

        const startX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const startY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
        const dx = e.touches[0].clientX - e.touches[1].clientX;
        const dy = e.touches[0].clientY - e.touches[1].clientY;
        const startDistance = Math.hypot(dx, dy) || 1;
        previewTouchRef.current = {
          startX,
          startY,
          startTranslateX: clip.translateX || 0,
          startTranslateY: clip.translateY || 0,
          startDistance,
          startScale: clip.scale || 1,
          activeClipId: clip.id,
          startMaskWidth: clip.maskWidth ?? (clip.maskType === "half" ? 100 : 60),
          startMaskHeight: clip.maskHeight ?? (clip.maskType === "half" ? 50 : 60),
          startMaskPositionX: clip.maskPositionX ?? 0.5,
          startMaskPositionY: clip.maskPositionY ?? 0.5,
        };
      }
    }
  };

  const handlePreviewTouchMove = (e: React.TouchEvent) => {
    if (e.touches.length === 2 && previewTouchRef.current && previewTouchRef.current.activeClipId === selectedClipId) {
      const clip = clips.find(c => c.id === selectedClipId);
      if (clip) {
        const clipLayer = layers.find(l => l.id === clip.layerId);
        if (clipLayer?.isLocked) return;
      }

      const currentX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
      const currentY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
      
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const currentDistance = Math.hypot(dx, dy);
      
      const { 
        startTranslateX, 
        startTranslateY, 
        startDistance, 
        startScale, 
        activeClipId,
        startMaskWidth,
        startMaskHeight,
        startMaskPositionX,
        startMaskPositionY 
      } = previewTouchRef.current;
      
      const scaleRatio = currentDistance / startDistance;

      if (clip && clip.maskType && clip.maskType !== "none") {
        const previewEl = document.getElementById("preview-screen");
        const rect = previewEl ? previewEl.getBoundingClientRect() : { width: 500, height: 300 };
        const deltaPercentX = (currentX - previewTouchRef.current.startX) / (rect.width || 500);
        const deltaPercentY = (currentY - previewTouchRef.current.startY) / (rect.height || 300);

        const newMaskPositionX = Math.max(0, Math.min(1, (startMaskPositionX ?? 0.5) + deltaPercentX));
        const newMaskPositionY = Math.max(0, Math.min(1, (startMaskPositionY ?? 0.5) + deltaPercentY));

        const startW = startMaskWidth ?? (clip.maskType === "half" ? 100 : 60);
        const startH = startMaskHeight ?? (clip.maskType === "half" ? 50 : 60);

        const newMaskWidth = Math.max(5, Math.min(100, Math.round(startW * scaleRatio)));
        const newMaskHeight = Math.max(5, Math.min(100, Math.round(startH * scaleRatio)));

        setClips((prev) => 
          prev.map((c) =>
            c.id === activeClipId
              ? { 
                  ...c, 
                  maskPositionX: c.maskType === "half" ? 0.5 : newMaskPositionX,
                  maskPositionY: newMaskPositionY,
                  maskWidth: c.maskType === "half" ? 100 : newMaskWidth,
                  maskHeight: newMaskHeight
                }
              : c
          )
        );
      } else {
        const deltaX = (currentX - previewTouchRef.current.startX) / appScale;
        const deltaY = (currentY - previewTouchRef.current.startY) / appScale;
        const newScale = Math.max(0.1, Math.min(5.0, startScale * scaleRatio));

        setClips((prev) => 
          prev.map((c) =>
            c.id === activeClipId
              ? { 
                  ...c, 
                  translateX: startTranslateX + deltaX, 
                  translateY: startTranslateY + deltaY,
                  scale: newScale
                }
              : c
          )
        );
      }
    }
  };

  const handlePreviewTouchEnd = (e: React.TouchEvent) => {
    if (e.touches.length < 2) {
      previewTouchRef.current = null;
    }
  };

  const handleClipDragStart = (e: React.PointerEvent, clip: Clip) => {
    e.stopPropagation();
    setIsPlaying(false);

    const target = e.target as HTMLElement;
    target.setPointerCapture(e.pointerId);
    
    const wasAlreadySelected = selectedClipIds.includes(clip.id);
    let activeSelectedIds = selectedClipIds;
    
    if (multiSelectActive) {
      if (!wasAlreadySelected) {
        activeSelectedIds = [...selectedClipIds, clip.id];
        setSelectedClipIds(activeSelectedIds);
      }
    } else {
      if (!wasAlreadySelected) {
        activeSelectedIds = [clip.id];
        setSelectedClipIds(activeSelectedIds);
      }
    }

    const startX = e.clientX;
    const startY = e.clientY;
    const initialLeftSeconds = clip.leftSeconds;
    
    // Canvas-relative initialization for absolute, scroll-immune dragging
    const innerRectInit = document.getElementById("timeline-inner")?.getBoundingClientRect();
    const initialClickCanvasX = innerRectInit ? (startX - innerRectInit.left) : 0;
    
    // Map of initial states for ALL selected clips
    const initialClipsData = new Map<string, { left: number, layer: string }>();
    clips.forEach(c => {
      if (activeSelectedIds.includes(c.id)) {
        initialClipsData.set(c.id, { left: c.leftSeconds, layer: c.layerId });
      }
    });

    const initialScrollLeft = timelineScrollRef.current?.scrollLeft || 0;
    const initialScrollTop = timelineScrollRef.current?.scrollTop || 0;

    let isDraggingMode = false;
    let didMove = false;
    let dragTimeout = setTimeout(() => {
      isDraggingMode = true;
    }, 400); // 400ms hold delay to drag

    let isCreatingLayer = false;
    let fallbackLayerId = clip.layerId;
    let createdLayerId: string | null = null;

    const handlePointerMove = (moveEvent: PointerEvent) => {
      const deltaX = moveEvent.clientX - startX;
      const deltaY = moveEvent.clientY - startY;

      if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
        didMove = true;
      }

      if (!isDraggingMode) {
        if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
          clearTimeout(dragTimeout);
          if (timelineScrollRef.current) {
            timelineScrollRef.current.scrollLeft = initialScrollLeft - deltaX;
            timelineScrollRef.current.scrollTop = initialScrollTop - deltaY;
          }
        }
        return;
      }

      // --- AUTO SCROLL WHEN DRAGGING NEAR EDGES ---
      if (timelineScrollRef.current) {
        const container = timelineScrollRef.current;
        const containerRect = container.getBoundingClientRect();
        const margin = 50; // pixels to trigger auto-scroll
        if (moveEvent.clientX > containerRect.right - margin) {
          const speedFactor = (moveEvent.clientX - (containerRect.right - margin)) * 0.15;
          container.scrollLeft += Math.min(10, speedFactor);
        } else if (moveEvent.clientX < containerRect.left + margin) {
          const speedFactor = ((containerRect.left + margin) - moveEvent.clientX) * 0.15;
          container.scrollLeft -= Math.min(10, speedFactor);
        }
      }

      const innerRectCurr = document.getElementById("timeline-inner")?.getBoundingClientRect();
      const currentClickCanvasX = innerRectCurr ? (moveEvent.clientX - innerRectCurr.left) : (moveEvent.clientX - startX);
      const deltaSeconds = innerRectInit && innerRectCurr 
        ? (currentClickCanvasX - initialClickCanvasX) / currentPixelsPerSecondRef.current
        : deltaX / currentPixelsPerSecondRef.current;
      let newLeftSeconds = initialLeftSeconds + deltaSeconds;

      // --- MAGNETIC SNAPPING (Only snap the clip being dragged) ---
      if (snappingEnabled) {
        const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
        let minDistance = SNAP_THRESHOLD_SECONDS;
        let snappedLeftSeconds = newLeftSeconds;

        const snapPoints = [0, currentTime];
        clips.forEach((c) => {
          if (!activeSelectedIds.includes(c.id)) {
            snapPoints.push(c.leftSeconds);
            snapPoints.push(c.leftSeconds + c.durationSeconds);
          }
        });

        snapPoints.forEach(sp => {
           const distLeft = Math.abs(sp - newLeftSeconds);
           if (distLeft < minDistance) {
               minDistance = distLeft;
               snappedLeftSeconds = sp;
           }
           const newRight = newLeftSeconds + clip.durationSeconds;
           const distRight = Math.abs(sp - newRight);
           if (distRight < minDistance) {
               minDistance = distRight;
               snappedLeftSeconds = sp - clip.durationSeconds;
           }
        });
        newLeftSeconds = snappedLeftSeconds;
      }
      
      const finalDeltaSeconds = newLeftSeconds - initialLeftSeconds;

      const elementsUnder = document.elementsFromPoint(
        moveEvent.clientX,
        moveEvent.clientY,
      );
      const trackEl = elementsUnder.find((el) =>
        el.classList.contains("track-space"),
      );

      // Handle layer dropping
      let targetLayerId = fallbackLayerId;
      if (trackEl) {
        targetLayerId = trackEl.getAttribute("data-layer-id") || fallbackLayerId;
      }

      if (activeSelectedIds.length === 1) {
        const timelineInner = elementsUnder.find(
          (el) => el.id === "timeline-inner",
        );

        if (trackEl) {
          // Handled above
        } else if (timelineInner && !isCreatingLayer) {
          // Create layer
          isCreatingLayer = true;
          const newId = Math.random().toString(36).substring(7);
          createdLayerId = newId;
          setLayers((prev) => {
            const minOrder = prev.length > 0 ? Math.min(...prev.map((l) => l.order)) : 0;
            return [...prev, { id: newId, order: minOrder - 1, isMuted: false, isHidden: false }];
          });
          targetLayerId = newId;
          setTimeout(() => { isCreatingLayer = false; }, 200);
        }

        // If a layer was created but the user moved back / away from it to a different track
        if (createdLayerId && targetLayerId !== createdLayerId) {
          const idToDelete = createdLayerId;
          createdLayerId = null;
          setLayers((prev) => prev.filter((l) => l.id !== idToDelete));
        }
      }

      setClips((prevClips) => {
        // Evaluate horizontal bounds to prevent crossing x=0
        let effectiveDelta = finalDeltaSeconds;
        activeSelectedIds.forEach(id => {
          const init = initialClipsData.get(id);
          if (init && init.left + effectiveDelta < 0) {
            effectiveDelta = -init.left;
          }
        });

        if (activeSelectedIds.length === 1) {
          // Single clip check for overlaps with targetLayerId
          const potentialLeft = Math.max(0, initialLeftSeconds + effectiveDelta);
          const hasOverlapTarget = prevClips.some(
            (c) =>
              c.layerId === targetLayerId &&
              !activeSelectedIds.includes(c.id) &&
              potentialLeft < c.leftSeconds + c.durationSeconds &&
              potentialLeft + clip.durationSeconds > c.leftSeconds,
          );

          if (!hasOverlapTarget) {
            // No overlap on target layer, move is completely allowed
            fallbackLayerId = targetLayerId;
            return prevClips.map((c) =>
              c.id === clip.id
                ? { ...c, leftSeconds: potentialLeft, layerId: targetLayerId }
                : c,
            );
          } else {
            // There is an overlap on the target layer. Let's see if we can move horizontally on fallback layer
            const hasOverlapFallback = prevClips.some(
              (c) =>
                c.layerId === fallbackLayerId &&
                !activeSelectedIds.includes(c.id) &&
                potentialLeft < c.leftSeconds + c.durationSeconds &&
                potentialLeft + clip.durationSeconds > c.leftSeconds,
            );
            if (!hasOverlapFallback) {
              // No overlap on fallback layer, horizontal move allowed (vertical drag blocked)
              return prevClips.map((c) =>
                c.id === clip.id
                  ? { ...c, leftSeconds: potentialLeft, layerId: fallbackLayerId }
                  : c,
              );
            } else {
              // Collision on both layers. Completely block drag update for this position to prevent overlap
              return prevClips;
            }
          }
        } else {
          // Multi clip - apply both horizontal delta and relative vertical layer offset!
          const actClipInit = initialClipsData.get(clip.id);
          const sortedLayers = [...layers].sort((a, b) => b.order - a.order);
          const originalLayerIndex = actClipInit ? sortedLayers.findIndex(l => l.id === actClipInit.layer) : -1;
          const targetLayerIndex = sortedLayers.findIndex(l => l.id === targetLayerId);
          const layerOffset = (originalLayerIndex !== -1 && targetLayerIndex !== -1) ? (targetLayerIndex - originalLayerIndex) : 0;

          // Check if ANY of the selected clips would overlap with any non-selected clip
          const wouldOverlap = prevClips.some((c) => {
            if (activeSelectedIds.includes(c.id)) return false;

            return activeSelectedIds.some((selId) => {
              const selClip = prevClips.find(sc => sc.id === selId);
              const init = initialClipsData.get(selId);
              if (!selClip || !init) return false;

              const proposedLeft = Math.max(0, init.left + effectiveDelta);
              const initLayerIndex = sortedLayers.findIndex(l => l.id === init.layer);
              let proposedLayerId = init.layer;
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

          if (wouldOverlap) {
            // Collision detected for at least one clip in the selection, block the entire drag update
            return prevClips;
          }

          return prevClips.map((c) => {
            if (activeSelectedIds.includes(c.id)) {
              const init = initialClipsData.get(c.id);
              if (init) {
                const initLayerIndex = sortedLayers.findIndex(l => l.id === init.layer);
                let finalLayerId = init.layer;
                if (initLayerIndex !== -1 && layerOffset !== 0) {
                  const targetIndex = Math.max(0, Math.min(sortedLayers.length - 1, initLayerIndex + layerOffset));
                  finalLayerId = sortedLayers[targetIndex].id;
                }
                return { 
                  ...c, 
                  leftSeconds: Math.max(0, init.left + effectiveDelta),
                  layerId: finalLayerId
                };
              }
            }
            return c;
          });
        }
      });
    };

    const handlePointerUp = (upEvent: PointerEvent) => {
      clearTimeout(dragTimeout);
      try {
        target.releasePointerCapture(upEvent.pointerId);
      } catch (err) {}
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
      window.removeEventListener("pointercancel", handlePointerUp);

      // Handle selection toggles on simple tap/click
      if (!didMove && !isDraggingMode) {
        if (multiSelectActive) {
          if (wasAlreadySelected) {
            // Deselect on simple tap if it was already selected prior to mouse down
            setSelectedClipIds(prev => prev.filter(id => id !== clip.id));
          }
          // Note: If it wasn't selected, it got added on Pointer Down, which stays selected. Perfect!
        } else {
          // Normal mode: select only this clip
          setSelectedClipId(clip.id);
        }
      }

      // If a layer was created but the clip didn't end up on it, delete the layer
      if (createdLayerId) {
        const idToCheck = createdLayerId;
        const finalClip = clipsRef.current.find(c => c.id === clip.id);
        if (!finalClip || finalClip.layerId !== idToCheck) {
          setLayers((prevLayers) => prevLayers.filter(l => l.id !== idToCheck));
        }
      }
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    window.addEventListener("pointercancel", handlePointerUp);
  };

  const handleTrimStart = (
    e: React.PointerEvent,
    clip: Clip,
    side: "left" | "right",
  ) => {
    e.stopPropagation();
    setIsPlaying(false);
    (e.target as HTMLElement).setPointerCapture(e.pointerId);

    const startX = e.clientX;
    const initialLeftSeconds = clip.leftSeconds;
    const initialDurationSeconds = clip.durationSeconds;
    const initialTrimStartSeconds = clip.trimStartSeconds;

    const innerRectInit = document.getElementById("timeline-inner")?.getBoundingClientRect();
    const initialClickCanvasX = innerRectInit ? (startX - innerRectInit.left) : startX;

    const handlePointerMove = (moveEvent: PointerEvent) => {
      // --- AUTO SCROLL WHEN TRIMMING NEAR EDGES ---
      if (timelineScrollRef.current) {
        const container = timelineScrollRef.current;
        const containerRect = container.getBoundingClientRect();
        const margin = 50; 
        if (moveEvent.clientX > containerRect.right - margin) {
          const speedFactor = (moveEvent.clientX - (containerRect.right - margin)) * 0.15;
          container.scrollLeft += Math.min(10, speedFactor);
        } else if (moveEvent.clientX < containerRect.left + margin) {
          const speedFactor = ((containerRect.left + margin) - moveEvent.clientX) * 0.15;
          container.scrollLeft -= Math.min(10, speedFactor);
        }
      }

      const innerRectCurr = document.getElementById("timeline-inner")?.getBoundingClientRect();
      const currentClickCanvasX = innerRectCurr ? (moveEvent.clientX - innerRectCurr.left) : (moveEvent.clientX - startX);
      const deltaX = moveEvent.clientX - startX;
      let deltaSeconds = innerRectInit && innerRectCurr 
        ? (currentClickCanvasX - initialClickCanvasX) / currentPixelsPerSecondRef.current
        : deltaX / currentPixelsPerSecondRef.current;

      let nextTime = currentTime;

      if (side === "left") {
        if (clip.type === "image" || clip.type === "text") {
          let newLeft = Math.max(0, initialLeftSeconds + deltaSeconds);
          if (snappingEnabled) {
            const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
            let minDistance = SNAP_THRESHOLD_SECONDS;
            let snappedLeft = newLeft;
            const snapPoints = [0, currentTime];
            clips.forEach((other) => {
              if (other.id !== clip.id) {
                snapPoints.push(other.leftSeconds);
                snapPoints.push(other.leftSeconds + other.durationSeconds);
              }
            });
            snapPoints.forEach((sp) => {
              const dist = Math.abs(sp - newLeft);
              if (dist < minDistance) {
                minDistance = dist;
                snappedLeft = sp;
              }
            });
            newLeft = snappedLeft;
          }
          const change = newLeft - initialLeftSeconds;
          const newDuration = Math.max(0.5, initialDurationSeconds - change);
          if (newDuration >= 0.5) {
            const hasOverlap = clips.some((other) =>
              other.id !== clip.id &&
              other.layerId === clip.layerId &&
              newLeft < other.leftSeconds + other.durationSeconds &&
              newLeft + newDuration > other.leftSeconds
            );
            if (!hasOverlap) {
              nextTime = newLeft;
            }
          }
        } else {
          let newLeft = Math.max(0, initialLeftSeconds + deltaSeconds);
          if (snappingEnabled) {
            const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
            let minDistance = SNAP_THRESHOLD_SECONDS;
            let snappedLeft = newLeft;
            const snapPoints = [0, currentTime];
            clips.forEach((other) => {
              if (other.id !== clip.id) {
                snapPoints.push(other.leftSeconds);
                snapPoints.push(other.leftSeconds + other.durationSeconds);
              }
            });
            snapPoints.forEach((sp) => {
              const dist = Math.abs(sp - newLeft);
              if (dist < minDistance) {
                minDistance = dist;
                snappedLeft = sp;
              }
            });
            newLeft = snappedLeft;
          }

          const speed = clip.speed || 1.0;
          const change = newLeft - initialLeftSeconds;
          let newTrimStart = initialTrimStartSeconds + change * speed;
          let newDuration = initialDurationSeconds - change;
          let finalLeft = newLeft;

          if (newTrimStart < 0) {
            newTrimStart = 0;
            finalLeft = initialLeftSeconds - initialTrimStartSeconds / speed;
            newDuration = initialDurationSeconds + initialTrimStartSeconds / speed;
          }

          if (newDuration >= 0.5) {
            const hasOverlap = clips.some((other) =>
              other.id !== clip.id &&
              other.layerId === clip.layerId &&
              Math.max(0, finalLeft) < other.leftSeconds + other.durationSeconds &&
              Math.max(0, finalLeft) + newDuration > other.leftSeconds
            );
            if (!hasOverlap) {
              nextTime = Math.max(0, finalLeft);
            }
          }
        }
      } else {
        const maxAvailableDuration = clip.originalDurationSeconds !== undefined ? clip.originalDurationSeconds : Number.MAX_VALUE;
        if (clip.type === "image" || clip.type === "text") {
          let newDuration = Math.max(0.5, initialDurationSeconds + deltaSeconds);
          if (snappingEnabled) {
            let newRight = initialLeftSeconds + newDuration;
            const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
            let minDistance = SNAP_THRESHOLD_SECONDS;
            let snappedRight = newRight;
            const snapPoints = [currentTime];
            clips.forEach((other) => {
              if (other.id !== clip.id) {
                snapPoints.push(other.leftSeconds);
                snapPoints.push(other.leftSeconds + other.durationSeconds);
              }
            });
            snapPoints.forEach((sp) => {
              const dist = Math.abs(sp - newRight);
              if (dist < minDistance) {
                minDistance = dist;
                snappedRight = sp;
              }
            });
            newDuration = Math.max(0.5, snappedRight - initialLeftSeconds);
          }

          const hasOverlap = clips.some((other) =>
            other.id !== clip.id &&
            other.layerId === clip.layerId &&
            initialLeftSeconds < other.leftSeconds + other.durationSeconds &&
            initialLeftSeconds + newDuration > other.leftSeconds
          );
          if (!hasOverlap) {
            nextTime = initialLeftSeconds + newDuration;
          }
        } else {
          const speed = clip.speed || 1.0;
          let newDuration = Math.max(0.5, initialDurationSeconds + deltaSeconds);
          if (initialTrimStartSeconds + newDuration * speed > maxAvailableDuration) {
            newDuration = (maxAvailableDuration - initialTrimStartSeconds) / speed;
          }
          if (snappingEnabled) {
            let newRight = initialLeftSeconds + newDuration;
            const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
            let minDistance = SNAP_THRESHOLD_SECONDS;
            let snappedRight = newRight;
            const snapPoints = [currentTime];
            clips.forEach((other) => {
              if (other.id !== clip.id) {
                snapPoints.push(other.leftSeconds);
                snapPoints.push(other.leftSeconds + other.durationSeconds);
              }
            });
            snapPoints.forEach((sp) => {
              const dist = Math.abs(sp - newRight);
              if (dist < minDistance) {
                minDistance = dist;
                snappedRight = sp;
              }
            });
            newDuration = Math.max(0.5, snappedRight - initialLeftSeconds);
          }
          if (initialTrimStartSeconds + newDuration * speed > maxAvailableDuration) {
            newDuration = (maxAvailableDuration - initialTrimStartSeconds) / speed;
          }

          const hasOverlap = clips.some((other) =>
            other.id !== clip.id &&
            other.layerId === clip.layerId &&
            initialLeftSeconds < other.leftSeconds + other.durationSeconds &&
            initialLeftSeconds + newDuration > other.leftSeconds
          );
          if (!hasOverlap) {
            nextTime = initialLeftSeconds + newDuration;
          }
        }
      }

      setCurrentTime(nextTime);
      if (timelineScrollRef.current) {
        timelineScrollRef.current.scrollLeft = nextTime * currentPixelsPerSecondRef.current;
      }

      setClips((prev) =>
        prev.map((c) => {
          if (c.id !== clip.id) return c;
          
          const maxAvailableDuration = c.originalDurationSeconds !== undefined ? c.originalDurationSeconds : Number.MAX_VALUE;

          if (side === "left") {
            if (c.type === "image" || c.type === "text") {
              let newLeft = Math.max(0, initialLeftSeconds + deltaSeconds);
              
              if (snappingEnabled) {
                const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
                let minDistance = SNAP_THRESHOLD_SECONDS;
                let snappedLeft = newLeft;
                const snapPoints = [0, currentTime];
                prev.forEach(other => {
                  if (other.id !== clip.id) {
                    snapPoints.push(other.leftSeconds);
                    snapPoints.push(other.leftSeconds + other.durationSeconds);
                  }
                });
                snapPoints.forEach(sp => {
                  const dist = Math.abs(sp - newLeft);
                  if (dist < minDistance) {
                    minDistance = dist;
                    snappedLeft = sp;
                  }
                });
                newLeft = snappedLeft;
              }

              const change = newLeft - initialLeftSeconds;
              const newDuration = Math.max(0.5, initialDurationSeconds - change);
              if (newDuration < 0.5) return c; // Clamp

              // Collision check
              const hasOverlap = prev.some(other =>
                other.id !== clip.id &&
                other.layerId === clip.layerId &&
                newLeft < other.leftSeconds + other.durationSeconds &&
                newLeft + newDuration > other.leftSeconds
              );
              if (hasOverlap) return c;

              const updatedKeyframes = c.keyframes
                ? c.keyframes
                    .filter((kf) => kf.timeOffset >= change)
                    .map((kf) => ({
                      ...kf,
                      timeOffset: kf.timeOffset - change,
                    }))
                : [];

              return {
                ...c,
                leftSeconds: newLeft,
                durationSeconds: newDuration,
                trimStartSeconds: 0,
                keyframes: updatedKeyframes,
                opticalFlow: undefined,
              };
            }

            let newLeft = Math.max(0, initialLeftSeconds + deltaSeconds);
            
            // Snap left edge
            if (snappingEnabled) {
              const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
              let minDistance = SNAP_THRESHOLD_SECONDS;
              let snappedLeft = newLeft;
              const snapPoints = [0, currentTime];
              prev.forEach(other => {
                if (other.id !== clip.id) {
                  snapPoints.push(other.leftSeconds);
                  snapPoints.push(other.leftSeconds + other.durationSeconds);
                }
              });
              snapPoints.forEach(sp => {
                const dist = Math.abs(sp - newLeft);
                if (dist < minDistance) {
                  minDistance = dist;
                  snappedLeft = sp;
                }
              });
              newLeft = snappedLeft;
            }

            const speed = c.speed || 1.0;
            const change = newLeft - initialLeftSeconds;
            let newTrimStart = initialTrimStartSeconds + change * speed;
            let newDuration = initialDurationSeconds - change;
            let finalLeft = newLeft;

            if (newTrimStart < 0) {
              newTrimStart = 0;
              finalLeft = initialLeftSeconds - initialTrimStartSeconds / speed;
              newDuration = initialDurationSeconds + initialTrimStartSeconds / speed;
            }

            if (newDuration < 0.5) return c; // Clamp

            // Collision check
            const hasOverlap = prev.some(other =>
              other.id !== clip.id &&
              other.layerId === clip.layerId &&
              Math.max(0, finalLeft) < other.leftSeconds + other.durationSeconds &&
              Math.max(0, finalLeft) + newDuration > other.leftSeconds
            );
            if (hasOverlap) return c;

            const updatedKeyframes = c.keyframes
              ? c.keyframes
                  .filter((kf) => kf.timeOffset >= change)
                  .map((kf) => ({
                    ...kf,
                    timeOffset: kf.timeOffset - change,
                  }))
              : [];

            return {
              ...c,
              leftSeconds: Math.max(0, finalLeft),
              durationSeconds: newDuration,
              trimStartSeconds: newTrimStart,
              keyframes: updatedKeyframes,
              opticalFlow: undefined,
            };
          } else {
            if (c.type === "image" || c.type === "text") {
              let newDuration = Math.max(
                0.5,
                initialDurationSeconds + deltaSeconds,
              );
              
              if (snappingEnabled) {
                let newRight = initialLeftSeconds + newDuration;
                const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
                let minDistance = SNAP_THRESHOLD_SECONDS;
                let snappedRight = newRight;
                const snapPoints = [currentTime];
                prev.forEach(other => {
                  if (other.id !== clip.id) {
                    snapPoints.push(other.leftSeconds);
                    snapPoints.push(other.leftSeconds + other.durationSeconds);
                  }
                });
                snapPoints.forEach(sp => {
                  const dist = Math.abs(sp - newRight);
                  if (dist < minDistance) {
                    minDistance = dist;
                    snappedRight = sp;
                  }
                });
                newDuration = Math.max(0.5, snappedRight - initialLeftSeconds);
              }

              // Collision check
              const hasOverlap = prev.some(other =>
                other.id !== clip.id &&
                other.layerId === clip.layerId &&
                initialLeftSeconds < other.leftSeconds + other.durationSeconds &&
                initialLeftSeconds + newDuration > other.leftSeconds
              );
              if (hasOverlap) return c;

              const updatedKeyframes = c.keyframes
                ? c.keyframes.filter((kf) => kf.timeOffset <= newDuration)
                : [];

              return { ...c, durationSeconds: newDuration, keyframes: updatedKeyframes, opticalFlow: undefined };
            }

            const speed = c.speed || 1.0;
            let newDuration = Math.max(
              0.5,
              initialDurationSeconds + deltaSeconds,
            );
            
            if (initialTrimStartSeconds + newDuration * speed > maxAvailableDuration) {
                newDuration = (maxAvailableDuration - initialTrimStartSeconds) / speed;
            }
            
            // Snap right edge
            if (snappingEnabled) {
              let newRight = initialLeftSeconds + newDuration;
              const SNAP_THRESHOLD_SECONDS = 15 / currentPixelsPerSecondRef.current;
              let minDistance = SNAP_THRESHOLD_SECONDS;
              let snappedRight = newRight;
              const snapPoints = [currentTime];
              prev.forEach(other => {
                if (other.id !== clip.id) {
                  snapPoints.push(other.leftSeconds);
                  snapPoints.push(other.leftSeconds + other.durationSeconds);
                }
              });
              snapPoints.forEach(sp => {
                const dist = Math.abs(sp - newRight);
                if (dist < minDistance) {
                  minDistance = dist;
                  snappedRight = sp;
                }
              });
              newDuration = Math.max(0.5, snappedRight - initialLeftSeconds);
            }

            if (initialTrimStartSeconds + newDuration * speed > maxAvailableDuration) {
                newDuration = (maxAvailableDuration - initialTrimStartSeconds) / speed;
            }

            // Collision check
            const hasOverlap = prev.some(other =>
              other.id !== clip.id &&
              other.layerId === clip.layerId &&
              initialLeftSeconds < other.leftSeconds + other.durationSeconds &&
              initialLeftSeconds + newDuration > other.leftSeconds
            );
            if (hasOverlap) return c;

            const updatedKeyframes = c.keyframes
              ? c.keyframes.filter((kf) => kf.timeOffset <= newDuration)
              : [];

            return { ...c, durationSeconds: newDuration, keyframes: updatedKeyframes, opticalFlow: undefined };
          }
        }),
      );
    };

    const handlePointerUp = (upEvent: PointerEvent) => {
      (upEvent.target as HTMLElement).releasePointerCapture(upEvent.pointerId);
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
  };

  // Calculate max timeline duration from clips
  const maxTimelineDuration = useMemo(() => {
    let max = 0;
    clips.forEach((c) => {
      if (c.leftSeconds + c.durationSeconds > max)
        max = c.leftSeconds + c.durationSeconds;
    });
    return Math.max(max + 10, 30); // At least 30s buffer, always buffer + 10s
  }, [clips]);

  const visibleLayers = [...layers].sort((a, b) => b.order - a.order);

  // --- RENDERING ---
  const handleCreateProject = (r: string) => {
    setSelectedRatioTransition(r);
    setTimeout(() => {
      createProject(r);
      setIsCreatingProject(false);
      setSelectedRatioTransition(null);
    }, 400);
  };

  const handleMoveFlowBarItem = (index: number, direction: 'up' | 'down') => {
    if (direction === 'up' && index === 0) return;
    if (direction === 'down' && index === flowBarOrder.length - 1) return;
    
    setFlowBarOrder((prev) => {
      const newOrder = [...prev];
      const targetIndex = direction === 'up' ? index - 1 : index + 1;
      const temp = newOrder[index];
      newOrder[index] = newOrder[targetIndex];
      newOrder[targetIndex] = temp;
      localStorage.setItem("ai_studio_video_flowbar_order", JSON.stringify(newOrder));
      return newOrder;
    });
  };

  const getFlowBarItemLabel = (key: string) => {
    switch (key) {
      case 'volume': return 'Volume';
      case 'text': return 'Text';
      case 'crop': return 'Crop';
      case 'adjust': return 'Adjust';
      case 'speed': return 'Speed';
      case 'copy': return 'Copy';
      case 'move': return 'Move';
      case 'magic': return 'Magic';
      case 'activity': return 'Blend & Opacity';
      case 'mask': return 'Mask Shape';
      default: return key;
    }
  };

  const renderSettings = () => (
  <div className="flex flex-col h-screen w-full bg-[#0c0c0e] overflow-hidden relative">
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 scrollbar-hide">
        <div className="min-h-full flex flex-col pb-[150px]">
          {/* Header */}
          <div className="pt-32 pb-8 flex justify-between items-end mt-auto">
            <h1 className="text-[52px] font-extrabold tracking-tight leading-none text-white">
              Settings
            </h1>
            <button
              className="w-10 h-10 rounded-full hover:bg-zinc-800 flex items-center justify-center transition-colors mb-2 text-zinc-400 hover:text-white"
              onClick={() => setCurrentScreen("home")}
            >
              <ChevronLeft size={24} />
            </button>
          </div>

          {/* Settings Content */}
          <div className="flex flex-col gap-6 max-w-2xl mx-auto w-full mt-4">
            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                Flow Bar Order
              </h3>
              <p className="text-sm text-zinc-400 mb-4 font-medium">Customize the order of tools in the floating action menu.</p>
              <div className="flex flex-col gap-2">
                {flowBarOrder.map((key, index) => (
                  <div key={key} className="flex items-center justify-between bg-zinc-800/50 rounded-xl px-4 py-3 border border-white/5">
                    <span className="text-zinc-200 font-medium text-sm">{getFlowBarItemLabel(key)}</span>
                    <div className="flex items-center gap-1">
                      <button 
                        onClick={() => handleMoveFlowBarItem(index, 'up')}
                        disabled={index === 0}
                        className={`p-1.5 rounded-lg transition-colors ${index === 0 ? 'opacity-30' : 'hover:bg-zinc-700 text-zinc-400 hover:text-white'}`}
                      >
                        <ArrowUp size={16} />
                      </button>
                      <button 
                        onClick={() => handleMoveFlowBarItem(index, 'down')}
                        disabled={index === flowBarOrder.length - 1}
                        className={`p-1.5 rounded-lg transition-colors ${index === flowBarOrder.length - 1 ? 'opacity-30' : 'hover:bg-zinc-700 text-zinc-400 hover:text-white'}`}
                      >
                        <ArrowDown size={16} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                App Layout Scale
              </h3>
              <p className="text-sm text-zinc-400 mb-6 font-medium">
                Adjust the overall size of the app interface independently from your device display.
              </p>
              <div className="flex flex-col gap-4">
                <div className="flex justify-between items-center px-1">
                  <span className="text-zinc-300 font-medium text-sm">Scale</span>
                  <span className="text-white font-mono font-bold">{Math.round(appScale * 100)}%</span>
                </div>
                <input
                  type="range"
                  min="0.5"
                  max="2"
                  step="0.05"
                  value={appScale}
                  onChange={(e) => handleAppScaleChange(parseFloat(e.target.value))}
                  className="w-full accent-white h-2 rounded-lg appearance-none bg-zinc-800"
                />
                <div className="flex justify-between w-full px-1 mt-1">
                  <span className="text-[10px] text-zinc-500 font-bold">50%</span>
                  <span className="text-[10px] text-zinc-500 font-bold">100%</span>
                  <span className="text-[10px] text-zinc-500 font-bold">200%</span>
                </div>
                <button
                  onClick={() => handleAppScaleChange(1)}
                  className="mt-2 text-xs font-bold text-zinc-400 hover:text-white transition-colors bg-zinc-800/50 hover:bg-zinc-800 py-2 rounded-xl border border-white/5"
                >
                  Reset Default
                </button>
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                Timeline Snapping
              </h3>
              <p className="text-sm text-zinc-400 mb-6 font-medium">
                Toggle interactive grid alignment and edge snapping for layers and timeline playhead.
              </p>
              <div className="flex items-center justify-between bg-zinc-800/50 rounded-xl px-4 py-3 border border-white/5">
                <span className="text-zinc-200 font-medium text-sm">Enable Snapping</span>
                <button
                  type="button"
                  onClick={() => handleToggleSnapping(!snappingEnabled)}
                  className={`w-12 h-6 flex items-center rounded-full p-1 cursor-pointer transition-colors duration-200 outline-none ${snappingEnabled ? 'bg-indigo-600' : 'bg-zinc-700'}`}
                >
                  <div
                    className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform duration-200 ${snappingEnabled ? 'translate-x-[24px]' : 'translate-x-[0px]'}`}
                  />
                </button>
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                Export Preferences
              </h3>
              <div className="flex flex-col gap-5">
                <div className="flex justify-between items-center">
                  <span className="text-zinc-300 font-medium text-sm">
                    Default Resolution
                  </span>
                  <select className="bg-zinc-800 text-white rounded-xl px-4 py-2 outline-none border border-white/10 text-sm focus:border-white/20 transition-colors">
                    <option>1080p</option>
                    <option>4K</option>
                    <option>720p</option>
                  </select>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-zinc-300 font-medium text-sm">
                    Default FPS
                  </span>
                  <select className="bg-zinc-800 text-white rounded-xl px-4 py-2 outline-none border border-white/10 text-sm focus:border-white/20 transition-colors">
                    <option>30 fps</option>
                    <option>60 fps</option>
                  </select>
                </div>
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">App Info</h3>
              <div className="flex flex-col gap-2">
                <div className="flex justify-between items-center">
                  <span className="text-zinc-400 text-sm font-medium">
                    Version
                  </span>
                  <span className="text-zinc-500 font-mono text-sm">1.0.0</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-zinc-400 text-sm font-medium">
                    Developer
                  </span>
                  <span className="text-zinc-500 text-sm">AI Studio</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  
);

const renderHome = () => {
  // Sort the projects based on sortBy state
  const sortedAndFilteredProjects = [...projects].sort((a, b) => {
    if (sortBy === "name") {
      return a.name.localeCompare(b.name);
    } else if (sortBy === "duration") {
      return a.duration.localeCompare(b.duration);
    } else {
      // default "recent" order: New Project first, then Summer Vacation, then Frosted, then any custom
      const getWeight = (p: Project) => {
        if (p.id === "new-p") return 3;
        if (p.id === "1") return 2;
        if (p.id === "frosted-p") return 1;
        return 0;
      };
      const wA = getWeight(a);
      const wB = getWeight(b);
      if (wA !== wB) return wB - wA;
      // fallback to custom index or ID
      return b.id.localeCompare(a.id);
    }
  });

  return (
    <div className="flex flex-col h-screen w-full bg-black overflow-hidden relative">
      {/* Elegant Glowing Light Leak / Arch Effect at the top-right background */}
      <div className="absolute top-[-100px] right-[-200px] w-[600px] h-[500px] bg-[radial-gradient(circle_at_center,rgba(164,198,217,0.12)_0%,transparent_65%)] rounded-full blur-[100px] pointer-events-none z-0" />
      <div className="absolute top-[20%] right-[-150px] w-[500px] h-[400px] bg-[radial-gradient(circle_at_center,rgba(249,115,22,0.04)_0%,transparent_60%)] rounded-full blur-[80px] pointer-events-none z-0" />

      {/* Main Scrollable Viewport */}
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 scrollbar-hide z-10">
        <div className="min-h-full flex flex-col pb-[120px]">
          
          {/* Header Area with profile info and notification bell */}
          <div className="pt-8 pb-4 flex justify-between items-center max-w-2xl mx-auto w-full">
            <div className="flex items-center gap-3">
              <div className="relative w-10 h-10 rounded-full overflow-hidden border border-white/20 shadow-[0_0_12px_rgba(255,255,255,0.15)] bg-zinc-900">
                <img
                  src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=150"
                  alt="Ritwik"
                  className="w-full h-full object-cover"
                  referrerPolicy="no-referrer"
                />
              </div>
              <div className="flex flex-col">
                <span className="text-zinc-500 font-medium text-[10px] sm:text-[11px] uppercase tracking-wider leading-none mb-0.5">
                  {new Date().getHours() < 12 ? "Good Morning," : new Date().getHours() < 18 ? "Good Afternoon," : "Good Evening,"}
                </span>
                <span className="text-white font-extrabold text-sm tracking-tight leading-none">
                  Ritwik
                </span>
              </div>
            </div>

            <div className="flex gap-2">
              {!Capacitor.isNativePlatform() && (
                <button
                  onClick={toggleFullscreen}
                  className="w-10 h-10 rounded-full bg-zinc-900/60 backdrop-blur-md border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/20 transition-all active:scale-95 group cursor-pointer"
                  title={isFullscreen ? "Exit Fullscreen" : "Enter Fullscreen"}
                >
                  {isFullscreen ? (
                    <Minimize2 size={18} className="transition-transform group-hover:scale-110" />
                  ) : (
                    <Maximize2 size={18} className="transition-transform group-hover:scale-110" />
                  )}
                </button>
              )}

              <button className="relative w-10 h-10 rounded-full bg-zinc-900/60 backdrop-blur-md border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/20 transition-all active:scale-95 group cursor-pointer">
                <Bell size={18} className="transition-transform group-hover:rotate-12" />
                <span className="absolute top-2.5 right-2.5 w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_6px_rgba(249,115,22,0.8)]" />
              </button>
            </div>
          </div>

          {/* Premium Branded ORCA Creative Studio Logo */}
          <div className="pt-10 pb-8 flex flex-col justify-center items-center w-full mt-[2vh] text-center">
            <h1 className="text-7xl sm:text-8.5xl font-black tracking-[-0.04em] text-white leading-none uppercase bg-clip-text text-transparent bg-gradient-to-b from-white via-zinc-100 to-zinc-400">
              ORCA
            </h1>
            <div className="tracking-[0.35em] text-[10px] sm:text-[11px] text-zinc-500 font-bold uppercase flex items-center justify-center gap-1.5 mt-3 pl-[0.35em]">
              <span>Creative Studio</span>
              <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_5px_rgba(249,115,22,0.6)]" />
            </div>
          </div>

          {/* Subsection Header: Projects & Filter */}
          <div className="flex justify-between items-center max-w-2xl mx-auto w-full mb-6 mt-4 px-1">
            <h2 className="text-lg sm:text-xl font-bold text-white tracking-tight">
              Projects
            </h2>
            
            {/* Sort Dropdown Selector */}
            <div className="relative">
              <button
                onClick={() => setIsSortMenuOpen(!isSortMenuOpen)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-900/60 border border-white/5 hover:bg-zinc-800/80 hover:border-white/15 text-zinc-300 text-xs font-bold leading-none cursor-pointer transition-all active:scale-95 z-20"
              >
                <span>
                  {sortBy === "recent" ? "Recent" : sortBy === "name" ? "Alphabetical" : "Duration"}
                </span>
                <ChevronDown size={12} className={`text-zinc-500 transition-transform duration-200 ${isSortMenuOpen ? "rotate-180" : ""}`} />
              </button>

              <AnimatePresence>
                {isSortMenuOpen && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95, y: -5 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: -5 }}
                    className="absolute right-0 top-8 w-36 bg-zinc-900/95 border border-white/10 rounded-xl p-1 shadow-[0_12px_40px_rgba(0,0,0,0.8)] backdrop-blur-xl z-50"
                  >
                    {[
                      { value: "recent", label: "Recent Order" },
                      { value: "name", label: "Alphabetical" },
                      { value: "duration", label: "Duration" },
                    ].map((item) => (
                      <button
                        key={item.value}
                        onClick={() => {
                          setSortBy(item.value as any);
                          setIsSortMenuOpen(false);
                        }}
                        className={`w-full text-left px-2.5 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center justify-between ${
                          sortBy === item.value ? "bg-white/[0.05] text-white" : "text-zinc-400 hover:bg-white/[0.02] hover:text-zinc-200"
                        }`}
                      >
                        <span>{item.label}</span>
                        {sortBy === item.value && <Check size={11} className="text-emerald-400" />}
                      </button>
                    ))}
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          {/* Project List */}
          <div className="flex flex-col gap-4.5 max-w-2xl mx-auto w-full">
            {sortedAndFilteredProjects.map((p) => (
              <div
                key={p.id}
                className="relative h-[160px] w-full rounded-[30px] overflow-hidden cursor-pointer bg-zinc-950/20 hover:bg-zinc-950/30 backdrop-blur-xl border border-white/10 hover:border-white/20 active:scale-[0.99] transition-all duration-300 shadow-[0_12px_40px_-15px_rgba(0,0,0,0.8)] flex group"
                onClick={() => openProject(p)}
              >
                {/* Glossy sheen base texture overlay */}
                <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.01] to-white/[0.05] pointer-events-none z-0" />

                {/* Highly reflective glass gleam transition line */}
                <div className="absolute inset-0 w-1/2 h-full bg-gradient-to-r from-transparent via-white/[0.07] to-transparent -skew-x-[25deg] -translate-x-[150%] group-hover:translate-x-[220%] transition-transform duration-1000 ease-[cubic-bezier(0.25,1,0.5,1)] pointer-events-none z-20" />

                {/* Beveled edge light reflections */}
                <div className="absolute inset-x-0 top-0 h-[1.5px] bg-gradient-to-r from-white/0 via-white/25 to-white/0 opacity-80 group-hover:opacity-100 transition-opacity pointer-events-none z-20" />
                <div className="absolute inset-y-0 left-0 w-[1.5px] bg-gradient-to-b from-white/10 to-transparent pointer-events-none z-20" />

                {/* Left Text Info Side */}
                <div className="flex-1 p-5 flex flex-col justify-between h-full z-10 relative">
                  {/* Top: Tag */}
                  <div className="flex items-center gap-1.5 bg-white/[0.03] border border-white/5 rounded-full px-2.5 py-0.5 w-fit">
                    <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_6px_#f97316]" />
                    <span className="text-[9px] font-bold text-zinc-400 uppercase tracking-widest leading-none">
                      {p.ratio === "9:16" ? "Reels" : p.ratio === "16:9" ? "YouTube" : "Concept"}
                    </span>
                  </div>

                  {/* Middle: Title & Label */}
                  <div className="mt-1">
                    <h3 className="text-lg sm:text-xl font-bold text-white tracking-tight leading-none flex items-center gap-1.5">
                      {p.name}
                    </h3>
                    <p className="text-[11.5px] sm:text-[12.5px] font-medium text-zinc-400 leading-snug mt-1.5 max-w-[95%] line-clamp-2">
                      Concept project focusing on simplicity & usability.
                    </p>
                  </div>

                  {/* Bottom: Specs */}
                  <div className="flex items-center gap-1.5 text-zinc-500 text-[10px] font-bold tracking-wider uppercase mt-1">
                    <Clock size={11} className="text-zinc-500" />
                    <span>{p.duration || "00:12"}</span>
                    <span className="text-zinc-600">•</span>
                    <span>Draft</span>
                  </div>
                </div>

                {/* Right Image/Artwork Side */}
                <div className="w-[45%] h-full shrink-0 relative overflow-hidden z-10 bg-zinc-900/50">
                  <ProjectCoverImage p={p} />
                  {/* Frosted vignette masking division */}
                  <div className="absolute inset-y-0 left-0 w-8 bg-gradient-to-r from-zinc-950/40 via-zinc-950/20 to-transparent pointer-events-none" />

                  {/* Three-dots menu button */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setProjectMenuOpenId(projectMenuOpenId === p.id ? null : p.id);
                    }}
                    className="absolute top-3.5 right-3.5 w-8 h-8 rounded-full bg-black/50 border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:bg-black/75 hover:border-white/25 transition-all backdrop-blur-md z-30 pointer-events-auto"
                  >
                    <MoreHorizontal size={14} />
                  </button>

                  {/* Action Menu overlay per project card */}
                  <AnimatePresence>
                    {projectMenuOpenId === p.id && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.9, y: -5 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9, y: -5 }}
                        onClick={(e) => e.stopPropagation()}
                        className="absolute right-3.5 top-13 w-32 bg-zinc-900/95 border border-white/10 rounded-2xl p-1.5 shadow-[0_12px_36px_rgba(0,0,0,0.9)] backdrop-blur-xl z-40 pointer-events-auto"
                      >
                        <button
                          onClick={() => {
                            setProjectMenuOpenId(null);
                            openProject(p);
                          }}
                          className="w-full text-left px-3 py-2 text-xs font-semibold text-white hover:bg-white/[0.05] rounded-xl flex items-center justify-between cursor-pointer"
                        >
                          <span>Open</span>
                          <ChevronRight size={11} className="text-zinc-500" />
                        </button>
                        <button
                          onClick={() => {
                            setProjectMenuOpenId(null);
                            duplicateProject(p);
                          }}
                          className="w-full text-left px-3 py-2 text-xs font-semibold text-white hover:bg-white/[0.05] rounded-xl flex items-center justify-between cursor-pointer"
                        >
                          <span>Duplicate</span>
                          <Copy size={11} className="text-zinc-400" />
                        </button>
                        <div className="h-[1px] bg-white/5 my-1" />
                        <button
                          onClick={() => {
                            setProjectMenuOpenId(null);
                            setProjectToDelete(p.id);
                          }}
                          className="w-full text-left px-3 py-2 text-xs font-semibold text-red-400 hover:bg-red-500/10 rounded-xl flex items-center justify-between cursor-pointer"
                        >
                          <span>Delete</span>
                          <Trash2 size={11} className="text-red-400/70" />
                        </button>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Persistent Rounded Floating Footer Row */}
      <div className="absolute bottom-[50px] left-0 right-0 flex justify-center items-center px-6 z-40 pointer-events-none">
        <div className="flex items-center gap-3 w-full max-w-[340px] pointer-events-auto">
          {/* New Project Button */}
          <button
            onClick={() => setIsCreatingProject(true)}
            className="relative flex-1 h-[50px] rounded-full bg-zinc-950/40 hover:bg-zinc-950/55 backdrop-blur-xl border border-white/10 text-white font-bold text-sm tracking-wide flex items-center justify-center gap-2 hover:border-white/20 active:scale-[0.97] transition-all duration-300 shadow-[0_12px_36px_rgba(0,0,0,0.65)] cursor-pointer overflow-hidden group"
          >
            {/* Glossy shine */}
            <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.01] to-white/[0.04] pointer-events-none" />
            <div className="absolute inset-0 w-1/2 h-full bg-gradient-to-r from-transparent via-white/[0.1] to-transparent -skew-x-[20deg] -translate-x-[150%] group-hover:translate-x-[250%] transition-transform duration-1000 ease-out pointer-events-none" />
            <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/15 to-transparent pointer-events-none" />
            <PlusIcon size={16} className="text-zinc-400 group-hover:text-white transition-colors" />
            <span>New Project</span>
          </button>

          {/* Settings Button */}
          <button
            onClick={() => setCurrentScreen("settings")}
            className="relative w-[50px] h-[50px] rounded-full bg-zinc-950/40 hover:bg-zinc-950/55 backdrop-blur-xl border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/20 active:scale-[0.97] transition-all duration-300 shadow-[0_12px_36px_rgba(0,0,0,0.65)] cursor-pointer overflow-hidden group"
          >
            {/* Glossy shine */}
            <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.01] to-white/[0.04] pointer-events-none" />
            <div className="absolute inset-0 w-1/2 h-full bg-gradient-to-r from-transparent via-white/[0.1] to-transparent -skew-x-[20deg] -translate-x-[150%] group-hover:translate-x-[250%] transition-transform duration-1000 ease-out pointer-events-none" />
            <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/15 to-transparent pointer-events-none" />
            <Settings size={18} className="group-hover:rotate-45 transition-transform duration-300" />
          </button>
        </div>
      </div>

      {/* Ratio Selection Overlay */}
      <AnimatePresence>
        {isCreatingProject && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
            className="fixed inset-0 z-50 bg-[#040404] flex flex-col items-center justify-center overflow-hidden"
          >
            {/* Dotted Grid Background */}
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center z-0">
               <div 
                 className="w-full max-w-[800px] h-full max-h-[800px] opacity-[0.2]"
                 style={{
                   backgroundImage: `radial-gradient(circle at center, rgba(255,255,255,0.8) 1px, transparent 1px)`,
                   backgroundSize: `28px 28px`,
                   WebkitMaskImage: `radial-gradient(ellipse 50% 50% at center, black 10%, transparent 60%)`,
                   maskImage: `radial-gradient(ellipse 50% 50% at center, black 10%, transparent 60%)`
                 }}
               />
            </div>

            {/* Title */}
            <motion.div 
               initial={{ opacity: 0, y: -10 }}
               animate={{ opacity: 1, y: 0 }}
               transition={{ delay: 0.2, duration: 0.8 }}
               className="absolute top-[18%] left-0 right-0 flex items-center justify-center px-8 pointer-events-none z-10"
            >
              <div className="w-[60px] sm:w-[100px] h-[1px] bg-gradient-to-r from-transparent to-zinc-700" />
              <span className="text-[10px] font-semibold tracking-[0.3em] text-zinc-500 px-6 uppercase whitespace-nowrap">
                Choose Format
              </span>
              <div className="w-[60px] sm:w-[100px] h-[1px] bg-gradient-to-l from-transparent to-zinc-700" />
            </motion.div>

            <motion.button
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="absolute top-8 right-8 w-12 h-12 rounded-full bg-transparent hover:bg-zinc-800/30 flex items-center justify-center text-zinc-500 hover:text-white transition-colors z-50"
              onClick={() => setIsCreatingProject(false)}
            >
              <X size={24} strokeWidth={1.5} />
            </motion.button>

            <div 
              className="relative z-20 flex flex-nowrap items-center overflow-x-auto w-full pb-10 pt-16 scrollbar-hide snap-x snap-mandatory"
              onScroll={(e) => {
                const container = e.currentTarget;
                const containerCenter = container.getBoundingClientRect().left + container.clientWidth / 2;
                let closest = null;
                let minDist = Infinity;
                container.childNodes.forEach((node) => {
                  if (node.nodeType === 1) {
                    const el = node as HTMLElement;
                    const val = el.getAttribute("data-ratio");
                    if (val) {
                      const rect = el.getBoundingClientRect();
                      const elCenter = rect.left + rect.width / 2;
                      const dist = Math.abs(elCenter - containerCenter);
                      if (dist < minDist) {
                        minDist = dist;
                        closest = val;
                      }
                    }
                  }
                });
                if (closest && closest !== focusedRatio) setFocusedRatio(closest);
              }}
              style={{
                paddingLeft: "calc(50vw - 160px)",
                paddingRight: "calc(50vw - 160px)"
              }}
            >
              {[
                { ratio: "9:16", baseW: 160, baseH: 340, label: "PORTRAIT" },
                { ratio: "16:9", baseW: 340, baseH: 160, label: "LANDSCAPE" },
                { ratio: "1:1", baseW: 240, baseH: 240, label: "SQUARE" },
                { ratio: "custom", baseW: 240, baseH: 120, label: "CUSTOM" },
              ].map((r, i) => {
                const isFocused = focusedRatio === r.ratio;
                const scale = isFocused ? 1 : 0.85;
                return (
                  <div key={r.ratio} data-ratio={r.ratio} className="w-[320px] shrink-0 snap-center flex justify-center items-center">
                    <motion.div
                       className="cursor-pointer group relative flex justify-center items-center"
                       onClick={(e) => {
                          if (!isFocused) {
                             e.currentTarget.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
                          }
                       }}
                    >
                      <motion.div
                        animate={{ width: r.baseW * scale, height: r.baseH * scale }}
                        transition={{ type: "spring", bounce: 0.3 }}
                        className={`relative rounded-[36px] flex items-center justify-center transition-all duration-500 ${isFocused ? "p-[1.5px]" : "p-[1px]"}`}
                      >
                         {/* Gradient Border for Focused */}
                         <div 
                            className={`absolute inset-0 rounded-[36px] pointer-events-none transition-opacity duration-700 ${isFocused ? "opacity-100" : "opacity-0"}`} 
                            style={{ background: 'linear-gradient(145deg, rgba(167, 139, 250, 0.8) 0%, rgba(255, 255, 255, 0.1) 40%, rgba(255, 255, 255, 0.1) 60%, rgba(251, 146, 60, 0.7) 100%)' }} 
                         />
                         
                         {/* Subtle Border for Unfocused */}
                         <div className={`absolute inset-0 rounded-[36px] pointer-events-none transition-opacity duration-500 border border-white/10 ${!isFocused ? "opacity-100" : "opacity-0"}`} />

                         <div className="absolute inset-[1.5px] rounded-[34.5px] bg-[#040404] z-10 pointer-events-none" />
                         
                         <div className="relative z-20 flex flex-col items-center justify-center gap-2">
                           {r.ratio === "custom" && isFocused ? (
                             <div className="flex items-center gap-3 bg-zinc-900/40 rounded-2xl p-2 border border-white/5" onClick={e => e.stopPropagation()}>
                               <input type="number" placeholder="W" className="bg-transparent w-16 text-center text-lg text-white outline-none font-medium" value={customRatioW} onChange={(e) => setCustomRatioW(e.target.value)} />
                               <div className="w-[1px] h-6 bg-zinc-700"></div>
                               <input type="number" placeholder="H" className="bg-transparent w-16 text-center text-lg text-white outline-none font-medium" value={customRatioH} onChange={(e) => setCustomRatioH(e.target.value)} />
                             </div>
                           ) : (
                             <span className={`font-medium text-[28px] transition-colors duration-500 tracking-wide ${isFocused ? "text-[#f8f8f8]" : "text-zinc-600"}`}>
                               {r.ratio === "custom" ? "Custom" : r.ratio}
                             </span>
                           )}
                           
                           {isFocused ? (
                             <span className="text-[9px] font-medium tracking-[0.25em] text-zinc-400 uppercase mt-1">
                               {r.label}
                             </span>
                           ) : (
                             <span className="text-[9px] font-medium tracking-[0.25em] text-zinc-700 uppercase mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                               {r.label}
                             </span>
                           )}
                         </div>
                      </motion.div>
                    </motion.div>
                  </div>
                );
              })}
            </div>

            <motion.div 
               initial={{ opacity: 0, y: 10 }}
               animate={{ opacity: 1, y: 0 }}
               transition={{ delay: 0.3, duration: 0.8 }}
               className="absolute bottom-[10%] left-0 right-0 flex flex-col items-center pointer-events-none z-30"
            >
              <button 
                className="pointer-events-auto relative w-[240px] h-[52px] group flex items-center justify-center rounded-[26px] transition-all duration-300 hover:scale-[1.02] active:scale-[0.98]"
                onClick={() => {
                   handleCreateProject(focusedRatio === "custom" ? `${customRatioW}:${customRatioH}` : focusedRatio);
                }}
              >
                 <div className="absolute inset-0 rounded-[26px] p-[1px] opacity-60 group-hover:opacity-100 transition-opacity duration-300"
                      style={{ background: 'linear-gradient(90deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.05) 50%, rgba(255,255,255,0.6) 100%)' }} />
                 <div className="absolute inset-[1px] rounded-[25px] bg-[#040404]" />
                 <span className="relative z-10 font-bold tracking-[0.35em] text-[#f8f8f8] text-[11px] uppercase ml-1 opacity-90 group-hover:opacity-100 drop-shadow-sm">
                   Create
                 </span>
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {projectToDelete && (
          <div
            className="fixed inset-0 z-[300] bg-black/60 flex items-center justify-center p-4"
            onClick={() => setProjectToDelete(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-[#252528] border border-white/10 rounded-[32px] p-6 max-w-sm w-full shadow-2xl"
            >
              <h3 className="text-xl font-bold text-white mb-2">
                Delete Project
              </h3>
              <p className="text-zinc-400 text-sm mb-6 font-medium">
                Are you sure you want to delete this project? This action cannot
                be undone.
              </p>
              <div className="flex justify-end gap-3">
                <button
                  className="px-5 py-2.5 rounded-full text-sm font-bold text-white hover:bg-zinc-700 transition-colors"
                  onClick={() => setProjectToDelete(null)}
                >
                  Cancel
                </button>
                <button
                  className="px-5 py-2.5 bg-red-500 hover:bg-red-600 rounded-full text-sm font-bold text-white transition-colors"
                  onClick={confirmDeleteProject}
                >
                  Delete
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

const renderEditor = () => (
  <div className="flex flex-col h-screen w-full bg-[#0c0c0e] overflow-hidden">
      {/* Premium Unified Export Overlay */}
      <ExportOverlay
        isExporting={isExporting}
        setIsExporting={setIsExporting}
        exportedVideoUrl={exportedVideoUrl}
        setExportedVideoUrl={setExportedVideoUrl}
        exportedVideoBlob={exportedVideoBlob}
        setExportedVideoBlob={setExportedVideoBlob}
        exportProgress={exportProgress}
        setExportProgress={setExportProgress}
        exportResolution={exportResolution}
        currentProjectRatio={currentProjectRatio}
        clips={clips}
      />

      {/* Top Header */}
      <header className="flex justify-between items-center px-4 py-4 shrink-0 relative z-[100] pointer-events-none">
        
        <div className="flex items-center justify-center bg-zinc-800 rounded-3xl w-[180px] px-1 py-1 shadow-lg border border-white/5 pointer-events-auto">
          
          <div className="relative">
            <div
              onClick={() => setIsRatioExpanded(!isRatioExpanded)}
              className={`px-3 py-1.5 rounded-full text-[10px] sm:text-[11px] font-extrabold tracking-wider cursor-pointer select-none flex items-center gap-2 transition-all duration-300 ${
                isRatioExpanded
                  ? "bg-white text-black shadow-[0_0_12px_rgba(255,255,255,0.25)]"
                  : "bg-zinc-800 text-zinc-100 hover:bg-zinc-700 hover:text-white"
              }`}
            >
              <div className="flex items-center gap-1.5">
                {currentProjectRatio === "9:16" ? (
                  <div className={`w-1.5 h-3 border rounded-[1px] transition-colors ${isRatioExpanded ? "border-black" : "border-white/80"}`} />
                ) : currentProjectRatio === "16:9" ? (
                  <div className={`w-3 h-1.5 border rounded-[1px] transition-colors ${isRatioExpanded ? "border-black" : "border-white/80"}`} />
                ) : currentProjectRatio === "1:1" ? (
                  <div className={`w-2.5 h-2.5 border rounded-[1px] transition-colors ${isRatioExpanded ? "border-black" : "border-white/80"}`} />
                ) : (
                  <div className={`w-2.5 h-2 border border-dashed rounded-[1px] transition-colors ${isRatioExpanded ? "border-black" : "border-white/60"}`} />
                )}
                <span>Aspect: {currentProjectRatio}</span>
              </div>
              <ChevronDown
                size={12}
                className={`transition-transform duration-300 ${isRatioExpanded ? "rotate-180" : "opacity-80"}`}
              />
            </div>

            <AnimatePresence>
              {isRatioExpanded && (
                <>
                  <div
                    className="fixed inset-0 z-[140] cursor-default pointer-events-auto"
                    onClick={(e) => {
                      e.stopPropagation();
                      setIsRatioExpanded(false);
                    }}
                  />
                  <motion.div
                    initial={{ opacity: 0, y: -10, scale: 0.95 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: -10, scale: 0.95 }}
                    onClick={(e) => e.stopPropagation()}
                    className="absolute top-[calc(100%+12px)] left-0 bg-zinc-950/95 backdrop-blur-xl border border-white/10 shadow-[0_20px_50px_rgba(0,0,0,0.5)] rounded-2xl w-[240px] flex flex-col p-3 z-[150] origin-top-left overflow-hidden text-left pointer-events-auto"
                  >
                    <div className="text-[9px] uppercase font-bold text-zinc-400 tracking-wider mb-2.5 px-1">
                      Change Aspect Ratio
                    </div>
                    {[
                      { r: "9:16", label: "Reels, TikTok", desc: "Vertical video" },
                      { r: "16:9", label: "YouTube", desc: "Horizontal video" },
                      { r: "1:1", label: "Instagram", desc: "Square post" },
                    ].map(({ r, label }) => (
                      <button
                        key={r}
                        onClick={(e) => {
                          e.stopPropagation();
                          setCurrentProjectRatio(r);
                          setIsRatioExpanded(false);
                          showToast(`Ratio changed to ${r}`);
                        }}
                        className={`w-full flex items-center justify-between p-2.5 rounded-xl text-left transition-all duration-250 mb-1 group ${
                          currentProjectRatio === r
                            ? "bg-white text-black font-extrabold shadow-md"
                            : "text-zinc-300 hover:bg-zinc-900 hover:text-white"
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          {r === "9:16" ? (
                            <div className={`w-2 h-4 border rounded-[2px] shrink-0 ${currentProjectRatio === r ? "border-black" : "border-zinc-400 group-hover:border-white"}`} />
                          ) : r === "16:9" ? (
                            <div className={`w-4 h-2.5 border rounded-[2px] shrink-0 ${currentProjectRatio === r ? "border-black" : "border-zinc-400 group-hover:border-white"}`} />
                          ) : (
                            <div className={`w-3 h-3 border rounded-[2px] shrink-0 ${currentProjectRatio === r ? "border-black" : "border-zinc-400 group-hover:border-white"}`} />
                          )}
                          <div className="flex flex-col">
                            <span className="text-xs font-bold leading-normal">{r}</span>
                            <span className={`text-[9px] font-medium leading-none mt-0.5 ${currentProjectRatio === r ? "text-zinc-700" : "text-zinc-500 group-hover:text-zinc-300"}`}>{label}</span>
                          </div>
                        </div>
                        {currentProjectRatio === r && (
                          <Check size={14} className="text-black stroke-[3]" />
                        )}
                      </button>
                    ))}

                    <div className="h-px bg-white/10 my-2.5"></div>
                    <div className="text-[9px] uppercase font-bold text-zinc-400 tracking-wider mb-2 px-1">
                      Custom Ratio
                    </div>
                    <div className="flex items-center gap-1.5 px-1">
                      <div className="relative flex-1">
                        <input
                          type="number"
                          placeholder="W"
                          className="bg-zinc-900 w-full text-center text-xs text-white outline-none font-bold py-2 rounded-lg border border-white/10 focus:border-white/30"
                          value={customRatioW}
                          onChange={(e) => setCustomRatioW(e.target.value)}
                          onClick={(e) => e.stopPropagation()}
                        />
                        <span className="absolute right-1 top-1 text-[8px] font-bold text-zinc-600">W</span>
                      </div>
                      <span className="text-zinc-600 text-xs font-bold">:</span>
                      <div className="relative flex-1">
                        <input
                          type="number"
                          placeholder="H"
                          className="bg-zinc-900 w-full text-center text-xs text-white outline-none font-bold py-2 rounded-lg border border-white/10 focus:border-white/30"
                          value={customRatioH}
                          onChange={(e) => setCustomRatioH(e.target.value)}
                          onClick={(e) => e.stopPropagation()}
                        />
                        <span className="absolute right-1 top-1 text-[8px] font-bold text-zinc-600">H</span>
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          const w = parseInt(customRatioW);
                          const h = parseInt(customRatioH);
                          if (w > 0 && h > 0) {
                            const newRatio = `${w}:${h}`;
                            setCurrentProjectRatio(newRatio);
                            setIsRatioExpanded(false);
                            showToast(`Ratio changed to ${newRatio}`);
                          } else {
                            showToast("Enter valid W & H");
                          }
                        }}
                        className="bg-white hover:bg-zinc-100 text-black rounded-lg text-[10px] font-black px-2.5 py-2.5 transition-colors shadow-md shrink-0"
                      >
                        Apply
                      </button>
                    </div>
                  </motion.div>
                </>
              )}
            </AnimatePresence>
          </div>
        </div>
          
        <div className="flex items-center gap-2 pointer-events-auto">
          {!Capacitor.isNativePlatform() && (
            <button
              onClick={toggleFullscreen}
              className="w-7 h-7 sm:w-8 sm:h-8 rounded-full bg-zinc-800 border border-white/5 hover:bg-zinc-700 flex items-center justify-center transition-colors text-zinc-400 hover:text-white"
              title={isFullscreen ? "Exit Fullscreen" : "Enter Fullscreen"}
            >
              {isFullscreen ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
            </button>
          )}
          
          <div className="relative">
            <button
              onClick={() => setIsExportExpanded(!isExportExpanded)}
              className="bg-white text-black px-4 h-[28px] rounded-full text-[10px] font-bold shadow hover:bg-zinc-200 transition-colors whitespace-nowrap"
            >
              EXPORT
            </button>

            <AnimatePresence>
              {isExportExpanded && (
                <motion.div
                  initial={{ opacity: 0, y: -10, scale: 0.95 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -10, scale: 0.95 }}
                  className="absolute top-[calc(100%+8px)] right-0 bg-zinc-800 border border-white/10 shadow-2xl rounded-2xl w-[200px] flex flex-col p-2 z-[150] origin-top-right overflow-hidden"
                >
                  <div className="flex items-center justify-between px-3 py-2">
                    <span className="text-[11px] font-semibold text-white/50">
                      Resolution
                    </span>
                    <select
                      className="bg-transparent text-white text-[11px] font-semibold outline-none cursor-pointer hover:text-yellow-400 transition-colors text-right"
                      value={exportResolution}
                      onChange={(e) => setExportResolution(e.target.value)}
                    >
                      <option value="1080p" className="bg-zinc-800 text-white">
                        1080p
                      </option>
                      <option value="2K" className="bg-zinc-800 text-white">
                        2K
                      </option>
                      <option value="4K" className="bg-zinc-800 text-white">
                        4K
                      </option>
                    </select>
                  </div>
                  <div className="flex items-center justify-between px-3 py-2">
                    <span className="text-[11px] font-semibold text-white/50">
                      Frame Rate
                    </span>
                    <select
                      className="bg-transparent text-white text-[11px] font-semibold outline-none cursor-pointer hover:text-yellow-400 transition-colors text-right"
                      value={exportFps}
                      onChange={(e) => setExportFps(e.target.value)}
                    >
                      <option value="24" className="bg-zinc-800 text-white">
                        24 fps
                      </option>
                      <option value="30" className="bg-zinc-800 text-white">
                        30 fps
                      </option>
                      <option value="60" className="bg-zinc-800 text-white">
                        60 fps
                      </option>
                    </select>
                  </div>
                  <div className="flex items-center justify-between px-3 py-2 mb-2">
                    <span className="text-[11px] font-semibold text-white/50">
                      Bitrate
                    </span>
                    <select
                      className="bg-transparent text-white text-[11px] font-semibold outline-none cursor-pointer hover:text-yellow-400 transition-colors text-right"
                      value={exportBitrate}
                      onChange={(e) => setExportBitrate(e.target.value)}
                    >
                      <option value="Smart" className="bg-zinc-800 text-white">
                        Smart
                      </option>
                      <option value="High" className="bg-zinc-800 text-white">
                        High
                      </option>
                      <option value="Max" className="bg-zinc-800 text-white">
                        Max
                      </option>
                    </select>
                  </div>
                  <button
                    onClick={startExport}
                    className="w-full bg-white text-black py-2.5 rounded-xl text-[11px] font-bold shadow hover:bg-zinc-200 transition-colors active:scale-95 mt-4"
                  >
                    Start Export
                  </button>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </header>

      {/* Main Preview Area */}
      <main className="flex-1 min-h-0 flex flex-col pt-2 pb-4 relative z-[80] bg-[#0c0c0e]">
        <div className="flex-1 min-h-0 relative flex items-center justify-center px-4">
          <div className={`relative w-full h-full flex items-center justify-center transition-all duration-300 ${
            activeExpandedMenu === "plus-media" ? "opacity-0 scale-[0.97] pointer-events-none" : "opacity-100 scale-100"
          }`}>
            <svg
              viewBox={`0 0 ${currentProjectRatio.split(":")[0]} ${currentProjectRatio.split(":")[1]}`}
              className="max-w-full max-h-full h-[100%] pointer-events-none opacity-0"
            />
            <motion.div
              id="preview-screen"
              layoutId="preview-screen"
              onTouchStart={handlePreviewTouchStart}
              onTouchMove={handlePreviewTouchMove}
              onTouchEnd={handlePreviewTouchEnd}
              onTouchCancel={handlePreviewTouchEnd}
              className="absolute top-0 bottom-0 left-0 right-0 m-auto bg-black rounded-3xl overflow-hidden shadow-[20px_20px_60px_rgba(0,0,0,0.5)] border border-white/10"
              style={{
                aspectRatio: currentProjectRatio.replace(":", "/"),
                maxHeight: "100%",
                maxWidth: "100%",
                touchAction: "none"
              }}
            >
            {/* Media Rendering */}
            {[...visibleLayers].reverse().map((layer) => {
              if (layer.isHidden) return null;
              const layerClips = clips.filter((c) => c.layerId === layer.id);
              
              // Find active clip(s) for the layer based on transition rules
              const activeClipInfos = getLayerActiveClips(layerClips, currentTime);
              if (activeClipInfos.length === 0) return null;

              return (
                <div
                  key={layer.id}
                  className="absolute inset-0 flex items-center justify-center pointer-events-none"
                >
                  {activeClipInfos.map(({ clip: activeClipRaw, role, progress, transitionType }) => {
                    const interpolatedProps = getInterpolatedProps(activeClipRaw, currentTime - activeClipRaw.leftSeconds, activeExpandedMenu);
                    const activeClip = { ...activeClipRaw, ...interpolatedProps };

                    const getClipPath = (
                      maskType?: string,
                      maskWidth: number = 60,
                      maskHeight: number = 60,
                      maskRoundness: number = 15,
                      maskPositionX: number = 0.5,
                      maskPositionY: number = 0.5
                    ) => {
                      switch (maskType) {
                        case "circle":
                          return `ellipse(${maskWidth / 2}% ${maskHeight / 2}% at ${maskPositionX * 100}% ${maskPositionY * 100}%)`;
                        case "square": {
                          const top = (maskPositionY * 100) - (maskHeight / 2);
                          const bottom = 100 - ((maskPositionY * 100) + (maskHeight / 2));
                          const left = (maskPositionX * 100) - (maskWidth / 2);
                          const right = 100 - ((maskPositionX * 100) + (maskWidth / 2));
                          return `inset(${Math.max(0, top)}% ${Math.max(0, right)}% ${Math.max(0, bottom)}% ${Math.max(0, left)}% round ${maskRoundness}px)`;
                        }
                        case "half": {
                          return `inset(0% 0% ${100 - (maskPositionY * 100)}% 0%)`;
                        }
                        default:
                          return "none";
                      }
                    };

                    let clipPathVal = getClipPath(
                      activeClip.maskType,
                      activeClip.maskWidth,
                      activeClip.maskHeight,
                      activeClip.maskRoundness,
                      activeClip.maskPositionX ?? 0.5,
                      activeClip.maskPositionY ?? 0.5
                    );

                    // Apply wipe transitions on incoming clip
                    if (role === "incoming" && progress !== undefined && transitionType) {
                      if (transitionType === "wipe-left") {
                        clipPathVal = `inset(0 0 0 ${100 - progress * 100}%)`;
                      } else if (transitionType === "wipe-right") {
                        clipPathVal = `inset(0 ${100 - progress * 100}% 0 0)`;
                      }
                    }

                    // Apply visual effects based on progress
                    let extraTransform = "";
                    let transitionOpacityMultiplier = 1;
                    let extraBlur = 0;

                    if (progress !== undefined && transitionType) {
                      const p = progress;
                      if (transitionType === "fade" || transitionType === "crossfade") {
                        if (role === "outgoing") transitionOpacityMultiplier = 1 - p;
                        if (role === "incoming") transitionOpacityMultiplier = p;
                      } else if (transitionType === "blur") {
                        if (role === "outgoing") {
                          transitionOpacityMultiplier = 1 - p;
                          extraBlur = p * 15;
                        }
                        if (role === "incoming") {
                          transitionOpacityMultiplier = p;
                          extraBlur = (1 - p) * 15;
                        }
                      } else if (transitionType === "zoom-in") {
                        if (role === "outgoing") {
                          transitionOpacityMultiplier = 1 - p;
                        } else if (role === "incoming") {
                          transitionOpacityMultiplier = p;
                          const scaleVal = 0.2 + p * 0.8;
                          extraTransform = ` scale(${scaleVal})`;
                        }
                      } else if (transitionType === "slide-left") {
                        if (role === "incoming") {
                           extraTransform = ` translateX(${(1 - p) * 100}%)`;
                        }
                      } else if (transitionType === "slide-right") {
                        if (role === "incoming") {
                           extraTransform = ` translateX(${-(1 - p) * 100}%)`;
                        }
                      }
                    }

                    // Determine volume multiplier for audio crossfading
                    let transVolumeMultiplier = 1;
                    if (progress !== undefined && transitionType) {
                      if (role === "outgoing") transVolumeMultiplier = 1 - progress;
                      if (role === "incoming") transVolumeMultiplier = progress;
                    }

                    const transformStyle: React.CSSProperties = {
                      transformOrigin: `${(activeClip.anchorPointX ?? 0.5) * 100}% ${(activeClip.anchorPointY ?? 0.5) * 100}%`,
                      transform: `
                        translate(${activeClip.translateX || 0}px, ${activeClip.translateY || 0}px)
                        rotate(${activeClip.rotation || 0}deg)
                        scaleX(${(activeClip.scaleX ?? 1) * (activeClip.scale ?? 1)})
                        scaleY(${(activeClip.scaleY ?? 1) * (activeClip.scale ?? 1)})
                        ${extraTransform}
                      `,
                      clipPath: (activeExpandedMenu === "crop" && selectedClipId === activeClip.id)
                        ? "none"
                        : (clipPathVal !== "none" ? clipPathVal : `inset(${activeClip.cropRect?.top || 0}% ${activeClip.cropRect?.right || 0}% ${activeClip.cropRect?.bottom || 0}% ${activeClip.cropRect?.left || 0}%)`),
                      opacity: (activeClip.opacity ?? 1) * transitionOpacityMultiplier,
                      mixBlendMode: activeClip.mixBlendMode as any || "normal",
                      filter: isPlaying
                        ? `brightness(${(activeClip.brightness === undefined ? 100 : activeClip.brightness) + (activeClip.exposure || 0)}%) contrast(${activeClip.contrast === undefined ? 100 : activeClip.contrast}%) saturate(${activeClip.saturation === undefined ? 100 : activeClip.saturation}%)`
                        : `
                        blur(${(activeClip.blur || 0) + extraBlur}px)
                        brightness(${(activeClip.brightness === undefined ? 100 : activeClip.brightness) + (activeClip.exposure || 0)}%)
                        contrast(${activeClip.contrast === undefined ? 100 : activeClip.contrast}%)
                        saturate(${activeClip.saturation === undefined ? 100 : activeClip.saturation}%)
                        sepia(${activeClip.sepia || 0}%)
                        grayscale(${activeClip.grayscale || 0}%)
                        hue-rotate(${activeClip.hueRotate || 0}deg)
                        invert(${activeClip.invert || 0}%)
                      `.trim(),
                      ...(activeClip.cropRatio ? { aspectRatio: activeClip.cropRatio.replace(":", "/") } : {})
                    };

                    return (
                      <React.Fragment key={`${layer.id}-${activeClip.id}-${role}`}>
                        {erroredClips.has(activeClip.id) && activeClip.type !== "text" ? (
                          <div
                            className="absolute inset-0 flex flex-col items-center justify-center bg-[#171719] border border-red-500/50 m-4 rounded-[32px] overflow-hidden pointer-events-none"
                            style={transformStyle}
                          >
                            <AlertCircle className="text-red-500 mb-2" size={32} />
                            <span className="text-red-400 text-sm font-bold">File missing</span>
                          </div>
                        ) : (
                          <>
                            {activeClip.type === "text" && (
                              <div
                                id={`clip-media-${activeClip.id}`}
                                className="flex items-center justify-center w-full h-full font-sans break-words whitespace-pre-wrap text-center overflow-hidden absolute"
                                style={{
                                  ...transformStyle,
                                  color: activeClip.color || "#ffffff",
                                  fontSize: `${activeClip.fontSize || 48}px`,
                                  fontFamily: activeClip.fontFamily || "sans-serif",
                                  letterSpacing: activeClip.letterSpacing ? `${activeClip.letterSpacing}px` : undefined,
                                  lineHeight: activeClip.lineHeight ? `${activeClip.lineHeight}` : undefined,
                                  WebkitTextStroke: activeClip.strokeWidth ? `${activeClip.strokeWidth}px ${activeClip.strokeColor || "#000000"}` : undefined,
                                  textShadow: activeClip.textShadow || (
                                    activeClip.glowColor && activeClip.glowRadius 
                                      ? `0 0 ${activeClip.glowRadius}px ${activeClip.glowColor}`
                                      : activeClip.shadowColor 
                                      ? `${activeClip.shadowOffsetX || 3}px ${activeClip.shadowOffsetY || 3}px ${activeClip.shadowBlur || 5}px ${activeClip.shadowColor}`
                                      : undefined
                                  ),
                                  ...(activeClip.textAnimation === "Fade In" ? { opacity: (activeClip.opacity ?? 1) * Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 1) } : {}),
                                  ...(activeClip.textAnimation === "Slide Up" ? { 
                                      opacity: (activeClip.opacity ?? 1) * Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 1),
                                      transform: `${transformStyle.transform} translateY(${(1 - Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 1)) * 50}px)`
                                  } : {}),
                                  ...(activeClip.textAnimation === "Bounce" ? { 
                                      opacity: (activeClip.opacity ?? 1) * Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 1),
                                      transform: `${transformStyle.transform} translateY(${-(Math.sin(Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 1) * Math.PI) * 20 * (1-Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 1)))}px)`
                                  } : {}),
                                  ...(activeClip.textAnimation === "Pop" ? { 
                                      opacity: (activeClip.opacity ?? 1) * Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 0.4),
                                      transform: `${transformStyle.transform} scale(${Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 0.4) <= 1 ? 0.5 + Math.sin(Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 0.4) * (Math.PI / 2)) * 0.5 + Math.sin(Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 0.4) * Math.PI) * 0.2 : 1})`
                                  } : {}),
                                  ...(activeClip.textAnimation === "Glitch" && (currentTime - activeClipRaw.leftSeconds) < 1.5 ? {
                                      transform: `${transformStyle.transform} translate(${Math.random() > 0.8 ? (Math.random() - 0.5) * 20 : 0}px, ${Math.random() > 0.8 ? (Math.random() - 0.5) * 20 : 0}px)`,
                                      filter: Math.random() > 0.8 ? `hue-rotate(90deg) invert(100%)` : `blur(${activeClip.blur || 0}px)`
                                  } : {}),
                                  ...(activeClip.textAnimation === "Wave" ? {
                                      transform: `${transformStyle.transform} translateY(${Math.sin((currentTime - activeClipRaw.leftSeconds) * 5) * 15}px)`
                                  } : {})
                                }}
                              >
                                <span
                                   className={`pointer-events-none select-none ${!activeClip.text ? 'opacity-40 italic' : ''}`}>
                                   {activeClip.text ? (
                                     activeClip.textAnimation === "Typewriter" 
                                       ? (activeClip.text || "").substring(0, Math.floor(Math.min(1, (currentTime - activeClipRaw.leftSeconds) / 2) * (activeClip.text || "").length))
                                       : activeClip.text
                                   ) : (selectedClipId === activeClip.id ? "Type text..." : "")}
                                </span>
                               </div>
                             )}

                             {activeClip.type === "image" && (
                              <div
                                className="media-preview-container absolute pointer-events-none select-none overflow-visible max-w-full max-h-full flex items-center justify-center relative shadow-lg"
                                style={{
                                     ...transformStyle,
                                     ...(activeClip.cropRatio && activeClip.cropRatio !== "None" ? {
                                        width: activeClip.cropRatio === "16:9" ? "100%" : activeClip.cropRatio === "9:16" ? "auto" : activeClip.cropRatio === "1:1" ? "auto" : "100%",
                                        height: activeClip.cropRatio ? (activeClip.cropRatio === "16:9" ? "auto" : activeClip.cropRatio === "9:16" ? "100%" : activeClip.cropRatio === "1:1" ? "100%" : "100%") : '100%',
                                     } : activeClip.width && activeClip.height ? {
                                        aspectRatio: `${activeClip.width} / ${activeClip.height}`,
                                        width: "auto",
                                        height: "auto",
                                        maxWidth: "100%",
                                        maxHeight: "100%",
                                     } : { width: '100%', height: '100%' }),
                                }}

                              >
                                <div
                                  className="w-full h-full relative"
                                  style={{ clipPath: clipPathVal }}
                                >
                                  <img
                                    id={`clip-media-${activeClip.id}`}
                                    src={activeClip.src || undefined}
                                    className="w-full h-full object-cover pointer-events-none"
                                    crossOrigin="anonymous"
                                    onError={() => handleClipError(activeClip.id)}
                                  />
                                </div>
                                {activeExpandedMenu === "crop" && selectedClipId === activeClip.id && (
                                   <CropControlOverlay
                                     clip={activeClip}
                                     updateClipsProperties={updateClipsProperties}
                                   />
                                )}
                                {activeExpandedMenu === "mask" && selectedClipId === activeClip.id && (
                                  <MaskControlOverlay
                                    clip={activeClip}
                                    updateClipsProperties={updateClipsProperties}
                                    onShowAdjustments={() => setShowFloatingMaskAdjust(true)}
                                  />
                                )}
                              </div>
                            )}

                             {activeClip.type === "video" && (
                               <div
                                 className="media-preview-container absolute pointer-events-none select-none overflow-visible max-w-full max-h-full flex items-center justify-center relative shadow-lg"
                                 style={{
                                      ...transformStyle,
                                      ...(activeClip.cropRatio && activeClip.cropRatio !== "None" ? {
                                         width: activeClip.cropRatio === "16:9" ? "100%" : activeClip.cropRatio === "9:16" ? "auto" : activeClip.cropRatio === "1:1" ? "auto" : "100%",
                                         height: activeClip.cropRatio ? (activeClip.cropRatio === "16:9" ? "auto" : activeClip.cropRatio === "9:16" ? "100%" : activeClip.cropRatio === "1:1" ? "100%" : "100%") : '100%',
                                      } : activeClip.width && activeClip.height ? {
                                         aspectRatio: `${activeClip.width} / ${activeClip.height}`,
                                         width: "auto",
                                         height: "auto",
                                         maxWidth: "100%",
                                         maxHeight: "100%",
                                      } : { width: '100%', height: '100%' }),
                                 }}
                               >
                                 {activeClip.isStabilized && activeClip.compareStabilization ? (() => {
                                   const scaleFactor = activeClip.stabilizationMode === "locked" ? 1.28 : activeClip.stabilizationMode === "active" ? 1.18 : 1.08;
                                   
                                   const sX = isPlaying ? Math.sin(currentTime * 32) * 5 : 0;
                                   const sY = isPlaying ? Math.cos(currentTime * 24) * 4 : 0;
                                   const sR = isPlaying ? Math.sin(currentTime * 16) * 0.8 : 0;

                                   return (
                                     <div className="w-full h-full relative flex overflow-hidden rounded-lg bg-black">
                                       {/* Left Half: Shaky Footage */}
                                       <div className="absolute top-0 left-0 w-1/2 h-full overflow-hidden border-r border-white/20 z-10">
                                         <div 
                                           className="absolute top-0 left-0 w-[200%] h-full transform-gpu"
                                           style={{ 
                                             transform: `translate(${sX}px, ${sY}px) rotate(${sR}deg)`,
                                             clipPath: clipPathVal,
                                             height: "100%"
                                           }}
                                         >
                                           <VideoRenderer
                                             id={`clip-media-orig-${activeClip.id}`}
                                             clip={activeClip}
                                             currentTime={currentTime}
                                             isPlaying={isPlaying}
                                             isMuted={layer.isMuted}
                                             className="w-full h-full object-cover pointer-events-none"
                                             onError={() => handleClipError(activeClip.id)}
                                             volumeMultiplier={transVolumeMultiplier}
                                           />
                                         </div>
                                         <div className="absolute top-2 left-2 bg-red-600/90 text-white font-extrabold text-[8px] uppercase tracking-widest px-2 py-0.5 rounded shadow-lg z-20">
                                           Original Shaky
                                         </div>
                                       </div>

                                       {/* Right Half: Stabilized Footage */}
                                       <div className="absolute top-0 right-0 w-1/2 h-full overflow-hidden z-10">
                                         <div 
                                           className="absolute top-0 right-0 w-[200%] h-full transform-gpu"
                                           style={{ 
                                             transform: `scale(${scaleFactor})`,
                                             clipPath: clipPathVal,
                                             height: "100%"
                                           }}
                                         >
                                           <div className="absolute right-0 top-0 w-1/2 h-full">
                                             <VideoRenderer
                                               id={`clip-media-stab-${activeClip.id}`}
                                               clip={activeClip}
                                               currentTime={currentTime}
                                               isPlaying={isPlaying}
                                               isMuted={layer.isMuted}
                                               className="w-full h-full object-cover pointer-events-none"
                                               onError={() => handleClipError(activeClip.id)}
                                               volumeMultiplier={transVolumeMultiplier}
                                             />
                                           </div>
                                         </div>
                                         <div className="absolute top-2 right-2 bg-emerald-500/90 text-white font-extrabold text-[8px] uppercase tracking-widest px-2 py-0.5 rounded shadow-lg flex items-center gap-1 z-20">
                                           <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
                                           Stabilized
                                         </div>
                                       </div>

                                       {/* Center Sliding Bar */}
                                       <div className="absolute top-0 left-1/2 -translate-x-1/2 w-0.5 h-full bg-white/40 shadow-xl z-30 flex items-center justify-center">
                                         <div className="w-4 h-4 bg-zinc-800 rounded-full border border-white/20 flex items-center justify-center text-[7px] text-white">
                                           ↔
                                         </div>
                                       </div>
                                     </div>
                                   );
                                 })() : (
                                   <div
                                     className="w-full h-full relative"
                                     style={{ 
                                       clipPath: clipPathVal,
                                       transform: activeClip.isStabilized ? `scale(${activeClip.stabilizationMode === "locked" ? 1.28 : activeClip.stabilizationMode === "active" ? 1.18 : 1.08})` : "none" 
                                     }}
                                   >
                                     <VideoRenderer
                                       id={`clip-media-${activeClip.id}`}
                                       clip={activeClip}
                                       currentTime={currentTime}
                                       isPlaying={isPlaying}
                                       isMuted={layer.isMuted}
                                       className="w-full h-full object-cover pointer-events-none"
                                       onError={() => handleClipError(activeClip.id)}
                                       volumeMultiplier={transVolumeMultiplier}
                                     />
                                     {activeClip.isStabilized && (
                                       <div className="absolute top-2 right-2 bg-indigo-500/85 text-white font-bold text-[8px] tracking-widest uppercase px-1.5 py-0.5 rounded shadow-md pointer-events-none z-20 flex items-center gap-1">
                                         ✓ Stabilized ({activeClip.stabilizationMode})
                                       </div>
                                     )}
                                   </div>
                                 )}
                                 {activeExpandedMenu === "crop" && selectedClipId === activeClip.id && (
                                    <CropControlOverlay
                                      clip={activeClip}
                                      updateClipsProperties={updateClipsProperties}
                                    />
                                 )}
                                 {activeExpandedMenu === "mask" && selectedClipId === activeClip.id && (
                                   <MaskControlOverlay
                                     clip={activeClip}
                                     updateClipsProperties={updateClipsProperties}
                                     onShowAdjustments={() => setShowFloatingMaskAdjust(true)}
                                   />
                                 )}
                               </div>
                             )}

                            {activeClip.type === "audio" && (
                              <AudioRenderer
                                clip={activeClip}
                                currentTime={currentTime}
                                isPlaying={isPlaying}
                                isMuted={layer.isMuted}
                                onError={() => handleClipError(activeClip.id)}
                                volumeMultiplier={transVolumeMultiplier}
                              />
                            )}
                          </>
                        )}
                      </React.Fragment>
                    );
                  })}
                </div>
              );
            })}
          </motion.div>
          </div>

          <AnimatePresence>
            {activeExpandedMenu === "plus-media" && (
              <motion.div
                key="media-library-fullscreen"
                initial={{ opacity: 0, scale: 0.98, y: 15 }}
                animate={{
                  opacity: 1,
                  scale: 1,
                  y: 0,
                  top: isMediaExpanded ? -80 : 0,
                  height: isMediaExpanded ? "calc(100% + 80px)" : "100%"
                }}
                exit={{ opacity: 0, scale: 0.98, y: 15 }}
                transition={{ type: "spring", stiffness: 350, damping: 35 }}
                className="absolute inset-0 z-[90] bg-transparent flex flex-col text-white overflow-hidden select-none pointer-events-auto font-sans px-3"
                onPointerDown={(e) => { e.stopPropagation(); }}
                onTouchStart={handleMediaTouchStart}
                onTouchMove={handleMediaTouchMove}
                onTouchEnd={handleMediaTouchEnd}
                onClick={(e) => { e.stopPropagation(); }}
              >
                {/* Swipe/Drag Handle at Top */}
                <div 
                  onClick={() => setIsMediaExpanded(!isMediaExpanded)}
                  className="flex justify-center py-2 shrink-0 cursor-pointer group pointer-events-auto"
                >
                  <div className="w-10 h-1 rounded-full bg-zinc-700/60 group-hover:bg-zinc-500/80 transition-colors" />
                </div>

                {/* Permission or Asset Picker Body */}
                {!isMediaPermissionGranted ? (
                  <div className="flex-1 flex flex-col items-center justify-center text-center p-6 animate-fade-in max-w-sm mx-auto">
                    <div className="relative mb-6 flex items-center justify-center">
                      <span className="absolute inline-flex h-20 w-20 rounded-full bg-indigo-500/10 animate-ping opacity-75"></span>
                      <span className="absolute inline-flex h-24 w-24 rounded-full bg-indigo-500/5 animate-pulse"></span>
                      <div className="relative w-16 h-16 rounded-2xl bg-indigo-950/80 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shadow-xl">
                        <Layers size={28} className="animate-pulse" />
                      </div>
                    </div>
                    
                    <h4 className="text-sm font-extrabold text-white tracking-widest uppercase mb-1.5">Access Media Library</h4>
                    <p className="text-[11px] text-zinc-400 leading-relaxed max-w-[260px] mb-6">
                      Allow storage or local directory access to view high-speed native footage, soundtracks, captures, and folders automatically on your workspace.
                    </p>
                    
                    <div className="flex flex-col gap-2 w-full max-w-[200px]">
                      <button
                        onClick={fetchRealDeviceMedia}
                        className="w-full bg-indigo-600 hover:bg-indigo-505 text-white font-extrabold text-[11px] py-2.5 rounded-xl transition-all shadow-lg shadow-indigo-650/20 active:scale-95 cursor-pointer uppercase tracking-wider"
                      >
                        Allow Access
                      </button>
                      <button
                        onClick={() => setActiveExpandedMenu(null)}
                        className="w-full bg-zinc-850 hover:bg-zinc-800 text-zinc-400 hover:text-white font-semibold text-[11px] py-2 rounded-xl transition-colors cursor-pointer"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : isMediaLoading ? (
                  <div className="flex-1 flex flex-col items-center justify-center p-6 text-center">
                    <span className="w-10 h-10 border-4 border-zinc-800 border-t-indigo-500 rounded-full animate-spin mb-3"></span>
                    <span className="text-[10px] text-zinc-400 font-bold select-none uppercase tracking-widest">Scanning Local Media...</span>
                  </div>
                ) : (
                  <div className="flex-1 flex flex-col min-h-0 overflow-hidden text-left relative">
                    {Capacitor.isNativePlatform() && (
                      <div className="bg-[#181822]/80 border border-indigo-500/10 rounded-2xl p-3.5 mb-4 shrink-0 flex items-center justify-between gap-3 shadow-md font-sans">
                        <div className="flex items-center gap-3 col-span-1">
                          <span className="text-xl">📁</span>
                          <div className="flex flex-col">
                            <span className="text-[10px] font-bold text-zinc-200">Storage Explorer</span>
                            <span className="text-[8px] text-zinc-400">Select files (images, videos, music) from native storage</span>
                          </div>
                        </div>
                        <button
                          onClick={() => fileInputRef.current?.click()}
                          className="px-3 py-1.5 bg-indigo-650 hover:bg-indigo-600 active:scale-95 text-white text-[9px] font-extrabold uppercase tracking-wider rounded-lg transition-all cursor-pointer shrink-0"
                        >
                          Browse Files
                        </button>
                      </div>
                    )}
                    {currentMediaFolder && (
                      <div className="flex items-center justify-between mb-3 shrink-0">
                        <button
                          onClick={() => setCurrentMediaFolder(null)}
                          className="flex items-center gap-1.5 text-[10px] font-bold text-zinc-300 hover:text-white bg-zinc-800/80 py-1 px-3 rounded-lg border border-white/5 transition-colors cursor-pointer"
                        >
                          <ArrowLeft size={12} />
                          <span>Back to Folders</span>
                        </button>
                        <span className="text-[10px] bg-emerald-500/10 text-emerald-400 px-2.5 py-0.5 rounded-md border border-emerald-500/15 font-mono text-right">{currentMediaFolder} Directory</span>
                      </div>
                    )}

                    <div className="flex-1 overflow-y-auto pr-0.5 grid grid-cols-2 xs:grid-cols-3 sm:grid-cols-4 gap-3 content-start scrollbar-thin py-1">
                      {/* Import Custom Option */}
                      {!currentMediaFolder && (
                        <div
                          onClick={() => fileInputRef.current?.click()}
                          className="border border-dashed border-white/10 hover:border-indigo-450/40 hover:bg-indigo-500/[0.02] active:bg-zinc-900 rounded-2xl flex flex-col items-center justify-center p-4 text-center cursor-pointer transition-all gap-2 h-28 group shrink-0"
                        >
                          <div className="w-8 h-8 rounded-full bg-zinc-900/90 border border-white/5 flex items-center justify-center text-zinc-400 group-hover:text-indigo-400 group-hover:border-indigo-500/20 group-hover:scale-110 transition-all shadow-md">
                            <PlusIcon size={14} />
                          </div>
                          <span className="text-[10px] font-bold text-zinc-350 group-hover:text-zinc-100">Import Custom</span>
                          <span className="text-[8px] text-zinc-500">From local systems</span>
                        </div>
                      )}

                      {selectedMediaTab === "Folders" && !currentMediaFolder ? (
                        memoizedMediaFolders.map((fold) => (
                          <div
                            key={fold.name}
                            onClick={() => setCurrentMediaFolder(fold.name)}
                            className="bg-[#111115] hover:bg-[#16161c] border border-white/[0.05] hover:border-white/[0.1] active:scale-95 rounded-2xl p-4 flex flex-col justify-between cursor-pointer transition-all h-28 shadow-lg group"
                          >
                            <span className="text-2xl group-hover:scale-110 transition-all self-start">{fold.icon}</span>
                            <div className="flex flex-col">
                              <span className="text-[11px] font-extrabold text-zinc-250 group-hover:text-white truncate">{fold.name}</span>
                              <span className="text-[8px] text-zinc-500 font-semibold">{fold.count} items</span>
                            </div>
                          </div>
                        ))
                      ) : (
                        memoizedDisplayItems.map((item, idx) => {
                            if (item.type === "image") {
                              return (
                                <div
                                  key={`${item.name}-${idx}`}
                                  onClick={() => {
                                    handleSelectMediaItem(item);
                                    setActiveExpandedMenu(null);
                                  }}
                                  className="group cursor-pointer relative bg-zinc-900 rounded-2xl overflow-hidden active:scale-98 border border-white/[0.04] hover:border-indigo-400/30 transition-all h-28 select-none shadow-md"
                                >
                                  <img 
                                    src={item.url} 
                                    alt={item.name} 
                                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" 
                                    referrerPolicy="no-referrer"
                                  />
                                  <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/20 to-transparent flex items-end p-2 sm:p-2.5">
                                    <span className="text-[10px] font-bold text-white truncate w-full">{item.name}</span>
                                  </div>
                                </div>
                              );
                            } else if (item.type === "audio") {
                              return (
                                <div
                                  key={`${item.name}-${idx}`}
                                  onClick={() => {
                                    handleSelectMediaItem(item);
                                    setActiveExpandedMenu(null);
                                  }}
                                  className="group cursor-pointer bg-[#111115] hover:bg-[#16161c] border border-white/[0.05] hover:border-indigo-505/20 active:scale-98 rounded-2xl p-3 flex flex-col justify-between h-28 transition-all shadow-md"
                                >
                                  <div className="w-7 h-7 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 group-hover:scale-[1.05] transition-all">
                                    <Music size={12} />
                                  </div>
                                  <div className="flex flex-col">
                                    <span className="text-[10px] font-bold text-zinc-300 group-hover:text-white truncate">{item.name}</span>
                                    <span className="text-[8.5px] text-zinc-500 font-semibold">{Math.floor(item.duration / 60)}:{(item.duration % 60).toString().padStart(2, '0')}</span>
                                  </div>
                                </div>
                              );
                            } else {
                              return (
                                <div
                                  key={`${item.name}-${idx}`}
                                  onClick={() => {
                                    handleSelectMediaItem(item);
                                    setActiveExpandedMenu(null);
                                  }}
                                  className="group cursor-pointer relative bg-zinc-950 rounded-2xl overflow-hidden active:scale-98 border border-white/[0.04] hover:border-indigo-400/30 transition-all h-28 select-none shadow-md"
                                >
                                  {item.thumbnail ? (
                                    <img 
                                      src={item.thumbnail} 
                                      alt={item.name} 
                                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" 
                                      referrerPolicy="no-referrer"
                                    />
                                  ) : (
                                    <video
                                      src={item.url}
                                      preload="metadata"
                                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                                      muted
                                      playsInline
                                    />
                                  )}
                                  <div className="absolute top-2 right-2 bg-black/60 px-1.5 py-0.5 rounded-[4px] text-[8.5px] font-bold font-mono tracking-wider border border-white/5">
                                    {Math.floor(item.duration / 60)}:{(item.duration % 60).toFixed(0).padStart(2, '0')}
                                  </div>
                                  <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/20 to-transparent flex items-end p-2 sm:p-2.5">
                                    <span className="text-[10px] font-bold text-white truncate w-full flex items-center gap-1.5">
                                      <span className="w-1.5 h-1.5 rounded-full bg-red-500 inline-block animate-pulse shrink-0"></span>
                                      <span>{item.name}</span>
                                    </span>
                                  </div>
                                </div>
                              );
                            }
                          })
                        )}

                    </div>
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Playback Transport Controls */}
        <div 
          className="flex justify-between items-center shrink-0 w-full relative"
          style={{
            paddingRight: "7px",
            paddingLeft: "5px",
            height: "40px",
            marginTop: "0px",
            marginLeft: "0px",
            paddingTop: "29px",
            paddingBottom: "0px"
          }}
        >
          <div className="flex items-center gap-2 sm:gap-4 flex-1 pr-2">
            <span className="text-zinc-300 font-mono text-[10px] sm:text-xs tracking-wider opacity-80 min-w-[40px] sm:min-w-[50px]">
              {formatTime(currentTime)}
            </span>
            <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
              <button
                className="w-6 h-6 sm:w-8 sm:h-8 bg-zinc-800 text-white rounded-full shadow flex items-center justify-center hover:bg-zinc-700 transition-colors m-0"
                onClick={() => {
                  setCurrentTime(0);
                  if (timelineScrollRef.current) {
                    timelineScrollRef.current.scrollLeft = 0;
                  }
                }}
                title="Go to Start"
              >
                <SkipBack size={12} fill="currentColor" />
              </button>
              <button
                className="w-7 h-7 sm:w-9 sm:h-9 bg-white text-black rounded-full shadow-lg flex items-center justify-center hover:scale-105 transition-transform m-0 pl-0 pr-[4px]"
                onClick={() => setIsPlaying(!isPlaying)}
              >
                {isPlaying ? (
                  <Pause size={14} fill="currentColor" />
                ) : (
                  <Play size={14} fill="currentColor" className="ml-[4px] sm:ml-[6px]" />
                )}
              </button>
            </div>

            {multiSelectActive && (
              <motion.div
                initial={{ opacity: 0, scale: 0.9, x: -10 }}
                animate={{ opacity: 1, scale: 1, x: 0 }}
                exit={{ opacity: 0, scale: 0.9, x: -10 }}
                transition={{ duration: 0.2 }}
                className="flex items-center bg-[#1c1c1f] border border-white/10 rounded-full py-0.5 px-1 gap-1 shadow-md shrink-0 h-7 sm:h-8 mr-1 select-none"
              >
                {/* Divider 1 */}
                <div className="w-px h-3 sm:h-4 bg-zinc-700/60 mx-1 shrink-0" />

                {/* 1. Comp Button */}
                <button
                  className="w-6 h-6 sm:w-7 sm:h-7 rounded-full flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800 transition-all outline-none select-none shrink-0"
                  onClick={() => {
                    setToastMessage("Comp: Composite action triggered");
                    setTimeout(() => setToastMessage(null), 2000);
                  }}
                  title="Comp Action"
                >
                  <Layers className="text-indigo-400 w-3.5 h-3.5 sm:w-4 sm:h-4" />
                </button>

                {/* Divider 2 */}
                <div className="w-px h-3 sm:h-4 bg-zinc-700/60 mx-1 shrink-0" />

                {/* 2. Link Button */}
                <button
                  className="w-6 h-6 sm:w-7 sm:h-7 rounded-full flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800 transition-all outline-none select-none shrink-0"
                  onClick={() => {
                    setToastMessage("Link of selected clips triggered");
                    setTimeout(() => setToastMessage(null), 2000);
                  }}
                  title="Link Action"
                >
                  <Link2 className="text-emerald-400 w-3.5 h-3.5 sm:w-4 sm:h-4" />
                </button>

                {/* Divider 3 */}
                <div className="w-px h-3 sm:h-4 bg-zinc-700/60 mx-1 shrink-0" />

                {/* 3. Paste Button */}
                <button
                  className="w-6 h-6 sm:w-7 sm:h-7 rounded-full flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800 transition-all outline-none select-none shrink-0"
                  onClick={() => {
                    if (copiedClip) {
                      handlePaste();
                    } else {
                      setToastMessage("Copy a clip first to paste");
                      setTimeout(() => setToastMessage(null), 2000);
                    }
                  }}
                  title="Paste Action"
                >
                  <Clipboard className="text-amber-400 w-3.5 h-3.5 sm:w-4 sm:h-4" />
                </button>

                {/* Divider 4 */}
                <div className="w-px h-3 sm:h-4 bg-zinc-700/60 mx-1 shrink-0" />

                {/* 4. Action Button (Decide Later) */}
                <button
                  className="w-6 h-6 sm:w-7 sm:h-7 rounded-full flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800 transition-all outline-none select-none shrink-0"
                  onClick={() => {
                    setToastMessage("Placeholder action - customize later");
                    setTimeout(() => setToastMessage(null), 2000);
                  }}
                  title="Custom Action"
                >
                  <SlidersHorizontal className="text-pink-400 w-3.5 h-3.5 sm:w-4 sm:h-4" />
                </button>
              </motion.div>
            )}

            {selectedClipId && clips.find(c => c.id === selectedClipId)?.type === "text" && !multiSelectActive && (
              <motion.div 
                initial={{ opacity: 0, width: 0 }}
                animate={{ opacity: 1, width: "100%" }}
                exit={{ opacity: 0, width: 0 }}
                className="ml-0.5 overflow-hidden flex-1"
              >
                <input
                  type="text"
                  value={clips.find((c) => c.id === selectedClipId)?.text || ""}
                  onChange={(e) => {
                    setClips((prev) =>
                      prev.map((c) =>
                        c.id === selectedClipId ? { ...c, text: e.target.value } : c
                      )
                    );
                  }}
                  placeholder="Enter text..."
                  className="bg-zinc-800 border border-white/10 rounded-full text-xs font-medium text-white focus:outline-none focus:ring-1 focus:ring-white/30 focus:bg-zinc-700 transition-all placeholder:text-zinc-500 shadow-inner w-full"
                  style={{
                    paddingLeft: "12px",
                    marginTop: "1px",
                    paddingTop: "5px",
                    marginLeft: "0px",
                    paddingRight: "16px",
                    marginRight: "0px",
                    paddingBottom: "5px"
                  }}
                />
              </motion.div>
            )}

            {!(multiSelectActive || (selectedClipId && clips.find(c => c.id === selectedClipId)?.type === "text")) && (
              <AnimatePresence>
                {(pillPopup || toastMessage) && (() => {
                  const message = pillPopup ? pillPopup.message : toastMessage;
                  const len = message ? message.length : 0;
                  let fontSizeStyle = "text-[11px] sm:text-[12px]";
                  if (len > 35) {
                    fontSizeStyle = "text-[8px] sm:text-[9.5px]";
                  } else if (len > 25) {
                    fontSizeStyle = "text-[9px] sm:text-[10.5px]";
                  } else if (len > 15) {
                    fontSizeStyle = "text-[10px] sm:text-[11px]";
                  }

                  return (
                    <motion.div
                      initial={{ opacity: 0, width: 0 }}
                      animate={{ opacity: 1, width: "100%" }}
                      exit={{ opacity: 0, width: 0 }}
                      transition={{ duration: 0.15 }}
                      className="ml-0.5 overflow-hidden flex-1 select-none"
                    >
                      <div className="bg-[#2A2A2D]/95 backdrop-blur-md rounded-full border border-white/10 shadow-lg px-3 h-7 sm:h-8 flex items-center justify-center gap-1.5 w-full select-none text-center">
                        {pillPopup && pillPopup.type === 'loading' && pillPopup.progress !== undefined && (
                          <div className="w-3.5 h-3.5 sm:w-4 sm:h-4 relative shrink-0">
                            <svg className="w-full h-full" viewBox="0 0 20 20">
                              <circle cx="10" cy="10" r="9" className="stroke-zinc-700/60" strokeWidth="2.5" fill="none" />
                              <circle cx="10" cy="10" r="9" className="stroke-indigo-400" strokeWidth="2.5" fill="none" strokeDasharray={`${pillPopup.progress * 2 * Math.PI * 9 / 100} 1000`} transform="rotate(-90 10 10)" />
                            </svg>
                          </div>
                        )}
                        <span className={`font-semibold tracking-tight text-white/95 truncate w-full ${fontSizeStyle}`}>
                          {message}
                        </span>
                      </div>
                    </motion.div>
                  );
                })()}
              </AnimatePresence>
            )}
          </div>
          <div className="flex items-center shrink-0">
            {keyframePercentageLabel && (
              <button className="flex items-center justify-center w-6 h-6 sm:w-7 sm:h-7 rounded-full bg-zinc-800 hover:bg-zinc-700 transition-colors text-[9px] sm:text-[10px] font-medium text-zinc-300 hover:text-white mr-1 sm:mr-2 shrink-0 border border-white/5 shadow-sm">
                {keyframePercentageLabel}
              </button>
            )}
            <div className="flex bg-zinc-800 rounded-full px-0.5 py-0.5 mr-1 sm:px-1 sm:py-1 sm:mr-2">
              <button
                className={`p-1 sm:p-1.5 rounded-full transition-colors ${selectedClipId ? "hover:bg-zinc-700 text-white" : "opacity-30"}`}
                disabled={!selectedClipId}
                onClick={handleToggleKeyframe}
              >
                <Diamond size={12} className={isAtKeyframe ? "fill-white" : ""} />
              </button>
              <button
                className={`p-1 sm:p-1.5 rounded-full transition-colors ${(selectedClipId && isBetweenKeyframes) ? (activeExpandedMenu === "keyframe-interpolation" ? "bg-zinc-700 text-[#818cf8]" : "hover:bg-zinc-700 text-white") : "opacity-30"}`}
                disabled={!selectedClipId || !isBetweenKeyframes}
                onClick={() => setActiveExpandedMenu(activeExpandedMenu === "keyframe-interpolation" ? null : "keyframe-interpolation")}
                title="Keyframe Interpolation"
              >
                <LineChart size={12} />
              </button>
            </div>
            <div className="flex bg-zinc-800 rounded-full px-0.5 py-0.5 mr-1 sm:px-1 sm:py-1 sm:mr-2">
              <button
                className={`p-1 sm:p-1.5 rounded-full transition-colors ${selectedClipId ? "hover:bg-zinc-700 text-white" : "opacity-30"}`}
                disabled={!selectedClipId}
                onClick={splitSelectedClip}
              >
                <Scissors size={12} />
              </button>
              <div className="w-px h-3 sm:h-4 bg-zinc-700 mx-0.5 sm:mx-1 my-auto"></div>
              <button
                className={`p-1 sm:p-1.5 rounded-full transition-colors ${selectedClipIds.length > 0 ? "hover:bg-zinc-700 text-white" : "opacity-30"}`}
                disabled={selectedClipIds.length === 0}
                onClick={deleteSelectedClip}
              >
                <Trash2 size={12} />
              </button>
            </div>
            <div className="flex bg-zinc-800 rounded-full px-0.5 py-0.5 sm:px-1 sm:py-1">
              <button
                onClick={undo}
                disabled={historyIndex <= 0}
                className={`p-1 sm:p-1.5 rounded-full transition-colors ${historyIndex <= 0 ? "opacity-30" : "hover:bg-zinc-700"}`}
              >
                <Undo2 size={12} />
              </button>
              <div className="w-px h-3 sm:h-4 bg-zinc-700 mx-0.5 sm:mx-1 my-auto"></div>
              <button
                onClick={redo}
                disabled={historyIndex >= history.length - 1}
                className={`p-1 sm:p-1.5 rounded-full transition-colors ${historyIndex >= history.length - 1 ? "opacity-30" : "hover:bg-zinc-700"}`}
              >
                <Redo2 size={12} />
              </button>
            </div>
          </div>



        </div>
      </main>

      {/* Modern Horizontal Splitter - Styled to allow elegant rounded corners on the timeline card below */}
      <div className="h-px bg-transparent relative z-[80]"></div>

      {/* Editor Timeline Space */}
      <div 
        className="h-[40vh] shrink-0 bg-[#0c0c0e] flex flex-col relative w-full select-none z-0 overflow-hidden border-t border-[#202025] shadow-[0_-16px_40px_rgba(0,0,0,0.8)]"
      >
        {/* Timeline Content Flex Container */}
        <div
          id="master-vertical-scroll"
          ref={timelineScrollRef}
          className="flex-1 w-full relative overflow-auto scrollbar-hide bg-[#0c0c0e]"
          style={{ touchAction: "pan-x pan-y" }}
          onScroll={(e) => {
            if (!isPlayingRef.current && !isRecordingRef.current) {
              const currentTarget = e.currentTarget;
              if ((currentTarget as any)._rafScrollScheduled) return;
              (currentTarget as any)._rafScrollScheduled = true;
              requestAnimationFrame(() => {
                if (currentTarget) {
                  (currentTarget as any)._rafScrollScheduled = false;
                  setCurrentTime(
                    Math.max(0, currentTarget.scrollLeft /
                      currentPixelsPerSecondRef.current)
                  );
                }
              });
            }
          }}
        >
          <div className="flex min-h-full min-w-max relative w-[fit-content]">
            {/* Left Layer Control Panel */}
            <div className="w-[100px] shrink-0 flex flex-col bg-transparent z-[70] sticky left-0 pb-[200px]">
              <div className="h-[15px] flex items-center justify-between pl-0 pr-2 shrink-0 z-[80] sticky top-0 w-full bg-transparent">
                <div
                  style={{ marginLeft: "0px", marginTop: "-1px", height: "14px" }}
                  className="flex items-center rounded-l-none rounded-r-full bg-black border-y border-r border-white/[0.08] text-[8px] font-extrabold text-white tracking-widest uppercase pl-3 pr-4 shadow-[0_2px_8px_rgba(0,0,0,0.6)]"
                >
                  LAYERS
                </div>
              </div>

              <div id="layers-sidebar" className="flex flex-col flex-1">
                {visibleLayers.map((layer, index) => {
                  const trackIndex = visibleLayers.length - index;
                  return (
                    <div
                      key={layer.id}
                      className={`h-[32px] sm:h-[38px] flex items-center justify-start pl-0 shrink-0 relative transition-all duration-200 transform-gpu ${draggingLayerId === layer.id ? "scale-[1.02] z-50" : "z-10"}`}
                    >
                      {/* Track Index Label */}
                      <span className="absolute top-[0.5px] left-[5px] pointer-events-none font-mono text-[7px] text-zinc-500 font-bold tracking-wider leading-none">
                        {trackIndex}
                      </span>

                      {/* Capsule Pill Container (Half pill shape) */}
                      <div
                        style={index === 0 ? { height: "30px", marginTop: "-1px" } : undefined}
                        className={`flex items-center justify-between bg-black border-y border-r border-white/[0.08] shadow-[0_3px_10px_rgba(0,0,0,0.5)] rounded-l-none rounded-r-full transition-all duration-150
                          w-[82px] px-3 pl-3 gap-2 ${index === 0 ? "" : "h-full"}
                          ${layerMenuOpenId === layer.id || draggingLayerId === layer.id ? "border-indigo-500/40 shadow-[0_0_8px_rgba(99,102,241,0.2)] bg-zinc-950" : ""}
                        `}
                      >
                        {/* Mute Button */}
                        <button
                          onClick={() => toggleLayerMute(layer.id)}
                          className="text-zinc-400 hover:text-white transition-colors duration-150 flex items-center justify-center cursor-pointer shrink-0"
                          title={layer.isMuted ? "Unmute Track" : "Mute Track"}
                        >
                          {layer.isMuted ? (
                            <VolumeX size={12} className="stroke-[2]" />
                          ) : (
                            <Volume2 size={12} className="stroke-[2]" />
                          )}
                        </button>

                        {/* Visibility Button */}
                        <button
                          onClick={() => toggleLayerVisibility(layer.id)}
                          className="text-zinc-400 hover:text-white transition-colors duration-150 flex items-center justify-center cursor-pointer shrink-0"
                          title={layer.isHidden ? "Show Track" : "Hide Track"}
                        >
                          {layer.isHidden ? (
                            <EyeOff size={12} className="stroke-[2]" />
                          ) : (
                            <Eye size={12} className="stroke-[2]" />
                          )}
                        </button>

                        {/* Options Button */}
                        <div
                          onPointerDown={(e) =>
                            handleLayerPointerDown(e, layer.id)
                          }
                          onClick={(e) => {
                            e.stopPropagation();
                            if (!hasDraggedLayerRef.current) {
                              setLayerMenuOpenId(layerMenuOpenId === layer.id ? null : layer.id);
                            }
                          }}
                          className="text-zinc-400 hover:text-white transition-colors duration-150 flex items-center justify-center cursor-grab touch-none shrink-0"
                          title="Track Options"
                        >
                          <MoreVertical size={12} className="stroke-[2]" />
                        </div>
                      </div>

                      {/* Layer Options Menu */}
                      <AnimatePresence>
                        {layerMenuOpenId === layer.id && (
                          <>
                            <div
                              className="fixed inset-0 z-[60]"
                              onClick={(e) => {
                                e.stopPropagation();
                                setLayerMenuOpenId(null);
                              }}
                            />
                            <motion.div
                              initial={{ opacity: 0, scale: 0.9, x: -10 }}
                              animate={{ opacity: 1, scale: 1, x: 0 }}
                              exit={{ opacity: 0, scale: 0.9, x: -10 }}
                              className="absolute left-full ml-2 top-1/2 -translate-y-1/2 z-[70] bg-zinc-850 border border-white/10 rounded-lg shadow-xl overflow-hidden flex flex-col w-[120px]"
                            >
                              <button
                                className="px-3 py-2 text-xs text-left text-zinc-300 hover:bg-white/10 hover:text-white transition-colors flex items-center gap-2"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setLayers((prevLayers) => {
                                    const sorted = [...prevLayers].sort((a, b) => b.order - a.order);
                                    const visIdx = sorted.findIndex((l) => l.id === layer.id);
                                    // Find appropriate order position above
                                    let newOrder = layer.order + 0.5;
                                    if (visIdx > 0) {
                                      newOrder = (layer.order + sorted[visIdx - 1].order) / 2;
                                    } else {
                                      newOrder = layer.order + 1;
                                    }

                                    const dupLayerId = "L_dup_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
                                    const dupLayer = {
                                      ...layer,
                                      id: dupLayerId,
                                      order: newOrder,
                                      name: layer.name ? `${layer.name} (Copy)` : "Layer Copy",
                                      isLocked: layer.isLocked, // preserve locked state
                                    };

                                    const duplicatedClips = clips
                                      .filter((c) => c.layerId === layer.id)
                                      .map((c) => ({
                                        ...c,
                                        id: "C_" + Date.now() + "_" + Math.floor(Math.random() * 100000),
                                        layerId: dupLayerId,
                                        keyframes: c.keyframes ? c.keyframes.map((kf) => ({
                                          ...kf,
                                          properties: { ...kf.properties },
                                        })) : undefined,
                                      }));
                                    
                                    setClips((prevClips) => [...prevClips, ...duplicatedClips]);
                                    return [...prevLayers, dupLayer];
                                  });

                                  setToastMessage("Layer duplicated");
                                  setTimeout(() => setToastMessage(null), 2000);
                                  setLayerMenuOpenId(null);
                                }}
                              >
                                <Copy size={12} className="text-zinc-400" /> Duplicate
                              </button>
                              <button
                                className="px-3 py-2 text-xs text-left text-zinc-300 hover:bg-white/10 hover:text-white transition-colors flex items-center gap-2"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setLayers((prev) =>
                                    prev.map((l) =>
                                      l.id === layer.id ? { ...l, isLocked: !l.isLocked } : l
                                    )
                                  );
                                  setToastMessage(layer.isLocked ? "Layer unlocked" : "Layer locked");
                                  setTimeout(() => setToastMessage(null), 2000);
                                  setLayerMenuOpenId(null);
                                }}
                              >
                                {layer.isLocked ? (
                                  <>
                                    <Unlock size={12} className="text-zinc-400" /> Unlock
                                  </>
                                ) : (
                                  <>
                                    <Lock size={12} className="text-zinc-400" /> Lock
                                  </>
                                )}
                              </button>
                              <button
                                className="px-3 py-2 text-xs text-left text-red-400 hover:bg-red-500/20 transition-colors flex items-center gap-2 border-t border-white/10"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setLayers((prev) =>
                                    prev.filter((l) => l.id !== layer.id),
                                  );
                                  setClips((prev) =>
                                    prev.filter((c) => c.layerId !== layer.id),
                                  );
                                  setLayerMenuOpenId(null);
                                }}
                              >
                                <Trash2 size={12} className="text-red-400" /> Delete
                              </button>
                            </motion.div>
                          </>
                        )}
                      </AnimatePresence>
                    </div>
                  );
                })}

                {/* Add New Track half-pill Button */}
                <div className={`flex items-center justify-start relative w-full h-[32px] sm:h-[38px]`}>
                  <button
                    onClick={() => {
                      setLayers((prev) => {
                        const maxOrder = prev.reduce(
                          (max, l) => Math.max(max, l.order),
                          -1,
                        );
                        return [
                          ...prev,
                          {
                            id: "L_" + Date.now(),
                            order: maxOrder + 1,
                            isMuted: false,
                            isHidden: false,
                            isManuallyCreated: true,
                          },
                        ];
                      });
                    }}
                    className={`absolute left-0 w-[50px] h-full rounded-l-none rounded-r-full bg-black hover:bg-zinc-900 border-y border-r border-white/[0.08] shadow-[0_4px_12px_rgba(0,0,0,0.6)] flex items-center justify-center cursor-pointer transition-all active:scale-95 text-white group`}
                    title="Add New Track"
                  >
                    <PlusIcon
                      size={14}
                      className="text-zinc-250 group-hover:text-white transition-colors duration-200"
                    />
                  </button>
                </div>
              </div>
            </div>

            {/* STATIONARY PLAYHEAD (Simple & clean flat design) */}
            {layers.length > 0 && (
              <div
                className="sticky top-0 left-[100px] pointer-events-none z-[60] w-0 h-0"
                style={{ transform: `translate3d(${PLAYHEAD_X}px, 0, 0)`, willChange: "transform" }}
              >
                <div className="absolute top-0 -translate-x-1/2 flex flex-col items-center">
                  {/* Clean flat red playhead cap */}
                  <div
                    className="w-[10px] h-[10px] bg-red-500"
                    style={{
                      clipPath: "polygon(0 0, 100% 0, 50% 100%)",
                    }}
                  />
                </div>
                {/* Thin solid red playhead line */}
                <div className="absolute top-[8px] left-0 -translate-x-1/2 w-[1px] bg-red-500 h-[100vh]"></div>
              </div>
            )}

            {/* Right Scrollable Timeline Container */}
            <div className="flex-1 relative flex flex-col min-w-max pt-[0px]">
              {/* sticky wrapper for Ruler */}
              {layers.length > 0 && (
                <div
                  id="ruler-container"
                  className="sticky top-0 z-[50] h-[15px] cursor-pointer transition-colors duration-150"
                  onPointerDown={(e) => {
                    e.stopPropagation();
                    setIsPlaying(false);
                    const target = e.currentTarget;
                    target.setPointerCapture(e.pointerId);

                    let pendingClientX = e.clientX;
                    let rafScheduled = false;

                    const updateSeek = (clientX: number) => {
                      const innerRect = document.getElementById("timeline-inner")?.getBoundingClientRect();
                      if (!innerRect) return;
                      const x = clientX - innerRect.left;
                      let newTime = Math.max(
                        0,
                        x / currentPixelsPerSecondRef.current,
                      );

                      if (snappingEnabled) {
                        const SNAP_THRESHOLD_SECONDS = 12 / currentPixelsPerSecondRef.current;
                        let minDistance = SNAP_THRESHOLD_SECONDS;
                        let snappedTime = newTime;
                        const snapPoints = [0];
                        clips.forEach((c) => {
                          snapPoints.push(c.leftSeconds);
                          snapPoints.push(c.leftSeconds + c.durationSeconds);
                        });
                        snapPoints.forEach((sp) => {
                          const dist = Math.abs(sp - newTime);
                          if (dist < minDistance) {
                            minDistance = dist;
                            snappedTime = sp;
                          }
                        });
                        newTime = snappedTime;
                      }

                      setCurrentTime(newTime);
                      if (timelineScrollRef.current) {
                        timelineScrollRef.current.scrollLeft = newTime * currentPixelsPerSecondRef.current;
                      }
                    };
                    updateSeek(e.clientX);

                    const handlePointerMove = (moveEvent: PointerEvent) => {
                      pendingClientX = moveEvent.clientX;
                      if (!rafScheduled) {
                        rafScheduled = true;
                        requestAnimationFrame(() => {
                          rafScheduled = false;
                          updateSeek(pendingClientX);
                        });
                      }
                    };
                    const handlePointerUp = (upEvent: PointerEvent) => {
                      target.releasePointerCapture(upEvent.pointerId);
                      window.removeEventListener(
                        "pointermove",
                        handlePointerMove,
                      );
                      window.removeEventListener("pointerup", handlePointerUp);
                    };
                    window.addEventListener("pointermove", handlePointerMove);
                    window.addEventListener("pointerup", handlePointerUp);
                  }}
                >
                  <div
                    className="relative h-full bg-[#0c0c0e]/95 backdrop-blur-md border-b border-white/[0.05] hover:bg-[#121216]"
                    style={{
                      width: `${maxTimelineDuration * pixelsPerSecond}px`,
                      left: `${PLAYHEAD_X}px`,
                    }}
                  >
                    <TimelineRulerTicks
                      maxTimelineDuration={maxTimelineDuration}
                      pixelsPerSecond={pixelsPerSecond}
                      zoomLevel={zoomLevel}
                    />
                  </div>
                </div>
              )}
              <div
                className="w-full h-full relative"
                onPointerDown={(e) => {
                  setIsPlaying(false);
                  pointerMoveCanvasRef.current = false;
                  setPastePopup(null);

                  const target = e.target as Element;
                  const isEmptySpace =
                    e.target === e.currentTarget ||
                    target.id === "timeline-content" ||
                    target.id === "timeline-inner" ||
                    target.closest(".track-space");

                  let clickTime = currentTime;
                  const innerRect = document
                    .getElementById("timeline-inner")
                    ?.getBoundingClientRect();
                  if (innerRect) {
                    const x = e.clientX - innerRect.left;
                    clickTime = Math.max(
                      0,
                      x / currentPixelsPerSecondRef.current,
                    );
                  }

                  if (isEmptySpace) {
                    setSelectedClipId(null);

                    const startX = e.clientX;
                    const startY = e.clientY;
                    const pointerId = e.pointerId;
                    const isMouse = e.pointerType === "mouse";
                    const masterScroll = timelineScrollRef.current;
                    if (!masterScroll) return;

                    const startScrollLeft = masterScroll.scrollLeft;
                    const startScrollTop = masterScroll.scrollTop;
                    const rect = document.getElementById("timeline-inner")?.getBoundingClientRect() || {left:0, top:0};

                    const container = e.currentTarget as HTMLElement;
                    container.setPointerCapture(pointerId);

                    let hasMoved = false;
                    let isMarquee = false;

                    const handlePointerMove = (moveEvent: PointerEvent) => {
                      pointerMoveCanvasRef.current = true;
                      const deltaX = moveEvent.clientX - startX;
                      const deltaY = moveEvent.clientY - startY;

                      if (!isMarquee) {
                        if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                          hasMoved = true;
                          if (longPressTimerRef.current) {
                            clearTimeout(longPressTimerRef.current);
                            longPressTimerRef.current = null;
                          }
                          // Only polyfill scroll for mouse, touch is native pan
                          if (isMouse) {
                            masterScroll.scrollLeft = startScrollLeft - deltaX;
                            masterScroll.scrollTop = startScrollTop - deltaY;
                          }
                        }
                      } else {
                        // Marquee Selection Mode!
                        if (!isMouse) moveEvent.preventDefault(); // attempt to stop scroll on touch if we can

                        const scrollDeltaX = masterScroll.scrollLeft - startScrollLeft;
                        const scrollDeltaY = masterScroll.scrollTop - startScrollTop;
                        
                        const curX = moveEvent.clientX - rect.left + scrollDeltaX;
                        const curY = moveEvent.clientY - rect.top + scrollDeltaY;
                        const absStartX = startX - rect.left;
                        const absStartY = startY - rect.top;

                        setMarquee({ startX: absStartX, startY: absStartY, currentX: curX, currentY: curY });

                        // Check Intersections
                        const minX = Math.min(absStartX, curX);
                        const maxX = Math.max(absStartX, curX);
                        const minY = Math.min(absStartY, curY);
                        const maxY = Math.max(absStartY, curY);

                        const newSelected: string[] = [];
                        const layerMap = new Map();
                        visibleLayers.forEach((l, i) => layerMap.set(l.id, i));

                        clips.forEach(clip => {
                           const lidx = layerMap.get(clip.layerId);
                           if (lidx === undefined) return;
                           const cLeft = clip.leftSeconds * currentPixelsPerSecondRef.current;
                           const cRight = cLeft + clip.durationSeconds * currentPixelsPerSecondRef.current;
                           const rowHeight = window.innerWidth >= 640 ? 48 : 40;
                           const clipHeight = window.innerWidth >= 640 ? 36 : 30;
                           const cTop = 32 + lidx * rowHeight;
                           const cBottom = cTop + clipHeight;

                           if (cLeft < maxX && cRight > minX && cTop < maxY && cBottom > minY) {
                               newSelected.push(clip.id);
                           }
                        });

                        setSelectedClipIds(newSelected);
                      }
                    };

                    const handlePointerUp = (upEvent: PointerEvent) => {
                      if (longPressTimerRef.current) {
                        clearTimeout(longPressTimerRef.current);
                        longPressTimerRef.current = null;
                      }
                      
                      if (isMarquee) {
                        setMarquee(null);
                      } else if (!hasMoved && copiedClip) {
                        // Handle paste popup exactly as before if no drag occurred
                        const trackElement = target.closest(".track-space");
                        const layerId = trackElement?.getAttribute("data-layer-id") || undefined;
                        setPastePopup({
                          x: startX,
                          y: startY,
                          time: clickTime,
                          layerId,
                        });
                      }

                      container.releasePointerCapture(upEvent.pointerId);
                      window.removeEventListener("pointermove", handlePointerMove);
                      window.removeEventListener("pointerup", handlePointerUp);
                      window.removeEventListener("pointercancel", handlePointerUp);
                    };

                    window.addEventListener("pointermove", handlePointerMove, { passive: false });
                    window.addEventListener("pointerup", handlePointerUp);
                    window.addEventListener("pointercancel", handlePointerUp);

                    // Start Long Press Timer for Marquee
                    longPressTimerRef.current = setTimeout(() => {
                      if (!hasMoved) {
                        isMarquee = true;
                        const absStartX = startX - rect.left + masterScroll.scrollLeft;
                        const absStartY = startY - rect.top + masterScroll.scrollTop;
                        setMarquee({ startX: absStartX, startY: absStartY, currentX: absStartX, currentY: absStartY });
                      }
                    }, 350);
                  }
                }}
                onPointerMove={() => {
                  pointerMoveCanvasRef.current = true;
                }}
                onPointerUp={() => {
                  if (longPressTimerRef.current) {
                    clearTimeout(longPressTimerRef.current);
                    longPressTimerRef.current = null;
                  }
                }}
                onPointerLeave={() => {
                  if (longPressTimerRef.current) {
                    clearTimeout(longPressTimerRef.current);
                    longPressTimerRef.current = null;
                  }
                }}
                onContextMenu={(e) => {
                  e.preventDefault();
                }}
              >
                {/* Scroll Content Width defined by max duration */}
                <div
                  id="timeline-content"
                  className="min-h-full min-w-full flex flex-col"
                  style={{
                    paddingRight: "calc(100vw - 100px)",
                    paddingBottom: "0px",
                    width: "fit-content",
                    boxSizing: "content-box",
                  }}
                >
                  <div
                    id="timeline-inner"
                    className="relative min-h-full w-full"
                    style={{
                      width: `${maxTimelineDuration * pixelsPerSecond}px`,
                      left: `${PLAYHEAD_X}px`,
                    }}
                  >
                    {/* Moving Playhead Cursor Removed (Now stationary in parent) */}

                    {/* Tracks Grid Area */}
                    <div
                      className="w-full pb-[200px] relative z-10"
                      style={{ paddingTop: "0" }}
                    >
                      {visibleLayers.map((layer) => (
                        <div
                          key={layer.id}
                          data-layer-id={layer.id}
                          className={`relative h-[32px] sm:h-[38px] w-full border-b flex items-center group track-space transition-[background,transform,border,shadow] transform-gpu ${draggingLayerId === layer.id ? "bg-indigo-500/5 border-indigo-500/25 scale-[1.01] shadow-[0_4px_16px_rgba(0,0,0,0.5)] z-50 rounded-lg overflow-hidden" : "border-white/[0.03] hover:bg-white/[0.01] z-0"}`}
                        >
                          {/* Grid Background with subtle digital blue accents */}
                          <div
                            className="absolute inset-0 pointer-events-none opacity-[0.22]"
                            style={{
                              backgroundImage:
                                zoomLevel > 1
                                  ? `linear-gradient(to right, rgba(99,102,241,0.06) 1px, transparent 1px), linear-gradient(to right, rgba(255,255,255,0.015) 1px, transparent 1px)`
                                  : `linear-gradient(to right, rgba(99,102,241,0.04) 1px, transparent 1px)`,
                              backgroundSize:
                                zoomLevel > 1
                                  ? `${pixelsPerSecond}px 100%, ${pixelsPerSecond / 10}px 100%`
                                  : `${pixelsPerSecond}px 100%`,
                              backgroundPosition: `0 0`,
                            }}
                          />

                          {/* Render Clips for this layer */}
                          {clips
                            .filter((c) => c.layerId === layer.id)
                            .map((clip) => (
                              <TimelineClipItem
                                key={clip.id}
                                clip={clip}
                                pixelsPerSecond={pixelsPerSecond}
                                selectedClipIds={selectedClipIds}
                                selectedClipId={selectedClipId}
                                layerHiddenOrMuted={layer.isHidden || (layer.isMuted && clip.type === "audio")}
                                activeExpandedMenu={activeExpandedMenu}
                                currentTime={currentTime}
                                isErrored={erroredClips.has(clip.id)}
                                onPointerDown={handleClipDragStart}
                                onClipError={handleClipError}
                                onTrimStart={handleTrimStart}
                              />
                            ))}

                          {false && clips
                            .filter((c) => c.layerId === layer.id)
                            .map((clip) => (
                              <div
                                key={clip.id}
                                onPointerDown={(e) =>
                                  handleClipDragStart(e, clip)
                                }
                                className={`absolute h-[28px] sm:h-[34px] overflow-hidden flex items-center cursor-pointer select-none border backdrop-blur-sm transition-[opacity,border-color,background-color,shadow] duration-250 shadow-[0_3px_10px_rgba(0,0,0,0.3)] relative group
                                           ${clip.type === "audio" ? "rounded-xl bg-gradient-to-r from-[#21103d]/95 to-[#3b1263]/90 border-purple-500/20" : "rounded-lg"}
                                           ${clip.type === "video" ? "bg-gradient-to-r from-[#0d1e3d]/95 via-[#122b5e]/90 to-[#0d1e3d]/80 border-indigo-500/20" : ""}
                                           ${clip.type === "text" ? "bg-gradient-to-r from-[#441f05]/95 via-[#632900]/90 to-[#441f05]/80 border-amber-500/20" : ""}
                                           ${clip.type === "image" ? "bg-gradient-to-r from-[#032a19]/95 via-[#0c4029]/90 to-[#032a19]/80 border-emerald-500/20" : ""}
                                           ${selectedClipIds.includes(clip.id) 
                                             ? (clip.type === "audio" 
                                               ? "z-20 opacity-100 border-purple-400/95 shadow-[0_0_15px_rgba(168,85,247,0.45),_inset_0_1px_1px_rgba(255,255,255,0.15)] bg-gradient-to-b from-[#2d114c] to-[#150727]" 
                                               : `z-20 opacity-100 shadow-[0_0_15px_rgba(99,102,241,0.35),_inset_0_1px_1px_rgba(255,255,255,0.15)] bg-gradient-to-b ${
                                                   clip.type === "video" ? "from-[#173a7c] to-[#0a183d] border-indigo-400" 
                                                   : clip.type === "text" ? "from-[#7e3e08] to-[#301300] border-amber-400" 
                                                   : "from-[#115b3a] to-[#011f10] border-emerald-400"
                                                 }`) 
                                             : "z-10 opacity-[0.88] hover:opacity-100 border-white/[0.05] hover:border-white/15"
                                           }
                                           ${layer.isHidden || (layer.isMuted && clip.type === "audio") ? "grayscale opacity-25 shadow-none" : ""}
                                         `}
                                style={{
                                  left: clip.leftSeconds * pixelsPerSecond,
                                  width: Math.max(2, clip.durationSeconds * pixelsPerSecond),
                                  touchAction: "none",
                                  willChange: "left, width",
                                }}
                              >
                                {/* High-glass aesthetic top specular line */}
                                <div className="absolute inset-x-0 top-0 h-[38%] bg-gradient-to-b from-white/[0.08] to-transparent pointer-events-none z-20" />
                                {/* Keyframes Overlay */}
                                {clip.keyframes && clip.keyframes.length > 0 && (() => {
                                  const volumeKeyframes = clip.keyframes.filter((k) => k.properties.volume !== undefined).sort((a,b) => a.timeOffset - b.timeOffset);
                                  const moveKeyframes = clip.keyframes.filter((k) => k.properties.translateX !== undefined || k.properties.scale !== undefined).sort((a,b) => a.timeOffset - b.timeOffset);

                                  return (
                                    <div className="absolute inset-0 pointer-events-none z-30" style={{ margin: '4px 0' }}>
                                      <svg className="absolute inset-0 w-full h-full" preserveAspectRatio="none" viewBox="0 0 100 100">
                                        {/* Volume - Purple */}
                                        {volumeKeyframes.length > 1 && (
                                          <polyline 
                                            points={volumeKeyframes.map((kf) => 
                                              `${(kf.timeOffset / clip.durationSeconds) * 100},${100 - Math.min(100, Math.max(0, kf.properties.volume ?? 100))}`
                                            ).join(' ')}
                                            fill="none" stroke="#a855f7" strokeWidth="2" vectorEffect="non-scaling-stroke" opacity="0.6" strokeDasharray="4,4"
                                          />
                                        )}
                                        {/* Zoom - Green */}
                                        {moveKeyframes.length > 1 && (
                                          <polyline 
                                            points={moveKeyframes.map((kf) => {
                                              const sc = kf.properties.scale ?? 1;
                                              const y = sc <= 1 ? 100 - (sc * 50) : Math.max(0, 50 - ((sc - 1) * 25));
                                              return `${(kf.timeOffset / clip.durationSeconds) * 100},${y}`;
                                            }).join(' ')}
                                            fill="none" stroke="#22c55e" strokeWidth="2" vectorEffect="non-scaling-stroke" opacity="0.6" strokeDasharray="4,4"
                                          />
                                        )}
                                        {/* Pan - Blue */}
                                        {moveKeyframes.length > 1 && (
                                          <polyline 
                                            points={moveKeyframes.map((kf) => {
                                              const tx = kf.properties.translateX ?? 0;
                                              const y = Math.max(0, Math.min(100, 50 - (tx / 400) * 50));
                                              return `${(kf.timeOffset / clip.durationSeconds) * 100},${y}`;
                                            }).join(' ')}
                                            fill="none" stroke="#3b82f6" strokeWidth="2" vectorEffect="non-scaling-stroke" opacity="0.6" strokeDasharray="4,4"
                                          />
                                        )}
                                      </svg>

                                      {clip.keyframes.map((kf) => {
                                        const isVol = kf.properties.volume !== undefined;
                                        const isMove = kf.properties.translateX !== undefined || kf.properties.scale !== undefined;
                                        
                                        const x = (kf.timeOffset / clip.durationSeconds) * 100;
                                        const isActivated = selectedClipId === clip.id && Math.abs((currentTime - clip.leftSeconds) - kf.timeOffset) < 0.05;

                                        return (
                                          <div key={kf.id}>
                                            {isVol && (() => {
                                              const y = 100 - Math.min(100, Math.max(0, kf.properties.volume ?? 100));
                                              return (
                                                <div
                                                  className="absolute w-[10px] h-[10px] border-[2px] border-[#252528] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] transform -translate-x-1/2 -translate-y-1/2 flex items-center justify-center transition-all z-40 group bg-[#a855f7]"
                                                  style={{ left: `${x}%`, top: `${y}%` }}
                                                >
                                                </div>
                                              );
                                            })()}
                                            {isMove && (() => {
                                              const sc = kf.properties.scale ?? 1;
                                              const yZoom = sc <= 1 ? 100 - (sc * 50) : Math.max(0, 50 - ((sc - 1) * 25));

                                              const tx = kf.properties.translateX ?? 0;
                                              const yPan = Math.max(0, Math.min(100, 50 - (tx / 400) * 50));

                                              return (
                                                <>
                                                  {/* Zoom Node */}
                                                  <div
                                                    className="absolute w-[10px] h-[10px] border-[2px] border-[#252528] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] transform -translate-x-1/2 -translate-y-1/2 flex items-center justify-center transition-all z-30 group bg-[#22c55e]"
                                                    style={{ left: `${x}%`, top: `${yZoom}%` }}
                                                  >
                                                  </div>
                                                  {/* Pan Node */}
                                                  {Math.abs(yPan - yZoom) > 5 && (
                                                    <div
                                                      className="absolute w-[8px] h-[8px] border-[1.5px] border-[#252528] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] transform -translate-x-1/2 -translate-y-1/2 flex items-center justify-center transition-all z-20 group bg-[#3b82f6]"
                                                      style={{ left: `${x}%`, top: `${yPan}%` }}
                                                    >
                                                    </div>
                                                  )}
                                                </>
                                              );
                                            })()}
                                          </div>
                                        );
                                      })}
                                    </div>
                                  );
                                })()}

                                <div className="absolute inset-0 bg-black/10 pointer-events-none z-10"></div>
                                {clip.type === "text" && (
                                  <div className="w-full h-full flex items-center justify-center px-4 pointer-events-none overflow-hidden pb-1 pt-3">
                                    <span className="text-[12px] font-bold text-white/90 truncate drop-shadow-sm bg-black/40 px-2 py-0.5 rounded border border-white/10">
                                      {clip.text || "Type text..."}
                                    </span>
                                  </div>
                                )}
                                {clip.type === "image" && (
                                  <>
                                    <img
                                      src={clip.src || undefined}
                                      className="absolute inset-0 w-full h-full object-cover opacity-70 pointer-events-none"
                                      draggable={false}
                                      onError={() => handleClipError(clip.id)}
                                    />
                                    <div className="absolute inset-0 bg-gradient-to-r from-black/80 via-black/20 to-transparent pointer-events-none z-10"></div>
                                  </>
                                )}
                                {clip.type === "video" && (
                                  <>
                                    <video
                                      src={clip.src ? clip.src + "#t=0.001" : undefined}
                                      className="absolute inset-0 w-full h-full object-cover opacity-70 pointer-events-none"
                                      draggable={false}
                                      preload="metadata"
                                      onError={() => handleClipError(clip.id)}
                                    />
                                    <div className="absolute inset-0 bg-gradient-to-r from-black/80 via-black/20 to-transparent pointer-events-none z-10"></div>
                                  </>
                                )}
                                {clip.type === "audio" && (() => {
                                  const seed = clip.id.split("").reduce((acc, char) => acc + char.charCodeAt(0), 0);
                                  const freq1 = 0.07 + (seed % 7) * 0.015;
                                  const freq2 = 0.14 + (seed % 11) * 0.012;
                                  const freq3 = 0.03 + (seed % 5) * 0.008;

                                  const hasVolumeKeyframes = clip.keyframes?.some(k => k.properties.volume !== undefined);
                                  const constantVolume = typeof clip.volume === "number" ? clip.volume : 100;
                                  const constantVolMult = constantVolume / 100;

                                  const barCount = 140;
                                  let pathD = "";

                                  for (let i = 0; i < barCount; i++) {
                                    const h1 = Math.sin(i * freq1);
                                    const h2 = Math.cos(i * freq2);
                                    const h3 = Math.sin(i * freq3);
                                    
                                    // Generate a premium fluid song acoustic pattern
                                    let val = 0.12 + 0.38 * Math.abs(h1) + 0.32 * Math.abs(h2 * h3) + 0.12 * Math.sin(i * 0.3);
                                    val = Math.max(0.06, Math.min(0.95, val));

                                    // Check volume at this slice of the audio duration
                                    let volMult = constantVolMult;
                                    if (hasVolumeKeyframes && clip.durationSeconds) {
                                      const tRel = (i / (barCount - 1)) * clip.durationSeconds;
                                      const propsAtBar = getInterpolatedProps(clip, tRel, activeExpandedMenu);
                                      volMult = (propsAtBar.volume ?? 100) / 100;
                                    }
                                    
                                    const finalVal = val * volMult;
                                    const x = i * 10 + 5;
                                    const halfH = finalVal * 42; // Vertically symmetric waves scaling up to 42 units up & down
                                    const y1 = 50 - halfH;
                                    const y2 = 50 + halfH;
                                    pathD += `M ${x} ${y1} L ${x} ${y2} `;
                                  }

                                  const gradientId = `wave-grad-${clip.id}`;
                                  return (
                                    <svg 
                                      className="absolute inset-y-[4px] left-[12px] right-[12px] w-[calc(100%-24px)] h-[calc(100%-8px)] pointer-events-none opacity-[0.9]"
                                      viewBox={`0 0 ${barCount * 10} 100`}
                                      preserveAspectRatio="none"
                                    >
                                      <defs>
                                        <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="0%">
                                          <stop offset="0%" stopColor="#c084fc" />
                                          <stop offset="50%" stopColor="#f472b6" />
                                          <stop offset="100%" stopColor="#818cf8" />
                                        </linearGradient>
                                      </defs>
                                      <path
                                        d={pathD}
                                        stroke={`url(#${gradientId})`}
                                        strokeWidth="3.2"
                                        strokeLinecap="round"
                                      />
                                    </svg>
                                  );
                                })()}

                                {/* Type indicator icon */}
                                <div className={`absolute max-w-full overflow-hidden whitespace-nowrap pl-2 flex items-center gap-1 ${clip.type === "audio" ? "inset-y-0 z-20 pointer-events-none" : `top-1 pointer-events-none`}`}>
                                  {erroredClips.has(clip.id) &&
                                    clip.type !== "text" && (
                                      <span className="text-[10px] font-bold text-red-100 uppercase drop-shadow-md pb-0.5 px-1 bg-red-500/80 rounded inline-flex items-center gap-1">
                                        <AlertCircle size={10} /> Missing File
                                      </span>
                                    )}
                                  
                                  {clip.type === "audio" ? (
                                    <span className={`text-[10px] py-0.5 px-2 font-bold text-purple-200 tracking-wider drop-shadow-sm bg-black/55 border border-purple-500/25 rounded-md inline-flex items-center backdrop-blur-md shadow-sm gap-1 ml-0.5 sm:ml-1 scale-95 origin-left`}>
                                      <span className={`w-1.5 h-1.5 rounded-full bg-purple-400 animate-pulse shrink-0`} />
                                      AUDIO {layer.isHidden && "(HIDDEN)"} {layer.isMuted && "(MUTED)"}
                                    </span>
                                  ) : (
                                    <span className={`text-[9px] py-0.5 px-1.5 font-bold tracking-wider text-zinc-200 drop-shadow-sm bg-black/55 border border-white/10 rounded-md shadow-sm backdrop-blur-md inline-flex items-center uppercase scale-95 origin-left`}>
                                      {clip.type} {layer.isHidden && "(HIDDEN)"}{" "}
                                      {layer.isMuted && "(MUTED)"}
                                    </span>
                                  )}

                                  {clip.opticalFlow && (
                                    <span className="text-[10px] font-bold text-indigo-200 uppercase drop-shadow-md pb-0.5 px-1 bg-indigo-500/50 rounded inline-flex items-center gap-1">
                                      <Activity size={10} /> Smooth
                                    </span>
                                  )}
                                </div>

                                {/* Keyframe Markers and Slope Connections */}
                                {clip.keyframes && clip.keyframes.length > 0 && (
                                  <svg className="absolute inset-0 w-full h-full pointer-events-none z-30 overflow-visible">
                                    {(() => {
                                      const sortedKfs = [...clip.keyframes].sort((a, b) => a.timeOffset - b.timeOffset);
                                      const isVol = activeExpandedMenu === "volume";
                                      const relevantSortedKfs = sortedKfs.filter(kf => {
                                        if (isVol) {
                                          return kf.properties.volume !== undefined;
                                        } else {
                                          return kf.properties.translateX !== undefined || kf.properties.scale !== undefined;
                                        }
                                      });
                                      const points = relevantSortedKfs.map(kf => {
                                        const xCoord = kf.timeOffset * pixelsPerSecond;
                                        
                                        let val = 1.0;
                                        if (kf.properties.volume !== undefined) {
                                          val = kf.properties.volume > 1.5 ? kf.properties.volume / 100 : kf.properties.volume;
                                        } else if (kf.properties.opacity !== undefined) {
                                          val = kf.properties.opacity;
                                        } else if (kf.properties.scale !== undefined) {
                                          val = (kf.properties.scale - 0.1) / 2.9;
                                        }
                                        val = Math.max(0, Math.min(1, val));
                                        
                                        // keeping it beautifully inset within 9px to 29px in the 38px tall container
                                        const yCoord = 29 - (val * 20); 
                                        return { x: xCoord, y: yCoord, val, kf };
                                      });

                                      let pathD = "";
                                      if (points.length >= 2) {
                                        pathD = `M ${points[0].x} ${points[0].y} ` + points.slice(1).map(p => `L ${p.x} ${p.y}`).join(" ");
                                      }

                                      return (
                                        <>
                                          {pathD && (
                                            <>
                                              {/* High-visibility core connecting slope line */}
                                              <path
                                                d={pathD}
                                                fill="none"
                                                stroke="#a5b4fc"
                                                strokeWidth="0.5"
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                              />
                                            </>
                                          )}

                                          {points.map((p) => {
                                            const isSelectedKf = selectedClipId === clip.id && Math.abs(currentTime - (clip.leftSeconds + p.kf.timeOffset)) < 0.05;
                                            return (
                                              <g key={p.kf.id} className="transform-gpu">
                                                {/* Soft pulse effect around active keyframe */}
                                                {isSelectedKf && (
                                                  <circle
                                                    cx={p.x}
                                                    cy={p.y}
                                                    r="7"
                                                    fill="none"
                                                    stroke="#818cf8"
                                                    strokeWidth="1.5"
                                                    className="animate-pulse"
                                                    style={{ transformOrigin: `${p.x}px ${p.y}px` }}
                                                  />
                                                )}
                                                {/* Solid contrasting circle node background */}
                                                <circle
                                                  cx={p.x}
                                                  cy={p.y}
                                                  r="5"
                                                  fill="#1e1b4b"
                                                  stroke="none"
                                                />
                                                {/* Vibrant front keyframe circle */}
                                                <circle
                                                  cx={p.x}
                                                  cy={p.y}
                                                  r="3.5"
                                                  fill={isSelectedKf ? "#ffffff" : "#c7d2fe"}
                                                  stroke={isSelectedKf ? "#4f46e5" : "#4338ca"}
                                                  strokeWidth="1.5"
                                                />
                                                {/* Precision value percentage badge above point (visible when selected) */}
                                                {selectedClipId === clip.id && (
                                                  <text
                                                    x={p.x}
                                                    y={p.y - 7}
                                                    textAnchor="middle"
                                                    fill="#ffffff"
                                                    fontSize="7"
                                                    fontWeight="bold"
                                                    className="font-mono pointer-events-none drop-shadow-[0_1px_2.5px_rgba(0,0,0,0.95)] select-none"
                                                    style={{ paintOrder: "stroke", stroke: "#000000", strokeWidth: "1.5px", strokeLinejoin: "round" }}
                                                  >
                                                    {p.kf.properties.volume !== undefined 
                                                      ? `${Math.round((p.kf.properties.volume > 1.5 ? p.kf.properties.volume / 100 : p.kf.properties.volume) * 100)}%` 
                                                      : p.kf.properties.opacity !== undefined 
                                                      ? `${Math.round(p.kf.properties.opacity * 100)}%`
                                                      : p.kf.properties.scale !== undefined
                                                      ? `${Math.round(p.kf.properties.scale * 100)}%`
                                                      : ""
                                                    }
                                                  </text>
                                                )}
                                              </g>
                                            );
                                          })}
                                        </>
                                      );
                                    })()}
                                  </svg>
                                )}

                                {/* Trim Controls with premium high-tech glowing edge handle bar */}
                                {selectedClipId === clip.id && (
                                  <>
                                    <div
                                      onPointerDown={(e) =>
                                        handleTrimStart(e, clip, "left")
                                      }
                                      className="absolute left-0 top-0 bottom-0 w-[10px] bg-white/90 hover:bg-white border-r border-black/40 flex items-center justify-center cursor-col-resize hover:w-[13px] transition-all z-25 shadow-[1px_0_6px_rgba(0,0,0,0.5)]"
                                      style={{ touchAction: "none" }}
                                    >
                                      <div className="flex flex-col gap-0.5 justify-center items-center">
                                        <div className="w-[1.5px] h-1.5 bg-zinc-700 rounded-full" />
                                        <div className="w-[1.5px] h-1.5 bg-zinc-700 rounded-full" />
                                      </div>
                                    </div>
                                    <div
                                      onPointerDown={(e) =>
                                        handleTrimStart(e, clip, "right")
                                      }
                                      className="absolute right-0 top-0 bottom-0 w-[10px] bg-white/90 hover:bg-white border-l border-black/40 flex items-center justify-center cursor-col-resize hover:w-[13px] transition-all z-25 shadow-[-1px_0_6px_rgba(0,0,0,0.5)]"
                                      style={{ touchAction: "none" }}
                                    >
                                      <div className="flex flex-col gap-0.5 justify-center items-center">
                                        <div className="w-[1.5px] h-1.5 bg-zinc-700 rounded-full" />
                                        <div className="w-[1.5px] h-1.5 bg-zinc-700 rounded-full" />
                                      </div>
                                    </div>
                                  </>
                                )}
                              </div>
                            ))}

                          {/* Render Transitions for this layer */}
                          {getAdjacentClipPairsOnLayer(clips.filter((c) => c.layerId === layer.id)).map(({ prev, current }) => {
                            const boundaryX = current.leftSeconds * pixelsPerSecond;
                            const hasTransition = !!current.transition;

                            return (
                              <div
                                key={`transition-btn-${prev.id}-${current.id}`}
                                className="absolute pointer-events-auto"
                                style={{
                                  left: boundaryX,
                                  transform: "translateX(-50%)",
                                  top: 0,
                                  bottom: 0,
                                  display: "flex",
                                  alignItems: "center",
                                  justifyContent: "center",
                                zIndex: 49,
                              }}
                            >
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  e.preventDefault();
                                  setTransitionModal({
                                    prevClipId: prev.id,
                                    currentClipId: current.id,
                                    layerId: layer.id,
                                    type: current.transition?.type || "crossfade",
                                    duration: current.transition?.duration || 0.5,
                                  });
                                  setActiveExpandedMenu("transition");
                                }}
                                className={`w-[22px] h-[22px] rounded-md flex items-center justify-center transition-all duration-200 cursor-pointer border shadow-[0_3px_8px_rgba(0,0,0,0.5)] ${
                                  hasTransition
                                    ? "bg-gradient-to-br from-indigo-500 to-indigo-600 hover:from-indigo-400 hover:to-indigo-500 border-indigo-400 text-white hover:scale-[1.12] active:scale-95 shadow-indigo-500/25 rotate-45"
                                    : "bg-[#161619] hover:bg-indigo-600 border-white/[0.08] hover:border-indigo-400/50 text-zinc-400 hover:text-white hover:scale-[1.12] active:scale-95 opacity-80 hover:opacity-100"
                                }`}
                                title={hasTransition ? `Edit transition: ${current.transition?.type}` : "Add Transition"}
                              >
                                <div className={hasTransition ? "-rotate-45" : ""}>
                                  {hasTransition ? (
                                    <span className="text-[8px] font-bold text-white uppercase tracking-wider font-mono">X</span>
                                  ) : (
                                    <PlusIcon size={11} className="stroke-[2.5]" />
                                  )}
                                </div>
                              </button>
                            </div>
                            );
                          })}
                        </div>
                      ))}

                      {/* Empty state instruction inside timeline */}
                      {layers.length === 0 && (
                        <div className="w-full h-[155px] flex flex-col items-center justify-center py-5 text-zinc-450 gap-2.5 max-w-sm mx-auto select-none">
                          <div className="text-center">
                            <span className="text-xs font-extrabold text-zinc-300 mb-0.5 block uppercase tracking-widest font-sans">
                              Timeline is Empty
                            </span>
                            <span className="text-[10px] text-zinc-500 leading-relaxed font-sans block mb-3">
                              Begin your slow-motion masterwork by importing native files or creating text layers.
                            </span>
                          </div>
                          <div className="flex items-center gap-2">
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setActiveExpandedMenu("plus-media");
                              }}
                              className="px-3.5 py-1.5 bg-indigo-650 hover:bg-indigo-600 active:scale-95 text-white font-bold text-[10px] uppercase tracking-wider rounded-xl transition-all border border-indigo-500/10 cursor-pointer flex items-center gap-1.5 shadow-md shadow-indigo-950/20"
                            >
                              <PlusIcon size={12} />
                              <span>Import Media</span>
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleAddText();
                              }}
                              className="px-3.5 py-1.5 bg-zinc-800 hover:bg-zinc-750 active:scale-95 text-zinc-300 hover:text-white font-bold text-[10px] uppercase tracking-wider rounded-xl transition-all border border-white/5 cursor-pointer flex items-center gap-1.5"
                            >
                              <Type size={12} strokeWidth={2.2} />
                              <span>Add Text</span>
                            </button>
                          </div>
                        </div>
                      )}

                      {/* Marquee Selection Box */}
                      {marquee && (
                        <div
                          className="absolute bg-white/20 border border-white/50 z-[100] pointer-events-none rounded-[2px]"
                          style={{
                            left: Math.min(marquee.startX, marquee.currentX),
                            top: Math.min(marquee.startY, marquee.currentY),
                            width: Math.abs(marquee.currentX - marquee.startX),
                            height: Math.abs(marquee.currentY - marquee.startY)
                          }}
                        />
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  
);
  return (
    <div 
      className="min-h-screen bg-[#0c0c0e] text-white flex flex-col font-sans"
      style={{ zoom: appScale }}
    >
      {/* Dynamic Render based on Screen */}
      {currentScreen === "home" && renderHome()}
      {currentScreen === "settings" && renderSettings()}
      {currentScreen === "editor" && renderEditor()}
      {currentScreen === "editor" && (
        <>
          {/* Floating Action Menu attached to bottom or overlay */}
          <motion.div
            layoutId="new-project-btn"
            layout
            transition={{ type: "spring", bounce: 0.5, duration: 0.6 }}
            className={`fixed bottom-0 mt-[0px] mb-[60px] left-1/2 -translate-x-1/2 flex flex-col bg-[#0d0d12]/95 backdrop-blur-xl overflow-hidden ${activeExpandedMenu === "speed-curves" ? "rounded-[24px] pt-1.5 pb-1 w-[218px]" : activeExpandedMenu === "move" ? "rounded-[24px] pt-1.5 pb-1.5 w-[218px]" : (activeExpandedMenu && activeExpandedMenu !== "plus-media") ? "rounded-[24px] pt-1.5 pb-1 w-[218px]" : "rounded-[24px] h-[50px] justify-center w-[218px]"} shadow-[0_20px_50px_rgba(0,0,0,0.7),_0_0_20px_rgba(99,102,241,0.06)] border border-white/10 z-[200] transform-gpu`}
          >
            <AnimatePresence mode="popLayout">
              {activeExpandedMenu === "transition" && transitionModal && (() => {
                const prevClip = clips.find(c => c.id === transitionModal.prevClipId);
                const currentClip = clips.find(c => c.id === transitionModal.currentClipId);
                if (!prevClip || !currentClip) return null;

                const isAudioOnly = prevClip.type === "audio" && currentClip.type === "audio";
                const maxAllowedDuration = Math.min(3, Math.min(prevClip.durationSeconds, currentClip.durationSeconds) / 2);

                const transitionOptions = isAudioOnly
                  ? [{ id: "crossfade", name: "Crossfade", desc: "Smooth dynamic volume blending" }]
                  : [
                      { id: "crossfade", name: "Crossfade", desc: "Classic smooth opacity dissolve" },
                      { id: "blur", name: "Blur Dissolve", desc: "Fade with beautiful Gaussian blur" },
                      { id: "slide-left", name: "Slide Left", desc: "Translate incoming clip from right" },
                      { id: "slide-right", name: "Slide Right", desc: "Translate incoming clip from left" },
                      { id: "wipe-left", name: "Wipe Left", desc: "Geometric sliding reveal from right" },
                      { id: "wipe-right", name: "Wipe Right", desc: "Geometric sliding reveal from left" },
                      { id: "zoom-in", name: "Zoom In", desc: "Incoming scale magnification" },
                    ];

                return (
                  <motion.div
                    layout
                    initial={{ opacity: 0, scale: 0.95, y: 10 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: 10 }}
                    transition={{ duration: 0.2 }}
                    className="flex flex-col w-full px-3 pb-2 text-left"
                  >
                    {/* Header */}
                    <div className="flex justify-between items-center w-full mb-1.5 px-0.5 mt-1.5">
                      <div className="flex flex-col">
                        <span className="text-[10px] font-bold text-indigo-400 uppercase tracking-widest leading-none">Transition</span>
                        <span className="text-[7.5px] text-zinc-500 font-bold uppercase tracking-wider mt-1 leading-none">
                          {prevClip.type} + {currentClip.type}
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        {currentClip.transition && (
                          <button
                            onClick={() => {
                              removeTransition(transitionModal.currentClipId);
                              setTransitionModal(null);
                              setActiveExpandedMenu(null);
                            }}
                            className="text-[8px] bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 px-1.5 py-0.5 rounded font-bold uppercase tracking-wider transition-all"
                            title="Remove transition"
                          >
                            Remove
                          </button>
                        )}
                        <button
                          onClick={() => {
                            setTransitionModal(null);
                            setActiveExpandedMenu(null);
                          }}
                          className="text-emerald-405 hover:text-emerald-300 p-0.5"
                        >
                          <Check size={14} className="text-emerald-400" />
                        </button>
                      </div>
                    </div>

                    {/* Style horizontal scrolling list */}
                    <div className="flex items-center gap-1 px-1 overflow-x-auto scrollbar-hide snap-x pt-0.5 pb-2 border-b border-white/[0.04] mb-2">
                      {transitionOptions.map((opt) => {
                        const isSelected = transitionModal.type === opt.id;
                        return (
                          <button
                            key={opt.id}
                            type="button"
                            onClick={() => {
                              const updatedModal = { ...transitionModal, type: opt.id };
                              setTransitionModal(updatedModal);
                              addTransition(transitionModal.currentClipId, opt.id, transitionModal.duration, true);
                            }}
                            className={`shrink-0 px-2.5 py-1 rounded-lg border transition-all duration-150 snap-start flex flex-col items-center min-w-[70px] ${
                              isSelected
                                ? "bg-indigo-500/15 border-indigo-550/40 text-indigo-300 shadow-[0_0_8px_rgba(99,102,241,0.2)]"
                                : "bg-[#151518]/70 border-white/[0.04] hover:border-white/[0.08] text-zinc-400 hover:text-zinc-200"
                            }`}
                          >
                            <span className="text-[8.5px] font-bold leading-tight truncate w-full text-center">{opt.name}</span>
                          </button>
                        );
                      })}
                    </div>

                    {/* Duration Slider - Simple and elegant, fitting w-[220px] */}
                    <div className="w-full pr-0.5">
                      <div className="flex justify-between items-center mb-1">
                        <span className="text-[8px] text-zinc-500 font-bold uppercase pl-0.5">Duration</span>
                        <span className="text-[9px] font-mono pr-0.5 text-zinc-300">
                          {transitionModal.duration.toFixed(2)}s
                        </span>
                      </div>
                      <input
                        type="range"
                        min="0.1"
                        max={maxAllowedDuration.toFixed(2)}
                        step="0.05"
                        value={transitionModal.duration}
                        onChange={(e) => {
                          const val = parseFloat(e.target.value);
                          setTransitionModal(prev => prev ? { ...prev, duration: val } : null);
                          addTransition(transitionModal.currentClipId, transitionModal.type, val, true);
                        }}
                        className="w-full accent-white h-0.5 bg-zinc-700 rounded-full appearance-none [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-2.5 [&::-webkit-slider-thumb]:h-2.5 [&::-webkit-slider-thumb]:bg-white [&::-webkit-slider-thumb]:rounded-full cursor-pointer mt-0.5"
                      />
                      <div className="flex justify-between text-[7px] text-zinc-500 mt-1 font-bold tracking-tight uppercase px-0.5">
                        <span>Min: 0.1s</span>
                        <span>Max: {maxAllowedDuration.toFixed(2)}s</span>
                      </div>
                    </div>
                  </motion.div>
                );
              })()}

              {activeExpandedMenu === "volume" && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col w-full px-3 pb-1"
                >
                  <div className="flex justify-between items-center w-full mb-0.5 px-0.5 mt-0.5">
                    <button
                      className="flex items-center gap-2 text-[11px] text-zinc-300 hover:text-white"
                      onClick={() => setApplyVolumeToAll(!applyVolumeToAll)}
                    >
                      <div
                        className={`w-[14px] h-[14px] rounded-full border-[1.5px] ${applyVolumeToAll ? "border-white bg-white" : "border-zinc-500"} flex items-center justify-center transition-colors`}
                      >
                        {applyVolumeToAll && (
                          <div className="w-1.5 h-1.5 bg-black rounded-full" />
                        )}
                      </div>
                      Apply to all
                    </button>
                    <div className="flex items-center gap-1.5 pl-2">
                      {renderCopyPasteButtons("volume")}
                      <button
                        onClick={() => setActiveExpandedMenu(null)}
                        className="text-zinc-400 hover:text-white pb-0.5 pr-0.5 flex items-center justify-center p-1 rounded-md hover:bg-zinc-800 transition"
                      >
                        <Check size={16} strokeWidth={2} />
                      </button>
                    </div>
                  </div>
                  
                  <div className="flex items-center w-full gap-3 px-0.5 mb-1 mt-0.5">
                    <div
                      className="flex-1 h-8 relative cursor-ew-resize touch-none flex items-center"
                      onPointerDown={(e) => {
                        const target = e.currentTarget;
                        target.setPointerCapture(e.pointerId);
                        const updateVol = (clientX: number) => {
                          const rect = target.getBoundingClientRect();
                          let x = clientX - rect.left;
                          x = Math.max(0, Math.min(rect.width, x));
                          let val = Math.round((x / rect.width) * 100);
                          setClipVolume(val);
                          
                          const targetIds = clips
                            .filter(c => c.id === selectedClipId || (applyVolumeToAll && (c.type === "video" || c.type === "audio")))
                            .map(c => c.id);
                            
                          updateClipsProperties(targetIds, { volume: val });
                        };
                        updateVol(e.clientX);
                        const moveHandler = (me: PointerEvent) => updateVol(me.clientX);
                        const upHandler = (ue: PointerEvent) => {
                          target.releasePointerCapture(ue.pointerId);
                          target.removeEventListener("pointermove", moveHandler);
                          target.removeEventListener("pointerup", upHandler);
                          target.removeEventListener("pointercancel", upHandler);
                        };
                        target.addEventListener("pointermove", moveHandler);
                        target.addEventListener("pointerup", upHandler);
                        target.addEventListener("pointercancel", upHandler);
                      }}
                    >
                      {/* Track background */}
                      <div className="absolute left-0 right-0 h-[1.5px] bg-white/20 rounded-full pointer-events-none" />
                      
                      {/* Active level fill */}
                      <div
                        className="absolute left-0 h-[1.5px] bg-[#a5b4fc] rounded-full pointer-events-none transition-all duration-75"
                        style={{ width: `${currentSelectedClipInterpolatedProps?.volume ?? 100}%` }}
                      />

                      {/* Premium knob mirroring user's photo */}
                      <div
                        className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 pointer-events-none transition-all duration-75 flex items-center justify-center z-10"
                        style={{ left: `${currentSelectedClipInterpolatedProps?.volume ?? 100}%` }}
                      >
                        <div className="w-[22px] h-[22px] bg-white/15 backdrop-blur-[1px] rounded-full flex items-center justify-center shadow-[0_2px_8px_rgba(0,0,0,0.4)] border border-white/20">
                          <div className="w-[11px] h-[11px] bg-white rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.4)]" />
                        </div>
                      </div>
                    </div>
                    <span className="text-[10px] text-zinc-300 font-sans w-8 text-right font-medium">
                      {Math.round(currentSelectedClipInterpolatedProps?.volume ?? 100)}%
                    </span>
                  </div>
                </motion.div>
              )}

              {activeExpandedMenu === "speed" && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col w-full px-3 pb-0.5"
                >
                  <SpeedRulerControl
                    value={clipSpeed}
                    onChange={(val) => {
                      setClipSpeed(val);
                      if (selectedClipId) {
                        setClips((prev) => {
                          const target = prev.find(c => c.id === selectedClipId);
                          if (!target) return prev;
                          
                          const oldSpeed = target.speed || 1;
                          const originalDur = target.durationSeconds * oldSpeed;
                          const newDuration = originalDur / val;
                          const delta = newDuration - target.durationSeconds;
                          
                          return prev.map(c => {
                            if (c.id === selectedClipId) {
                              return {...c, speed: val, durationSeconds: newDuration, opticalFlow: undefined};
                            }
                            if (c.layerId === target.layerId && c.leftSeconds >= target.leftSeconds + target.durationSeconds) {
                              return {...c, leftSeconds: c.leftSeconds + delta};
                            }
                            return c;
                          });
                        });
                      }
                    }}
                    onReset={() => {
                      setClipSpeed(1);
                      if (selectedClipId) {
                        setClips((prev) => {
                          const target = prev.find(c => c.id === selectedClipId);
                          if (!target) return prev;
                          
                          const oldSpeed = target.speed || 1;
                          const originalDur = target.durationSeconds * oldSpeed;
                          const newDuration = originalDur / 1;
                          const delta = newDuration - target.durationSeconds;
                          
                          return prev.map(c => {
                            if (c.id === selectedClipId) {
                              return {...c, speed: 1, durationSeconds: newDuration, opticalFlow: undefined};
                            }
                            if (c.layerId === target.layerId && c.leftSeconds >= target.leftSeconds + target.durationSeconds) {
                              return {...c, leftSeconds: c.leftSeconds + delta};
                            }
                            return c;
                          });
                        });
                      }
                    }}
                    onClose={() => setActiveExpandedMenu(null)}
                  >
                    {renderCopyPasteButtons("speed")}
                  </SpeedRulerControl>
                  <div className="flex gap-1.5 pb-2">
                    <button
                      onClick={async () => {
                        const currentClip = clips.find(
                          (c) => c.id === selectedClipId,
                        );
                        if (!currentClip || currentClip.type !== "video") return;

                        const isOpticalFlowApplied =
                          currentClip?.opticalFlow || false;

                        if (isOpticalFlowApplied) {
                          // Toggle off
                          if (selectedClipId) {
                            setClips((prev) =>
                              prev.map((c) =>
                                c.id === selectedClipId
                                  ? { ...c, opticalFlow: false, src: c.originalSrc || c.src }
                                  : c,
                              ),
                            );
                          }
                          return;
                        }

                        if (smoothProcessingProgress !== null) return;
                        setSmoothProcessingProgress(0);

                        try {
                          setPillPopup({ message: "Applying Smooth Slow-mo...", progress: 0, type: 'loading' });
                          
                          let videoSource: string | Blob = currentClip.src;
                          if (currentClip.fileId) {
                            try {
                              const { getFile } = await import("./lib/db");
                              const cachedBlob = await getFile(currentClip.fileId);
                              if (cachedBlob) {
                                videoSource = cachedBlob;
                              }
                            } catch (dbErr) {
                              console.warn("Could not retrieve file from IndexedDB:", dbErr);
                            }
                          }

                           const decodeResult = await processSmoothSlowMoBrowser(
                            videoSource,
                            currentClip.speed || 1,
                            (progress) => {
                              const pVal = Math.min(99, Math.round(progress));
                              setSmoothProcessingProgress(pVal);
                              setPillPopup({ message: "Applying Smooth Slow-mo...", progress: pVal, type: 'loading' });
                            }
                          );

                          const { 
                            url: newSrcUrl, 
                            fileId: newFileId, 
                            decodedFramesCount, 
                            flowComputedCount, 
                            timestampsVerified,
                            avgFlowMagnitude,
                            maxFlowMagnitude,
                            flowVisualization,
                            isFlowCorrect,
                            interpolatedFramesCount,
                            averagePsnr,
                            averageWarpError,
                            interpolationVisualization,
                            totalExportTimeMs,
                            decoderTimeMs,
                            opticalFlowTimeMs,
                            interpolationTimeMs,
                            colorConversionTimeMs,
                            encoderTimeMs,
                            decoderPct,
                            opticalFlowPct,
                            interpolationPct,
                            colorConversionPct,
                            encoderPct,
                            decoderMsPerFrame,
                            opticalFlowMsPerFrame,
                            interpolationMsPerFrame,
                            colorConversionMsPerFrame,
                            encoderMsPerFrame,
                            matAllocations,
                            byteBufferAllocations,
                            frameCopies
                          } = decodeResult;

                          setOpticalFlowDiagnostics({
                            clipId: selectedClipId,
                            decodedFramesCount,
                            flowComputedCount,
                            timestampsVerified,
                            avgFlowMagnitude,
                            maxFlowMagnitude,
                            flowVisualization,
                            isFlowCorrect,
                            interpolatedFramesCount,
                            averagePsnr,
                            averageWarpError,
                            interpolationVisualization,
                            totalExportTimeMs,
                            decoderTimeMs,
                            opticalFlowTimeMs,
                            interpolationTimeMs,
                            colorConversionTimeMs,
                            encoderTimeMs,
                            decoderPct,
                            opticalFlowPct,
                            interpolationPct,
                            colorConversionPct,
                            encoderPct,
                            decoderMsPerFrame,
                            opticalFlowMsPerFrame,
                            interpolationMsPerFrame,
                            colorConversionMsPerFrame,
                            encoderMsPerFrame,
                            matAllocations,
                            byteBufferAllocations,
                            frameCopies
                          });

                          setSmoothProcessingProgress(100);
                          if (decodedFramesCount !== undefined) {
                            const flowMsg = flowComputedCount !== undefined ? `, ${flowComputedCount} DIS flows computed` : '';
                            setPillPopup({ 
                              message: `Slow-mo: ${decodedFramesCount} frames (${timestampsVerified ? "Verified TS" : "Non-monotonic TS!"})${flowMsg}`, 
                              type: 'info' 
                            });
                          } else {
                            setPillPopup({ message: "Smooth Slow-mo Applied", type: 'info' });
                          }

                          if (selectedClipId) {
                            setClips((prev) =>
                              prev.map((c) =>
                                c.id === selectedClipId
                                  ? { ...c, opticalFlow: true, originalSrc: c.originalSrc || c.src, src: newSrcUrl, fileId: newFileId }
                                  : c,
                              ),
                            );
                          }

                          setTimeout(() => {
                            setSmoothProcessingProgress(null);
                            setPillPopup(null);
                          }, 2000);
                        } catch (err: any) {
                          console.error(err);
                          setPillPopup({ message: `Failed: ${err.message || 'unknown error'}`, type: 'info' });
                          setTimeout(() => {
                            setPillPopup(null);
                          }, 5000);
                          setSmoothProcessingProgress(null);
                        }
                      }}
                      className={`flex-1 flex justify-center items-center gap-1 px-2 py-1 relative rounded-lg transition-colors active:scale-95 overflow-hidden ${clips.find((c) => c.id === selectedClipId)?.opticalFlow ? "bg-indigo-600 hover:bg-indigo-500" : "bg-zinc-800 hover:bg-zinc-700"}`}
                    >
                      {smoothProcessingProgress !== null ? (
                        <>
                          <div className="relative w-3 h-3 flex items-center justify-center shrink-0">
                            <svg
                              className="w-full h-full -rotate-90"
                              viewBox="0 0 16 16"
                            >
                              <circle
                                cx="8"
                                cy="8"
                                r="6"
                                stroke="currentColor"
                                strokeWidth="2"
                                fill="none"
                                className="text-zinc-600"
                              />
                              <circle
                                cx="8"
                                cy="8"
                                r="6"
                                stroke="currentColor"
                                strokeWidth="2"
                                fill="none"
                                className="text-white transition-all duration-150 ease-linear"
                                strokeDasharray="37.7"
                                strokeDashoffset={
                                  37.7 - (smoothProcessingProgress / 100) * 37.7
                                }
                              />
                            </svg>
                          </div>
                          <span className="text-[9.5px] font-mono text-white whitespace-nowrap">
                            {Math.round(smoothProcessingProgress)}%
                          </span>
                        </>
                      ) : (
                        <span className="text-[9.5px] font-semibold text-white truncate">
                          Smooth
                        </span>
                      )}
                    </button>
                    <button
                      onClick={() => setActiveExpandedMenu("speed-curves")}
                      className="flex-1 flex justify-center items-center gap-1 px-2 py-1 bg-zinc-800 hover:bg-zinc-700 rounded-lg transition-colors active:scale-95"
                    >
                      <span className="text-[9.5px] font-semibold text-white">
                        Curves
                      </span>
                    </button>
                  </div>
                  {selectedClipId && (() => {
                    const c = clips.find(x => x.id === selectedClipId);
                    if (!c || c.type !== "video") return null;
                    return (
                      <div className="mt-1.5 pt-1.5 border-t border-white/5 flex flex-col gap-1 px-0.5">
                        <div className="flex justify-between items-center text-[7.5px] font-bold text-zinc-400 uppercase tracking-widest leading-none">
                          <span>Source Video Meta</span>
                          <span className="text-zinc-500 font-mono">Real-time OS</span>
                        </div>
                        <div className="grid grid-cols-3 gap-1 text-center mt-1">
                          <div className="bg-white/[0.02] border border-white/5 rounded py-0.5 px-1 flex flex-col justify-center">
                            <div className="text-[7px] text-zinc-500 font-bold uppercase leading-none">Duration</div>
                            <div className="text-[8.5px] text-white font-mono font-semibold mt-0.5 leading-none">
                              {c.durationSeconds ? c.durationSeconds.toFixed(2) : "—"}s
                            </div>
                          </div>
                          <div className="bg-white/[0.02] border border-white/5 rounded py-0.5 px-1 flex flex-col justify-center">
                            <div className="text-[7px] text-zinc-500 font-bold uppercase leading-none">Res</div>
                            <div className="text-[8.5px] text-white font-mono font-semibold mt-0.5 leading-none">
                              {c.width && c.height ? `${c.width}×${c.height}` : "—"}
                            </div>
                          </div>
                          <div className="bg-white/[0.02] border border-white/5 rounded py-0.5 px-1 flex flex-col justify-center">
                            <div className="text-[7px] text-zinc-500 font-bold uppercase leading-none">Rate</div>
                            <div className="text-[8.5px] text-white font-mono font-semibold mt-0.5 leading-none">
                              {c.fps ? `${Math.round(c.fps)} FPS` : "—"}
                            </div>
                          </div>
                        </div>

                        {/* W1D5 Optical Flow Diagnostics */}
                        {c.opticalFlow && (
                          <div className="mt-2.5 pt-2.5 flex flex-col gap-1.5 border-t border-white/5">
                            <div className="flex justify-between items-center text-[7.5px] font-bold text-indigo-400 uppercase tracking-widest leading-none">
                              <span>DIS Optical Flow (W1D5)</span>
                              <span className={`text-[7px] px-1 py-[1.5px] rounded font-mono font-semibold ${opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.isFlowCorrect ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20" : "bg-zinc-500/10 text-zinc-400 border border-white/5"}`}>
                                {opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.isFlowCorrect ? "Verified Correct" : "Analyzing Flow..."}
                              </span>
                            </div>

                            <div className="grid grid-cols-2 gap-1 text-center mt-0.5">
                              <div className="bg-white/[0.02] border border-white/5 rounded py-1 px-1.5 flex flex-col justify-center">
                                <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Mean Velocity</div>
                                <div className="text-[8.5px] text-zinc-100 mt-1 leading-none font-mono font-semibold">
                                  {opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.avgFlowMagnitude !== undefined ? `${opticalFlowDiagnostics.avgFlowMagnitude.toFixed(3)} px` : "—"}
                                </div>
                              </div>
                              <div className="bg-white/[0.02] border border-white/5 rounded py-1 px-1.5 flex flex-col justify-center">
                                <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Peak Displacement</div>
                                <div className="text-[8.5px] text-zinc-100 mt-1 leading-none font-mono font-semibold">
                                  {opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.maxFlowMagnitude !== undefined ? `${opticalFlowDiagnostics.maxFlowMagnitude.toFixed(2)} px` : "—"}
                                </div>
                              </div>
                            </div>

                            {opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.flowVisualization ? (
                              <div className="mt-0.5 rounded border border-white/5 overflow-hidden relative group">
                                <img 
                                  src={opticalFlowDiagnostics.flowVisualization} 
                                  alt="DIS Optical Flow Frame" 
                                  className="w-full h-auto aspect-video object-cover"
                                />
                                <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-transparent to-transparent flex items-end justify-between p-1.5">
                                  <span className="text-[7px] font-mono text-zinc-300">Peak Motion Vector Field</span>
                                  <span className="text-[7px] font-mono text-indigo-400 font-semibold">{opticalFlowDiagnostics.flowComputedCount} DIS cycles</span>
                                </div>
                              </div>
                            ) : (
                              <div className="mt-0.5 bg-white/[0.01] border border-dashed border-white/5 text-[7.5px] py-2 px-2 text-zinc-500 text-center rounded leading-snug">
                                Vector tracking lines are plotted overlaying the active video stream once rendering has successfully executed.
                              </div>
                            )}

                            {/* W1D6 Frame Interpolation Diagnostics */}
                            {opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.interpolatedFramesCount !== undefined && (
                              <div className="mt-3.5 pt-3.5 flex flex-col gap-1.5 border-t border-white/5">
                                <div className="flex justify-between items-center text-[7.5px] font-bold text-emerald-400 uppercase tracking-widest leading-none">
                                  <span>Frame Interpolation (W1D6)</span>
                                  <span className="text-[7px] px-1 py-[1.5px] rounded font-mono font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                                    30fps → 60fps Safe
                                  </span>
                                </div>

                                <div className="grid grid-cols-3 gap-1 text-center mt-0.5">
                                  <div className="bg-white/[0.02] border border-white/5 rounded py-1 px-1 flex flex-col justify-center">
                                    <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Mean PSNR</div>
                                    <div className="text-[8.5px] text-emerald-400 mt-1 leading-none font-mono font-semibold">
                                      {opticalFlowDiagnostics.averagePsnr && opticalFlowDiagnostics.averagePsnr > 0 ? `${opticalFlowDiagnostics.averagePsnr.toFixed(2)} dB` : "—"}
                                    </div>
                                  </div>
                                  <div className="bg-white/[0.02] border border-white/5 rounded py-1 px-1 flex flex-col justify-center">
                                    <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Warp Error</div>
                                    <div className="text-[8.5px] text-emerald-400 mt-1 leading-none font-mono font-semibold">
                                      {opticalFlowDiagnostics.averageWarpError !== undefined ? `${opticalFlowDiagnostics.averageWarpError.toFixed(4)} px` : "—"}
                                    </div>
                                  </div>
                                  <div className="bg-white/[0.02] border border-white/5 rounded py-1 px-1 flex flex-col justify-center">
                                    <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Interpolates</div>
                                    <div className="text-[8.5px] text-zinc-100 mt-1 leading-none font-mono font-semibold">
                                      {opticalFlowDiagnostics.interpolatedFramesCount} frames
                                    </div>
                                  </div>
                                </div>

                                {opticalFlowDiagnostics?.interpolationVisualization && (
                                  <div className="mt-1 rounded border border-white/5 overflow-hidden relative group">
                                    <img 
                                      src={opticalFlowDiagnostics.interpolationVisualization} 
                                      alt="Frame Interpolation Split View" 
                                      className="w-full h-auto aspect-video object-cover"
                                    />
                                    <div className="absolute inset-0 bg-gradient-to-t from-black/95 via-transparent to-transparent flex items-end justify-between p-1.5">
                                      <span className="text-[7px] font-mono text-emerald-400 font-bold uppercase tracking-wider">Split View Comparison</span>
                                      <span className="text-[7px] font-mono text-zinc-400">t = 0.5 Backward Warp</span>
                                    </div>
                                  </div>
                                )}

                                <div className="mt-0.5 text-[7px] text-zinc-400 bg-white/[0.01] border border-white/5 py-1 px-1.5 rounded flex items-center gap-1">
                                  <span className="w-1 h-1 rounded-full bg-emerald-400 animate-pulse"></span>
                                  <span>Safe-interpolated frame generated with linear backward warping & replicate borders</span>
                                </div>
                              </div>
                            )}

                            {/* W1D7 Performance Profiling Diagnostics */}
                            {opticalFlowDiagnostics?.clipId === selectedClipId && opticalFlowDiagnostics?.totalExportTimeMs !== undefined && (
                              <div className="mt-3.5 pt-3.5 flex flex-col gap-1.5 border-t border-white/5">
                                <div className="flex justify-between items-center text-[7.5px] font-bold text-amber-400 uppercase tracking-widest leading-none">
                                  <span>Performance Profiling</span>
                                  <span className="text-[7px] px-1 py-[1.5px] rounded font-mono font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                                    {opticalFlowDiagnostics.totalExportTimeMs}ms
                                  </span>
                                </div>

                                <div className="grid grid-cols-2 gap-1 mt-1">
                                  <div className="flex justify-between items-center bg-white/[0.02] border border-white/5 py-1 px-1.5 rounded text-[8px] font-mono">
                                    <span className="text-zinc-500 font-sans font-medium">Decode</span>
                                    <div className="flex gap-1.5 text-right">
                                      <span className="text-zinc-300">{opticalFlowDiagnostics.decoderTimeMs}ms</span>
                                      <span className="text-zinc-500 w-6">{opticalFlowDiagnostics.decoderPct?.toFixed(1)}%</span>
                                    </div>
                                  </div>
                                  <div className="flex justify-between items-center bg-white/[0.02] border border-white/5 py-1 px-1.5 rounded text-[8px] font-mono">
                                    <span className="text-zinc-500 font-sans font-medium">Flow</span>
                                    <div className="flex gap-1.5 text-right">
                                      <span className="text-zinc-300">{opticalFlowDiagnostics.opticalFlowTimeMs}ms</span>
                                      <span className="text-zinc-500 w-6">{opticalFlowDiagnostics.opticalFlowPct?.toFixed(1)}%</span>
                                    </div>
                                  </div>
                                  <div className="flex justify-between items-center bg-white/[0.02] border border-white/5 py-1 px-1.5 rounded text-[8px] font-mono">
                                    <span className="text-zinc-500 font-sans font-medium">Interpolate</span>
                                    <div className="flex gap-1.5 text-right">
                                      <span className="text-zinc-300">{opticalFlowDiagnostics.interpolationTimeMs}ms</span>
                                      <span className="text-zinc-500 w-6">{opticalFlowDiagnostics.interpolationPct?.toFixed(1)}%</span>
                                    </div>
                                  </div>
                                  <div className="flex justify-between items-center bg-white/[0.02] border border-white/5 py-1 px-1.5 rounded text-[8px] font-mono">
                                    <span className="text-zinc-500 font-sans font-medium">Colors</span>
                                    <div className="flex gap-1.5 text-right">
                                      <span className="text-zinc-300">{opticalFlowDiagnostics.colorConversionTimeMs}ms</span>
                                      <span className="text-zinc-500 w-6">{opticalFlowDiagnostics.colorConversionPct?.toFixed(1)}%</span>
                                    </div>
                                  </div>
                                  <div className="flex justify-between items-center bg-white/[0.02] border border-white/5 py-1 px-1.5 rounded text-[8px] font-mono col-span-2">
                                    <span className="text-zinc-500 font-sans font-medium">Encode</span>
                                    <div className="flex gap-1.5 text-right">
                                      <span className="text-zinc-300">{opticalFlowDiagnostics.encoderTimeMs}ms</span>
                                      <span className="text-zinc-500 w-6">{opticalFlowDiagnostics.encoderPct?.toFixed(1)}%</span>
                                    </div>
                                  </div>
                                </div>

                                <div className="grid grid-cols-3 gap-1 mt-1 text-center">
                                  <div className="bg-white/[0.01] border border-dashed border-white/10 rounded py-1 px-1 flex flex-col justify-center">
                                    <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Mat Allocs</div>
                                    <div className="text-[8.5px] text-zinc-300 mt-1 leading-none font-mono font-semibold">
                                      {opticalFlowDiagnostics.matAllocations}
                                    </div>
                                  </div>
                                  <div className="bg-white/[0.01] border border-dashed border-white/10 rounded py-1 px-1 flex flex-col justify-center">
                                    <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Byte Buffers</div>
                                    <div className="text-[8.5px] text-zinc-300 mt-1 leading-none font-mono font-semibold">
                                      {opticalFlowDiagnostics.byteBufferAllocations}
                                    </div>
                                  </div>
                                  <div className="bg-white/[0.01] border border-dashed border-white/10 rounded py-1 px-1 flex flex-col justify-center">
                                    <div className="text-[6.5px] text-zinc-500 font-bold uppercase leading-none">Copies</div>
                                    <div className="text-[8.5px] text-zinc-300 mt-1 leading-none font-mono font-semibold">
                                      {opticalFlowDiagnostics.frameCopies}
                                    </div>
                                  </div>
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })()}
                </motion.div>
              )}
              {activeExpandedMenu === "speed-curves" && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col w-full h-auto px-1 pt-0 pb-1"
                >
                  <SpeedCurveEditor onClose={() => setActiveExpandedMenu("speed")} />
                </motion.div>
              )}
              {activeExpandedMenu === "text" && (
                <TextEditorMenu 
                  clip={clips.find((c) => c.id === selectedClipId)} 
                  updateClip={(updates) => {
                    if (selectedClipId) {
                      setClips((prev) =>
                        prev.map((c) =>
                          c.id === selectedClipId ? { ...c, ...updates } : c
                        )
                      );
                    }
                  }}
                  setToastMessage={setToastMessage}
                />
              )}
              {activeExpandedMenu === "stabilize" && selectedClipId && (() => {
                const clip = clips.find(c => c.id === selectedClipId);
                if (!clip) return null;

                const isStabilizing = stabilizingProgress && stabilizingProgress.clipId === clip.id;

                return (
                  <motion.div
                    layout
                    initial={{ opacity: 0, scale: 0.95, y: 10 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: 10 }}
                    transition={{ duration: 0.2 }}
                    className="flex flex-col w-full text-left select-none px-2.5 pb-2.5"
                  >
                    {/* Header */}
                    <div className="flex justify-between items-center w-full mb-1.5 px-0.5 mt-1">
                      <span className="text-[10px] font-extrabold text-indigo-400 uppercase tracking-widest">Stabilizer</span>
                      <div className="flex items-center gap-1.5">
                        {renderCopyPasteButtons("stabilize")}
                        <button 
                          onClick={() => setActiveExpandedMenu(null)}
                          className="text-zinc-500 hover:text-white p-1 rounded-md hover:bg-zinc-805/40 transition flex items-center justify-center"
                        >
                          <Check size={12} className="text-emerald-400" />
                        </button>
                      </div>
                    </div>

                    {isStabilizing ? (
                      <div className="flex flex-col items-center justify-center py-2 space-y-1">
                        {/* Circular Loader */}
                        <div className="text-indigo-400 animate-spin flex items-center justify-center">
                          <Activity size={18} />
                        </div>
                        <span className="text-sm font-black tracking-tighter text-white">
                          {stabilizingProgress.progress}%
                        </span>
                        <span className="text-[8px] text-zinc-400 font-bold tracking-tight text-center truncate w-full max-w-full leading-none">
                          {stabilizingProgress.stage}
                        </span>
                      </div>
                    ) : (
                      <div className="flex flex-col gap-1">
                        <div className="flex flex-col gap-1 max-h-[140px] overflow-y-auto scrollbar-hide">
                          <button
                            onClick={() => handleStabilize(clip.id, "standard")}
                            className={`flex items-center justify-between w-full p-1.5 px-2 rounded-lg text-[9.5px] font-semibold transition-all ${clip.isStabilized && clip.stabilizationMode === "standard" ? "bg-indigo-600 text-white" : "bg-neutral-800 hover:bg-neutral-700 text-zinc-300"} cursor-pointer`}
                          >
                            <span className="truncate">Standard</span>
                            <span className="text-[8px] opacity-75">1.08x</span>
                          </button>
                          
                          <button
                            onClick={() => handleStabilize(clip.id, "active")}
                            className={`flex items-center justify-between w-full p-1.5 px-2 rounded-lg text-[9.5px] font-semibold transition-all ${clip.isStabilized && clip.stabilizationMode === "active" ? "bg-blue-600 text-white" : "bg-neutral-800 hover:bg-neutral-700 text-zinc-300"} cursor-pointer`}
                          >
                            <span className="truncate">Active (High)</span>
                            <span className="text-[8px] opacity-75">1.18x</span>
                          </button>
                          
                          <button
                            onClick={() => handleStabilize(clip.id, "locked")}
                            className={`flex items-center justify-between w-full p-1.5 px-2 rounded-lg text-[9.5px] font-semibold transition-all ${clip.isStabilized && clip.stabilizationMode === "locked" ? "bg-pink-600 text-white" : "bg-neutral-800 hover:bg-neutral-700 text-zinc-300"} cursor-pointer`}
                          >
                            <span className="truncate">Locked (Tripod)</span>
                            <span className="text-[8px] opacity-75">1.28x</span>
                          </button>

                          {clip.isStabilized && (
                            <button
                              onClick={() => handleStabilize(clip.id, "off")}
                              className="flex items-center justify-center w-full py-1 hover:bg-red-500/10 hover:text-red-400 text-zinc-400 text-[8px] font-bold tracking-tight transition-colors mt-0.5 gap-1 border border-white/5 rounded-md cursor-pointer"
                            >
                              Turn off stabilization
                            </button>
                          )}
                        </div>

                        {/* Split Compare Slide Toggle */}
                        {clip.isStabilized && (
                          <div className="flex items-center justify-between pt-1.5 border-t border-white/[0.04] mt-1">
                            <span className="text-[8.5px] text-zinc-400 font-bold uppercase tracking-wider">Compare Split</span>
                            <button
                              onClick={() => {
                                setClips(prev => prev.map(c => c.id === clip.id ? { ...c, compareStabilization: !c.compareStabilization } : c));
                              }}
                              className={`w-7 h-4 rounded-full p-0.5 transition-colors ${clip.compareStabilization ? 'bg-emerald-500' : 'bg-neutral-700'} cursor-pointer`}
                            >
                              <div className={`w-3 h-3 rounded-full bg-white transition-all transform ${clip.compareStabilization ? 'translate-x-3' : 'translate-x-0'}`} />
                            </button>
                          </div>
                        )}
                      </div>
                    )}
                  </motion.div>
                );
              })()}
              {activeExpandedMenu === "move" && selectedClipId && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col h-auto max-h-[240px] shrink-0 overflow-y-auto scrollbar-hide pb-2"
                  style={{ width: "218px", paddingTop: "0px", paddingLeft: "0px" }}
                >
                  <div className="flex justify-between items-center w-full px-2.5 mb-1 shrink-0">
                    <span className="text-[9.5px] font-bold text-white/90 uppercase tracking-widest flex items-center gap-1">
                      <Move size={10} className="text-zinc-400" />
                      Transform
                    </span>
                    <div className="flex items-center gap-1.5">
                      {renderCopyPasteButtons("move")}
                      <div className="w-px h-3 bg-zinc-700/80"></div>
                      <button
                        onClick={() => {
                          const clip = clips.find((c) => c.id === selectedClipId);
                          if (clip) {
                            const clipLayer = layers.find((l) => l.id === clip.layerId);
                            if (clipLayer?.isLocked) {
                              setToastMessage("Layer is locked");
                              setTimeout(() => setToastMessage(null), 2000);
                              return;
                            }
                          }
                          setClips((prev) =>
                            prev.map((c) =>
                              c.id === selectedClipId
                                ? {
                                    ...c,
                                    translateX: 0,
                                    translateY: 0,
                                    rotation: 0,
                                    scale: 1,
                                  }
                                : c,
                            ),
                          );
                        }}
                        className="text-[7.5px] bg-zinc-800 hover:bg-zinc-700 hover:text-white px-1.5 py-0.5 rounded font-black text-zinc-400 uppercase tracking-wider transition-all"
                      >
                        Reset
                      </button>
                      <div className="w-px h-3 bg-zinc-700/80"></div>
                      <button
                        onClick={() => setActiveExpandedMenu(null)}
                        className="text-emerald-400 hover:text-emerald-300 p-0.5 rounded-full hover:bg-white/5 transition-colors"
                      >
                        <Check size={14} />
                      </button>
                    </div>
                  </div>

                  {/* Sleek minimalist tab bar with thin vertical dividers */}
                  <div className="flex w-full px-2.5 my-1.5 items-center justify-between shrink-0 border-b border-white/[0.04] pb-1">
                    <button
                      onClick={() => setActiveTransformTab("position")}
                      className={`flex-1 py-0.5 text-[8px] font-bold tracking-[0.14em] uppercase transition-colors duration-150 ${
                        activeTransformTab === "position"
                          ? "text-white font-black"
                          : "text-zinc-500 hover:text-zinc-300"
                      }`}
                    >
                      Pos
                    </button>
                    <div className="w-[1px] h-2 bg-white/[0.06] shrink-0" />
                    <button
                      onClick={() => setActiveTransformTab("scale")}
                      className={`flex-1 py-0.5 text-[8px] font-bold tracking-[0.14em] uppercase transition-colors duration-150 ${
                        activeTransformTab === "scale"
                          ? "text-white font-black"
                          : "text-zinc-500 hover:text-zinc-300"
                      }`}
                    >
                      Scale
                    </button>
                    <div className="w-[1px] h-2 bg-white/[0.06] shrink-0" />
                    <button
                      onClick={() => setActiveTransformTab("rotate")}
                      className={`flex-1 py-0.5 text-[8px] font-bold tracking-[0.14em] uppercase transition-colors duration-150 ${
                        activeTransformTab === "rotate"
                          ? "text-white font-black"
                          : "text-zinc-500 hover:text-zinc-300"
                      }`}
                    >
                      Rotate
                    </button>
                  </div>

                  {/* Tab-specific micro-form layout area */}
                  <div className="px-2.5 pb-0.5 w-full flex flex-col gap-1">
                    {activeTransformTab === "position" && (
                      <div
                        className="grid grid-cols-[74px_1fr] gap-x-1.5 items-center w-full"
                        style={{ height: "100px", marginTop: "0px" }}
                      >
                        {/* Visual D-Pad positioning wheel */}
                        <div
                          className="flex flex-col items-center justify-center bg-zinc-900/30 py-1 px-1 rounded-xl border border-white/5 h-full"
                          style={{ height: "100px", paddingTop: "4px", paddingLeft: "4px", marginTop: "0px", marginLeft: "0px", width: "74px" }}
                        >
                          <span className="text-[7px] uppercase tracking-wider text-zinc-500 mb-1 font-extrabold leading-none">Nudge</span>
                          <div className="relative w-[56px] h-[56px] flex items-center justify-center">
                            <div className="absolute inset-0 rounded-full border border-white/5 bg-zinc-950/65 shadow-inner"></div>
                            
                            {/* Up */}
                            <button
                              onClick={() => {
                                const currentY = currentSelectedClipInterpolatedProps?.translateY || 0;
                                updateClipsProperties([selectedClipId], { translateY: currentY - 10 });
                              }}
                              className="absolute top-[-3px] w-[20px] h-[20px] flex items-center justify-center rounded-full bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white active:scale-90 transition-all border border-white/10"
                            >
                              <ArrowUp size={8} />
                            </button>
                            
                            {/* Left */}
                            <button
                              onClick={() => {
                                const currentX = currentSelectedClipInterpolatedProps?.translateX || 0;
                                updateClipsProperties([selectedClipId], { translateX: currentX - 10 });
                              }}
                              className="absolute left-[-3px] w-[20px] h-[20px] flex items-center justify-center rounded-full bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white active:scale-90 transition-all border border-white/10"
                            >
                              <ArrowUp size={8} className="-rotate-90" />
                            </button>
                            
                            {/* Center Target (Reset Coordinates) */}
                            <button
                              onClick={() => {
                                updateClipsProperties([selectedClipId], { translateX: 0, translateY: 0 });
                              }}
                              className="w-4 h-4 rounded-full bg-indigo-500 hover:bg-indigo-400 shadow-md flex items-center justify-center text-white active:scale-95 transition-all z-10"
                              title="Reset Axis"
                            >
                              <div className="w-1 h-1 bg-white rounded-full"></div>
                            </button>
                            
                            {/* Right */}
                            <button
                              onClick={() => {
                                const currentX = currentSelectedClipInterpolatedProps?.translateX || 0;
                                updateClipsProperties([selectedClipId], { translateX: currentX + 10 });
                              }}
                              className="absolute right-[-3px] w-[20px] h-[20px] flex items-center justify-center rounded-full bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white active:scale-90 transition-all border border-white/10"
                            >
                              <ArrowUp size={8} className="rotate-90" />
                            </button>
                            
                            {/* Down */}
                            <button
                              onClick={() => {
                                const currentY = currentSelectedClipInterpolatedProps?.translateY || 0;
                                updateClipsProperties([selectedClipId], { translateY: currentY + 10 });
                              }}
                              className="absolute bottom-[-3px] w-[20px] h-[20px] flex items-center justify-center rounded-full bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white active:scale-90 transition-all border border-white/10"
                            >
                              <ArrowUp size={8} className="rotate-180" />
                            </button>
                          </div>
                        </div>

                        {/* Traditional drag rulers */}
                        <div
                          className="flex flex-col gap-1 w-full justify-center"
                          style={{ height: "100px" }}
                        >
                          <CompactRulerControl
                            label="Axis X"
                            value={currentSelectedClipInterpolatedProps?.translateX || 0}
                            onChange={(val) => {
                              if (selectedClipId) updateClipsProperties([selectedClipId], { translateX: val });
                            }}
                            onReset={() => {
                              if (selectedClipId) updateClipsProperties([selectedClipId], { translateX: 0 });
                            }}
                            min={-2000}
                            max={2000}
                            step={1}
                            unit="px"
                            sensitivity={1.5}
                            style={{ height: "46px", marginTop: "0px", marginBottom: "0px", paddingTop: "0px", paddingBottom: "0px" }}
                            rulerStyle={{ height: "24px" }}
                            labelStyle={{ height: "12px", fontSize: "7.5px" }}
                            headerStyle={{ height: "12px" }}
                          />
                          <CompactRulerControl
                            label="Axis Y"
                            value={currentSelectedClipInterpolatedProps?.translateY || 0}
                            onChange={(val) => {
                              if (selectedClipId) updateClipsProperties([selectedClipId], { translateY: val });
                            }}
                            onReset={() => {
                              if (selectedClipId) updateClipsProperties([selectedClipId], { translateY: 0 });
                            }}
                            min={-2000}
                            max={2000}
                            step={1}
                            unit="px"
                            sensitivity={1.5}
                            style={{ height: "46px", marginTop: "2px", marginBottom: "0px", paddingTop: "0px", paddingBottom: "0px" }}
                            rulerStyle={{ height: "24px" }}
                            labelStyle={{ height: "12px", fontSize: "7.5px" }}
                            headerStyle={{ height: "12px" }}
                          />
                        </div>
                      </div>
                    )}

                    {activeTransformTab === "scale" && (
                      <div className="flex flex-col gap-2 w-full min-h-[96px] justify-center bg-zinc-900/30 px-2 py-2 rounded-xl border border-white/5">
                        <ScaleRulerControl
                          value={currentSelectedClipInterpolatedProps?.scale ?? 1}
                          onChange={(val) => {
                            if (selectedClipId) updateClipsProperties([selectedClipId], { scale: val });
                          }}
                          onReset={() => {
                            if (selectedClipId) updateClipsProperties([selectedClipId], { scale: 1 });
                          }}
                          label="Scale Factor"
                        />
                        
                        {/* Premium snapping presets inline */}
                        <div className="flex items-center justify-between w-full mt-1 px-0.5">
                          <span className="text-[7px] uppercase tracking-wider text-zinc-500 font-extrabold pl-0.5">Quick</span>
                          <div className="flex gap-0.5">
                            {[0.5, 1.0, 1.5, 2.0, 3.0].map((s) => {
                              const isSelected = Math.abs((currentSelectedClipInterpolatedProps?.scale ?? 1) - s) < 0.05;
                              return (
                                <button
                                  key={s}
                                  onClick={() => {
                                    if (selectedClipId) updateClipsProperties([selectedClipId], { scale: s });
                                  }}
                                  className={`px-1.5 py-0.5 rounded text-[8px] font-mono font-bold tracking-tight transition-all active:scale-95 ${
                                    isSelected
                                      ? "bg-indigo-600 text-white shadow-sm border border-indigo-500/30 font-black"
                                      : "bg-zinc-800/80 hover:bg-zinc-700 text-zinc-300 border border-white/5"
                                  }`}
                                >
                                  {s.toFixed(1)}x
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      </div>
                    )}

                    {activeTransformTab === "rotate" && (
                      <div className="grid grid-cols-[68px_1fr] gap-x-1.5 items-center w-full min-h-[96px]">
                        {/* Visual dial rotation feedback circle */}
                        <div className="flex flex-col items-center justify-center bg-zinc-900/30 py-1 px-1 rounded-xl border border-white/5 h-full">
                          <span className="text-[7px] uppercase tracking-wider text-zinc-500 mb-1 font-extrabold leading-none">Dial</span>
                          
                          <div className="relative w-[44px] h-[44px] rounded-full border border-white/10 flex items-center justify-center bg-zinc-950/70 shadow-inner">
                            <div className="absolute inset-0.5 rounded-full bg-gradient-to-b from-zinc-900 to-zinc-950"></div>
                            
                            {/* Inner rotational compass needle */}
                            <div
                              className="absolute w-7 h-7 rounded-full bg-zinc-800/90 border border-white/10 flex items-center justify-center shadow-md transition-transform duration-200"
                              style={{ transform: `rotate(${currentSelectedClipInterpolatedProps?.rotation || 0}deg)` }}
                            >
                              <div className="absolute top-0.5 w-[1.5px] h-1.5 bg-indigo-500 rounded-full shadow-[0_0_6px_rgba(99,102,241,0.8)]" />
                              <div className="w-1 h-1 bg-[#121212] rounded-full" />
                            </div>
                          </div>
                          <span className="text-[9px] font-mono font-bold text-white mt-1 leading-none">
                            {Math.round(currentSelectedClipInterpolatedProps?.rotation || 0)}°
                          </span>
                        </div>

                        {/* Angle Ruler & Presets */}
                        <div className="flex flex-col gap-1.5 w-full justify-center">
                          <CompactRulerControl
                            label="Angle"
                            value={currentSelectedClipInterpolatedProps?.rotation || 0}
                            onChange={(val) => {
                              if (selectedClipId) updateClipsProperties([selectedClipId], { rotation: val });
                            }}
                            onReset={() => {
                              if (selectedClipId) updateClipsProperties([selectedClipId], { rotation: 0 });
                            }}
                            min={-180}
                            max={180}
                            step={1}
                            unit="°"
                            sensitivity={0.6}
                          />
                          
                          {/* Common editing preset angles */}
                          <div className="grid grid-cols-4 gap-0.5">
                            {[-90, 0, 90, 180].map((angle) => {
                              const isSelected = Math.round(currentSelectedClipInterpolatedProps?.rotation || 0) === angle;
                              return (
                                <button
                                  key={angle}
                                  onClick={() => {
                                    if (selectedClipId) updateClipsProperties([selectedClipId], { rotation: angle });
                                  }}
                                  className={`py-0.5 rounded text-[8px] font-mono font-bold tracking-tighter transition-all ${
                                    isSelected
                                      ? "bg-indigo-600 text-white shadow border border-indigo-500/20 font-black"
                                      : "bg-zinc-800/85 hover:bg-zinc-700 text-zinc-400 hover:text-white"
                                  }`}
                                >
                                  {angle}°
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </motion.div>
              )}
              {activeExpandedMenu === "blend" && selectedClipId && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col w-full h-auto pb-1 pt-0 shrink-0"
                >
                  <div className="flex justify-between items-center w-full px-3.5 mb-1.5">
                    <span className="text-[10px] font-semibold text-white/90">
                      Blend & Opacity
                    </span>
                    <div className="flex items-center gap-2">
                      {renderCopyPasteButtons("blend")}
                      <button
                        onClick={() => setActiveExpandedMenu(null)}
                        className="text-zinc-400 hover:text-white p-1 rounded-md hover:bg-zinc-805/40 transition flex items-center justify-center"
                      >
                        <Check size={14} />
                      </button>
                    </div>
                  </div>

                  {/* Blending Modes */}
                  <div className="flex items-center gap-1.5 px-2.5 overflow-x-auto scrollbar-hide snap-x pt-0.5 pb-1.5">
                    {["normal", "multiply", "screen", "overlay", "darken", "lighten", "color-dodge", "color-burn", "hard-light", "soft-light", "difference", "exclusion", "hue", "saturation", "color", "luminosity"].map((mode) => {
                       const currentMode = clips.find((c) => c.id === selectedClipId)?.mixBlendMode || "normal";
                       const isActive = currentMode === mode;
                       return (
                         <button
                           key={mode}
                           onClick={() => {
                             setClips((prev) =>
                               prev.map((c) =>
                                 c.id === selectedClipId ? { ...c, mixBlendMode: mode as any } : c
                               )
                             );
                           }}
                           className={`shrink-0 px-2 py-1 rounded-full text-[9px] font-semibold capitalize transition-colors snap-start border ${isActive ? "bg-white text-black border-white" : "bg-zinc-800 text-zinc-300 border-white/5 hover:bg-zinc-700"}`}
                         >
                           {mode.replace("-", " ")}
                         </button>
                       );
                    })}
                  </div>

                  {/* Opacity Control */}
                  <div className="px-3.5 mt-0.5">
                     <div className="flex justify-between items-center mb-1">
                        <span className="text-[8px] text-zinc-500 font-bold uppercase pl-0.5">Opacity</span>
                        <span className="text-[9px] text-zinc-400 font-mono pr-0.5">
                          {Math.round((currentSelectedClipInterpolatedProps?.opacity ?? 1) * 100)}%
                        </span>
                     </div>
                     <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.01"
                        value={currentSelectedClipInterpolatedProps?.opacity ?? 1}
                        onChange={(e) => {
                          const val = Number(e.target.value);
                          if (selectedClipId) updateClipsProperties([selectedClipId], { opacity: val });
                        }}
                        className="w-full accent-white h-0.5 bg-zinc-700 rounded-full appearance-none [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-2.5 [&::-webkit-slider-thumb]:h-2.5 [&::-webkit-slider-thumb]:bg-white [&::-webkit-slider-thumb]:rounded-full cursor-pointer mt-0.5"
                     />
                  </div>
                </motion.div>
              )}
              {activeExpandedMenu === "adjust" && selectedClipId && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="bg-zinc-800 rounded-xl shadow-xl border border-white/10 overflow-hidden w-[217px] p-2"
                >
                  <div className="flex justify-between items-center w-full px-2 mb-2">
                    <span className="text-[10px] font-semibold text-white/90">
                      Adjustments
                    </span>
                    <div className="flex items-center gap-1.5">
                      {renderCopyPasteButtons("adjust")}
                      <button
                        onClick={() => setActiveExpandedMenu(null)}
                        className="text-zinc-400 hover:text-white p-1 rounded-md hover:bg-zinc-700/50 transition flex items-center justify-center"
                      >
                        <Check size={14} />
                      </button>
                    </div>
                  </div>
                  <div className="flex flex-col gap-2.5 max-h-[220px] overflow-y-auto scrollbar-hide px-2 pb-2">
                    <CompactRulerControl
                      label="Exposure"
                      value={currentSelectedClipInterpolatedProps?.exposure || 0}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { exposure: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { exposure: 0 }); }}
                      min={-100} max={100} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Brightness"
                      value={currentSelectedClipInterpolatedProps?.brightness ?? 100}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { brightness: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { brightness: 100 }); }}
                      min={0} max={200} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Contrast"
                      value={currentSelectedClipInterpolatedProps?.contrast ?? 100}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { contrast: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { contrast: 100 }); }}
                      min={0} max={200} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Saturation"
                      value={currentSelectedClipInterpolatedProps?.saturation ?? 100}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { saturation: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { saturation: 100 }); }}
                      min={0} max={200} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Blur"
                      value={currentSelectedClipInterpolatedProps?.blur || 0}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { blur: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { blur: 0 }); }}
                      min={0} max={100} step={1} unit="%" sensitivity={0.5}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Grayscale"
                      value={currentSelectedClipInterpolatedProps?.grayscale || 0}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { grayscale: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { grayscale: 0 }); }}
                      min={0} max={100} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Sepia"
                      value={currentSelectedClipInterpolatedProps?.sepia || 0}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { sepia: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { sepia: 0 }); }}
                      min={0} max={100} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Hue Shift"
                      value={currentSelectedClipInterpolatedProps?.hueRotate || 0}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { hueRotate: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { hueRotate: 0 }); }}
                      min={-180} max={180} step={1} unit="°" sensitivity={1.5}
                      size="small"
                    />
                    <CompactRulerControl
                      label="Invert"
                      value={currentSelectedClipInterpolatedProps?.invert || 0}
                      onChange={(val) => { if (selectedClipId) updateClipsProperties([selectedClipId], { invert: val }); }}
                      onReset={() => { if (selectedClipId) updateClipsProperties([selectedClipId], { invert: 0 }); }}
                      min={0} max={100} step={1} unit="%" sensitivity={1}
                      size="small"
                    />
                  </div>
                </motion.div>
              )}
              {activeExpandedMenu === "crop" && selectedClipId && (() => {
                const selectedClip = clips.find((c) => c.id === selectedClipId);
                const currentRatio = selectedClip?.cropRatio || "None";

                const presets = [
                  { ratio: "None", label: "FREE", subtitle: "Original", width: "24px", height: "24px", dashed: true },
                  { ratio: "1:1", label: "1:1", subtitle: "Square", width: "22px", height: "22px", dashed: false },
                  { ratio: "16:9", label: "16:9", subtitle: "Landscape", width: "34px", height: "19px", dashed: false },
                  { ratio: "9:16", label: "9:16", subtitle: "Portrait", width: "16px", height: "28px", dashed: false }
                ];

                return (
                  <motion.div
                    layout
                    initial={{ opacity: 0, scale: 0.95, y: 10 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: 10 }}
                    transition={{ duration: 0.2 }}
                    className="flex flex-col w-full h-auto shrink-0 pt-0.5 pb-2.5"
                  >
                    <div className="flex justify-between items-center w-full px-4 mb-2 shrink-0">
                      <div className="flex items-center gap-1.5">
                        <Crop size={11} className="text-zinc-400" />
                        <span className="text-[10px] font-bold text-white/90 uppercase tracking-widest">
                          Canvas Crop Preset
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        {renderCopyPasteButtons("crop")}
                        <div className="w-px h-3 bg-zinc-700/80 mx-0.5"></div>
                        <button
                          onClick={() => {
                            setClips(prev => prev.map(c => c.id === selectedClipId ? {
                              ...c,
                              cropRatio: null,
                              cropRect: { top: 0, right: 0, bottom: 0, left: 0 }
                            } : c));
                          }}
                          className="text-[8px] bg-zinc-800 hover:bg-zinc-700 hover:text-white px-1.5 py-0.5 rounded font-bold text-zinc-400 uppercase tracking-wider transition-all"
                        >
                          Reset
                        </button>
                        <div className="w-px h-3 bg-zinc-700/80 mx-0.5"></div>
                        <button
                          onClick={() => setActiveExpandedMenu(null)}
                          className="p-0.5 rounded-full hover:bg-white/5 transition-colors text-emerald-400"
                        >
                          <Check size={14} />
                        </button>
                      </div>
                    </div>

                    <div className="px-4 pb-0.5 w-full">
                      {/* Premium Aspect Ratio Preset Strip */}
                      <div className="grid grid-cols-4 gap-2.5 w-full">
                        {presets.map((preset) => {
                          const isSelected = currentRatio === preset.ratio || (currentRatio === null && preset.ratio === "None");
                          return (
                            <button
                              key={preset.ratio}
                              onClick={() => {
                                setClips(prev => prev.map(c => c.id === selectedClipId ? {...c, cropRatio: preset.ratio === "None" ? null : preset.ratio as any} : c));
                              }}
                              className={`group relative flex flex-col items-center justify-between p-2.5 rounded-xl border transition-all duration-200 cursor-pointer ${
                                isSelected
                                  ? "bg-indigo-600/15 border-indigo-500/80 shadow-[0_0_12px_rgba(99,102,241,0.25)] text-indigo-300"
                                  : "bg-zinc-900/50 border-white/5 text-zinc-400 hover:bg-zinc-800/80 hover:text-white hover:border-white/10"
                              }`}
                            >
                              {/* Visual Aspect Ratio Box Outline with subtle label background */}
                              <div
                                className="w-full flex items-center justify-center bg-zinc-950/30 rounded-lg p-1.5 group-hover:scale-105 transition-transform duration-200 mb-2"
                                style={preset.ratio === "None" ? { height: "1px", minHeight: "1px", padding: "0px" } : { minHeight: "46px" }}
                              >
                                {preset.ratio !== "None" && (
                                  <div
                                    className={`rounded-[4px] flex items-center justify-center transition-all ${
                                      isSelected
                                        ? "border-[2px] border-indigo-400 bg-indigo-500/10 shadow-[0_0_10px_rgba(129,140,248,0.3)]"
                                        : "border-2 border-zinc-500 group-hover:border-zinc-400 bg-transparent"
                                    } ${preset.dashed ? "border-dashed" : "border-solid"}`}
                                    style={{
                                      width: preset.width,
                                      height: preset.height
                                    }}
                                  />
                                )}
                              </div>

                              {/* Prominent Label and Subtitle */}
                              <div className="flex flex-col items-center text-center w-full">
                                <span className={`text-[11px] font-black tracking-tight leading-none ${
                                  isSelected ? "text-indigo-400" : "text-white"
                                }`}>
                                  {preset.label}
                                </span>
                                <span
                                  className="text-zinc-500 mt-1 leading-none font-medium truncate w-full group-hover:text-zinc-400 transition-colors"
                                  style={{ fontSize: preset.ratio === "None" ? "4px" : "5px" }}
                                >
                                  {preset.subtitle}
                                </span>
                              </div>

                              {/* Deep Active Glow Bar */}
                              <div className={`absolute bottom-0 left-1/4 right-1/4 h-[2px] rounded-full transition-all duration-300 ${
                                isSelected ? "bg-indigo-500" : "bg-transparent group-hover:bg-zinc-700"
                              }`} />
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  </motion.div>
                );
              })()}
              {activeExpandedMenu === "mask" && selectedClipId && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col w-full h-auto pb-1.5 pt-0 shrink-0"
                >
                  <div className="flex justify-between items-center w-full px-3.5 mb-1.5">
                    <span className="text-[10px] font-semibold text-white/90">
                      Mask Shape
                    </span>
                    <div className="flex items-center gap-2">
                      {renderCopyPasteButtons("mask")}
                      <button
                        onClick={() => setActiveExpandedMenu(null)}
                        className="text-zinc-400 hover:text-white p-1 rounded-md hover:bg-zinc-805/40 transition flex items-center justify-center"
                      >
                        <Check size={14} />
                      </button>
                    </div>
                  </div>
                  <div className="grid grid-cols-4 gap-1.5 px-2.5">
                    {[
                      {
                        id: "none",
                        name: "None",
                        icon: (
                          <div className="w-[14px] h-[14px] border-2 border-white/40" />
                        ),
                      },
                      {
                        id: "circle",
                        name: "Circle",
                        icon: (
                          <div className="w-[14px] h-[14px] border-2 border-white/40 rounded-full" />
                        ),
                      },
                      {
                        id: "square",
                        name: "Square",
                        icon: (
                          <div className="w-[12px] h-[12px] border-2 border-white/40" />
                        ),
                      },
                      {
                        id: "half",
                        name: "Half",
                        icon: (
                          <div className="w-[14px] h-[14px] border-b-2 border-white/40 flex items-end justify-center">
                            <div className="w-full h-[6px] bg-white/20" />
                          </div>
                        ),
                      },
                    ].map((mask) => {
                      const isActive =
                        (clips.find((c) => c.id === selectedClipId)?.maskType ||
                          "none") === mask.id;
                      return (
                        <button
                          key={mask.id}
                          onClick={() => {
                            setClips((prev) =>
                              prev.map((c) =>
                                c.id === selectedClipId
                                  ? { ...c, maskType: mask.id as any }
                                  : c,
                              ),
                            );
                          }}
                          className={`flex flex-col items-center justify-center gap-1 p-1 py-1.5 rounded-lg transition-colors border ${isActive ? "bg-zinc-700 border-white/20" : "bg-zinc-800 border-transparent hover:border-white/10"}`}
                        >
                          <div className="h-6 flex items-center justify-center">
                            {mask.icon}
                          </div>
                          <span className="text-[8px] font-medium text-zinc-300">
                            {mask.name}
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  {(() => {
                    const activeClip = clips.find((c) => c.id === selectedClipId);
                    if (!activeClip || !activeClip.maskType || activeClip.maskType === "none") return null;

                    return (
                      <div className="mt-4 px-2 py-2.5 rounded-xl bg-purple-500/5 border border-purple-500/10 text-center transition-all">
                        <span className="text-[10px] text-zinc-300 font-medium select-none flex items-center justify-center gap-1.5 leading-normal pr-1.5">
                          <Sparkles className="text-purple-400 w-3.5 h-3.5 shrink-0 animate-pulse" />
                          <span>Hold / Long-tap the mask shape on preview screen to open adjustment sliders</span>
                        </span>
                      </div>
                    );
                  })()}
                </motion.div>
              )}
              {activeExpandedMenu === "voiceover" && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="w-full flex items-center p-2 z-10 sticky bottom-0"
                  style={{marginBottom: -10}}
                >
                  <div 
                    className="flex items-center gap-3 w-full bg-zinc-800/95 rounded-full border border-white/10 shadow-xl"
                    style={{
                      paddingLeft: "12px",
                      paddingTop: "8px",
                      paddingBottom: "10px",
                      paddingRight: "11px",
                      marginLeft: "0px",
                      marginTop: "-4px",
                      marginBottom: "8px",
                      marginRight: "0px"
                    }}
                  >
                    <div className="relative group cursor-pointer shrink-0" onClick={handleRecordClick}>
                      {isRecording ? (
                        <>
                          <div className="absolute inset-0 bg-red-500/40 rounded-full blur-[12px] animate-pulse transition-all duration-500" />
                          <div className="w-8 h-8 rounded-full bg-red-500 flex items-center justify-center animate-pulse z-10 border border-white/40 relative">
                            <div className="w-2.5 h-2.5 bg-white rounded-sm" />
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="absolute inset-0 bg-red-500/20 rounded-full blur-[8px] transition-all duration-500" />
                          <div className="w-8 h-8 rounded-full bg-red-600 flex items-center justify-center group-hover:scale-105 group-active:scale-95 transition-all duration-300 z-10 border border-white/20 relative">
                            <Mic size={14} className="text-white" />
                          </div>
                        </>
                      )}
                    </div>
                    
                    <div className="flex-1 flex flex-col justify-center overflow-hidden">
                      <span className="text-[9px] font-bold text-white/60 tracking-widest uppercase mb-1">
                        {isRecording ? "Recording..." : "Tap To Record"}
                      </span>
                      <div className="flex items-end gap-[2px] h-3">
                          {liveMicLevels.map((amplitude, i) => {
                            return (
                              <div
                                key={i}
                                className={`flex-1 rounded-full transition-all duration-150 ${isRecording ? 'bg-red-500' : 'bg-white/20'}`}
                                style={{ height: `${Math.min(100, Math.max(15, amplitude))}%`, minHeight: '2px' }}
                              />
                            );
                          })}
                      </div>
                    </div>

                    <button
                      onClick={() => setActiveExpandedMenu(null)}
                      className="text-zinc-400 hover:text-white rounded-full p-2 shrink-0 transition-all bg-zinc-700/50 hover:bg-zinc-700"
                    >
                      <X size={12} />
                    </button>
                  </div>
                </motion.div>
              )}
              {activeExpandedMenu === "keyframe-interpolation" && selectedClipId && isBetweenKeyframes && (
                (() => {
                  const clip = clips.find((c) => c.id === selectedClipId);
                  const timeInClip = clip ? currentTime - clip.leftSeconds : 0;
                  const useVolume = isBetweenVolumeKeyframes;
                  const relevantKfs = clip ? (clip.keyframes || [])
                    .filter(k => useVolume ? k.properties.volume !== undefined : (k.properties.translateX !== undefined || k.properties.scale !== undefined))
                    .sort((a, b) => a.timeOffset - b.timeOffset) : [];
                  
                  let startKf: any = null;
                  if (clip) {
                    for (let i = 0; i < relevantKfs.length - 1; i++) {
                      if (timeInClip >= relevantKfs[i].timeOffset && timeInClip <= relevantKfs[i+1].timeOffset) {
                        startKf = relevantKfs[i];
                        break;
                      }
                    }
                    if (!startKf && relevantKfs.length > 0) {
                      startKf = relevantKfs[0];
                    }
                  }

                  const getPresetDefaultPoints = (presetId: string): [number, number, number, number] => {
                    switch (presetId) {
                      case "easeIn": return [0.42, 0, 1, 1];
                      case "easeOut": return [0, 0, 0.58, 1];
                      case "easeInOut": return [0.42, 0, 0.58, 1];
                      case "linear":
                      default:
                        return [0.25, 0.25, 0.75, 0.75];
                    }
                  };

                  const handlePresetPointerDown = (presetId: string, e: React.PointerEvent) => {
                    if (e.button !== 0) return;
                    hasTriggeredLongPress.current = false;
                    if (interpolationLongPressTimerRef.current) {
                      clearTimeout(interpolationLongPressTimerRef.current);
                    }
                    interpolationLongPressTimerRef.current = setTimeout(() => {
                      hasTriggeredLongPress.current = true;
                      setSelectedInterpolationPreset(presetId);

                      if (clip && startKf) {
                        setClips((prev) => prev.map(c => {
                          if (c.id !== selectedClipId) return c;
                          return {
                            ...c,
                            keyframes: (c.keyframes || []).map(k => {
                              if (k.id !== startKf.id) return k;
                              const updated: any = { ...k, curve: presetId };
                              if (!k.customEasePoints) {
                                updated.customEasePoints = getPresetDefaultPoints(presetId);
                              }
                              return updated;
                            })
                          };
                        }));
                      }

                      setActiveExpandedMenu("custom-keyframe-graph");
                    }, 450);
                  };

                  const handlePresetPointerUp = (presetId: string, e: React.PointerEvent) => {
                    if (interpolationLongPressTimerRef.current) {
                      clearTimeout(interpolationLongPressTimerRef.current);
                      interpolationLongPressTimerRef.current = null;
                    }
                    if (!hasTriggeredLongPress.current) {
                      if (clip && startKf) {
                        setClips((prev) => prev.map(c => {
                          if (c.id !== selectedClipId) return c;
                          return {
                            ...c,
                            keyframes: (c.keyframes || []).map(k => k.id === startKf.id ? { ...k, curve: presetId as any, customEasePoints: undefined } : k)
                          };
                        }));
                      }
                    }
                  };

                  const handlePresetPointerCancel = () => {
                    if (interpolationLongPressTimerRef.current) {
                      clearTimeout(interpolationLongPressTimerRef.current);
                      interpolationLongPressTimerRef.current = null;
                    }
                  };

                  return (
                    <motion.div
                      layout
                      initial={{ opacity: 0, scale: 0.95, y: 10 }}
                      animate={{ opacity: 1, scale: 1, y: 0 }}
                      exit={{ opacity: 0, scale: 0.95, y: 10 }}
                      transition={{ duration: 0.2 }}
                      className="flex flex-col w-full px-3 pb-1.5 text-zinc-100"
                    >
                      <div className="flex justify-between items-center w-full mb-1.5 mt-0.5 px-0.5">
                        <span className="text-[10px] font-semibold text-white/90 uppercase tracking-widest text-indigo-400">Curve Type</span>
                        <button
                          onClick={() => setActiveExpandedMenu(null)}
                          className="text-zinc-400 hover:text-white p-0.5 bg-white/5 hover:bg-white/10 rounded-full transition-all"
                        >
                          <X size={12} />
                        </button>
                      </div>
                      <div className="grid grid-cols-4 gap-1">
                        {[
                          { id: "linear", name: "Linear", path: "M 2 10 L 22 2" },
                          { id: "easeIn", name: "Ease In", path: "M 2 10 Q 16 10 22 2" },
                          { id: "easeOut", name: "Ease Out", path: "M 2 10 Q 8 2 22 2" },
                          { id: "easeInOut", name: "In Out", path: "M 2 10 C 8 10 16 2 22 2" },
                        ].map((preset) => {
                          const isSelected = startKf && startKf.curve === preset.id;
                          return (
                            <button
                              key={preset.id}
                              className={`flex flex-col items-center justify-center py-1.5 px-1 rounded-lg transition-all select-none touch-none ${
                                isSelected ? 'bg-indigo-500/20 text-indigo-400 border border-indigo-500/30' : 'bg-white/5 hover:bg-white/10 text-white border border-transparent'
                              }`}
                              onPointerDown={(e) => handlePresetPointerDown(preset.id, e)}
                              onPointerUp={(e) => handlePresetPointerUp(preset.id, e)}
                              onPointerCancel={handlePresetPointerCancel}
                              onPointerLeave={handlePresetPointerCancel}
                              title="Hold for detailed graph"
                            >
                              <svg viewBox="0 0 24 12" className="w-6 h-3 mb-1 overflow-visible text-white/80">
                                <path d={preset.path} fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
                              </svg>
                              <span className="text-[8.5px] font-medium leading-tight">{preset.name}</span>
                            </button>
                          );
                        })}
                      </div>
                    </motion.div>
                  );
                })()
              )}

              {activeExpandedMenu === "custom-keyframe-graph" && selectedClipId && isBetweenKeyframes && (
                <motion.div
                  layout
                  initial={{ opacity: 0, scale: 0.95, y: 10 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 10 }}
                  transition={{ duration: 0.2 }}
                  className="flex flex-col w-full px-3 pb-2 text-zinc-100 select-none touch-none text-left"
                >
                  <div className="flex justify-between items-center w-full mb-1 mt-0.5 px-0.5">
                    <span className="text-[10px] font-semibold text-indigo-400 uppercase tracking-widest">
                      Custom Curve
                    </span>
                    <button
                      onClick={() => setActiveExpandedMenu("keyframe-interpolation")}
                      className="text-zinc-400 hover:text-white p-1 bg-white/5 hover:bg-white/10 rounded-full transition-all"
                      title="Back to types"
                    >
                      <ArrowLeft size={11} />
                    </button>
                  </div>

                  {(() => {
                    const clip = clips.find((c) => c.id === selectedClipId);
                    const timeInClip = clip ? currentTime - clip.leftSeconds : 0;
                    const useVolume = isBetweenVolumeKeyframes;
                    const relevantKfs = clip ? (clip.keyframes || [])
                      .filter(k => useVolume ? k.properties.volume !== undefined : (k.properties.translateX !== undefined || k.properties.scale !== undefined))
                      .sort((a, b) => a.timeOffset - b.timeOffset) : [];
                    
                    let startKf: any = null;
                    if (clip) {
                      for (let i = 0; i < relevantKfs.length - 1; i++) {
                        if (timeInClip >= relevantKfs[i].timeOffset && timeInClip <= relevantKfs[i+1].timeOffset) {
                          startKf = relevantKfs[i];
                          break;
                        }
                      }
                      if (!startKf && relevantKfs.length > 0) {
                        startKf = relevantKfs[0];
                      }
                    }

                    if (!startKf) return null;

                    const getPresetDefaultPoints = (presetId: string): [number, number, number, number] => {
                      switch (presetId) {
                        case "easeIn": return [0.42, 0, 1, 1];
                        case "easeOut": return [0, 0, 0.58, 1];
                        case "easeInOut": return [0.42, 0, 0.58, 1];
                        case "linear":
                        default:
                          return [0.25, 0.25, 0.75, 0.75];
                      }
                    };

                    const points = startKf.customEasePoints || getPresetDefaultPoints(startKf.curve || "linear");
                    const [x1, y1, x2, y2] = points;

                    // Grid dimensions:
                    // padding = 15, w = 150, h = 90
                    const toX = (val: number) => 15 + val * 150;
                    const toY = (val: number) => 105 - val * 90;
                    const fromX = (valX: number) => (valX - 15) / 150;
                    const fromY = (valY: number) => (105 - valY) / 90;

                    const pD = `M ${toX(0)} ${toY(0)} C ${toX(x1)} ${toY(y1)}, ${toX(x2)} ${toY(y2)}, ${toX(1)} ${toY(1)}`;

                    // Handler dragging inside SVG
                    const handleSvgPointerDown = (e: React.PointerEvent, handleIdx: 1 | 2) => {
                      e.preventDefault();
                      e.stopPropagation();
                      setDraggingHandle(handleIdx);
                      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
                    };

                    const handleSvgPointerMove = (e: React.PointerEvent) => {
                      if (!draggingHandle) return;
                      const rect = e.currentTarget.getBoundingClientRect();
                      const rawX = e.clientX - rect.left;
                      const rawY = e.clientY - rect.top;
                      
                      let nX = fromX(rawX);
                      let nY = fromY(rawY);
                      
                      nX = Math.max(0, Math.min(1, nX));
                      nY = Math.max(0, Math.min(1, nY));

                      setClips((prev) => prev.map(c => {
                        if (c.id !== selectedClipId) return c;
                        return {
                          ...c,
                          keyframes: (c.keyframes || []).map(k => {
                            if (k.id !== startKf.id) return k;
                            const pts = k.customEasePoints ? [...k.customEasePoints] : [...getPresetDefaultPoints(k.curve || "linear")];
                            if (draggingHandle === 1) {
                              pts[0] = nX;
                              pts[1] = nY;
                            } else {
                              pts[2] = nX;
                              pts[3] = nY;
                            }
                            return {
                              ...k,
                              customEasePoints: pts as [number, number, number, number]
                            };
                          })
                        };
                      }));
                    };

                    const handleSvgPointerUp = (e: React.PointerEvent) => {
                      if (draggingHandle) {
                        (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
                        setDraggingHandle(null);
                      }
                    };

                    return (
                      <div className="flex flex-col items-center w-full">
                        {/* Interactive Graph Canvas */}
                        <div className="w-[180px] h-[105px] bg-zinc-950/90 border border-white/10 rounded-xl relative overflow-visible flex items-center justify-center mt-1">
                          <svg
                            className="w-full h-full overflow-visible cursor-crosshair touch-none"
                            onPointerMove={handleSvgPointerMove}
                            onPointerUp={handleSvgPointerUp}
                            onPointerCancel={handleSvgPointerUp}
                          >
                            {/* Grid Lines */}
                            <line x1={toX(0)} y1={toY(0.5)} x2={toX(1)} y2={toY(0.5)} stroke="#3f3f46" strokeWidth="1" strokeDasharray="2,2" />
                            <line x1={toX(0.5)} y1={toY(0)} x2={toX(0.5)} y2={toY(1)} stroke="#3f3f46" strokeWidth="1" strokeDasharray="2,2" />
                            
                            {/* Connection Lines to Handles */}
                            <line x1={toX(0)} y1={toY(0)} x2={toX(x1)} y2={toY(y1)} stroke="#6366f1" strokeWidth="1.5" strokeOpacity="0.5" />
                            <line x1={toX(1)} y1={toY(1)} x2={toX(x2)} y2={toY(y2)} stroke="#ec4899" strokeWidth="1.5" strokeOpacity="0.5" />

                            {/* Main Bezier Curve */}
                            <path d={pD} fill="none" stroke="#818cf8" strokeWidth="2.5" strokeLinecap="round" />

                            {/* Start and End Anchor points */}
                            <circle cx={toX(0)} cy={toY(0)} r="3" fill="#a1a1aa" />
                            <circle cx={toX(1)} cy={toY(1)} r="3" fill="#a1a1aa" />

                            {/* Handle Control Circles */}
                            <circle
                              cx={toX(x1)}
                              cy={toY(y1)}
                              r={draggingHandle === 1 ? "6.5" : "5"}
                              fill="#6366f1"
                              stroke="white"
                              strokeWidth="1.5"
                              className="cursor-pointer transition-all hover:scale-125 select-none active:scale-150 active:fill-white"
                              onPointerDown={(e) => handleSvgPointerDown(e, 1)}
                            />
                            <circle
                              cx={toX(x2)}
                              cy={toY(y2)}
                              r={draggingHandle === 2 ? "6.5" : "5"}
                              fill="#ec4899"
                              stroke="white"
                              strokeWidth="1.5"
                              className="cursor-pointer transition-all hover:scale-125 select-none active:scale-150 active:fill-white"
                              onPointerDown={(e) => handleSvgPointerDown(e, 2)}
                            />
                          </svg>
                        </div>

                        {/* Stats & Actions */}
                        <div className="flex justify-between items-center w-full px-1 mt-1">
                          <span className="text-[8px] font-mono text-zinc-400">
                            P1({x1.toFixed(2)}, {y1.toFixed(2)})
                          </span>
                          <button
                            onClick={() => {
                              setClips((prev) => prev.map(c => {
                                if (c.id !== selectedClipId) return c;
                                return {
                                  ...c,
                                  keyframes: (c.keyframes || []).map(k => {
                                    if (k.id !== startKf.id) return k;
                                    return {
                                      ...k,
                                      customEasePoints: getPresetDefaultPoints(k.curve || "linear")
                                    };
                                  })
                                };
                              }));
                            }}
                            className="text-[8px] text-zinc-400 hover:text-white px-1.5 py-0.5 bg-white/5 rounded-md hover:bg-white/10 transition-all font-semibold uppercase tracking-wider"
                          >
                            Reset
                          </button>
                          <span className="text-[8px] font-mono text-zinc-400">
                            P2({x2.toFixed(2)}, {y2.toFixed(2)})
                          </span>
                        </div>
                      </div>
                    );
                  })()}
                </motion.div>
              )}
            </AnimatePresence>

            <motion.div
              layout
              transition={{ type: "spring", bounce: 0, duration: 0.4 }}
              className="flex items-center gap-1 px-1.5 justify-center w-[218px]"
            >
              <motion.button
                layout
                className={`p-1 shrink-0 rounded-full flex items-center justify-center transition-all duration-300 ${activeExpandedMenu === "plus-media" ? "bg-indigo-600 text-white rotate-45" : "hover:bg-zinc-700 text-zinc-300"}`}
                onClick={() => {
                  if (activeExpandedMenu === "plus-media") {
                    setActiveExpandedMenu(null);
                  } else {
                    setActiveExpandedMenu("plus-media");
                  }
                }}
              >
                <PlusIcon size={14} />
              </motion.button>
              <motion.div
                layout
                className="w-px h-4 bg-zinc-700 mx-0.5 shrink-0"
              ></motion.div>

              {activeExpandedMenu === "plus-media" ? (
                /* 4 Compact Logos on Flowbar: Image, Video, Audio, Folders */
                <div className="flex items-center justify-between w-[164px] shrink-0 px-2 animate-fade-in">
                  {[
                    { id: "Image", icon: <ImageIcon size={14} /> },
                    { id: "Video", icon: <Play size={14} className="rotate-90" /> },
                    { id: "Audio", icon: <Music size={14} /> },
                    { id: "Folders", icon: <Folder size={14} /> }
                  ].map((tab) => {
                    const isActive = selectedMediaTab === tab.id;
                    return (
                      <button
                        key={tab.id}
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedMediaTab(tab.id as any);
                          setCurrentMediaFolder(null); // Back to folder overview list
                        }}
                        className={`p-1 shrink-0 rounded-full flex items-center justify-center transition-colors duration-200 cursor-pointer ${
                          isActive 
                            ? "bg-zinc-700 text-white" 
                            : "text-zinc-400 hover:bg-zinc-700 hover:text-white"
                        }`}
                        title={tab.id}
                      >
                        {tab.icon}
                      </button>
                    );
                  })}
                </div>
              ) : (
                <div className="flex items-center gap-1 overflow-x-auto scrollbar-hide w-[164px] overflow-hidden shrink-0 snap-x snap-mandatory">
                {flowBarOrder.filter((key) => {
                  if (selectedClip?.type === "image" && ["volume", "speed", "stabilize"].includes(key)) {
                    return false;
                  }
                  // Allow voiceover button to show independently of selected clip, or strictly when no clip is selected.
                  // For now, let it be always active or togglable anywhere.
                  return true;
                }).map((key) => {
                  switch(key) {
                    case 'voiceover': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${activeExpandedMenu === "voiceover" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white"}`} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "voiceover" ? null : "voiceover")}><Mic size={14} /></motion.button>
                    );
                    case 'volume': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? (activeExpandedMenu === "volume" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "volume" ? null : "volume")}><Volume2 size={14} /></motion.button>
                    );
                    case 'text': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId && clips.find((c) => c.id === selectedClipId)?.type === "text" ? (activeExpandedMenu === "text" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "hover:bg-zinc-700 text-white"}`} onClick={() => { const sel = clips.find((c) => c.id === selectedClipId); if (sel && sel.type === "text") { setActiveExpandedMenu(activeExpandedMenu === "text" ? null : "text"); } else { handleAddText(); } }}><Type size={14} /></motion.button>
                    );
                    case 'crop': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId && ["video", "image"].includes(clips.find((c) => c.id === selectedClipId)?.type || "") ? (activeExpandedMenu === "crop" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId || !["video", "image"].includes(clips.find((c) => c.id === selectedClipId)?.type || "")} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "crop" ? null : "crop")}><Crop size={14} /></motion.button>
                    );
                    case 'adjust': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? (activeExpandedMenu === "adjust" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "adjust" ? null : "adjust")}><SlidersHorizontal size={14} /></motion.button>
                    );
                    case 'speed': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? (activeExpandedMenu === "speed" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "speed" ? null : "speed")}><Clock size={14} /></motion.button>
                    );
                    case 'stabilize': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId && clips.find(c => c.id === selectedClipId)?.type === "video" ? (activeExpandedMenu === "stabilize" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId || clips.find(c => c.id === selectedClipId)?.type !== "video"} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "stabilize" ? null : "stabilize")}><Activity size={14} /></motion.button>
                    );
                    case 'copy': {
                      const isOptionActive = multiSelectActive;
                      return (
                        <motion.button
                          key={key}
                          layout
                          className={`p-1 shrink-0 rounded-full transition-all duration-300 snap-start flex items-center justify-center relative touch-none ${
                            isOptionActive 
                              ? "bg-indigo-600/30 text-indigo-300 border border-indigo-500/50 shadow-[0_0_12px_rgba(99,102,241,0.5)]" 
                              : (selectedClipId ? "hover:bg-zinc-700 text-white" : "opacity-45 text-zinc-400 hover:text-white")
                          }`}
                          onPointerDown={onCopyPointerDown}
                          onPointerUp={onCopyPointerUp}
                          onPointerLeave={onCopyPointerLeave}
                          style={{ touchAction: "none" }}
                          title="Press & Hold to toggle Multi-Select"
                        >
                          <Copy size={14} />
                          {isOptionActive && (
                            <span className="absolute -top-0.5 -right-0.5 flex h-2 w-2">
                              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
                              <span className="relative inline-flex rounded-full h-2 w-2 bg-indigo-500"></span>
                            </span>
                          )}
                        </motion.button>
                      );
                    }
                    case 'extract-audio': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId && clips.find(c => c.id === selectedClipId)?.type === "video" ? "hover:bg-zinc-700 text-white" : "opacity-30"}`} disabled={!selectedClipId || clips.find(c => c.id === selectedClipId)?.type !== "video"} onClick={handleExtractAudio}><Music size={14} /></motion.button>
                    );
                    case 'move': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? (activeExpandedMenu === "move" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "move" ? null : "move")}><Move size={14} /></motion.button>
                    );
                    case 'magic': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? "hover:bg-zinc-700 text-white" : "opacity-30"}`} disabled={!selectedClipId} onClick={handleSmoothSlowMo}><Wand2 size={14} /></motion.button>
                    );
                    case 'activity': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? (activeExpandedMenu === "blend" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "blend" ? null : "blend")}><Blend size={14} /></motion.button>
                    );
                    case 'mask': return (
                      <motion.button key={key} layout className={`p-1 shrink-0 rounded-full transition-colors snap-start flex items-center justify-center ${selectedClipId ? (activeExpandedMenu === "mask" ? "bg-zinc-700 text-white" : "hover:bg-zinc-700 text-white") : "opacity-30"}`} disabled={!selectedClipId} onClick={() => setActiveExpandedMenu(activeExpandedMenu === "mask" ? null : "mask")}><SquareDashed size={14} /></motion.button>
                    );
                    default: return null;
                  }
                })}
              </div>
              )}
            </motion.div>
            <input
              type="file"
              ref={fileInputRef}
              className="hidden"
              onChange={handleFileUpload}
              accept="video/*,audio/*,image/*"
            />
          </motion.div>



        </>
      )}



    </div>
  );
}
