import React from "react";
import { motion } from "motion/react";
import { ArrowUp, Move, Check } from "lucide-react";
import { Clip, Layer } from "../types";
import { CompactRulerControl, ScaleRulerControl } from "./Controls";

interface MovePanelProps {
  selectedClipId: string | null;
  clips: Clip[];
  layers: Layer[];
  setClips: React.Dispatch<React.SetStateAction<Clip[]>>;
  currentSelectedClipInterpolatedProps: any;
  updateClipsProperties: (ids: string[], updates: any) => void;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
  setToastMessage: (msg: string | null) => void;
  activeTransformTab: string;
  setActiveTransformTab: (tab: string) => void;
}

export const MovePanel: React.FC<MovePanelProps> = ({
  selectedClipId,
  clips,
  layers,
  setClips,
  currentSelectedClipInterpolatedProps,
  updateClipsProperties,
  renderCopyPasteButtons,
  setActiveExpandedMenu,
  setToastMessage,
  activeTransformTab,
  setActiveTransformTab,
}) => {
  if (!selectedClipId) return null;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95, y: 10 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95, y: 10 }}
      transition={{ duration: 0.2 }}
      className="flex flex-col h-auto max-h-[190px] shrink-0 overflow-y-auto scrollbar-hide pb-2"
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
                  updateClipsProperties([selectedClipId], { translateX: val });
                }}
                onReset={() => {
                  updateClipsProperties([selectedClipId], { translateX: 0 });
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
                  updateClipsProperties([selectedClipId], { translateY: val });
                }}
                onReset={() => {
                  updateClipsProperties([selectedClipId], { translateY: 0 });
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
                updateClipsProperties([selectedClipId], { scale: val });
              }}
              onReset={() => {
                updateClipsProperties([selectedClipId], { scale: 1 });
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
                        updateClipsProperties([selectedClipId], { scale: s });
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
                  updateClipsProperties([selectedClipId], { rotation: val });
                }}
                onReset={() => {
                  updateClipsProperties([selectedClipId], { rotation: 0 });
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
                        updateClipsProperties([selectedClipId], { rotation: angle });
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
  );
};
