import React from "react";
import { motion } from "motion/react";
import { Check } from "lucide-react";
import { CompactRulerControl } from "./Controls";

interface AdjustPanelProps {
  selectedClipId: string | null;
  currentSelectedClipInterpolatedProps: any;
  updateClipsProperties: (ids: string[], updates: any) => void;
  renderCopyPasteButtons: (type: string) => React.ReactNode;
  setActiveExpandedMenu: (menu: string | null) => void;
}

export const AdjustPanel: React.FC<AdjustPanelProps> = ({
  selectedClipId,
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
      <div className="flex flex-col gap-2.5 max-h-[140px] overflow-y-auto scrollbar-hide px-2 pb-2">
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
  );
};
