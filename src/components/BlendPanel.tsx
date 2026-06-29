import React from "react";
import { motion } from "motion/react";
import { Check } from "lucide-react";
import { Clip } from "../types";

interface BlendPanelProps {
  selectedClipId: string | null;
  clips: Clip[];
  setClips: React.Dispatch<React.SetStateAction<Clip[]>>;
  currentSelectedClipInterpolatedProps: any;
  updateClipsProperties: (ids: string[], updates: any) => void;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
}

export const BlendPanel: React.FC<BlendPanelProps> = ({
  selectedClipId,
  clips,
  setClips,
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
      className="flex flex-col w-full h-auto bg-transparent px-2.5 pt-1.5 pb-1 gap-2"
    >
      <div className="flex justify-between items-center w-full">
        <span className="text-[7px] sm:text-[8px] tracking-[0.15em] font-medium text-[#71717a] uppercase tracking-widest pl-0.5">
          Blending
        </span>
        <div className="flex items-center gap-0.5">
          {renderCopyPasteButtons("blend")}
          <button
            onClick={() => setActiveExpandedMenu(null)}
            className="text-zinc-400 hover:text-white p-0.5 rounded-full hover:bg-white/10 transition flex items-center justify-center"
          >
            <Check size={12} />
          </button>
        </div>
      </div>

      {/* Blending Modes - Premium Horizontal Swipeable Row */}
      <div className="flex items-center gap-1 w-full overflow-x-auto scrollbar-hide py-0.5 snap-x">
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
               className={`px-2 py-1 rounded-[6px] text-[7.5px] font-bold uppercase tracking-[0.05em] snap-start shrink-0 transition-all border ${
                 isActive 
                   ? "bg-white text-black border-white shadow-sm" 
                   : "bg-[#18181b]/50 text-[#a1a1aa] border-transparent hover:text-white hover:bg-[#27272a]/80"
               }`}
             >
               {mode.replace("-", " ")}
             </button>
           );
        })}
      </div>

      {/* Opacity Control - Sleek Single-Row Layout */}
      <div className="flex items-center gap-2 mt-0.5">
        <span className="text-[7px] sm:text-[7.5px] font-semibold tracking-wider text-[#71717a] uppercase shrink-0 w-8">OPACITY</span>
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
          className="flex-1 accent-[#ef4444] h-1 bg-white/10 rounded-full appearance-none [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-2.5 [&::-webkit-slider-thumb]:h-2.5 [&::-webkit-slider-thumb]:bg-white [&::-webkit-slider-thumb]:rounded-full cursor-pointer"
        />
        <span className="text-[7.5px] text-white font-mono bg-[#18181b]/50 border border-white/5 px-1.5 py-0.5 rounded shrink-0 min-w-[28px] text-center">
          {Math.round((currentSelectedClipInterpolatedProps?.opacity ?? 1) * 100)}%
        </span>
      </div>
    </motion.div>
  );
};
