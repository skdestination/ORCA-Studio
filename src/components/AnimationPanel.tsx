import React, { useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Check, Sparkles } from "lucide-react";
import { Clip } from "../types";

interface AnimationPanelProps {
  selectedClipId: string | null;
  clips: Clip[];
  setClips: React.Dispatch<React.SetStateAction<Clip[]>>;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
  showToast: (message: string) => void;
  playInstantPreview?: (clipId: string, type: "in" | "out" | "overall" | "motionIn" | "motionOut") => void;
}

type TabType = "in" | "out" | "all";

export const AnimationPanel: React.FC<AnimationPanelProps> = ({
  selectedClipId,
  clips,
  setClips,
  renderCopyPasteButtons,
  setActiveExpandedMenu,
  showToast,
  playInstantPreview,
}) => {
  const [activeTab, setActiveTab] = useState<TabType>("in");

  if (!selectedClipId) return null;
  const clip = clips.find((c) => c.id === selectedClipId);
  if (!clip) return null;

  const currentAnimIn = clip.animationIn || "None";
  const currentAnimOut = clip.animationOut || "None";
  const currentAnimOverall = clip.animationOverall || "None";

  const animInOptions = [
    "None",
    "Fade In",
    "Slide Up",
    "Slide Down",
    "Slide Left",
    "Slide Right",
    "Zoom In",
    "Zoom Out In",
    "Bounce In",
    "Spin In",
    "Flip In X",
    "Flip In Y",
    "Glitch In"
  ] as const;

  const animOutOptions = [
    "None",
    "Fade Out",
    "Slide Up Out",
    "Slide Down Out",
    "Slide Left Out",
    "Slide Right Out",
    "Zoom Out",
    "Zoom In Out",
    "Bounce Out",
    "Spin Out",
    "Flip Out X",
    "Flip Out Y",
    "Glitch Out"
  ] as const;

  const animOverallOptions = [
    "None",
    "Camera Shake",
    "Violent Shake",
    "Glitch Shake",
    "Soft Drift",
    "Spin",
    "Pulse",
    "Float",
    "Jiggle",
    "Wobble",
    "Heartbeat",
    "Pendulum",
    "Vibrate",
    "Cinematic Pan",
    "Flicker",
    "Ken Burns Zoom"
  ] as const;

  const handleSelectAnimIn = (option: string) => {
    setClips((prev) =>
      prev.map((c) =>
        c.id === clip.id ? { ...c, animationIn: option } : c
      )
    );
    showToast(`In Animation: ${option}`);
    setTimeout(() => {
      playInstantPreview?.(clip.id, "in");
    }, 50);
  };

  const handleSelectAnimOut = (option: string) => {
    setClips((prev) =>
      prev.map((c) =>
        c.id === clip.id ? { ...c, animationOut: option } : c
      )
    );
    showToast(`Out Animation: ${option}`);
    setTimeout(() => {
      playInstantPreview?.(clip.id, "out");
    }, 50);
  };

  const handleSelectAnimOverall = (option: string) => {
    setClips((prev) =>
      prev.map((c) =>
        c.id === clip.id ? { ...c, animationOverall: option } : c
      )
    );
    showToast(`Overall Animation: ${option}`);
    setTimeout(() => {
      playInstantPreview?.(clip.id, "overall");
    }, 50);
  };

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
      <div className="flex justify-between items-center w-full mb-2 px-0.5 mt-1">
        <span className="text-[10px] font-extrabold text-indigo-400 uppercase tracking-widest flex items-center gap-1">
          <Sparkles size={10} className="text-indigo-400" />
          Animation Presets
        </span>
        <div className="flex items-center gap-1.5">
          {renderCopyPasteButtons("animation")}
          <button 
            onClick={() => setActiveExpandedMenu(null)}
            className="text-zinc-500 hover:text-white p-1 rounded-md hover:bg-zinc-800/40 transition flex items-center justify-center"
          >
            <Check size={12} className="text-emerald-400" />
          </button>
        </div>
      </div>

      {/* Tabs bar: In, Out, All */}
      <div className="grid grid-cols-3 gap-1 bg-zinc-950/40 p-1 rounded-xl mb-2.5 border border-white/5">
        <button
          onClick={() => setActiveTab("in")}
          className={`py-1 rounded-lg text-[9px] font-bold transition-all text-center cursor-pointer ${
            activeTab === "in"
              ? "bg-indigo-600 text-white shadow-sm"
              : "text-zinc-400 hover:text-white"
          }`}
        >
          In
          {currentAnimIn !== "None" && <span className="ml-1 text-[7px] text-indigo-200">•</span>}
        </button>
        <button
          onClick={() => setActiveTab("out")}
          className={`py-1 rounded-lg text-[9px] font-bold transition-all text-center cursor-pointer ${
            activeTab === "out"
              ? "bg-indigo-600 text-white shadow-sm"
              : "text-zinc-400 hover:text-white"
          }`}
        >
          Out
          {currentAnimOut !== "None" && <span className="ml-1 text-[7px] text-indigo-200">•</span>}
        </button>
        <button
          onClick={() => setActiveTab("all")}
          className={`py-1 rounded-lg text-[9px] font-bold transition-all text-center cursor-pointer ${
            activeTab === "all"
              ? "bg-indigo-600 text-white shadow-sm"
              : "text-zinc-400 hover:text-white"
          }`}
        >
          All
          {currentAnimOverall !== "None" && <span className="ml-1 text-[7px] text-indigo-200">•</span>}
        </button>
      </div>

      {/* Content presets grid */}
      <div className="relative overflow-hidden min-h-[90px] flex flex-col justify-start">
        <AnimatePresence mode="wait">
          {activeTab === "in" && (
            <motion.div
              key="in"
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -5 }}
              transition={{ duration: 0.12 }}
              className="grid grid-cols-2 gap-1.5 w-full overflow-y-auto max-h-[145px] pr-0.5 scrollbar-hide"
            >
              {animInOptions.map((opt) => (
                <button
                  key={opt}
                  onClick={() => handleSelectAnimIn(opt)}
                  className={`py-1.5 px-2 rounded-lg text-[9px] font-semibold transition-all ${
                    currentAnimIn === opt
                      ? "bg-indigo-600/30 text-indigo-300 font-bold border border-indigo-500/40"
                      : "bg-neutral-800 hover:bg-neutral-700 text-zinc-300 border border-white/5"
                  } cursor-pointer`}
                >
                  {opt}
                </button>
              ))}
            </motion.div>
          )}

          {activeTab === "out" && (
            <motion.div
              key="out"
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -5 }}
              transition={{ duration: 0.12 }}
              className="grid grid-cols-2 gap-1.5 w-full overflow-y-auto max-h-[145px] pr-0.5 scrollbar-hide"
            >
              {animOutOptions.map((opt) => (
                <button
                  key={opt}
                  onClick={() => handleSelectAnimOut(opt)}
                  className={`py-1.5 px-2 rounded-lg text-[9px] font-semibold transition-all ${
                    currentAnimOut === opt
                      ? "bg-indigo-600/30 text-indigo-300 font-bold border border-indigo-500/40"
                      : "bg-neutral-800 hover:bg-neutral-700 text-zinc-300 border border-white/5"
                  } cursor-pointer`}
                >
                  {opt}
                </button>
              ))}
            </motion.div>
          )}

          {activeTab === "all" && (
            <motion.div
              key="all"
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -5 }}
              transition={{ duration: 0.12 }}
              className="grid grid-cols-2 gap-1.5 w-full overflow-y-auto max-h-[145px] pr-0.5 scrollbar-hide"
            >
              {animOverallOptions.map((opt) => (
                <button
                  key={opt}
                  onClick={() => handleSelectAnimOverall(opt)}
                  className={`py-1.5 px-2 rounded-lg text-[9px] font-semibold transition-all ${
                    currentAnimOverall === opt
                      ? "bg-indigo-600/30 text-indigo-300 font-bold border border-indigo-500/40"
                      : "bg-neutral-800 hover:bg-neutral-700 text-zinc-300 border border-white/5"
                  } cursor-pointer`}
                >
                  {opt}
                </button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.div>
  );
};
