import React, { useRef } from "react";
import { Check } from "lucide-react";

export function MinusIcon({ size = 24 }: { size?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <line x1="5" y1="12" x2="19" y2="12"></line>
    </svg>
  );
}

export function CompactRulerControl({
  value,
  min,
  max,
  onChange,
  onReset,
  onClose,
  sensitivity = 1,
  label = "Value",
  step,
  unit = "",
  style,
  rulerStyle,
  labelStyle,
  headerStyle,
  hideTicks = false,
  size = "normal",
}: {
  value: number;
  min: number;
  max: number;
  onChange: (val: number) => void;
  onReset: () => void;
  onClose?: () => void;
  sensitivity?: number;
  label?: string;
  step?: number;
  unit?: string;
  style?: React.CSSProperties;
  rulerStyle?: React.CSSProperties;
  labelStyle?: React.CSSProperties;
  headerStyle?: React.CSSProperties;
  hideTicks?: boolean;
  size?: "small" | "normal";
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isDragging = useRef(false);
  const startX = useRef(0);
  const startValue = useRef(0);

  const isSmall = size === "small";

  // Dynamic Intervals Logic based on Range
  const range = max - min;
  let majorInterval = 10;
  let minorInterval = 2;

  if (range <= 2) {
    majorInterval = 0.5;
    minorInterval = 0.1;
  } else if (range <= 5) {
    majorInterval = 1;
    minorInterval = 0.2;
  } else if (range <= 15) {
    majorInterval = 2;
    minorInterval = 0.5;
  } else if (range <= 50) {
    majorInterval = 10;
    minorInterval = 2;
  } else if (range <= 150) {
    majorInterval = 20;
    minorInterval = 5;
  } else if (range <= 400) {
    majorInterval = 50;
    minorInterval = 10;
  } else {
    majorInterval = 100;
    minorInterval = 20;
  }

  // Configuration for spacing
  const MAJOR_TICK_SPACING = size === "small" ? 36 : 60; // Pixels between major ticks
  const pixelsPerUnit = MAJOR_TICK_SPACING / majorInterval;

  const handlePointerDown = (e: React.PointerEvent) => {
    isDragging.current = true;
    startX.current = e.clientX;
    startValue.current = value;
    document.body.style.cursor = "ew-resize";
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging.current) return;
    const deltaX = e.clientX - startX.current;

    // Direct physical scroll tracking matching the grab state
    let newVal = startValue.current - (deltaX / pixelsPerUnit) * sensitivity;
    newVal = Math.max(min, Math.min(max, newVal));

    if (step) {
      const precision = step.toString().includes(".")
        ? step.toString().split(".")[1].length
        : 0;
      const rounded = Math.round(newVal / step) * step;
      newVal = Number(rounded.toFixed(precision));
    } else {
      newVal = Math.round(newVal);
    }

    onChange(newVal);
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    isDragging.current = false;
    document.body.style.cursor = "";
    if ((e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    }
  };

  // Virtual Windowing: only render nearby ticks
  const visibleWidth = 360; // Safe padded range
  const valueRangeInWidth = visibleWidth / pixelsPerUnit;
  const startVal = Math.max(
    min,
    Math.floor((value - valueRangeInWidth) / minorInterval) * minorInterval
  );
  const endVal = Math.min(
    max,
    Math.ceil((value + valueRangeInWidth) / minorInterval) * minorInterval
  );

  const ticks: number[] = [];
  let count = 0;
  for (let val = startVal; val <= endVal && count < 100; val += minorInterval) {
    // Avoid floating point rounding accumulation
    const roundedVal = Number(val.toFixed(5));
    if (roundedVal >= min - 0.0001 && roundedVal <= max + 0.0001) {
      ticks.push(roundedVal);
    }
    count++;
  }

  // Display form factor formatted neatly
  const displayVal = Number(value.toFixed(2));

  if (hideTicks) {
    const percentage = ((value - min) / (max - min)) * 100;
    return (
      <div className={`flex flex-col w-full select-none ${size === "small" ? "mt-0 pb-0.5" : "mt-0.5 pb-1"} font-sans`} style={style}>
        <div className="flex justify-between items-center mb-1 pl-0.5 pr-0.5" style={headerStyle}>
          <span className={size === "small" ? "text-[8.5px] font-bold text-zinc-400 uppercase tracking-wider" : "text-[10px] font-bold text-zinc-400 uppercase tracking-wider"} style={labelStyle}>
            {label}
          </span>
          <div className="flex items-center gap-1.5">
            <span className={size === "small" ? "text-[8px] text-indigo-400 font-mono font-bold w-10 text-align-right text-right bg-indigo-500/10 px-1 py-0.5 rounded border border-indigo-500/10 leading-none" : "text-[10px] text-indigo-400 font-mono font-bold w-12 text-align-right text-right bg-indigo-500/10 px-1.5 py-0.5 rounded border border-indigo-500/10"}>
              {displayVal}
              {unit}
            </span>
            <button
              className={size === "small" ? "text-[7.5px] w-3.5 h-3.5 flex items-center justify-center bg-zinc-800 rounded-full text-zinc-400 hover:text-white hover:bg-zinc-700 transition-all font-bold" : "text-[8.5px] w-4.5 h-4.5 flex items-center justify-center bg-zinc-800 rounded-full text-zinc-400 hover:text-white hover:bg-zinc-700 transition-all font-bold"}
              onClick={onReset}
              title={`Reset ${label}`}
            >
              R
            </button>
            {onClose && (
              <>
                <div className="w-px h-3.5 bg-zinc-700 mx-0.5"></div>
                <button
                  onClick={onClose}
                  className="text-zinc-400 hover:text-white ml-0.5"
                >
                  <Check size={14} />
                </button>
              </>
            )}
          </div>
        </div>
        <div className={size === "small" ? "h-[14px] relative flex items-center px-1" : "h-[24px] relative flex items-center px-1"}>
          <input
            type="range"
            min={min}
            max={max}
            step={step || 1}
            value={value}
            onChange={(e) => onChange(parseFloat(e.target.value))}
            className="w-full h-1 bg-zinc-750 rounded-full appearance-none cursor-pointer outline-none accent-indigo-500"
            style={{
              background: `linear-gradient(to right, rgb(99, 102, 241) ${percentage}%, rgba(24, 24, 27, 0.45) ${percentage}%)`
            }}
          />
        </div>
      </div>
    );
  }

  return (
    <div className={`flex flex-col w-full select-none ${size === "small" ? "mt-0 pb-0.5" : "mt-0.5 pb-1.5"} font-sans`} style={style}>
      <div className="flex justify-between items-center mb-1 pl-0.5 pr-0.5" style={headerStyle}>
        <span className={size === "small" ? "text-[8.5px] font-bold text-zinc-400 uppercase tracking-wider" : "text-[10px] font-bold text-zinc-400 uppercase tracking-wider"} style={labelStyle}>
          {label}
        </span>
        <div className="flex items-center gap-1.5">
          <span className={size === "small" ? "text-[8px] text-indigo-400 font-mono font-bold w-10 text-align-right text-right bg-indigo-500/10 px-1 py-0.5 rounded border border-indigo-500/10 leading-none" : "text-[10px] text-indigo-400 font-mono font-bold w-12 text-align-right text-right bg-indigo-500/10 px-1.5 py-0.5 rounded border border-indigo-500/10"}>
            {displayVal}
            {unit}
          </span>
          <button
            className={size === "small" ? "text-[7.5px] w-3.5 h-3.5 flex items-center justify-center bg-zinc-800 rounded-full text-zinc-400 hover:text-white hover:bg-zinc-700 transition-all font-bold" : "text-[8.5px] w-4.5 h-4.5 flex items-center justify-center bg-zinc-800 rounded-full text-zinc-400 hover:text-white hover:bg-zinc-700 transition-all font-bold"}
            onClick={onReset}
            title={`Reset ${label}`}
          >
            R
          </button>
          {onClose && (
            <>
              <div className="w-px h-3.5 bg-zinc-700 mx-0.5"></div>
              <button
                onClick={onClose}
                className="text-zinc-400 hover:text-white ml-0.5"
              >
                <Check size={14} />
              </button>
            </>
          )}
        </div>
      </div>

      <div
        ref={containerRef}
        className={size === "small" ? "h-[22px] mx-0 relative overflow-hidden cursor-ew-resize touch-none bg-zinc-950/20 rounded-lg border border-white/5 shadow-inner" : "h-[42px] mx-0 relative overflow-hidden cursor-ew-resize touch-none bg-zinc-950/20 rounded-xl border border-white/5 shadow-inner"}
        style={rulerStyle}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      >
        <div
          className="absolute top-0 bottom-0 left-1/2"
          style={{ transform: `translateX(${- (value - min) * pixelsPerUnit}px)` }}
        >
          {ticks.map((tickVal) => {
            const isMajor = Math.abs(tickVal % majorInterval) < (minorInterval / 4);
            const isCurrent = Math.abs(value - tickVal) < (minorInterval / 2.1);

            return (
              <div
                key={tickVal}
                className="absolute flex flex-col items-center justify-center pointer-events-none h-full"
                style={{
                  left: `${(tickVal - min) * pixelsPerUnit}px`,
                  transform: "translateX(-50%)",
                }}
              >
                <div
                  className={`w-[1.5px] rounded-full transition-all duration-150 ${
                    isCurrent
                      ? (isSmall ? "h-[14px] bg-indigo-400" : "h-[20px] bg-indigo-400 shadow-[0_0_8px_rgba(99,102,241,0.6)]")
                      : isMajor
                      ? (isSmall ? "h-[8px] bg-white/50" : "h-[12px] bg-white/60")
                      : (isSmall ? "h-[4px] bg-white/20" : "h-[6px] bg-white/20")
                  }`}
                />
                {isMajor && !isSmall && (
                  <span
                    className={`absolute bottom-1 text-[8px] translate-y-[8px] tracking-tight ${
                      isCurrent ? "text-indigo-400 font-bold" : "text-zinc-500 font-medium"
                    }`}
                  >
                    {Number(tickVal.toFixed(2))}
                  </span>
                )}
              </div>
            );
          })}
        </div>
        <div className={size === "small" ? "absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[2px] h-[14px] bg-indigo-500 rounded-full pointer-events-none z-10" : "absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-[11px] w-[2px] h-[22px] bg-indigo-500 rounded-full pointer-events-none shadow-[0_0_6px_rgba(99,102,241,0.8)] z-10"} />
      </div>
    </div>
  );
}

const SPEED_VALUES = [
  0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5,
  1.6, 1.7, 1.8, 1.9, 2.0, 3.0, 4.0, 5.0, 10.0, 20.0, 50.0,
];

function valToPos(val: number) {
  for (let i = 0; i < SPEED_VALUES.length - 1; i++) {
    if (val >= SPEED_VALUES[i] && val <= SPEED_VALUES[i + 1]) {
      const ratio =
        (val - SPEED_VALUES[i]) / (SPEED_VALUES[i + 1] - SPEED_VALUES[i]);
      return i + ratio;
    }
  }
  if (val <= SPEED_VALUES[0]) return 0;
  return SPEED_VALUES.length - 1;
}

function posToVal(pos: number) {
  if (pos <= 0) return SPEED_VALUES[0];
  if (pos >= SPEED_VALUES.length - 1)
    return SPEED_VALUES[SPEED_VALUES.length - 1];
  const i = Math.floor(pos);
  const ratio = pos - i;
  return SPEED_VALUES[i] + ratio * (SPEED_VALUES[i + 1] - SPEED_VALUES[i]);
}

export function SpeedRulerControl({
  value,
  onChange,
  onReset,
  onClose,
  children,
}: {
  value: number;
  onChange: (val: number) => void;
  onReset: () => void;
  onClose: () => void;
  children?: React.ReactNode;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isDragging = useRef(false);
  const startX = useRef(0);
  const startPos = useRef(0);

  const TICK_SPACING = 30;
  const VIRTUAL_POS = valToPos(value);

  const handlePointerDown = (e: React.PointerEvent) => {
    isDragging.current = true;
    startX.current = e.clientX;
    startPos.current = valToPos(value);
    document.body.style.cursor = "ew-resize";
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging.current) return;
    const deltaX = e.clientX - startX.current;

    let newPos = startPos.current - deltaX / TICK_SPACING;
    let newVal = posToVal(newPos);

    const nearestIndex = Math.round(newPos);
    if (Math.abs(newPos - nearestIndex) < 0.1) {
      newVal =
        SPEED_VALUES[
          Math.max(0, Math.min(SPEED_VALUES.length - 1, nearestIndex))
        ];
    } else {
      newVal = Number(newVal.toFixed(2));
    }

    onChange(newVal);
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    isDragging.current = false;
    document.body.style.cursor = "";
    if ((e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    }
  };

  return (
    <div className="flex flex-col w-full select-none mt-0.5 pb-1.5 font-sans">
      <div className="flex justify-between items-center mb-1.5 pl-0.5 pr-0.5">
        <span className="text-[10px] font-semibold text-white/90">Speed</span>
        <div className="flex items-center gap-1.5">
          {children}
          <span className="text-[10px] text-white font-mono w-10 text-right mr-1">
            {value}x
          </span>
          <button
            className="text-[8.5px] w-4.5 h-4.5 flex items-center justify-center bg-zinc-800 rounded-full text-zinc-400 hover:text-white transition-colors"
            onClick={onReset}
          >
            R
          </button>
          <div className="w-px h-3.5 bg-zinc-700 mx-0.5"></div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-white ml-0.5"
          >
            <Check size={14} />
          </button>
        </div>
      </div>

      <div
        ref={containerRef}
        className="h-[38px] mx-0 relative overflow-hidden cursor-ew-resize touch-none"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      >
        <div
          className="absolute top-0 bottom-0 left-1/2"
          style={{ transform: `translateX(${-VIRTUAL_POS * TICK_SPACING}px)` }}
        >
          {SPEED_VALUES.map((speed, i) => {
            const isMajor =
              speed === 1.0 ||
              speed === 2.0 ||
              speed === 5.0 ||
              speed === 10.0 ||
              speed === 20.0 ||
              speed === 50.0 ||
              speed === 0.1 ||
              speed === 0.5;
            const isCurrent = Math.round(VIRTUAL_POS) === i;

            return (
              <div
                key={i}
                className="absolute flex flex-col items-center justify-center pointer-events-none h-full"
                style={{
                  left: `${i * TICK_SPACING}px`,
                  transform: "translateX(-50%)",
                }}
              >
                <div
                  className={`w-[2px] rounded-full transition-all duration-150 ${isCurrent ? "h-[18px] bg-yellow-400 shadow-[0_0_8px_rgba(250,204,21,0.5)]" : isMajor ? "h-[12px] bg-white/60" : "h-[6px] bg-white/20"}`}
                />
                {isMajor && (
                  <span
                    className={`absolute bottom-0 text-[8px] translate-y-[9px] ${isCurrent ? "text-yellow-400 font-bold" : "text-zinc-500"}`}
                  >
                    {speed}x
                  </span>
                )}
              </div>
            );
          })}
        </div>
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-[9px] w-[2px] h-[22px] bg-white rounded-full pointer-events-none shadow-[0_0_6px_rgba(255,255,255,0.8)]" />
      </div>
    </div>
  );
}

const SCALE_VALUES = [
  0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
  1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0,
  2.5, 3.0, 3.5, 4.0, 4.5, 5.0
];

function valToScalePos(val: number) {
  for (let i = 0; i < SCALE_VALUES.length - 1; i++) {
    if (val >= SCALE_VALUES[i] && val <= SCALE_VALUES[i + 1]) {
      const ratio =
        (val - SCALE_VALUES[i]) / (SCALE_VALUES[i + 1] - SCALE_VALUES[i]);
      return i + ratio;
    }
  }
  if (val <= SCALE_VALUES[0]) return 0;
  return SCALE_VALUES.length - 1;
}

function scalePosToVal(pos: number) {
  if (pos <= 0) return SCALE_VALUES[0];
  if (pos >= SCALE_VALUES.length - 1)
    return SCALE_VALUES[SCALE_VALUES.length - 1];
  const i = Math.floor(pos);
  const ratio = pos - i;
  return SCALE_VALUES[i] + ratio * (SCALE_VALUES[i + 1] - SCALE_VALUES[i]);
}

export function ScaleRulerControl({
  value,
  onChange,
  onReset,
  onClose,
  label = "Scale",
}: {
  value: number;
  onChange: (val: number) => void;
  onReset: () => void;
  onClose?: () => void;
  label?: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isDragging = useRef(false);
  const startX = useRef(0);
  const startPos = useRef(0);

  const TICK_SPACING = 30;
  const VIRTUAL_POS = valToScalePos(value);

  const handlePointerDown = (e: React.PointerEvent) => {
    isDragging.current = true;
    startX.current = e.clientX;
    startPos.current = valToScalePos(value);
    document.body.style.cursor = "ew-resize";
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging.current) return;
    const deltaX = e.clientX - startX.current;

    let newPos = startPos.current - deltaX / TICK_SPACING;
    let newVal = scalePosToVal(newPos);

    const nearestIndex = Math.round(newPos);
    if (Math.abs(newPos - nearestIndex) < 0.1) {
      newVal =
        SCALE_VALUES[
          Math.max(0, Math.min(SCALE_VALUES.length - 1, nearestIndex))
        ];
    } else {
      newVal = Number(newVal.toFixed(2));
    }

    onChange(newVal);
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    isDragging.current = false;
    document.body.style.cursor = "";
    if ((e.currentTarget as HTMLElement).hasPointerCapture(e.pointerId)) {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    }
  };

  return (
    <div className="flex flex-col w-full select-none mt-0.5 pb-1.5 font-sans">
      <div className="flex justify-between items-center mb-1.5 pl-0.5 pr-0.5">
        <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider">{label}</span>
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] text-indigo-400 font-mono font-bold w-12 text-right mr-1 bg-indigo-500/10 px-1.5 py-0.5 rounded border border-indigo-500/10">
            {value.toFixed(2)}x
          </span>
          <button
            className="text-[8.5px] w-4.5 h-4.5 flex items-center justify-center bg-zinc-800 rounded-full text-zinc-400 hover:text-white hover:bg-zinc-700 transition-all font-bold"
            onClick={onReset}
            title="Reset Scale Factor"
          >
            R
          </button>
          {onClose && (
            <>
              <div className="w-px h-3.5 bg-zinc-700 mx-0.5"></div>
              <button
                onClick={onClose}
                className="text-zinc-400 hover:text-white ml-0.5"
              >
                <Check size={14} />
              </button>
            </>
          )}
        </div>
      </div>

      <div
        ref={containerRef}
        className="h-[42px] mx-0 relative overflow-hidden cursor-ew-resize touch-none bg-zinc-950/20 rounded-xl border border-white/5 shadow-inner"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      >
        <div
          className="absolute top-0 bottom-0 left-1/2"
          style={{ transform: `translateX(${-VIRTUAL_POS * TICK_SPACING}px)` }}
        >
          {SCALE_VALUES.map((scaleValue, i) => {
            const isMajor =
              scaleValue === 0.1 ||
              scaleValue === 0.5 ||
              scaleValue === 1.0 ||
              scaleValue === 1.5 ||
              scaleValue === 2.0 ||
              scaleValue === 3.0 ||
              scaleValue === 4.0 ||
              scaleValue === 5.0;
            const isCurrent = Math.round(VIRTUAL_POS) === i;

            return (
              <div
                key={i}
                className="absolute flex flex-col items-center justify-center pointer-events-none h-full"
                style={{
                  left: `${i * TICK_SPACING}px`,
                  transform: "translateX(-50%)",
                }}
              >
                <div
                  className={`w-[2px] rounded-full transition-all duration-150 ${isCurrent ? "h-[20px] bg-indigo-400 shadow-[0_0_8px_rgba(99,102,241,0.6)]" : isMajor ? "h-[12px] bg-white/60" : "h-[6px] bg-white/20"}`}
                />
                {isMajor && (
                  <span
                    className={`absolute bottom-1 text-[8px] translate-y-[8px] ${isCurrent ? "text-indigo-400 font-bold" : "text-zinc-500"}`}
                  >
                    {scaleValue === 1.0 ? "1x" : `${scaleValue}x`}
                  </span>
                )}
              </div>
            );
          })}
        </div>
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-[11px] w-[2px] h-[22px] bg-indigo-500 rounded-full pointer-events-none shadow-[0_0_6px_rgba(99,102,241,0.8)]" />
      </div>
    </div>
  );
}

