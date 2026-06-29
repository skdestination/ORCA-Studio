import React from 'react';
import { Clip } from '../types';

export interface TextRendererProps {
  activeClip: Clip;
  activeClipRaw: Clip;
  currentTime: number;
  selectedClipId: string | null;
  transformStyle: React.CSSProperties;
}

export const TextRenderer = React.memo(({
  activeClip,
  activeClipRaw,
  currentTime,
  selectedClipId,
  transformStyle,
}: TextRendererProps) => {
  const textElapsed = currentTime - activeClipRaw.leftSeconds;
  const tIn = Math.min(1, Math.max(0, textElapsed / 1.0));
  const tShort = Math.min(1, Math.max(0, textElapsed / 0.5));
  const anim = activeClip.textAnimation;
  const baseOpacity = activeClip.opacity ?? 1;

  const animationStyle = React.useMemo(() => {
    if (!anim || anim === "None") return {};

    if (anim === "Fade In") {
      return { opacity: baseOpacity * tIn };
    }
    if (anim === "Slide Up") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateY(${(1 - tIn) * 50}px)`
      };
    }
    if (anim === "Slide Down") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateY(${-(1 - tIn) * 50}px)`
      };
    }
    if (anim === "Slide Left") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateX(${(1 - tIn) * 80}px)`
      };
    }
    if (anim === "Slide Right") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateX(${-(1 - tIn) * 80}px)`
      };
    }
    if (anim === "Zoom In") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} scale(${0.3 + tIn * 0.7})`
      };
    }
    if (anim === "Zoom Out") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} scale(${1.8 - (1 - tIn) * 0.8})`
      };
    }
    if (anim === "Bounce") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateY(${-(Math.sin(tIn * Math.PI) * 25 * (1 - tIn))}px)`
      };
    }
    if (anim === "Pop") {
      const popT = Math.min(1, Math.max(0, textElapsed / 0.4));
      return {
        opacity: baseOpacity * popT,
        transform: `${transformStyle.transform || ""} scale(${popT <= 1 ? 0.5 + Math.sin(popT * (Math.PI / 2)) * 0.5 + Math.sin(popT * Math.PI) * 0.25 : 1})`
      };
    }
    if (anim === "Glitch") {
      const isGlitching = textElapsed < 1.5;
      return {
        transform: isGlitching ? `${transformStyle.transform || ""} translate(${Math.random() > 0.8 ? (Math.random() - 0.5) * 20 : 0}px, ${Math.random() > 0.8 ? (Math.random() - 0.5) * 20 : 0}px)` : transformStyle.transform,
        filter: (isGlitching && Math.random() > 0.8) ? 'hue-rotate(90deg) invert(100%)' : `blur(${activeClip.blur || 0}px)`
      };
    }
    if (anim === "Wave") {
      return {
        transform: `${transformStyle.transform || ""} translateY(${Math.sin(textElapsed * 5) * 15}px)`
      };
    }
    if (anim === "Shake") {
      return {
        transform: `${transformStyle.transform || ""} translate(${Math.sin(textElapsed * 28) * 6}px, ${Math.cos(textElapsed * 32) * 6}px) rotate(${Math.sin(textElapsed * 15) * 1.5}deg)`
      };
    }
    if (anim === "Elastic") {
      const scaleVal = tIn === 1 ? 1 : Math.sin(tIn * Math.PI * 2.5) * 0.3 * (1 - tIn) + tIn;
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} scale(${scaleVal})`
      };
    }
    if (anim === "Swing") {
      const rotVal = Math.sin(textElapsed * 4) * 15 * Math.max(0, 1.5 - textElapsed);
      return {
        opacity: baseOpacity * tIn,
        transformOrigin: 'top center',
        transform: `${transformStyle.transform || ""} rotate(${rotVal}deg)`
      };
    }
    if (anim === "Heartbeat") {
      const hbScale = 1 + ((textElapsed * 2) % Math.PI < 0.6 ? Math.sin(((textElapsed * 2) % Math.PI) * (Math.PI / 0.6)) * 0.12 : 0);
      return {
        transform: `${transformStyle.transform || ""} scale(${hbScale})`
      };
    }
    if (anim === "Reveal Left") {
      return {
        clipPath: `inset(0 ${(1 - tIn) * 100}% 0 0)`
      };
    }
    if (anim === "Reveal Right") {
      return {
        clipPath: `inset(0 0 0 ${(1 - tIn) * 100}%)`
      };
    }
    if (anim === "Blur Fade") {
      return {
        opacity: baseOpacity * tIn,
        filter: `blur(${(1 - tIn) * 20}px)`
      };
    }
    if (anim === "Blur Slide Up") {
      return {
        opacity: baseOpacity * tIn,
        filter: `blur(${(1 - tIn) * 15}px)`,
        transform: `${transformStyle.transform || ""} translateY(${(1 - tIn) * 40}px)`
      };
    }
    if (anim === "Pulse Glow") {
      const glowRad = 8 + Math.sin(textElapsed * 6) * 6;
      return {
        filter: `drop-shadow(0 0 ${glowRad}px ${activeClip.glowColor || activeClip.color || '#ef4444'})`
      };
    }
    if (anim === "Tracking (Expand)") {
      return {
        letterSpacing: `${(activeClip.letterSpacing || 0) + (1 - tIn) * 24}px`,
        opacity: baseOpacity * tIn
      };
    }
    if (anim === "Ramp Up") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateY(${(1 - tIn) * 60}px) skewY(${(1 - tIn) * -12}deg)`
      };
    }
    if (anim === "Jello") {
      const factor = Math.max(0, 1 - textElapsed);
      const skewX = Math.sin(textElapsed * 10) * 12 * factor;
      const skewY = Math.cos(textElapsed * 10) * 12 * factor;
      const scale = 1 + Math.sin(textElapsed * 12) * 0.15 * factor;
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} skewX(${skewX}deg) skewY(${skewY}deg) scale(${scale})`
      };
    }
    if (anim === "Fall Down") {
      const valY = tShort < 1 ? -120 * (1 - tShort) : -(Math.sin((textElapsed - 0.5) * Math.PI * 2) * 25 * Math.max(0, 1.2 - textElapsed));
      return {
        opacity: baseOpacity * tShort,
        transform: `${transformStyle.transform || ""} translateY(${valY}px)`
      };
    }
    if (anim === "Roll In") {
      return {
        opacity: baseOpacity * tIn,
        transform: `${transformStyle.transform || ""} translateX(${(1 - tIn) * -150}px) rotate(${(1 - tIn) * -270}deg)`
      };
    }

    return {};
  }, [anim, textElapsed, tIn, tShort, baseOpacity, transformStyle.transform, activeClip.glowColor, activeClip.color, activeClip.letterSpacing, activeClip.blur]);

  const styleWithAnimations = React.useMemo(() => {
    return {
      ...transformStyle,
      color: activeClip.color || "#ffffff",
      fontSize: `${activeClip.fontSize || 48}px`,
      fontFamily: activeClip.fontFamily || "sans-serif",
      letterSpacing: activeClip.letterSpacing ? `${activeClip.letterSpacing}px` : undefined,
      lineHeight: activeClip.lineHeight ? `${activeClip.lineHeight}` : undefined,
      WebkitTextStroke: activeClip.strokeWidth ? `${activeClip.strokeWidth}px ${activeClip.strokeColor || "#000000"}` : undefined,
      textShadow: activeClip.textShadow || (
        activeClip.glowColor && activeClip.glowRadius 
          ? `0 0 ${activeClip.glowRadius}px ${activeClip.glowColor}`
          : activeClip.shadowColor 
          ? `${activeClip.shadowOffsetX || 3}px ${activeClip.shadowOffsetY || 3}px ${activeClip.shadowBlur || 5}px ${activeClip.shadowColor}`
          : undefined
      ),
      ...animationStyle
    };
  }, [transformStyle, activeClip, animationStyle]);

  const displayedText = React.useMemo(() => {
    if (!activeClip.text) {
      return selectedClipId === activeClip.id ? "Type text..." : "";
    }
    if (anim === "Typewriter") {
      const progress = Math.min(1, Math.max(0, textElapsed / 2));
      const charsToShow = Math.floor(progress * activeClip.text.length);
      return activeClip.text.substring(0, charsToShow);
    }
    return activeClip.text;
  }, [activeClip.text, anim, textElapsed, selectedClipId, activeClip.id]);

  return (
    <div
      id={`clip-media-${activeClip.id}`}
      className="flex items-center justify-center w-full h-full font-sans break-words whitespace-pre-wrap text-center overflow-hidden absolute"
      style={styleWithAnimations}
    >
      <span className={`pointer-events-none select-none ${!activeClip.text ? 'opacity-40 italic' : ''}`}>
        {displayedText}
      </span>
    </div>
  );
});

TextRenderer.displayName = "TextRenderer";
