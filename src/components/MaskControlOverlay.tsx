import React, { useState, useRef, useEffect } from "react";

interface MaskControlOverlayProps {
  clip: any;
  updateClipsProperties: (clipIds: string[], updates: any) => void;
  onShowAdjustments?: () => void;
}

export function MaskControlOverlay({ clip, updateClipsProperties, onShowAdjustments }: MaskControlOverlayProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [activeDrag, setActiveDrag] = useState<"width" | "height" | "roundness" | null>(null);

  if (!clip) return null;

  const maskType = clip.maskType;
  const maskWidth = clip.maskWidth ?? (maskType === "half" ? 100 : 60);
  const maskHeight = clip.maskHeight ?? (maskType === "half" ? 50 : 60);
  const maskRoundness = clip.maskRoundness ?? 15;
  const maskPositionX = clip.maskPositionX ?? 0.5;
  const maskPositionY = clip.maskPositionY ?? 0.5;

  const halfW = maskWidth / 2;
  const halfH = maskHeight / 2;

  const handleStartDrag = (e: React.MouseEvent | React.TouchEvent, type: "width" | "height" | "roundness") => {
    e.preventDefault();
    e.stopPropagation();
    setActiveDrag(type);
  };

  const longPressTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const startPress = (e: React.MouseEvent | React.TouchEvent) => {
    if (longPressTimerRef.current) clearTimeout(longPressTimerRef.current);
    longPressTimerRef.current = setTimeout(() => {
      onShowAdjustments?.();
    }, 450); // 450ms hold delay for mask long-tap adjustment activation
  };

  const endPress = () => {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

  useEffect(() => {
    if (!activeDrag) return;

    const handleMove = (clientX: number, clientY: number) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const w = rect.width;
      const h = rect.height;

      if (activeDrag === "width") {
        const centerX = rect.left + maskPositionX * w;
        const dx = Math.abs(clientX - centerX);
        const newWidth = Math.max(5, Math.min(100, Math.round((dx / w) * 200)));
        updateClipsProperties([clip.id], { maskWidth: newWidth });
      } else if (activeDrag === "height") {
        if (maskType === "half") {
          const dy = clientY - rect.top;
          const newHeight = Math.max(0, Math.min(100, Math.round((dy / h) * 100)));
          updateClipsProperties([clip.id], { maskHeight: newHeight, maskPositionY: newHeight / 100 });
        } else {
          const centerY = rect.top + maskPositionY * h;
          const dy = Math.abs(clientY - centerY);
          const newHeight = Math.max(5, Math.min(100, Math.round((dy / h) * 200)));
          updateClipsProperties([clip.id], { maskHeight: newHeight });
        }
      } else if (activeDrag === "roundness") {
        const rightEdgeX = rect.left + maskPositionX * w + (halfW / 100) * w;
        const topEdgeY = rect.top + maskPositionY * h - (halfH / 100) * h;
        const dx = rightEdgeX - clientX;
        const dy = clientY - topEdgeY;
        const dragVal = Math.max(0, Math.min(100, Math.round((dx + dy) / 2)));
        updateClipsProperties([clip.id], { maskRoundness: dragVal });
      }
    };

    const onMouseMove = (e: MouseEvent) => {
      handleMove(e.clientX, e.clientY);
    };

    const onTouchMove = (e: TouchEvent) => {
      if (e.touches.length > 0) {
        handleMove(e.touches[0].clientX, e.touches[0].clientY);
      }
    };

    const onMouseUp = () => setActiveDrag(null);
    const onTouchEnd = () => setActiveDrag(null);

    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    window.addEventListener("touchmove", onTouchMove);
    window.addEventListener("touchend", onTouchEnd);

    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
      window.removeEventListener("touchmove", onTouchMove);
      window.removeEventListener("touchend", onTouchEnd);
    };
  }, [activeDrag, clip.id, maskType, halfW, halfH, maskPositionX, maskPositionY, updateClipsProperties]);

  if (!maskType || maskType === "none") return null;

  return (
    <div
      ref={containerRef}
      className="absolute inset-0 pointer-events-none z-50 overflow-visible"
    >
      {/* SVG stroke representing the mask boundary in the preview */}
      <svg className="absolute inset-0 w-full h-full pointer-events-none overflow-visible">
        {maskType === "circle" && (
          <ellipse
            cx={`${maskPositionX * 100}%`}
            cy={`${maskPositionY * 100}%`}
            rx={`${halfW}%`}
            ry={`${halfH}%`}
            fill="rgba(168, 85, 247, 0.04)"
            stroke="#a855f7"
            strokeWidth="3"
            strokeDasharray="4 3"
            className="drop-shadow-[0_2px_4px_rgba(0,0,0,0.7)] cursor-pointer pointer-events-auto hover:stroke-[#c084fc] transition-colors"
            onMouseDown={startPress}
            onTouchStart={startPress}
            onMouseUp={endPress}
            onTouchEnd={endPress}
            onMouseLeave={endPress}
          />
        )}
        {maskType === "square" && (
          <rect
            x={`${maskPositionX * 100 - halfW}%`}
            y={`${maskPositionY * 100 - halfH}%`}
            width={`${maskWidth}%`}
            height={`${maskHeight}%`}
            rx={`${maskRoundness / 2}%`}
            ry={`${maskRoundness / 2}%`}
            fill="rgba(34, 197, 94, 0.04)"
            stroke="#22c55e"
            strokeWidth="3"
            strokeDasharray="4 3"
            className="drop-shadow-[0_2px_4px_rgba(0,0,0,0.7)] cursor-pointer pointer-events-auto hover:stroke-[#4ade80] transition-colors"
            onMouseDown={startPress}
            onTouchStart={startPress}
            onMouseUp={endPress}
            onTouchEnd={endPress}
            onMouseLeave={endPress}
          />
        )}
        {maskType === "half" && (
          <>
            <line
              x1="0%"
              y1={`${maskPositionY * 100}%`}
              x2="100%"
              y2={`${maskPositionY * 100}%`}
              stroke="transparent"
              strokeWidth="18"
              className="cursor-row-resize pointer-events-auto"
              onMouseDown={startPress}
              onTouchStart={startPress}
              onMouseUp={endPress}
              onTouchEnd={endPress}
              onMouseLeave={endPress}
            />
            <line
              x1="0%"
              y1={`${maskPositionY * 100}%`}
              x2="100%"
              y2={`${maskPositionY * 100}%`}
              stroke="#3b82f6"
              strokeWidth="2"
              strokeDasharray="4 3"
              className="drop-shadow-[0_1px_3px_rgba(0,0,0,0.8)] pointer-events-none"
            />
          </>
        )}
      </svg>

      {/* Interactive Controls & Arrow Handles */}
      {(maskType === "circle" || maskType === "square") && (
        <div
          style={{
            left: `${maskPositionX * 100 + halfW}%`,
            top: `${maskPositionY * 100}%`,
            transform: "translate(-50%, -50%)"
          }}
          onMouseDown={(e) => handleStartDrag(e, "width")}
          onTouchStart={(e) => handleStartDrag(e, "width")}
          className="absolute w-7 h-7 flex items-center justify-center rounded-full bg-zinc-950/90 text-white border-2 border-white shadow-xl cursor-ew-resize pointer-events-auto hover:scale-110 active:scale-95 transition-transform z-50 animate-pulse"
          title="Drag to adjust width"
        >
          <span className="text-[14px] font-bold select-none leading-none">↔</span>
        </div>
      )}

      {(maskType === "circle" || maskType === "square") && (
        <div
          style={{
            left: `${maskPositionX * 100}%`,
            top: `${maskPositionY * 100 + halfH}%`,
            transform: "translate(-50%, -50%)"
          }}
          onMouseDown={(e) => handleStartDrag(e, "height")}
          onTouchStart={(e) => handleStartDrag(e, "height")}
          className="absolute w-7 h-7 flex items-center justify-center rounded-full bg-zinc-950/90 text-white border-2 border-white shadow-xl cursor-ns-resize pointer-events-auto hover:scale-110 active:scale-95 transition-transform z-50 animate-pulse"
          title="Drag to adjust height"
        >
          <span className="text-[14px] font-bold select-none leading-none">↕</span>
        </div>
      )}

      {maskType === "half" && (
        <div
          style={{
            left: `50%`,
            top: `${maskPositionY * 100}%`,
            transform: "translate(-50%, -50%)"
          }}
          onMouseDown={(e) => handleStartDrag(e, "height")}
          onTouchStart={(e) => handleStartDrag(e, "height")}
          className="absolute w-7 h-7 flex items-center justify-center rounded-full bg-zinc-950/90 text-[#3b82f6] border-2 border-[#3b82f6] shadow-xl cursor-ns-resize pointer-events-auto hover:scale-110 active:scale-95 transition-transform z-50"
          title="Drag and adjust split height"
        >
          <span className="text-[14px] font-bold select-none leading-none">↕</span>
        </div>
      )}

      {maskType === "square" && (
        <div
          style={{
            left: `${maskPositionX * 100 + halfW - (maskRoundness / 4)}%`,
            top: `${maskPositionY * 100 - halfH + (maskRoundness / 4)}%`,
            transform: "translate(-50%, -50%)"
          }}
          onMouseDown={(e) => handleStartDrag(e, "roundness")}
          onTouchStart={(e) => handleStartDrag(e, "roundness")}
          className="absolute w-6 h-6 flex items-center justify-center rounded-full bg-emerald-950 text-emerald-400 border-2 border-emerald-400 shadow-xl cursor-pointer pointer-events-auto hover:scale-110 active:scale-95 transition-transform z-50"
          title="Drag to adjust corner rounding"
        >
          <span className="text-[11px] font-bold select-none leading-none">◯</span>
        </div>
      )}
    </div>
  );
}
