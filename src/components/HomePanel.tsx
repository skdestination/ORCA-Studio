import React, { useState, useEffect } from "react";
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
} from "lucide-react";
import { Project } from "../types";
import { getFile } from "../lib/db";

// ProjectCoverImage local component
function ProjectCoverImage({ p }: { p: Project }) {
  const [dbSrc, setDbSrc] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    if (p.thumbnailFileId) {
      getFile(p.thumbnailFileId).then(blob => {
        if (blob) {
          objectUrl = URL.createObjectURL(blob);
          setDbSrc(objectUrl);
        }
      }).catch(console.error);
    }
    
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [p.thumbnailFileId]);

  return (
    <img
      src={dbSrc || p.thumbnail || undefined}
      alt=""
      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
      referrerPolicy="no-referrer"
    />
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
  // Sort the projects based on sortBy state
  const sortedAndFilteredProjects = [...projects].sort((a, b) => {
    if (sortBy === "name") {
      return a.name.localeCompare(b.name);
    } else if (sortBy === "duration") {
      return a.duration.localeCompare(b.duration);
    } else {
      // default "recent" order: New Project first, then Summer Vacation, then Frosted, then any custom
      const getWeight = (p: Project) => {
        if (p.id === "new-p") return 3;
        if (p.id === "1") return 2;
        if (p.id === "frosted-p") return 1;
        return 0;
      };
      const wA = getWeight(a);
      const wB = getWeight(b);
      if (wA !== wB) return wB - wA;
      // fallback to custom index or ID
      return b.id.localeCompare(a.id);
    }
  });

  return (
    <div className="flex flex-col h-screen w-full bg-black overflow-hidden relative">
      {/* Elegant Glowing Light Leak / Arch Effect at the top-right background */}
      <div className="absolute top-[-100px] right-[-200px] w-[600px] h-[500px] bg-[radial-gradient(circle_at_center,rgba(164,198,217,0.12)_0%,transparent_65%)] rounded-full blur-[100px] pointer-events-none z-0" />
      <div className="absolute top-[20%] right-[-150px] w-[500px] h-[400px] bg-[radial-gradient(circle_at_center,rgba(249,115,22,0.04)_0%,transparent_60%)] rounded-full blur-[80px] pointer-events-none z-0" />

      {/* Main Scrollable Viewport */}
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 scrollbar-hide z-10">
        <div className="min-h-full flex flex-col pb-[120px]">
          
          {/* Header Area with profile info and notification bell */}
          <div className="pt-8 pb-4 flex justify-between items-center max-w-2xl mx-auto w-full">
            <div className="flex items-center gap-3">
              <div className="relative w-10 h-10 rounded-full overflow-hidden border border-white/20 shadow-[0_0_12px_rgba(255,255,255,0.15)] bg-zinc-900">
                <img
                  src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=150"
                  alt="Ritwik"
                  className="w-full h-full object-cover"
                  referrerPolicy="no-referrer"
                />
              </div>
              <div className="flex flex-col">
                <span className="text-zinc-500 font-medium text-[10px] sm:text-[11px] uppercase tracking-wider leading-none mb-0.5">
                  {new Date().getHours() < 12 ? "Good Morning," : new Date().getHours() < 18 ? "Good Afternoon," : "Good Evening,"}
                </span>
                <span className="text-white font-extrabold text-sm tracking-tight leading-none">
                  Ritwik
                </span>
              </div>
            </div>

            <div className="flex gap-2">
              {!Capacitor.isNativePlatform() && (
                <button
                  onClick={toggleFullscreen}
                  className="w-10 h-10 rounded-full bg-zinc-900/60 backdrop-blur-md border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/20 transition-all active:scale-95 group cursor-pointer"
                  title={isFullscreen ? "Exit Fullscreen" : "Enter Fullscreen"}
                >
                  {isFullscreen ? (
                    <Minimize2 size={18} className="transition-transform group-hover:scale-110" />
                  ) : (
                    <Maximize2 size={18} className="transition-transform group-hover:scale-110" />
                  )}
                </button>
              )}

              <button className="relative w-10 h-10 rounded-full bg-zinc-900/60 backdrop-blur-md border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/20 transition-all active:scale-95 group cursor-pointer">
                <Bell size={18} className="transition-transform group-hover:rotate-12" />
                <span className="absolute top-2.5 right-2.5 w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_6px_rgba(249,115,22,0.8)]" />
              </button>
            </div>
          </div>

          {/* Premium Branded ORCA Creative Studio Logo */}
          <div className="pt-10 pb-8 flex flex-col justify-center items-center w-full mt-[2vh] text-center">
            <h1 className="text-7xl sm:text-8.5xl font-black tracking-[-0.04em] text-white leading-none uppercase bg-clip-text text-transparent bg-gradient-to-b from-white via-zinc-100 to-zinc-400">
              ORCA
            </h1>
            <div className="tracking-[0.35em] text-[10px] sm:text-[11px] text-zinc-500 font-bold uppercase flex items-center justify-center gap-1.5 mt-3 pl-[0.35em]">
              <span>Creative Studio</span>
              <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_5px_rgba(249,115,22,0.6)]" />
            </div>
          </div>

          {/* Subsection Header: Projects & Filter */}
          <div className="flex justify-between items-center max-w-2xl mx-auto w-full mb-6 mt-4 px-1">
            <h2 className="text-lg sm:text-xl font-bold text-white tracking-tight">
              Projects
            </h2>
            
            {/* Sort Dropdown Selector */}
            <div className="relative">
              <button
                onClick={() => setIsSortMenuOpen(!isSortMenuOpen)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-900/60 border border-white/5 hover:bg-zinc-800/80 hover:border-white/15 text-zinc-300 text-xs font-bold leading-none cursor-pointer transition-all active:scale-95 z-20"
              >
                <span>
                  {sortBy === "recent" ? "Recent" : sortBy === "name" ? "Alphabetical" : "Duration"}
                </span>
                <ChevronDown size={12} className={`text-zinc-500 transition-transform duration-200 ${isSortMenuOpen ? "rotate-180" : ""}`} />
              </button>

              <AnimatePresence>
                {isSortMenuOpen && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95, y: -5 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: -5 }}
                    className="absolute right-0 top-8 w-36 bg-zinc-900/95 border border-white/10 rounded-xl p-1 shadow-[0_12px_40px_rgba(0,0,0,0.8)] backdrop-blur-xl z-50"
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
                        className={`w-full text-left px-2.5 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center justify-between ${
                          sortBy === item.value ? "bg-white/[0.05] text-white" : "text-zinc-400 hover:bg-white/[0.02] hover:text-zinc-200"
                        }`}
                      >
                        <span>{item.label}</span>
                        {sortBy === item.value && <Check size={11} className="text-emerald-400" />}
                      </button>
                    ))}
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          {/* Project List */}
          <div className="flex flex-col gap-4.5 max-w-2xl mx-auto w-full">
            {sortedAndFilteredProjects.map((p) => (
              <div
                key={p.id}
                className="relative h-[160px] w-full rounded-[30px] overflow-hidden cursor-pointer bg-zinc-950/20 hover:bg-zinc-950/30 backdrop-blur-xl border border-white/10 hover:border-white/20 active:scale-[0.99] transition-all duration-300 shadow-[0_12px_40px_-15px_rgba(0,0,0,0.8)] flex group"
                onClick={() => openProject(p)}
              >
                {/* Glossy sheen base texture overlay */}
                <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.01] to-white/[0.05] pointer-events-none z-0" />

                {/* Highly reflective glass gleam transition line */}
                <div className="absolute inset-0 w-1/2 h-full bg-gradient-to-r from-transparent via-white/[0.07] to-transparent -skew-x-[25deg] -translate-x-[150%] group-hover:translate-x-[220%] transition-transform duration-1000 ease-[cubic-bezier(0.25,1,0.5,1)] pointer-events-none z-20" />

                {/* Beveled edge light reflections */}
                <div className="absolute inset-x-0 top-0 h-[1.5px] bg-gradient-to-r from-white/0 via-white/25 to-white/0 opacity-80 group-hover:opacity-100 transition-opacity pointer-events-none z-20" />
                <div className="absolute inset-y-0 left-0 w-[1.5px] bg-gradient-to-b from-white/10 to-transparent pointer-events-none z-20" />

                {/* Left Text Info Side */}
                <div className="flex-1 p-5 flex flex-col justify-between h-full z-10 relative">
                  {/* Top: Tag */}
                  <div className="flex items-center gap-1.5 bg-white/[0.03] border border-white/5 rounded-full px-2.5 py-0.5 w-fit">
                    <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shadow-[0_0_6px_#f97316]" />
                    <span className="text-[9px] font-bold text-zinc-400 uppercase tracking-widest leading-none">
                      {p.ratio === "9:16" ? "Reels" : p.ratio === "16:9" ? "YouTube" : "Concept"}
                    </span>
                  </div>

                  {/* Middle: Title & Label */}
                  <div className="mt-1">
                    <h3 className="text-lg sm:text-xl font-bold text-white tracking-tight leading-none flex items-center gap-1.5">
                      {p.name}
                    </h3>
                    <p className="text-[11.5px] sm:text-[12.5px] font-medium text-zinc-400 leading-snug mt-1.5 max-w-[95%] line-clamp-2">
                      Concept project focusing on simplicity & usability.
                    </p>
                  </div>

                  {/* Bottom: Specs */}
                  <div className="flex items-center gap-1.5 text-zinc-500 text-[10px] font-bold tracking-wider uppercase mt-1">
                    <Clock size={11} className="text-zinc-500" />
                    <span>{p.duration || "00:12"}</span>
                    <span className="text-zinc-600">•</span>
                    <span>Draft</span>
                  </div>
                </div>

                {/* Right Image/Artwork Side */}
                <div className="w-[45%] h-full shrink-0 relative overflow-hidden z-10 bg-zinc-900/50">
                  <ProjectCoverImage p={p} />
                  {/* Frosted vignette masking division */}
                  <div className="absolute inset-y-0 left-0 w-8 bg-gradient-to-r from-zinc-950/40 via-zinc-950/20 to-transparent pointer-events-none" />

                  {/* Three-dots menu button */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setProjectMenuOpenId(projectMenuOpenId === p.id ? null : p.id);
                    }}
                    className="absolute top-3.5 right-3.5 w-8 h-8 rounded-full bg-black/50 border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:bg-black/75 hover:border-white/25 transition-all backdrop-blur-md z-30 pointer-events-auto"
                  >
                    <MoreHorizontal size={14} />
                  </button>

                  {/* Action Menu overlay per project card */}
                  <AnimatePresence>
                    {projectMenuOpenId === p.id && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.9, y: -5 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9, y: -5 }}
                        onClick={(e) => e.stopPropagation()}
                        className="absolute right-3.5 top-13 w-32 bg-zinc-900/95 border border-white/10 rounded-2xl p-1.5 shadow-[0_12px_36px_rgba(0,0,0,0.9)] backdrop-blur-xl z-40 pointer-events-auto"
                      >
                        <button
                          onClick={() => {
                            setProjectMenuOpenId(null);
                            openProject(p);
                          }}
                          className="w-full text-left px-3 py-2 text-xs font-semibold text-white hover:bg-white/[0.05] rounded-xl flex items-center justify-between cursor-pointer"
                        >
                          <span>Open</span>
                          <ChevronRight size={11} className="text-zinc-500" />
                        </button>
                        <button
                          onClick={() => {
                            setProjectMenuOpenId(null);
                            duplicateProject(p);
                          }}
                          className="w-full text-left px-3 py-2 text-xs font-semibold text-white hover:bg-white/[0.05] rounded-xl flex items-center justify-between cursor-pointer"
                        >
                          <span>Duplicate</span>
                          <Copy size={11} className="text-zinc-400" />
                        </button>
                        <div className="h-[1px] bg-white/5 my-1" />
                        <button
                          onClick={() => {
                            setProjectMenuOpenId(null);
                            setProjectToDelete(p.id);
                          }}
                          className="w-full text-left px-3 py-2 text-xs font-semibold text-red-400 hover:bg-red-500/10 rounded-xl flex items-center justify-between cursor-pointer"
                        >
                          <span>Delete</span>
                          <Trash2 size={11} className="text-red-400/70" />
                        </button>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Persistent Rounded Floating Footer Row */}
      <div className="absolute bottom-[50px] left-0 right-0 flex justify-center items-center px-6 z-40 pointer-events-none">
        <div className="flex items-center gap-3 w-full max-w-[340px] pointer-events-auto">
          {/* New Project Button */}
          <button
            onClick={() => setIsCreatingProject(true)}
            className="relative flex-1 h-[50px] rounded-full bg-zinc-950/40 hover:bg-zinc-950/55 backdrop-blur-xl border border-white/10 text-white font-bold text-sm tracking-wide flex items-center justify-center gap-2 hover:border-white/20 active:scale-[0.97] transition-all duration-300 shadow-[0_12px_36px_rgba(0,0,0,0.65)] cursor-pointer overflow-hidden group"
          >
            {/* Glossy shine */}
            <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.01] to-white/[0.04] pointer-events-none" />
            <div className="absolute inset-0 w-1/2 h-full bg-gradient-to-r from-transparent via-white/[0.1] to-transparent -skew-x-[20deg] -translate-x-[150%] group-hover:translate-x-[250%] transition-transform duration-1000 ease-out pointer-events-none" />
            <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/15 to-transparent pointer-events-none" />
            <PlusIcon size={16} className="text-zinc-400 group-hover:text-white transition-colors" />
            <span>New Project</span>
          </button>

          {/* Settings Button */}
          <button
            onClick={() => setCurrentScreen("settings")}
            className="relative w-[50px] h-[50px] rounded-full bg-zinc-950/40 hover:bg-zinc-950/55 backdrop-blur-xl border border-white/10 flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/20 active:scale-[0.97] transition-all duration-300 shadow-[0_12px_36px_rgba(0,0,0,0.65)] cursor-pointer overflow-hidden group"
          >
            {/* Glossy shine */}
            <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.01] to-white/[0.04] pointer-events-none" />
            <div className="absolute inset-0 w-1/2 h-full bg-gradient-to-r from-transparent via-white/[0.1] to-transparent -skew-x-[20deg] -translate-x-[150%] group-hover:translate-x-[250%] transition-transform duration-1000 ease-out pointer-events-none" />
            <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/15 to-transparent pointer-events-none" />
            <Settings size={18} className="group-hover:rotate-45 transition-transform duration-300" />
          </button>
        </div>
      </div>

      {/* Ratio Selection Overlay */}
      <AnimatePresence>
        {isCreatingProject && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
            className="fixed inset-0 z-50 bg-[#040404] flex flex-col items-center justify-center overflow-hidden"
          >
            {/* Dotted Grid Background */}
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center z-0">
               <div 
                 className="w-full max-w-[800px] h-full max-h-[800px] opacity-[0.2]"
                 style={{
                   backgroundImage: `radial-gradient(circle at center, rgba(255,255,255,0.8) 1px, transparent 1px)`,
                   backgroundSize: `28px 28px`,
                   WebkitMaskImage: `radial-gradient(ellipse 50% 50% at center, black 10%, transparent 60%)`,
                   maskImage: `radial-gradient(ellipse 50% 50% at center, black 10%, transparent 60%)`
                 }}
               />
            </div>

            {/* Title */}
            <motion.div 
               initial={{ opacity: 0, y: -10 }}
               animate={{ opacity: 1, y: 0 }}
               transition={{ delay: 0.2, duration: 0.8 }}
               className="absolute top-[18%] left-0 right-0 flex items-center justify-center px-8 pointer-events-none z-10"
            >
              <div className="w-[60px] sm:w-[100px] h-[1px] bg-gradient-to-r from-transparent to-zinc-700" />
              <span className="text-[10px] font-semibold tracking-[0.3em] text-zinc-500 px-6 uppercase whitespace-nowrap">
                Choose Format
              </span>
              <div className="w-[60px] sm:w-[100px] h-[1px] bg-gradient-to-l from-transparent to-zinc-700" />
            </motion.div>

            <motion.button
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="absolute top-8 right-8 w-12 h-12 rounded-full bg-transparent hover:bg-zinc-800/30 flex items-center justify-center text-zinc-500 hover:text-white transition-colors z-50"
              onClick={() => setIsCreatingProject(false)}
            >
              <X size={24} strokeWidth={1.5} />
            </motion.button>

            <div 
              className="relative z-20 flex flex-nowrap items-center overflow-x-auto w-full pb-10 pt-16 scrollbar-hide snap-x snap-mandatory"
              onScroll={(e) => {
                const container = e.currentTarget;
                const containerCenter = container.getBoundingClientRect().left + container.clientWidth / 2;
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
                paddingRight: "calc(50vw - 160px)"
              }}
            >
              {[
                { ratio: "9:16", baseW: 160, baseH: 340, label: "PORTRAIT" },
                { ratio: "16:9", baseW: 340, baseH: 160, label: "LANDSCAPE" },
                { ratio: "1:1", baseW: 240, baseH: 240, label: "SQUARE" },
                { ratio: "custom", baseW: 240, baseH: 120, label: "CUSTOM" },
              ].map((r, i) => {
                const isFocused = focusedRatio === r.ratio;
                const scale = isFocused ? 1 : 0.85;
                return (
                  <div key={r.ratio} data-ratio={r.ratio} className="w-[320px] shrink-0 snap-center flex justify-center items-center">
                    <motion.div
                       className="cursor-pointer group relative flex justify-center items-center"
                       onClick={(e) => {
                          if (!isFocused) {
                             e.currentTarget.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
                          }
                       }}
                    >
                      <motion.div
                        animate={{ width: r.baseW * scale, height: r.baseH * scale }}
                        transition={{ type: "spring", bounce: 0.3 }}
                        className={`relative rounded-[36px] flex items-center justify-center transition-all duration-500 ${isFocused ? "p-[1.5px]" : "p-[1px]"}`}
                      >
                         {/* Gradient Border for Focused */}
                         <div 
                            className={`absolute inset-0 rounded-[36px] pointer-events-none transition-opacity duration-700 ${isFocused ? "opacity-100" : "opacity-0"}`} 
                            style={{ background: 'linear-gradient(145deg, rgba(167, 139, 250, 0.8) 0%, rgba(255, 255, 255, 0.1) 40%, rgba(255, 255, 255, 0.1) 60%, rgba(251, 146, 60, 0.7) 100%)' }} 
                         />
                         
                         {/* Subtle Border for Unfocused */}
                         <div className={`absolute inset-0 rounded-[36px] pointer-events-none transition-opacity duration-500 border border-white/10 ${!isFocused ? "opacity-100" : "opacity-0"}`} />

                         <div className="absolute inset-[1.5px] rounded-[34.5px] bg-[#040404] z-10 pointer-events-none" />
                         
                         <div className="relative z-20 flex flex-col items-center justify-center gap-2">
                           {r.ratio === "custom" && isFocused ? (
                             <div className="flex items-center gap-3 bg-zinc-900/40 rounded-2xl p-2 border border-white/5" onClick={e => e.stopPropagation()}>
                               <input type="number" placeholder="W" className="bg-transparent w-16 text-center text-lg text-white outline-none font-medium" value={customRatioW} onChange={(e) => setCustomRatioW(e.target.value)} />
                               <div className="w-[1px] h-6 bg-zinc-700"></div>
                               <input type="number" placeholder="H" className="bg-transparent w-16 text-center text-lg text-white outline-none font-medium" value={customRatioH} onChange={(e) => setCustomRatioH(e.target.value)} />
                             </div>
                           ) : (
                             <span className={`font-medium text-[28px] transition-colors duration-500 tracking-wide ${isFocused ? "text-[#f8f8f8]" : "text-zinc-600"}`}>
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

            <motion.div 
               initial={{ opacity: 0, y: 10 }}
               animate={{ opacity: 1, y: 0 }}
               transition={{ delay: 0.3, duration: 0.8 }}
               className="absolute bottom-[10%] left-0 right-0 flex flex-col items-center pointer-events-none z-30"
            >
              <button 
                className="pointer-events-auto relative w-[240px] h-[52px] group flex items-center justify-center rounded-[26px] transition-all duration-300 hover:scale-[1.02] active:scale-[0.98]"
                onClick={() => {
                   handleCreateProject(focusedRatio === "custom" ? `${customRatioW}:${customRatioH}` : focusedRatio);
                }}
              >
                 <div className="absolute inset-0 rounded-[26px] p-[1px] opacity-60 group-hover:opacity-100 transition-opacity duration-300"
                      style={{ background: 'linear-gradient(90deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.05) 50%, rgba(255,255,255,0.6) 100%)' }} />
                 <div className="absolute inset-[1px] rounded-[25px] bg-[#040404]" />
                 <span className="relative z-10 font-bold tracking-[0.35em] text-[#f8f8f8] text-[11px] uppercase ml-1 opacity-90 group-hover:opacity-100 drop-shadow-sm">
                   Create
                 </span>
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {projectToDelete && (
          <div
            className="fixed inset-0 z-[300] bg-black/60 flex items-center justify-center p-4"
            onClick={() => setProjectToDelete(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-[#252528] border border-white/10 rounded-[32px] p-6 max-w-sm w-full shadow-2xl"
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
                  className="px-5 py-2.5 rounded-full text-sm font-bold text-white hover:bg-zinc-700 transition-colors"
                  onClick={() => setProjectToDelete(null)}
                >
                  Cancel
                </button>
                <button
                  className="px-5 py-2.5 bg-red-500 hover:bg-red-600 rounded-full text-sm font-bold text-white transition-colors"
                  onClick={confirmDeleteProject}
                >
                  Delete
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {unsupportedFileDetails && (
          <div
            className="fixed inset-0 z-[300] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-fade-in"
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
                The file <span className="text-white font-semibold break-all">"{unsupportedFileDetails.name}"</span> is not supported by your device's video decoder.
              </p>
              <div className="bg-[#2A2A2D]/50 rounded-2xl p-3 mb-6 text-left border border-white/5 space-y-1">
                <div className="flex justify-between text-[11px] font-semibold">
                  <span className="text-zinc-500">File Type:</span>
                  <span className="text-zinc-300">{unsupportedFileDetails.type || "unknown"}</span>
                </div>
                <div className="flex justify-between text-[11px] font-semibold">
                  <span className="text-zinc-500">File Size:</span>
                  <span className="text-zinc-300">{(unsupportedFileDetails.size / (1024 * 1024)).toFixed(1)} MB</span>
                </div>
                <div className="text-[10px] text-zinc-500 leading-normal mt-2 border-t border-white/5 pt-2">
                  Tip: Use standard MP4 (H.264 video with AAC audio) for the best slow-motion performance and compatibility.
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
