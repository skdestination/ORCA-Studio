import React, { useRef, useEffect, useLayoutEffect } from "react";
import { Clip } from "../types";

export const VideoRenderer = React.memo(({
  id,
  clip,
  currentTime,
  isPlaying,
  playbackEngine,
  isMuted,
  style,
  className,
  onPointerDown,
  onError,
  volumeMultiplier = 1,
}: {
  id?: string;
  clip: Clip;
  currentTime: number;
  isPlaying: boolean;
  playbackEngine?: React.MutableRefObject<any>;
  isMuted: boolean;
  style?: React.CSSProperties;
  className?: string;
  onPointerDown?: (e: React.PointerEvent) => void;
  onError?: () => void;
  volumeMultiplier?: number;
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);

  // Sync for play/pause and seek
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    video.volume = (typeof clip.volume === "number" ? clip.volume / 100 : 1) * volumeMultiplier;
    const effectiveSpeed = clip.opticalFlow ? 1 : (clip.speed || 1);
    video.playbackRate = effectiveSpeed;

    if (!isPlaying) {
      // Seeking while paused: synchronize
      let effectiveTrimStart = clip.trimStartSeconds;
      if (clip.opticalFlow && clip.speed) {
         effectiveTrimStart = clip.trimStartSeconds / clip.speed;
      }

      const totalSourceSpan = clip.durationSeconds * effectiveSpeed;
      const targetTime = Math.max(0,
        clip.isReversed
          ? (effectiveTrimStart + totalSourceSpan - (currentTime - clip.leftSeconds) * effectiveSpeed)
          : (effectiveTrimStart + (currentTime - clip.leftSeconds) * effectiveSpeed)
      );

      if (Math.abs(video.currentTime - targetTime) > 0.04) {
        try {
          video.currentTime = targetTime;
        } catch (e) {}
      }
      if (!video.paused) video.pause();
    } else {
        // Playing: Handle Play/Pause based on isPlaying
        if (video.paused) {
          const playPromise = video.play();
          if (playPromise !== undefined) {
            playPromise.catch((e) => {
              if (e.name !== "AbortError") console.error("Video play failed", e);
            });
          }
        }
    }
  }, [
    isPlaying,
    currentTime,
    clip.leftSeconds,
    clip.trimStartSeconds,
    clip.volume,
    clip.speed,
    clip.opticalFlow,
    clip.isReversed,
    clip.durationSeconds,
    volumeMultiplier,
  ]);

  // Continuous sync when playing
  useLayoutEffect(() => {
    let raf: number;
    const syncTime = () => {
      const engine = playbackEngine?.current;
      if (videoRef.current && engine && isPlaying) {
        const video = videoRef.current;
        const currentTime = engine.currentTimeRef.current;
        
        const effectiveSpeed = clip.opticalFlow ? 1 : (clip.speed || 1);
        let effectiveTrimStart = clip.trimStartSeconds;
        if (clip.opticalFlow && clip.speed) {
            effectiveTrimStart = clip.trimStartSeconds / clip.speed;
        }

        const totalSourceSpan = clip.durationSeconds * effectiveSpeed;
        const targetTime = Math.max(0,
            clip.isReversed
              ? (effectiveTrimStart + totalSourceSpan - (currentTime - clip.leftSeconds) * effectiveSpeed)
              : (effectiveTrimStart + (currentTime - clip.leftSeconds) * effectiveSpeed)
        );

        if (Math.abs(video.currentTime - targetTime) > 0.08) {
          try {
            video.currentTime = targetTime;
          } catch (e) {}
        }
      }
      raf = requestAnimationFrame(syncTime);
    };
    if (isPlaying) {
      raf = requestAnimationFrame(syncTime);
    }
    return () => cancelAnimationFrame(raf);
  }, [isPlaying, clip.leftSeconds, clip.trimStartSeconds, clip.speed, clip.opticalFlow, clip.isReversed, clip.durationSeconds]);

  return (
    <video
      id={id}
      ref={videoRef}
      src={clip.src || undefined}
      className={className || "w-full h-full object-cover"}
      muted={isMuted}
      playsInline
      style={style}
      crossOrigin="anonymous"
      onPointerDown={onPointerDown}
      onError={onError}
    />
  );
});

export const AudioRenderer = React.memo(({
  clip,
  currentTime,
  isPlaying,
  playbackEngine,
  isMuted,
  onError,
  volumeMultiplier = 1,
}: {
  clip: Clip;
  currentTime: number;
  isPlaying: boolean;
  playbackEngine?: React.MutableRefObject<any>;
  isMuted: boolean;
  onError?: () => void;
  volumeMultiplier?: number;
}) => {
  const audioRef = useRef<HTMLAudioElement>(null);

  // Sync for play/pause and seek
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    audio.volume = (typeof clip.volume === "number" ? clip.volume / 100 : 1) * volumeMultiplier;
    const effectiveSpeed = clip.opticalFlow ? 1 : (clip.speed || 1);
    audio.playbackRate = effectiveSpeed;

    if (!isPlaying) {
      // Seeking while paused: synchronize
      let effectiveTrimStart = clip.trimStartSeconds;
      if (clip.opticalFlow && clip.speed) {
        effectiveTrimStart = clip.trimStartSeconds / clip.speed;
      }

      const totalSourceSpan = clip.durationSeconds * effectiveSpeed;
      const targetTime = Math.max(0,
        clip.isReversed
          ? (effectiveTrimStart + totalSourceSpan - (currentTime - clip.leftSeconds) * effectiveSpeed)
          : (effectiveTrimStart + (currentTime - clip.leftSeconds) * effectiveSpeed)
      );

      if (Math.abs(audio.currentTime - targetTime) > 0.08) {
        try {
          audio.currentTime = targetTime;
        } catch (e) {}
      }
      if (!audio.paused) audio.pause();
    } else {
        // Playing: Handle Play/Pause
        if (audio.paused) {
          const playPromise = audio.play();
          if (playPromise !== undefined) {
            playPromise.catch((e) => {
              if (e.name !== "AbortError") console.error("Audio play failed", e);
            });
          }
        }
    }
  }, [
    isPlaying,
    currentTime,
    clip.leftSeconds,
    clip.trimStartSeconds,
    clip.volume,
    clip.speed,
    clip.opticalFlow,
    clip.isReversed,
    clip.durationSeconds,
    volumeMultiplier,
  ]);

  // Continuous sync when playing
  useLayoutEffect(() => {
    let raf: number;
    const syncTime = () => {
      const engine = playbackEngine?.current;
      if (audioRef.current && engine && isPlaying) {
        const audio = audioRef.current;
        const currentTime = engine.currentTimeRef.current;
        
        const effectiveSpeed = clip.opticalFlow ? 1 : (clip.speed || 1);
        let effectiveTrimStart = clip.trimStartSeconds;
        if (clip.opticalFlow && clip.speed) {
            effectiveTrimStart = clip.trimStartSeconds / clip.speed;
        }

        const totalSourceSpan = clip.durationSeconds * effectiveSpeed;
        const targetTime = Math.max(0,
            clip.isReversed
              ? (effectiveTrimStart + totalSourceSpan - (currentTime - clip.leftSeconds) * effectiveSpeed)
              : (effectiveTrimStart + (currentTime - clip.leftSeconds) * effectiveSpeed)
        );

        if (Math.abs(audio.currentTime - targetTime) > 0.08) {
          try {
            audio.currentTime = targetTime;
          } catch (e) {}
        }
      }
      raf = requestAnimationFrame(syncTime);
    };
    if (isPlaying) {
      raf = requestAnimationFrame(syncTime);
    }
    return () => cancelAnimationFrame(raf);
  }, [isPlaying, clip.leftSeconds, clip.trimStartSeconds, clip.speed, clip.opticalFlow, clip.isReversed, clip.durationSeconds]);

  return (
    <audio ref={audioRef} src={clip.src || undefined} muted={isMuted} onError={onError} />
  );
});
