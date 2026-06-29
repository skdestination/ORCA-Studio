import React from "react";
import { motion } from "motion/react";
import { Check } from "lucide-react";
import { Clip } from "../types";

interface MotionPanelProps {
  selectedClipId: string | null;
  clips: Clip[];
  setClips: React.Dispatch<React.SetStateAction<Clip[]>>;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
  showToast: (message: string) => void;
}

export const MotionPanel: React.FC<MotionPanelProps> = ({
  selectedClipId,
  clips,
  setClips,
  renderCopyPasteButtons,
  setActiveExpandedMenu,
  showToast,
}) => {
  if (!selectedClipId) return null;
  const clip = clips.find((c) => c.id === selectedClipId);
  if (!clip) return null;

  const isActivated = !!(clip.motionInBlurApplied || clip.motionOutBlurApplied);

  const handleToggle = () => {
    const nextState = !isActivated;
    setClips((prev) =>
      prev.map((c) =>
        c.id === clip.id
          ? {
              ...c,
              motionInBlurApplied: nextState,
              motionOutBlurApplied: nextState,
              motionInBlurStrength: nextState ? 50 : 0,
              motionOutBlurStrength: nextState ? 50 : 0,
            }
          : c
      )
    );
    showToast(nextState ? "Motion Blur Activated" : "Motion Blur Deactivated");
  };

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95, y: 10 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95, y: 10 }}
      transition={{ duration: 0.2 }}
      className="flex flex-col w-full text-left select-none px-3 pb-3"
    >
      {/* Header */}
      <div className="flex justify-between items-center w-full mb-3 px-0.5 mt-1">
        <span className="text-[10px] font-extrabold text-indigo-400 uppercase tracking-widest">
          Motion Blur
        </span>
        <div className="flex items-center gap-1.5">
          {renderCopyPasteButtons("motion")}
          <button
            onClick={() => setActiveExpandedMenu(null)}
            className="text-zinc-500 hover:text-white p-1 rounded-md hover:bg-zinc-800/40 transition flex items-center justify-center"
          >
            <Check size={12} className="text-emerald-400" />
          </button>
        </div>
      </div>

      {/* Main Control Panel */}
      <div className="bg-zinc-900/60 rounded-2xl p-4 border border-white/5 flex flex-col gap-3">
        <div className="flex flex-col gap-1">
          <span className="text-[10px] font-bold text-zinc-300 uppercase tracking-wider">
            Cinematic Motion Blur
          </span>
          <p className="text-[9px] text-zinc-400 leading-relaxed">
            Adds realistic optical and transform motion blur automatically on moving, scaling, or rotating media.
          </p>
        </div>

        <button
          onClick={handleToggle}
          className={`w-full py-2.5 rounded-xl text-[10px] font-extrabold tracking-wider uppercase transition-all duration-300 cursor-pointer ${
            isActivated
              ? "bg-emerald-600 text-white shadow-[0_2px_15px_rgba(16,185,129,0.3)] hover:bg-emerald-500"
              : "bg-indigo-600 text-white hover:bg-indigo-500 hover:shadow-[0_0_15px_rgba(99,102,241,0.4)]"
          }`}
        >
          {isActivated ? "✓ Activated" : "Activate"}
        </button>
      </div>
    </motion.div>
  );
};
