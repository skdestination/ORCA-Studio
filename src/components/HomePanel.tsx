import React, { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Capacitor } from "@capacitor/core";
import {
  Bell,
  Minimize2,
  Maximize2,
  ChevronDown,
  Check,
  Clock,
  MoreHorizontal,
  ChevronRight,
  Copy,
  Trash2,
  PlusIcon,
  Settings,
  X,
  AlertCircle,
  Pencil,
  BarChart2,
  Folder,
  Sparkles,
  Music,
  Film,
  Type,
} from "lucide-react";
import { Project } from "../types";
import { getFile } from "../lib/db";

// Helper function to extract frame at ~0.1s from video URL or Blob
function extractVideoFrame(videoUrl: string, seekTime = 0.1): Promise<string> {
  return new Promise((resolve, reject) => {
    const video = document.createElement("video");
    video.crossOrigin = "anonymous";
    video.muted = true;
    video.playsInline = true;

    let isSettled = false;
    const timeout = setTimeout(() => {
      if (!isSettled) {
        isSettled = true;
        reject(new Error("Video frame extraction timeout"));
      }
    }, 4000);

    video.onloadedmetadata = () => {
      video.currentTime = Math.min(seekTime, (video.duration || 1) / 2);
    };

    video.onseeked = () => {
      if (isSettled) return;
      isSettled = true;
      clearTimeout(timeout);
      try {
        const canvas = document.createElement("canvas");
        canvas.width = video.videoWidth || 640;
        canvas.height = video.videoHeight || 360;
        const ctx = canvas.getContext("2d");
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const dataUrl = canvas.toDataURL("image/jpeg", 0.85);
          resolve(dataUrl);
        } else {
          reject(new Error("Canvas context null"));
        }
      } catch (err) {
        reject(err);
      }
    };

    video.onerror = (e) => {
      if (!isSettled) {
        isSettled = true;
        clearTimeout(timeout);
        reject(e);
      }
    };

    video.src = videoUrl;
  });
}

// ProjectCoverImage component: extracts and renders thumbnail from the FIRST clip on project timeline
function ProjectCoverImage({ p }: { p: Project }) {
  const [thumbUrl, setThumbUrl] = useState<string | null>(null);
  const [firstClipType, setFirstClipType] = useState<"video" | "image" | "audio" | "text" | null>(null);
  const [firstClipName, setFirstClipName] = useState<string | null>(null);

  // Find the first clip on the timeline (position 0 / minimum leftSeconds)
  const firstClip = React.useMemo(() => {
    if (!p.clips || p.clips.length === 0) return null;
    return [...p.clips].sort((a, b) => a.leftSeconds - b.leftSeconds)[0];
  }, [p.clips]);

  useEffect(() => {
    let active = true;
    let objectUrlToRevoke: string | null = null;

    if (!firstClip) {
      // Fallback if no clips: check thumbnailFileId or p.thumbnail
      if (p.thumbnailFileId) {
        getFile(p.thumbnailFileId)
          .then((blob) => {
            if (active && blob) {
              objectUrlToRevoke = URL.createObjectURL(blob);
              setThumbUrl(objectUrlToRevoke);
              setFirstClipType("image");
            } else if (active) {
              setThumbUrl(p.thumbnail || null);
              setFirstClipType(null);
            }
          })
          .catch(() => {
            if (active) {
              setThumbUrl(p.thumbnail || null);
              setFirstClipType(null);
            }
          });
      } else {
        setThumbUrl(p.thumbnail || null);
        setFirstClipType(null);
      }
      return;
    }

    setFirstClipType(firstClip.type);
    setFirstClipName(firstClip.name || firstClip.text || null);

    // 1. IMAGE CLIP
    if (firstClip.type === "image") {
      if (firstClip.fileId) {
        getFile(firstClip.fileId)
          .then((blob) => {
            if (active && blob) {
              objectUrlToRevoke = URL.createObjectURL(blob);
              setThumbUrl(objectUrlToRevoke);
            } else if (active) {
              setThumbUrl(firstClip.src || p.thumbnail || null);
            }
          })
          .catch(() => {
            if (active) {
              setThumbUrl(firstClip.src || p.thumbnail || null);
            }
          });
      } else {
        setThumbUrl(firstClip.src || p.thumbnail || null);
      }
    }
    // 2. VIDEO CLIP
    else if (firstClip.type === "video") {
      const loadAndExtractVideo = async () => {
        let mediaUrl = firstClip.src;
        if (firstClip.fileId) {
          try {
            const blob = await getFile(firstClip.fileId);
            if (blob) {
              objectUrlToRevoke = URL.createObjectURL(blob);
              mediaUrl = objectUrlToRevoke;
            }
          } catch (e) {
            console.error("Failed to fetch video blob for thumbnail", e);
          }
        }

        if (mediaUrl) {
          try {
            const extracted = await extractVideoFrame(mediaUrl, 0.1);
            if (active) {
              setThumbUrl(extracted);
              return;
            }
          } catch (err) {
            console.warn("Video frame extraction failed, using fallback thumbnail", err);
          }
        }

        if (active) {
          setThumbUrl(firstClip.thumbnail || p.thumbnail || null);
        }
      };

      loadAndExtractVideo();
    }
    // 3. AUDIO CLIP (Render purple/violet waveform placeholder)
    else if (firstClip.type === "audio") {
      setThumbUrl(null);
    }
    // 4. TEXT CLIP
    else if (firstClip.type === "text") {
      setThumbUrl(null);
    }
    // Fallback
    else {
      setThumbUrl(p.thumbnail || null);
    }

    return () => {
      active = false;
      if (objectUrlToRevoke) {
        URL.revokeObjectURL(objectUrlToRevoke);
      }
    };
  }, [firstClip, p.thumbnailFileId, p.thumbnail]);

  // Render Image or Video Extracted Frame
  if (thumbUrl) {
    return (
      <img
        src={thumbUrl}
        alt={p.name}
        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 pointer-events-none"
        referrerPolicy="no-referrer"
      />
    );
  }

  // Render Audio-only Clip Placeholder (#a855f7 / #c084fc theme)
  if (firstClipType === "audio") {
    return (
      <div className="w-full h-full bg-gradient-to-br from-purple-950 via-zinc-900 to-indigo-950 flex flex-col items-center justify-center p-4 relative overflow-hidden group-hover:scale-105 transition-transform duration-700">
        {/* Radial Glow */}
        <div className="absolute w-32 h-32 rounded-full bg-purple-600/20 blur-2xl top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2" />

        {/* Audio Waveform Bars */}
        <div className="flex items-center gap-1 mb-3 z-10">
          {[40, 75, 50, 90, 60, 100, 70, 85, 45, 95, 65, 80, 50, 70, 35].map((h, i) => (
            <div
              key={i}
              className="w-1.5 rounded-full bg-gradient-to-t from-[#a855f7] to-[#c084fc] shadow-[0_0_8px_rgba(168,85,247,0.6)]"
              style={{ height: `${h * 0.45}px` }}
            />
          ))}
        </div>

        {/* Track Label Badge */}
        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-purple-500/20 border border-purple-400/30 text-purple-200 z-10 shadow-lg">
          <Music size={13} className="text-[#c084fc]" />
          <span className="text-[10px] font-bold tracking-wider uppercase truncate max-w-[140px]">
            {firstClipName || "Audio Track"}
          </span>
        </div>
      </div>
    );
  }

  // Render Text Clip Placeholder
  if (firstClipType === "text") {
    return (
      <div className="w-full h-full bg-gradient-to-br from-indigo-950 via-zinc-900 to-slate-950 flex flex-col items-center justify-center p-6 relative overflow-hidden group-hover:scale-105 transition-transform duration-700">
        <div className="absolute w-28 h-28 rounded-full bg-indigo-500/15 blur-2xl top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2" />
        <Type size={28} className="text-indigo-400 mb-2 z-10" />
        <p className="text-xs font-bold text-indigo-100 z-10 text-center line-clamp-2 px-2">
          "{firstClipName || "Text Title"}"
        </p>
      </div>
    );
  }

  // Default Empty Project Card Placeholder
  return (
    <div className="w-full h-full bg-gradient-to-br from-zinc-900 via-zinc-950 to-black flex flex-col items-center justify-center p-4 relative overflow-hidden group-hover:scale-105 transition-transform duration-700">
      <div className="w-12 h-12 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center text-zinc-500 mb-2 shadow-inner">
        <Film size={22} />
      </div>
      <span className="text-[10px] font-semibold tracking-widest text-zinc-600 uppercase">
        Empty Timeline
      </span>
    </div>
  );
}

interface HomePanelProps {
  projects: Project[];
  sortBy: "recent" | "name" | "duration";
  setSortBy: (sort: "recent" | "name" | "duration") => void;
  isSortMenuOpen: boolean;
  setIsSortMenuOpen: (open: boolean) => void;
  setCurrentScreen: (screen: string) => void;
  isFullscreen: boolean;
  toggleFullscreen: () => void;
  openProject: (p: Project) => void;
  projectMenuOpenId: string | null;
  setProjectMenuOpenId: (id: string | null) => void;
  duplicateProject: (p: Project) => void;
  projectToDelete: string | null;
  setProjectToDelete: (id: string | null) => void;
  confirmDeleteProject: () => void;
  unsupportedFileDetails: any;
  setUnsupportedFileDetails: (details: any) => void;
  isCreatingProject: boolean;
  setIsCreatingProject: (creating: boolean) => void;
  focusedRatio: string;
  setFocusedRatio: (ratio: string) => void;
  customRatioW: string;
  setCustomRatioW: (w: string) => void;
  customRatioH: string;
  setCustomRatioH: (h: string) => void;
  handleCreateProject: (ratio: string) => void;
}

export const HomePanel: React.FC<HomePanelProps> = ({
  projects,
  sortBy,
  setSortBy,
  isSortMenuOpen,
  setIsSortMenuOpen,
  setCurrentScreen,
  isFullscreen,
  toggleFullscreen,
  openProject,
  projectMenuOpenId,
  setProjectMenuOpenId,
  duplicateProject,
  projectToDelete,
  setProjectToDelete,
  confirmDeleteProject,
  unsupportedFileDetails,
  setUnsupportedFileDetails,
  isCreatingProject,
  setIsCreatingProject,
  focusedRatio,
  setFocusedRatio,
  customRatioW,
  setCustomRatioW,
  customRatioH,
  setCustomRatioH,
  handleCreateProject,
}) => {
  const [activeCardId, setActiveCardId] = useState<string | null>(null);
  const [showStatsModal, setShowStatsModal] = useState<Project | null>(null);
  const carouselRef = useRef<HTMLDivElement>(null);

  // Sort projects based on sortBy state
  const sortedProjects = [...projects].sort((a, b) => {
    if (sortBy === "name") {
      return a.name.localeCompare(b.name);
    } else if (sortBy === "duration") {
      return (a.duration || "").localeCompare(b.duration || "");
    } else {
      const getWeight = (p: Project) => {
        if (p.id === "new-p") return 3;
        if (p.id === "1") return 2;
        if (p.id === "frosted-p") return 1;
        return 0;
      };
      const wA = getWeight(a);
      const wB = getWeight(b);
      if (wA !== wB) return wB - wA;
      return b.id.localeCompare(a.id);
    }
  });

  useEffect(() => {
    if (sortedProjects.length > 0 && !activeCardId) {
      setActiveCardId(sortedProjects[0].id);
    }
  }, [sortedProjects, activeCardId]);

  return (
    <div className="flex flex-col h-screen w-full bg-[#050507] text-white overflow-hidden relative font-sans select-none">
      {/* Background Lighting & Subtle Radial Glows */}
      <div className="absolute top-[-120px] left-1/2 -translate-x-1/2 w-[700px] h-[500px] bg-[radial-gradient(circle_at_center,rgba(255,255,255,0.08)_0%,transparent_60%)] rounded-full blur-[120px] pointer-events-none z-0" />
      <div className="absolute bottom-[-100px] right-[-100px] w-[500px] h-[500px] bg-[radial-gradient(circle_at_center,rgba(249,115,22,0.05)_0%,transparent_65%)] rounded-full blur-[100px] pointer-events-none z-0" />
      <div className="absolute top-[30%] left-[-150px] w-[450px] h-[450px] bg-[radial-gradient(circle_at_center,rgba(168,85,247,0.04)_0%,transparent_60%)] rounded-full blur-[100px] pointer-events-none z-0" />

      {/* Main Container */}
      <div 
        className="flex-1 flex flex-col justify-between overflow-y-auto scrollbar-hide z-10 px-4 sm:px-8 max-w-lg sm:max-w-xl md:max-w-2xl mx-auto w-full"
        style={{
          paddingTop: "calc(1.25rem + env(safe-area-inset-top, 0px))",
          paddingBottom: "calc(6.5rem + env(safe-area-inset-bottom, 0px))",
        }}
      >
        {/* Top Header */}
        <div className="flex justify-between items-center w-full">
          {/* User Profile */}
          <div className="flex items-center gap-3">
            <div className="relative w-11 h-11 rounded-full overflow-hidden border border-white/20 shadow-[0_0_16px_rgba(255,255,255,0.18)] bg-zinc-900 p-[1.5px]">
              <div className="w-full h-full rounded-full overflow-hidden">
                <img
                  src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=150"
                  alt="Ritwik"
                  className="w-full h-full object-cover"
                  referrerPolicy="no-referrer"
                />
              </div>
            </div>
            <div className="flex flex-col">
              <span className="text-zinc-500 font-semibold text-[9.5px] uppercase tracking-[0.2em] leading-none mb-1">
                {new Date().getHours() < 12
                  ? "GOOD MORNING,"
                  : new Date().getHours() < 18
                  ? "GOOD AFTERNOON,"
                  : "GOOD EVENING,"}
              </span>
              <span className="text-white font-extrabold text-base tracking-tight leading-none">
                Ritwik
              </span>
            </div>
          </div>

          {/* Action Buttons Top Right */}
          <div className="flex items-center gap-2.5">
            {!Capacitor.isNativePlatform() && (
              <button
                onClick={toggleFullscreen}
                className="w-10 h-10 rounded-full bg-zinc-900/80 backdrop-blur-xl border border-white/10 flex items-center justify-center text-zinc-300 hover:text-white hover:border-white/25 active:scale-95 transition-all shadow-[0_4px_16px_rgba(0,0,0,0.5)] cursor-pointer"
                title={isFullscreen ? "Exit Fullscreen" : "Enter Fullscreen"}
              >
                {isFullscreen ? (
                  <Minimize2 size={16} />
                ) : (
                  <Maximize2 size={16} />
                )}
              </button>
            )}

            <button
              className="relative w-10 h-10 rounded-full bg-zinc-900/80 backdrop-blur-xl border border-white/10 flex items-center justify-center text-zinc-300 hover:text-white hover:border-white/25 active:scale-95 transition-all shadow-[0_4px_16px_rgba(0,0,0,0.5)] cursor-pointer"
              title="Notifications"
            >
              <Bell size={16} />
              <span className="absolute top-2.5 right-2.5 w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_8px_rgba(249,115,22,0.9)]" />
            </button>
          </div>
        </div>

        {/* Pencil Edit Icon Below Top Right */}
        <div className="flex justify-end w-full -mt-2">
          <button
            className="w-9 h-9 rounded-full bg-zinc-900/60 backdrop-blur-xl border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/25 active:scale-95 transition-all cursor-pointer shadow-md"
            title="Edit Dashboard"
          >
            <Pencil size={14} />
          </button>
        </div>

        {/* Center Title Branding */}
        <div className="flex flex-col items-center justify-center text-center my-4">
          <h1 className="text-6xl sm:text-7xl font-black tracking-[0.02em] text-white uppercase leading-none drop-shadow-[0_0_35px_rgba(255,255,255,0.4)]">
            ORCA
          </h1>
          <div className="tracking-[0.45em] text-[10px] sm:text-[11px] text-zinc-400 font-extrabold uppercase flex items-center justify-center gap-1.5 mt-2.5 pl-[0.45em]">
            <span>CREATIVE STUDIO</span>
            <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_8px_rgba(249,115,22,0.8)]" />
          </div>

          {/* Subtle React Sandbox Indicator */}
          <div className="mt-3 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 flex items-center gap-2 shadow-[0_0_12px_rgba(99,102,241,0.12)]">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-pulse" />
            <span className="text-[8.5px] font-bold tracking-[0.2em] text-indigo-300 uppercase leading-none">
              React Sandbox Preview
            </span>
          </div>
        </div>

        {/* Projects Section Header */}
        <div className="flex justify-between items-center w-full my-2 px-1">
          <h2 className="text-xl font-bold text-white tracking-tight">
            Projects
          </h2>

          {/* Sort Dropdown Selector */}
          <div className="relative">
            <button
              onClick={() => setIsSortMenuOpen(!isSortMenuOpen)}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-zinc-900/80 border border-white/10 hover:border-white/20 text-zinc-200 text-xs font-semibold leading-none cursor-pointer transition-all active:scale-95 shadow-lg backdrop-blur-xl"
            >
              <span>
                {sortBy === "recent"
                  ? "Recent"
                  : sortBy === "name"
                  ? "Alphabetical"
                  : "Duration"}
              </span>
              <ChevronDown
                size={12}
                className={`text-zinc-400 transition-transform duration-200 ${
                  isSortMenuOpen ? "rotate-180" : ""
                }`}
              />
            </button>

            <AnimatePresence>
              {isSortMenuOpen && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.95, y: -5 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: -5 }}
                  className="absolute right-0 top-9 w-36 bg-zinc-900/95 border border-white/10 rounded-2xl p-1.5 shadow-[0_12px_40px_rgba(0,0,0,0.9)] backdrop-blur-xl z-50"
                >
                  {[
                    { value: "recent", label: "Recent Order" },
                    { value: "name", label: "Alphabetical" },
                    { value: "duration", label: "Duration" },
                  ].map((item) => (
                    <button
                      key={item.value}
                      onClick={() => {
                        setSortBy(item.value as any);
                        setIsSortMenuOpen(false);
                      }}
                      className={`w-full text-left px-3 py-1.5 text-xs font-semibold rounded-xl transition-colors flex items-center justify-between ${
                        sortBy === item.value
                          ? "bg-white/10 text-white"
                          : "text-zinc-400 hover:bg-white/5 hover:text-zinc-200"
                      }`}
                    >
                      <span>{item.label}</span>
                      {sortBy === item.value && (
                        <Check size={11} className="text-orange-400" />
                      )}
                    </button>
                  ))}
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* Project Cards Horizontal Carousel */}
        <div className="w-full relative my-3 min-h-[380px] sm:min-h-[420px] flex items-center">
          <div
            ref={carouselRef}
            className="flex items-center gap-4 overflow-x-auto scrollbar-hide snap-x snap-mandatory py-4 px-[10vw] sm:px-[15vw] w-full"
            onScroll={(e) => {
              const container = e.currentTarget;
              const containerCenter =
                container.getBoundingClientRect().left +
                container.clientWidth / 2;
              let closestId: string | null = null;
              let minDist = Infinity;
              container.childNodes.forEach((node) => {
                if (node.nodeType === 1) {
                  const el = node as HTMLElement;
                  const pid = el.getAttribute("data-project-id");
                  if (pid) {
                    const rect = el.getBoundingClientRect();
                    const elCenter = rect.left + rect.width / 2;
                    const dist = Math.abs(elCenter - containerCenter);
                    if (dist < minDist) {
                      minDist = dist;
                      closestId = pid;
                    }
                  }
                }
              });
              if (closestId && closestId !== activeCardId) {
                setActiveCardId(closestId);
              }
            }}
          >
            {sortedProjects.map((p) => {
              const isActive = activeCardId === p.id;
              return (
                <div
                  key={p.id}
                  data-project-id={p.id}
                  onClick={(e) => {
                    if (!isActive) {
                      e.currentTarget.scrollIntoView({
                        behavior: "smooth",
                        block: "nearest",
                        inline: "center",
                      });
                      setActiveCardId(p.id);
                    } else {
                      openProject(p);
                    }
                  }}
                  className={`snap-center shrink-0 w-[240px] sm:w-[270px] h-[370px] sm:h-[410px] rounded-[32px] relative overflow-hidden cursor-pointer transition-all duration-500 border ${
                    isActive
                      ? "scale-100 border-white/30 shadow-[0_20px_50px_rgba(0,0,0,0.95)] z-20"
                      : "scale-[0.88] opacity-60 border-white/10 shadow-lg hover:opacity-80 z-10"
                  }`}
                >
                  {/* Background Artwork */}
                  <div className="absolute inset-0 z-0 bg-zinc-900">
                    <ProjectCoverImage p={p} />
                  </div>

                  {/* Frosted Glass Bottom Panel (Blur Gradient - Increased Intensity, No Black Gradient) */}
                  <div className="absolute inset-x-0 bottom-0 z-10 h-[48%] pointer-events-none rounded-b-[31px] overflow-hidden">
                    <div
                      className="absolute inset-0 border-t border-white/20"
                      style={{
                        backdropFilter: "blur(40px)",
                        WebkitBackdropFilter: "blur(40px)",
                        maskImage: "linear-gradient(to top, rgba(0,0,0,1) 0%, rgba(0,0,0,0.85) 60%, rgba(0,0,0,0) 100%)",
                        WebkitMaskImage: "linear-gradient(to top, rgba(0,0,0,1) 0%, rgba(0,0,0,0.85) 60%, rgba(0,0,0,0) 100%)",
                      }}
                    />
                  </div>

                  {/* Glass Sheen Top Edge */}
                  <div className="absolute inset-x-0 top-0 h-[1.5px] bg-gradient-to-r from-transparent via-white/40 to-transparent pointer-events-none z-20" />

                  {/* Active Card Content Overlay */}
                  <div className="absolute inset-0 z-20 p-5 flex flex-col justify-end text-left pointer-events-none">
                    <div className="mb-3 space-y-1.5">
                      <h3 className="text-xl font-extrabold text-white tracking-tight leading-snug drop-shadow-[0_2px_10px_rgba(0,0,0,0.9)] line-clamp-1">
                        {p.name}
                      </h3>
                      <div className="flex items-center gap-2 pt-0.5">
                        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase bg-white/15 backdrop-blur-md text-white border border-white/25 shadow-sm">
                          {p.ratio === "9:16" ? "9:16 Portrait" : p.ratio === "16:9" ? "16:9 Landscape" : p.ratio || "9:16"}
                        </span>
                        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-semibold text-zinc-200 bg-black/30 backdrop-blur-md border border-white/15 shadow-sm">
                          {p.duration || "1 Min 29s"}
                        </span>
                      </div>
                    </div>

                    {/* Bottom Action Icons Bar on the Active Card */}
                    {isActive && (
                      <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="flex items-center justify-between w-full pt-2.5 pointer-events-auto"
                      >
                        {/* 1. Delete Button (LEFT) */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setProjectToDelete(p.id);
                          }}
                          className="w-11 h-11 rounded-full bg-black/40 hover:bg-red-500/20 backdrop-blur-xl border border-white/20 hover:border-red-400/50 flex items-center justify-center text-zinc-300 hover:text-red-400 active:scale-90 transition-all cursor-pointer shadow-2xl"
                          title="Delete Project"
                        >
                          <Trash2 size={17} />
                        </button>

                        {/* 2. Duplicate Button (EXACT CENTER) */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            duplicateProject(p);
                          }}
                          className="w-11 h-11 rounded-full bg-black/40 hover:bg-white/20 backdrop-blur-xl border border-white/20 hover:border-white/40 flex items-center justify-center text-zinc-200 hover:text-white active:scale-90 transition-all cursor-pointer shadow-2xl"
                          title="Duplicate Project"
                        >
                          <Copy size={17} />
                        </button>

                        {/* 3. More Options Button (RIGHT) */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setShowStatsModal(p);
                          }}
                          className="w-11 h-11 rounded-full bg-black/40 hover:bg-white/20 backdrop-blur-xl border border-white/20 hover:border-white/40 flex items-center justify-center text-zinc-200 hover:text-white active:scale-90 transition-all cursor-pointer shadow-2xl"
                          title="More Options & Details"
                        >
                          <MoreHorizontal size={19} />
                        </button>
                      </motion.div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Persistent Bottom Floating Action Bar */}
      <div 
        className="absolute left-0 right-0 flex justify-center items-center px-6 z-40 pointer-events-none"
        style={{
          bottom: "calc(1.75rem + env(safe-area-inset-bottom, 0px))",
        }}
      >
        <div className="flex items-center gap-3.5 w-full max-w-[340px] pointer-events-auto">
          {/* New Project Button */}
          <button
            onClick={() => setIsCreatingProject(true)}
            className="relative flex-1 h-[54px] rounded-full bg-zinc-900/80 hover:bg-zinc-800/90 backdrop-blur-2xl border border-white/15 text-white font-bold text-sm tracking-wide flex items-center justify-center gap-2 hover:border-white/30 active:scale-[0.97] transition-all duration-300 shadow-[0_12px_36px_rgba(0,0,0,0.85)] cursor-pointer overflow-hidden group"
          >
            {/* Top Gloss Reflection */}
            <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/30 to-transparent pointer-events-none" />
            <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.02] to-white/[0.06] pointer-events-none" />
            <PlusIcon
              size={18}
              className="text-white group-hover:scale-110 transition-transform"
            />
            <span className="font-semibold text-sm tracking-tight">
              New Project
            </span>
          </button>

          {/* Settings Circular Button */}
          <button
            onClick={() => setCurrentScreen("settings")}
            className="relative w-[54px] h-[54px] rounded-full bg-zinc-900/80 hover:bg-zinc-800/90 backdrop-blur-2xl border border-white/15 flex items-center justify-center text-zinc-300 hover:text-white hover:border-white/30 active:scale-[0.97] transition-all duration-300 shadow-[0_12px_36px_rgba(0,0,0,0.85)] cursor-pointer overflow-hidden group"
            title="Settings"
          >
            <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/30 to-transparent pointer-events-none" />
            <Settings
              size={20}
              className="group-hover:rotate-45 transition-transform duration-300"
            />
          </button>
        </div>
      </div>

      {/* Project Analytics/Stats Modal */}
      <AnimatePresence>
        {showStatsModal && (
          <div
            className="fixed inset-0 z-[300] bg-black/70 backdrop-blur-md flex items-center justify-center p-4"
            onClick={() => setShowStatsModal(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-zinc-900 border border-white/15 rounded-[32px] p-6 max-w-sm w-full shadow-2xl relative"
            >
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-bold text-white flex items-center gap-2">
                  <BarChart2 size={18} className="text-orange-400" />
                  Project Info
                </h3>
                <button
                  onClick={() => setShowStatsModal(null)}
                  className="w-8 h-8 rounded-full bg-zinc-800 text-zinc-400 hover:text-white flex items-center justify-center"
                >
                  <X size={16} />
                </button>
              </div>

              <div className="space-y-3 text-xs text-zinc-300">
                <div className="p-3 rounded-2xl bg-zinc-800/60 border border-white/5 flex justify-between items-center">
                  <span className="text-zinc-400">Name</span>
                  <span className="font-bold text-white">
                    {showStatsModal.name}
                  </span>
                </div>

                <div className="p-3 rounded-2xl bg-zinc-800/60 border border-white/5 flex justify-between items-center">
                  <span className="text-zinc-400">Aspect Ratio</span>
                  <span className="font-bold text-white">
                    {showStatsModal.ratio}
                  </span>
                </div>

                <div className="p-3 rounded-2xl bg-zinc-800/60 border border-white/5 flex justify-between items-center">
                  <span className="text-zinc-400">Duration</span>
                  <span className="font-bold text-white">
                    {showStatsModal.duration || "01:29"}
                  </span>
                </div>

                <div className="p-3 rounded-2xl bg-zinc-800/60 border border-white/5 flex justify-between items-center">
                  <span className="text-zinc-400">Render Engine</span>
                  <span className="font-bold text-orange-400">
                    ORCA Native (Metal/GL)
                  </span>
                </div>
              </div>

              <button
                className="w-full mt-5 py-3 rounded-full bg-white text-black font-bold text-xs hover:bg-zinc-200 transition-colors"
                onClick={() => {
                  const p = showStatsModal;
                  setShowStatsModal(null);
                  openProject(p);
                }}
              >
                Open in Native NLE
              </button>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Format Selection Modal */}
      <AnimatePresence>
        {isCreatingProject && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.4 }}
            className="fixed inset-0 z-50 bg-[#050507] flex flex-col items-center justify-center overflow-hidden"
          >
            {/* Dotted Grid Pattern Background */}
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center z-0">
              <div
                className="w-full max-w-[800px] h-full max-h-[800px] opacity-[0.2]"
                style={{
                  backgroundImage: `radial-gradient(circle at center, rgba(255,255,255,0.8) 1px, transparent 1px)`,
                  backgroundSize: `28px 28px`,
                  WebkitMaskImage: `radial-gradient(ellipse 50% 50% at center, black 10%, transparent 60%)`,
                  maskImage: `radial-gradient(ellipse 50% 50% at center, black 10%, transparent 60%)`,
                }}
              />
            </div>

            {/* Header Title */}
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="absolute top-[18%] left-0 right-0 flex items-center justify-center px-8 pointer-events-none z-10"
            >
              <div className="w-[60px] sm:w-[100px] h-[1px] bg-gradient-to-r from-transparent to-zinc-700" />
              <span className="text-[10px] font-semibold tracking-[0.3em] text-zinc-400 px-6 uppercase whitespace-nowrap">
                Choose Format
              </span>
              <div className="w-[60px] sm:w-[100px] h-[1px] bg-gradient-to-l from-transparent to-zinc-700" />
            </motion.div>

            {/* Close Button */}
            <button
              className="absolute top-8 right-8 w-11 h-11 rounded-full bg-zinc-900 border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white transition-colors z-50 cursor-pointer"
              onClick={() => setIsCreatingProject(false)}
            >
              <X size={20} />
            </button>

            {/* Formats Slider */}
            <div
              className="relative z-20 flex flex-nowrap items-center overflow-x-auto w-full pb-10 pt-16 scrollbar-hide snap-x snap-mandatory"
              onScroll={(e) => {
                const container = e.currentTarget;
                const containerCenter =
                  container.getBoundingClientRect().left +
                  container.clientWidth / 2;
                let closest = null;
                let minDist = Infinity;
                container.childNodes.forEach((node) => {
                  if (node.nodeType === 1) {
                    const el = node as HTMLElement;
                    const val = el.getAttribute("data-ratio");
                    if (val) {
                      const rect = el.getBoundingClientRect();
                      const elCenter = rect.left + rect.width / 2;
                      const dist = Math.abs(elCenter - containerCenter);
                      if (dist < minDist) {
                        minDist = dist;
                        closest = val;
                      }
                    }
                  }
                });
                if (closest) setFocusedRatio(closest);
              }}
              style={{
                paddingLeft: "calc(50vw - 160px)",
                paddingRight: "calc(50vw - 160px)",
              }}
            >
              {[
                { ratio: "9:16", baseW: 160, baseH: 340, label: "PORTRAIT" },
                { ratio: "16:9", baseW: 340, baseH: 160, label: "LANDSCAPE" },
                { ratio: "1:1", baseW: 240, baseH: 240, label: "SQUARE" },
                { ratio: "custom", baseW: 240, baseH: 120, label: "CUSTOM" },
              ].map((r) => {
                const isFocused = focusedRatio === r.ratio;
                const scale = isFocused ? 1 : 0.85;
                return (
                  <div
                    key={r.ratio}
                    data-ratio={r.ratio}
                    className="w-[320px] shrink-0 snap-center flex justify-center items-center"
                  >
                    <motion.div
                      className="cursor-pointer group relative flex justify-center items-center"
                      onClick={(e) => {
                        if (!isFocused) {
                          e.currentTarget.scrollIntoView({
                            behavior: "smooth",
                            block: "nearest",
                            inline: "center",
                          });
                        }
                      }}
                    >
                      <motion.div
                        animate={{
                          width: r.baseW * scale,
                          height: r.baseH * scale,
                        }}
                        transition={{ type: "spring", bounce: 0.3 }}
                        className={`relative rounded-[36px] flex items-center justify-center transition-all duration-500 ${
                          isFocused ? "p-[1.5px]" : "p-[1px]"
                        }`}
                      >
                        {/* Gradient Border for Focused */}
                        <div
                          className={`absolute inset-0 rounded-[36px] pointer-events-none transition-opacity duration-700 ${
                            isFocused ? "opacity-100" : "opacity-0"
                          }`}
                          style={{
                            background:
                              "linear-gradient(145deg, rgba(167, 139, 250, 0.8) 0%, rgba(255, 255, 255, 0.1) 40%, rgba(255, 255, 255, 0.1) 60%, rgba(251, 146, 60, 0.7) 100%)",
                          }}
                        />

                        {/* Subtle Border for Unfocused */}
                        <div
                          className={`absolute inset-0 rounded-[36px] pointer-events-none transition-opacity duration-500 border border-white/10 ${
                            !isFocused ? "opacity-100" : "opacity-0"
                          }`}
                        />

                        <div className="absolute inset-[1.5px] rounded-[34.5px] bg-[#050507] z-10 pointer-events-none" />

                        <div className="relative z-20 flex flex-col items-center justify-center gap-2">
                          {r.ratio === "custom" && isFocused ? (
                            <div
                              className="flex items-center gap-3 bg-zinc-900/40 rounded-2xl p-2 border border-white/5"
                              onClick={(e) => e.stopPropagation()}
                            >
                              <input
                                type="number"
                                placeholder="W"
                                className="bg-transparent w-16 text-center text-lg text-white outline-none font-medium"
                                value={customRatioW}
                                onChange={(e) =>
                                  setCustomRatioW(e.target.value)
                                }
                              />
                              <div className="w-[1px] h-6 bg-zinc-700" />
                              <input
                                type="number"
                                placeholder="H"
                                className="bg-transparent w-16 text-center text-lg text-white outline-none font-medium"
                                value={customRatioH}
                                onChange={(e) =>
                                  setCustomRatioH(e.target.value)
                                }
                              />
                            </div>
                          ) : (
                            <span
                              className={`font-medium text-[28px] transition-colors duration-500 tracking-wide ${
                                isFocused ? "text-[#f8f8f8]" : "text-zinc-600"
                              }`}
                            >
                              {r.ratio === "custom" ? "Custom" : r.ratio}
                            </span>
                          )}

                          {isFocused ? (
                            <span className="text-[9px] font-medium tracking-[0.25em] text-zinc-400 uppercase mt-1">
                              {r.label}
                            </span>
                          ) : (
                            <span className="text-[9px] font-medium tracking-[0.25em] text-zinc-700 uppercase mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
                              {r.label}
                            </span>
                          )}
                        </div>
                      </motion.div>
                    </motion.div>
                  </div>
                );
              })}
            </div>

            {/* Create Button */}
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="absolute bottom-[10%] left-0 right-0 flex flex-col items-center pointer-events-none z-30"
            >
              <button
                className="pointer-events-auto relative w-[240px] h-[52px] group flex items-center justify-center rounded-[26px] transition-all duration-300 hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
                onClick={() => {
                  handleCreateProject(
                    focusedRatio === "custom"
                      ? `${customRatioW}:${customRatioH}`
                      : focusedRatio
                  );
                }}
              >
                <div
                  className="absolute inset-0 rounded-[26px] p-[1px] opacity-60 group-hover:opacity-100 transition-opacity duration-300"
                  style={{
                    background:
                      "linear-gradient(90deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.05) 50%, rgba(255,255,255,0.6) 100%)",
                  }}
                />
                <div className="absolute inset-[1px] rounded-[25px] bg-[#050507]" />
                <span className="relative z-10 font-bold tracking-[0.35em] text-[#f8f8f8] text-[11px] uppercase ml-1 opacity-90 group-hover:opacity-100 drop-shadow-sm">
                  Create
                </span>
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Delete Confirmation Modal */}
      <AnimatePresence>
        {projectToDelete && (
          <div
            className="fixed inset-0 z-[300] bg-black/70 backdrop-blur-md flex items-center justify-center p-4"
            onClick={() => setProjectToDelete(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-zinc-900 border border-white/10 rounded-[32px] p-6 max-w-sm w-full shadow-2xl"
            >
              <h3 className="text-xl font-bold text-white mb-2">
                Delete Project
              </h3>
              <p className="text-zinc-400 text-sm mb-6 font-medium">
                Are you sure you want to delete this project? This action cannot
                be undone.
              </p>
              <div className="flex justify-end gap-3">
                <button
                  className="px-5 py-2.5 rounded-full text-sm font-bold text-white hover:bg-zinc-800 transition-colors"
                  onClick={() => setProjectToDelete(null)}
                >
                  Cancel
                </button>
                <button
                  className="px-5 py-2.5 bg-red-500 hover:bg-red-600 rounded-full text-sm font-bold text-white transition-colors shadow-lg"
                  onClick={confirmDeleteProject}
                >
                  Delete
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Unsupported File Warning Modal */}
      <AnimatePresence>
        {unsupportedFileDetails && (
          <div
            className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-md flex items-center justify-center p-4"
            onClick={() => setUnsupportedFileDetails(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 15 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 15 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-[#1C1C1E] border border-white/10 rounded-[32px] p-6 max-w-sm w-full shadow-2xl relative overflow-hidden text-center"
            >
              <div className="w-12 h-12 bg-red-500/10 border border-red-500/20 rounded-full flex items-center justify-center mx-auto mb-4 text-red-400">
                <AlertCircle size={24} />
              </div>
              <h3 className="text-lg font-extrabold text-white mb-2">
                Unsupported Format
              </h3>
              <p className="text-zinc-400 text-xs sm:text-sm mb-4 leading-relaxed font-medium">
                The file{" "}
                <span className="text-white font-semibold break-all">
                  "{unsupportedFileDetails.name}"
                </span>{" "}
                is not supported by your device's video decoder.
              </p>
              <div className="bg-[#2A2A2D]/50 rounded-2xl p-3 mb-6 text-left border border-white/5 space-y-1">
                <div className="flex justify-between text-[11px] font-semibold">
                  <span className="text-zinc-500">File Type:</span>
                  <span className="text-zinc-300">
                    {unsupportedFileDetails.type || "unknown"}
                  </span>
                </div>
                <div className="flex justify-between text-[11px] font-semibold">
                  <span className="text-zinc-500">File Size:</span>
                  <span className="text-zinc-300">
                    {(unsupportedFileDetails.size / (1024 * 1024)).toFixed(1)}{" "}
                    MB
                  </span>
                </div>
              </div>
              <div className="flex justify-center">
                <button
                  className="px-6 py-2 bg-white hover:bg-zinc-200 active:bg-zinc-300 rounded-full text-xs font-bold text-black transition-colors"
                  onClick={() => setUnsupportedFileDetails(null)}
                >
                  Dismiss
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};
