import React from "react";
import { motion } from "motion/react";
import { Check, Volume2 } from "lucide-react";
import { Clip } from "../types";

interface VolumePanelProps {
  selectedClipId: string | null;
  clips: Clip[];
  applyVolumeToAll: boolean;
  setApplyVolumeToAll: (val: boolean) => void;
  setClipVolume: (val: number) => void;
  currentSelectedClipInterpolatedProps: any;
  updateClipsProperties: (ids: string[], updates: any) => void;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
}

export const VolumePanel: React.FC<VolumePanelProps> = ({
  selectedClipId,
  clips,
  applyVolumeToAll,
  setApplyVolumeToAll,
  setClipVolume,
  currentSelectedClipInterpolatedProps,
  updateClipsProperties,
  renderCopyPasteButtons,
  setActiveExpandedMenu,
}) => {
  return (
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
  );
};
