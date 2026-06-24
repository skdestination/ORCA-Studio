import React, { useRef } from "react";

interface CropControlOverlayProps {
  clip: any;
  updateClipsProperties: (clipIds: string[], updates: any) => void;
}

export function CropControlOverlay({ clip, updateClipsProperties }: CropControlOverlayProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  const cropTop = clip.cropRect?.top || 0;
  const cropRight = clip.cropRect?.right || 0;
  const cropBottom = clip.cropRect?.bottom || 0;
  const cropLeft = clip.cropRect?.left || 0;

  const handleCropEdgeDrag = (
    e: React.PointerEvent<HTMLDivElement>,
    edge: 'tl' | 'tr' | 'bl' | 'br' | 't' | 'b' | 'l' | 'r'
  ) => {
    e.stopPropagation();
    e.preventDefault();
    
    const target = e.currentTarget;
    target.setPointerCapture(e.pointerId);

    const container = target.closest(".media-preview-container");
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const initialCrop = {
      top: clip.cropRect?.top || 0,
      right: clip.cropRect?.right || 0,
      bottom: clip.cropRect?.bottom || 0,
      left: clip.cropRect?.left || 0,
    };

    const startX = e.clientX;
    const startY = e.clientY;

    const handlePointerMove = (moveEv: PointerEvent) => {
      const deltaX = moveEv.clientX - startX;
      const deltaY = moveEv.clientY - startY;

      const deltaXPercent = (deltaX / rect.width) * 100;
      const deltaYPercent = (deltaY / rect.height) * 100;

      let newCrop = { ...initialCrop };

      if (edge.includes('t')) {
        newCrop.top = Math.max(0, Math.min(100 - newCrop.bottom - 5, initialCrop.top + deltaYPercent));
      }
      if (edge.includes('b')) {
        newCrop.bottom = Math.max(0, Math.min(100 - newCrop.top - 5, initialCrop.bottom - deltaYPercent));
      }
      if (edge.includes('l')) {
        newCrop.left = Math.max(0, Math.min(100 - newCrop.right - 5, initialCrop.left + deltaXPercent));
      }
      if (edge.includes('r')) {
        newCrop.right = Math.max(0, Math.min(100 - newCrop.left - 5, initialCrop.right - deltaXPercent));
      }

      updateClipsProperties([clip.id], { cropRect: newCrop });
    };

    const handlePointerUp = (upEv: PointerEvent) => {
      target.releasePointerCapture(upEv.pointerId);
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
  };

  return (
    <div
      ref={containerRef}
      className="absolute border-2 border-white/95 grid grid-cols-3 grid-rows-3 shadow-[0_0_0_9999px_rgba(0,0,0,0.55)] z-50 pointer-events-auto"
      style={{
        top: `${cropTop}%`,
        right: `${cropRight}%`,
        bottom: `${cropBottom}%`,
        left: `${cropLeft}%`,
        touchAction: "none"
      }}
    >
      <div className="border-r border-b border-white/25"></div>
      <div className="border-r border-b border-white/25"></div>
      <div className="border-b border-white/25"></div>
      <div className="border-r border-b border-white/25"></div>
      <div className="border-r border-b border-white/25"></div>
      <div className="border-b border-white/25"></div>
      <div className="border-r border-white/25"></div>
      <div className="border-r border-white/25"></div>
      <div></div>

      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'tl')}
        className="absolute -top-1 -left-1 w-4 h-4 border-t-4 border-l-4 border-white pointer-events-auto cursor-nwse-resize z-55 hover:scale-110 active:scale-95 transition-transform"
      />
      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'tr')}
        className="absolute -top-1 -right-1 w-4 h-4 border-t-4 border-r-4 border-white pointer-events-auto cursor-nesw-resize z-55 hover:scale-110 active:scale-95 transition-transform"
      />
      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'bl')}
        className="absolute -bottom-1 -left-1 w-4 h-4 border-b-4 border-l-4 border-white pointer-events-auto cursor-nesw-resize z-55 hover:scale-110 active:scale-95 transition-transform"
      />
      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'br')}
        className="absolute -bottom-1 -right-1 w-4 h-4 border-b-4 border-r-4 border-white pointer-events-auto cursor-nwse-resize z-55 hover:scale-110 active:scale-95 transition-transform"
      />

      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 't')}
        className="absolute top-0 left-1/2 -translate-x-1/2 -translate-y-1 w-7 h-1.5 bg-white rounded-full border border-zinc-900 pointer-events-auto cursor-ns-resize z-55 hover:scale-y-125 transition-transform"
      />
      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'b')}
        className="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-1 w-7 h-1.5 bg-white rounded-full border border-zinc-900 pointer-events-auto cursor-ns-resize z-55 hover:scale-y-125 transition-transform"
      />
      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'l')}
        className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-1 h-7 w-1.5 bg-white rounded-full border border-zinc-900 pointer-events-auto cursor-ew-resize z-55 hover:scale-x-125 transition-transform"
      />
      <div
        onPointerDown={(e) => handleCropEdgeDrag(e, 'r')}
        className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-1 h-7 w-1.5 bg-white rounded-full border border-zinc-900 pointer-events-auto cursor-ew-resize z-55 hover:scale-x-125 transition-transform"
      />
    </div>
  );
}
