import React, { useState, useEffect } from "react";
import { thumbnailProvider, getLevelForPixelsPerSecond } from "../lib/thumbnailCache";

interface TimelineThumbnailProps {
  clipId: string;
  sourceUrl: string;
  timeOffset: number;
  width: number;
  pixelsPerSecond: number;
}

export const TimelineThumbnail = React.memo(({
  clipId,
  sourceUrl,
  timeOffset,
  width,
  pixelsPerSecond,
}: TimelineThumbnailProps) => {
  // Determine target pyramid level based on the current timeline zoom (pixelsPerSecond)
  const targetLevel = getLevelForPixelsPerSecond(pixelsPerSecond);

  const [dataUrl, setDataUrl] = useState<string | null>(() => {
    // 1. Check if the exact target level thumbnail is already available in memory
    const exactMatch = thumbnailProvider.getSync(sourceUrl, targetLevel, timeOffset);
    if (exactMatch) return exactMatch;

    // 2. If not, progressively find the highest available lower-resolution level as a placeholder
    for (let l = targetLevel - 1; l >= 0; l--) {
      const lowerResolutionMatch = thumbnailProvider.getSync(sourceUrl, l, timeOffset);
      if (lowerResolutionMatch) return lowerResolutionMatch;
    }
    return null;
  });

  useEffect(() => {
    let active = true;

    // We subscribe to all levels from 0 to targetLevel. When any level updates,
    // we display the highest available level up to targetLevel. This guarantees 
    // a gorgeous, smooth, and immediate progressive visual upscale!
    const unsubscribers = Array.from({ length: targetLevel + 1 }).map((_, l) => {
      return thumbnailProvider.subscribe(sourceUrl, l, timeOffset, (url) => {
        if (!active) return;
        
        // Find highest resolution available
        const bestUrl = thumbnailProvider.getSync(sourceUrl, targetLevel, timeOffset);
        if (bestUrl) {
          setDataUrl(bestUrl);
        } else {
          for (let checkL = targetLevel - 1; checkL >= 0; checkL--) {
            const lowerMatch = thumbnailProvider.getSync(sourceUrl, checkL, timeOffset);
            if (lowerMatch) {
              setDataUrl(lowerMatch);
              break;
            }
          }
        }
      });
    });

    // Request the target level progressively starting from level 0 up to targetLevel.
    // We add a debounced delay of 150ms to avoid flooding the video seek engine during active trimming/dragging.
    // If the thumbnail is already cached, it is already loaded synchronously in the initial state.
    const generateTimeout = setTimeout(() => {
      thumbnailProvider.getOrGenerate({
        clipId,
        sourceUrl,
        timeOffset,
        level: targetLevel,
      })
        .then((url) => {
          if (active) setDataUrl(url);
        })
        .catch(() => {
          // Handled or cancelled
        });
    }, 150);

    return () => {
      active = false;
      clearTimeout(generateTimeout);
      unsubscribers.forEach((unsub) => unsub());
    };
  }, [clipId, sourceUrl, timeOffset, targetLevel]);

  return (
    <div 
      className="h-full border-r border-zinc-950/20 bg-zinc-800/30 flex-shrink-0 relative overflow-hidden"
      style={{ width: `${width}px` }}
    >
      {dataUrl ? (
        <img 
          src={dataUrl} 
          alt="" 
          className="w-full h-full object-cover select-none pointer-events-none transition-opacity duration-300 ease-in-out"
          referrerPolicy="no-referrer"
        />
      ) : (
        <div className="absolute inset-0 bg-gradient-to-r from-zinc-800/15 via-zinc-700/25 to-zinc-800/15 animate-pulse" />
      )}
    </div>
  );
});
