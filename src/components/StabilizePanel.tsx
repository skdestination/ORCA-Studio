import React from "react";
import { motion } from "motion/react";
import { Check, Activity } from "lucide-react";
import { Clip } from "../types";

interface StabilizePanelProps {
  selectedClipId: string | null;
  clips: Clip[];
  setClips: React.Dispatch<React.SetStateAction<Clip[]>>;
  stabilizingProgress: { clipId: string; progress: number; stage: string } | null;
  handleStabilize: (clipId: string, mode: "standard" | "active" | "locked" | "off") => void;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
}

export const StabilizePanel: React.FC<StabilizePanelProps> = ({
  selectedClipId,
  clips,
  setClips,
  stabilizingProgress,
  handleStabilize,
  renderCopyPasteButtons,
  setActiveExpandedMenu,
}) => {
  if (!selectedClipId) return null;
  const clip = clips.find((c) => c.id === selectedClipId);
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
                  setClips((prev) =>
                    prev.map((c) =>
                      c.id === clip.id ? { ...c, compareStabilization: !c.compareStabilization } : c
                    )
                  );
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
};
