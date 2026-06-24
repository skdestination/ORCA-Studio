import React from "react";
import { AlertCircle, Activity } from "lucide-react";
import { Clip } from "../types";

interface ClipRendererProps {
  clip: Clip;
  isSelected: boolean;
  currentTime: number;
  pixelsPerSecond: number;
  isCompactMode: boolean;
  layerHiddenOrMuted: boolean;
  activeExpandedMenu: string | null;
  isErrored: boolean;
  selectedClipId: string | null;
  selectedClipIds: string[];
  onTrimStart: (e: React.PointerEvent, clip: Clip, edge: "left" | "right") => void;
}

export const ClipRenderer = React.memo(({
  clip,
  isSelected,
  currentTime,
  pixelsPerSecond,
  isCompactMode,
  layerHiddenOrMuted,
  activeExpandedMenu,
  isErrored,
  selectedClipId,
  selectedClipIds,
  onTrimStart,
}: ClipRendererProps) => {
  const pathD = ""; // Placeholder, needs actual implementation logic if used

  return (
    <div className="absolute inset-0">
      {/* Type indicator icon */}
      <div className={`absolute max-w-full overflow-hidden whitespace-nowrap pl-2 flex items-center gap-1 ${clip.type === "audio" ? "inset-y-0 z-20 pointer-events-none" : `${isCompactMode ? "top-0.5" : "top-1"} pointer-events-none`}`}>
        {isErrored && clip.type !== "text" && (
          <span className="text-[10px] font-bold text-red-100 uppercase drop-shadow-md pb-0.5 px-1 bg-red-500/80 rounded inline-flex items-center gap-1">
            <AlertCircle size={10} /> Missing File
          </span>
        )}
        
        {clip.type === "audio" ? (
          <span className={`${isCompactMode ? "text-[8px] sm:text-[9px] py-0 px-1" : "text-[10px] py-0.5 px-2"} font-bold text-purple-200 tracking-wider drop-shadow-sm bg-black/55 border border-purple-500/25 rounded-md inline-flex items-center backdrop-blur-md shadow-sm gap-1 ml-0.5 sm:ml-1 scale-95 origin-left`}>
            <span className={`${isCompactMode ? "w-1 h-1" : "w-1.5 h-1.5"} rounded-full bg-purple-400 animate-pulse shrink-0`} />
            AUDIO {layerHiddenOrMuted && "(MUTED)"}
          </span>
        ) : (
          <span className={`${isCompactMode ? "text-[8px] sm:text-[9px] py-0 px-1" : "text-[9px] py-0.5 px-1.5"} font-bold tracking-wider text-zinc-200 drop-shadow-sm bg-black/55 border border-white/10 rounded-md shadow-sm backdrop-blur-md inline-flex items-center uppercase scale-95 origin-left`}>
            {clip.type} {layerHiddenOrMuted && "(HIDDEN)"}
          </span>
        )}

        {clip.opticalFlow && (
          <span className="text-[10px] font-bold text-indigo-200 uppercase drop-shadow-md pb-0.5 px-1 bg-indigo-500/50 rounded inline-flex items-center gap-1">
            <Activity size={10} /> Smooth
          </span>
        )}
      </div>

      {/* Simplified Keyframe placeholder - full SVG logic requires more complex dependencies from App */}
      
      {/* Trim Controls with premium glowing edge handle bar */}
      {isSelected && (
        <>
          <div
            onPointerDown={(e) => onTrimStart(e, clip, "left")}
            className="absolute left-0 top-0 bottom-0 w-[10px] bg-white/90 hover:bg-white border-r border-black/40 flex items-center justify-center cursor-col-resize hover:w-[13px] transition-all z-25 shadow-[1px_0_6px_rgba(0,0,0,0.5)]"
            style={{ touchAction: "none" }}
          >
            <div className="flex flex-col gap-0.5 justify-center items-center">
              <div className="w-[1.5px] h-1.5 bg-zinc-700 rounded-full" />
              <div className="w-[1.5px] h-1.5 bg-zinc-700 rounded-full" />
            </div>
          </div>
          <div
            onPointerDown={(e) => onTrimStart(e, clip, "right")}
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
    prev.isCompactMode === next.isCompactMode &&
    prev.layerHiddenOrMuted === next.layerHiddenOrMuted &&
    prev.activeExpandedMenu === next.activeExpandedMenu &&
    prev.isErrored === next.isErrored &&
    prev.selectedClipIds === next.selectedClipIds
  );
});
