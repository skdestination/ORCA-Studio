import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Check } from 'lucide-react';

export function TextEditorMenu({ 
  clip,
  updateClip,
  setToastMessage
}: {
  clip: any;
  updateClip: (updates: any) => void;
  setToastMessage: (msg: string | null) => void;
}) {
  const [activeTab, setActiveTab] = useState<'preset' | 'font' | 'style' | 'animation'>('style');
  const [activeSubTab, setActiveSubTab] = useState<'text' | 'stroke' | 'glow' | 'spacing' | 'shadow'>('text');
  const [customFonts, setCustomFonts] = useState<{name: string, url: string}[]>([]);
  const fontInputRef = useRef<HTMLInputElement>(null);

  const defaultPresets = [
    { name: "Classic Pure", color: "#ffffff", fontSize: 44, fontFamily: "Inter", textAnimation: "None" },
    { name: "Neon Vibes", color: "#ff2a85", fontSize: 50, fontFamily: "Impact", textAnimation: "Bounce" },
    { name: "Retro Synth", color: "#e2db81", fontSize: 46, fontFamily: "monospace", textAnimation: "Typewriter" },
    { name: "Cinematic Gold", color: "#ffd700", fontSize: 48, fontFamily: "Georgia", textAnimation: "Fade In" },
    { name: "Nordic Minimal", color: "#cbd5e1", fontSize: 38, fontFamily: "sans-serif", textAnimation: "Slide Up" },
    { name: "Bold Block", color: "#ffffff", fontSize: 56, fontFamily: "Arial", textAnimation: "Fade In" }
  ];
  const [presets, setPresets] = useState(defaultPresets);

  useEffect(() => {
    try {
      const saved = localStorage.getItem('ai_studio_custom_fonts');
      if (saved) setCustomFonts(JSON.parse(saved));
    } catch(e){}
    
    try {
      const savedPresets = localStorage.getItem('ai_studio_custom_presets');
      if (savedPresets) {
        setPresets([...defaultPresets, ...JSON.parse(savedPresets)]);
      }
    } catch(e){}
  }, []);

  const handleSavePreset = () => {
    const newPreset = {
      name: `Custom ${presets.length - defaultPresets.length + 1}`,
      color: clip?.color || "#ffffff",
      fontSize: clip?.fontSize || 48,
      fontFamily: clip?.fontFamily || "Inter",
      textAnimation: clip?.textAnimation || "None",
    };
    
    const customPresets = [...presets.slice(defaultPresets.length), newPreset];
    try {
      localStorage.setItem('ai_studio_custom_presets', JSON.stringify(customPresets));
    } catch (err) {
      console.warn("Could not save preset to localStorage");
    }
    
    setPresets([...defaultPresets, ...customPresets]);
    setToastMessage("Preset saved!");
    setTimeout(() => setToastMessage(null), 2000);
  };

  const handleFontUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const newFontName = file.name.split('.')[0];
    const reader = new FileReader();
    reader.onload = (e) => {
      const b64 = e.target?.result as string;
      const newFont = new FontFace(newFontName, `url(${b64})`);
      newFont.load().then((loadedFace) => {
        document.fonts.add(loadedFace);
        const newFonts = [...customFonts, { name: newFontName, url: b64 }];
        setCustomFonts(newFonts);
        try {
          localStorage.setItem('ai_studio_custom_fonts', JSON.stringify(newFonts));
        } catch (err) {
          console.warn("Could not save font to localStorage");
        }
        updateClip({ fontFamily: newFontName });
        setToastMessage(`Added font: ${newFontName}`);
        setTimeout(() => setToastMessage(null), 2000);
      }).catch((err) => {
        console.error(err);
        setToastMessage("Failed to load font");
        setTimeout(() => setToastMessage(null), 2000);
      });
    };
    reader.readAsDataURL(file);
  };

  const fonts = ["Inter", "Space Grotesk", "sans-serif", "serif", "monospace", "Impact", "Arial", "Georgia", "Courier New"];
  const animations = ["None", "Fade In", "Slide Up", "Typewriter", "Bounce", "Pop", "Glitch", "Wave"];
  
  const paletteColors = ["#ffffff", "#dbeafe", "#94a3b8", "#475569", "#18181b", "#eab308"];

  const tabs = [
    { id: 'preset', label: 'PRESET' },
    { id: 'font', label: 'FONT' },
    { id: 'style', label: 'STYLE' },
    { id: 'animation', label: 'ANIMATION' }
  ] as const;

  const subTabs = [
    { id: 'text', label: 'TEXT' },
    { id: 'stroke', label: 'STROKE' },
    { id: 'glow', label: 'GLOW' },
    { id: 'spacing', label: 'SPACING' },
    { id: 'shadow', label: 'SHADOW' }
  ] as const;

  const getActiveColor = () => {
    if (activeSubTab === 'stroke') return clip?.strokeColor || "#000000";
    if (activeSubTab === 'glow') return clip?.glowColor || "#ffffff";
    if (activeSubTab === 'shadow') return clip?.shadowColor || "#000000";
    return clip?.color || "#ffffff";
  };

  const setActiveColor = (color: string) => {
    if (activeSubTab === 'stroke') {
      updateClip({ strokeColor: color, strokeWidth: clip?.strokeWidth || 1.5 });
    } else if (activeSubTab === 'glow') {
      updateClip({ glowColor: color, glowRadius: clip?.glowRadius || 8 });
    } else if (activeSubTab === 'shadow') {
      updateClip({ shadowColor: color, shadowBlur: clip?.shadowBlur || 5, shadowOffsetX: clip?.shadowOffsetX || 3, shadowOffsetY: clip?.shadowOffsetY || 3 });
    } else {
      updateClip({ color });
    }
  };

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95, y: 10 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95, y: 10 }}
      transition={{ type: "spring", bounce: 0, duration: 0.3 }}
      className="flex flex-col w-full bg-transparent shadow-none px-2.5 pt-1.5 pb-1 gap-2 overflow-visible font-sans select-none text-left"
    >
      {/* Top Tabs */}
      <div className="flex items-center justify-between w-full relative shrink-0 px-2 mt-0">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button 
              key={tab.id}
              onClick={() => setActiveTab(tab.id)} 
              className="relative flex flex-col items-center justify-center py-1 outline-none transition-colors duration-200 cursor-pointer"
            >
              <span className={`text-[6px] sm:text-[7px] tracking-[0.15em] font-medium uppercase transition-colors ${
                isActive ? 'text-[#f4f4f5]' : 'text-[#71717a] hover:text-[#a1a1aa]'
              }`}>
                {tab.label}
              </span>
              {isActive && (
                <motion.div 
                  layoutId="activeTabUnderlineMain"
                  className="absolute bottom-[-4px] left-[5%] right-[5%] h-[1px] bg-[#ef4444]"
                  transition={{ type: "spring", stiffness: 380, damping: 30 }}
                />
              )}
            </button>
          )
        })}
      </div>

      {/* Main Content Area */}
      <div className="w-full flex-1 min-h-[76px] overflow-visible">
        <AnimatePresence mode="wait">
          {activeTab === 'preset' && (
            <motion.div
              key="presets"
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
              transition={{ duration: 0.15 }}
              className="flex flex-col gap-1 max-h-[76px] pt-1"
            >
              <div className="grid grid-cols-2 gap-1.5 overflow-y-auto scrollbar-hide pb-1">
                {presets.map((p, i) => {
                  const isSelected = clip?.color === p.color && clip?.fontSize === p.fontSize && clip?.fontFamily === p.fontFamily && clip?.textAnimation === p.textAnimation;
                  return (
                    <button
                      key={`${p.name}-${i}`}
                      onClick={() => updateClip({ color: p.color, fontSize: p.fontSize, fontFamily: p.fontFamily, textAnimation: p.textAnimation })}
                      className={`relative flex items-center justify-start gap-2.5 p-1 px-2 rounded-[8px] border transition-all h-[28px] bg-zinc-950/20 cursor-pointer ${
                        isSelected ? 'border-[#ef4444]/40 bg-zinc-900/60 shadow-[0_2px_8px_rgba(239,68,68,0.15)]' : 'border-white/[0.03] hover:border-white/[0.08] hover:bg-zinc-900/10'
                      }`}
                    >
                      <span className="text-[10px] font-black leading-none drop-shadow-md w-5 text-center shrink-0" style={{ color: p.color, fontFamily: p.fontFamily }}>Aa</span>
                      <span className="text-[6.5px] font-medium text-[#a1a1aa] uppercase tracking-[0.05em] truncate w-full leading-none">{p.name}</span>
                      {isSelected && <div className="absolute top-1 right-1 w-[3px] h-[3px] rounded-full bg-[#ef4444] shadow-[0_0_6px_#ef4444]" />}
                    </button>
                  );
                })}
              </div>
              <button 
                onClick={handleSavePreset}
                className="w-full flex items-center justify-center gap-1.5 p-1.5 rounded-[8px] bg-[#27272a]/50 hover:bg-[#27272a] border border-white/5 hover:border-white/10 transition-colors shrink-0 mb-0.5"
              >
                <Plus size={8} className="text-[#a1a1aa]" />
                <span className="text-[6.5px] font-semibold tracking-wider text-[#d4d4d8] uppercase">Save Current as Preset</span>
              </button>
            </motion.div>
          )}

          {activeTab === 'font' && (
            <motion.div key="fonts" initial={{ opacity: 0, scale: 0.98 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }} transition={{ duration: 0.15 }} className="flex flex-col max-h-[76px] gap-1.5 pt-1">
              <div className="flex items-center justify-between w-full">
                <span className="text-[6.5px] font-semibold tracking-wider text-[#71717a] uppercase leading-none">FONT FAMILY</span>
                <button onClick={() => fontInputRef.current?.click()} className="flex items-center justify-center gap-1 px-1.5 py-0.5 text-[5.5px] uppercase tracking-wider font-extrabold bg-[#27272a] border border-white/5 hover:border-white/10 hover:bg-[#3f3f46] rounded-md transition-all text-[#d4d4d8] cursor-pointer"><Plus size={6} className="text-[#a1a1aa]" /> UPLOAD</button>
              </div>
              <input type="file" ref={fontInputRef} className="hidden" accept=".ttf,.otf,.woff,.woff2" onChange={handleFontUpload} />
              <div className="grid grid-cols-2 gap-1.5 auto-rows-min overflow-y-auto scrollbar-hide max-h-[58px] pb-1">
                {[...customFonts.map(f => f.name), ...fonts].map(f => {
                  const isSelected = clip?.fontFamily === f || (!clip?.fontFamily && f === "Inter");
                  return (
                    <button key={f} onClick={() => updateClip({ fontFamily: f })} className={`flex items-center justify-center py-1 px-1.5 rounded-[6px] transition-all outline-none border text-center h-[22px] cursor-pointer ${isSelected ? 'bg-[#27272a] border-[#ef4444]/45 text-white font-medium' : 'border-transparent bg-[#18181b]/50 text-[#a1a1aa] hover:text-white hover:bg-[#27272a]/80'}`}>
                      <span className="text-[6.5px] truncate w-full tracking-[0.02em] font-medium" style={{ fontFamily: f }}>{f}</span>
                    </button>
                  );
                })}
              </div>
            </motion.div>
          )}

          {activeTab === 'style' && (
            <motion.div key="style" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }} className="flex w-full items-start justify-between">
              
              {/* Left Side: Controls */}
              <div className="flex flex-col flex-1 max-w-[125px] gap-1.5 mt-0.5 pr-1">
                {/* Palette */}
                {activeSubTab !== 'spacing' && (
                  <div className="flex flex-col gap-1">
                    <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">PALETTE</span>
                    <div className="flex gap-1.5 items-center">
                      {paletteColors.map((color, i) => {
                        const currentVal = getActiveColor();
                        const isChosen = currentVal.toLowerCase() === color.toLowerCase();
                        return (
                          <button
                            key={i}
                            onClick={() => setActiveColor(color)}
                            className={`w-[11px] h-[11px] sm:w-[12px] sm:h-[12px] rounded-full transition-all outline-none border border-black/10 relative flex items-center justify-center cursor-pointer hover:scale-110 active:scale-95`}
                            style={{ backgroundColor: color }}
                          >
                            {isChosen && <Check size={7} className="text-[#09090b] outline-none stroke-[3]" strokeLinecap="round" />}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Sliders Area */}
                {activeSubTab === 'text' && (
                  <>
                    <div className="flex flex-col gap-1.5 mt-1">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">SIZE</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="12" max="150" step="1" value={clip?.fontSize || 48} onChange={(e) => updateClip({ fontSize: parseInt(e.target.value) })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none">
                            <div className="h-full bg-[#f4f4f5]" style={{ width: `${Math.max(0, Math.min(100, ((clip?.fontSize || 48) - 12) / 138 * 100))}%` }} />
                          </div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${Math.max(0, Math.min(100, ((clip?.fontSize || 48) - 12) / 138 * 100))}%` }} />
                        </div>
                        <span className="text-[6.5px] sm:text-[7px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{clip?.fontSize || 48}px</span>
                      </div>
                    </div>
                    <div className="flex flex-col gap-1.5 mt-0.5">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">OPACITY</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="0" max="100" step="1" value={Math.round((clip?.opacity ?? 1) * 100)} onChange={(e) => updateClip({ opacity: parseInt(e.target.value) / 100 })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none">
                            <div className="h-full bg-[#f4f4f5]" style={{ width: `${Math.max(0, Math.min(100, (clip?.opacity ?? 1) * 100))}%` }} />
                          </div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${Math.max(0, Math.min(100, (clip?.opacity ?? 1) * 100))}%` }} />
                        </div>
                        <span className="text-[6.5px] sm:text-[7px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{Math.round((clip?.opacity ?? 1) * 100)}%</span>
                      </div>
                    </div>
                  </>
                )}

                {activeSubTab === 'stroke' && (
                  <>
                    <div className="flex flex-col gap-1.5 mt-1">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">WIDTH</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="0" max="10" step="0.5" value={clip?.strokeWidth || 0} onChange={(e) => updateClip({ strokeWidth: parseFloat(e.target.value) })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none"><div className="h-full bg-[#f4f4f5]" style={{ width: `${((clip?.strokeWidth || 0) / 10) * 100}%` }} /></div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${((clip?.strokeWidth || 0) / 10) * 100}%` }} />
                        </div>
                        <span className="text-[6.5px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{clip?.strokeWidth || 0}px</span>
                      </div>
                    </div>
                  </>
                )}

                {activeSubTab === 'glow' && (
                  <>
                    <div className="flex flex-col gap-1.5 mt-1">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">RADIUS</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="0" max="30" step="1" value={clip?.glowRadius || 0} onChange={(e) => updateClip({ glowRadius: parseInt(e.target.value) })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none"><div className="h-full bg-[#f4f4f5]" style={{ width: `${((clip?.glowRadius || 0) / 30) * 100}%` }} /></div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${((clip?.glowRadius || 0) / 30) * 100}%` }} />
                        </div>
                        <span className="text-[6.5px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{clip?.glowRadius || 0}px</span>
                      </div>
                    </div>
                  </>
                )}

                {activeSubTab === 'spacing' && (
                  <>
                    <div className="flex flex-col gap-1.5 mt-1">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">LETTER SPACE</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="-4" max="20" step="0.5" value={clip?.letterSpacing || 0} onChange={(e) => updateClip({ letterSpacing: parseFloat(e.target.value) })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none"><div className="h-full bg-[#f4f4f5]" style={{ width: `${((parseFloat(clip?.letterSpacing || 0) + 4) / 24) * 100}%` }} /></div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${((parseFloat(clip?.letterSpacing || 0) + 4) / 24) * 100}%` }} />
                        </div>
                        <span className="text-[6.5px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{clip?.letterSpacing || 0}px</span>
                      </div>
                    </div>
                    <div className="flex flex-col gap-1.5 mt-0.5">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">LINE HEIGHT</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="0.8" max="2.5" step="0.05" value={clip?.lineHeight || 1.25} onChange={(e) => updateClip({ lineHeight: parseFloat(e.target.value) })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none"><div className="h-full bg-[#f4f4f5]" style={{ width: `${((parseFloat(clip?.lineHeight || 1.25) - 0.8) / 1.7) * 100}%` }} /></div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${((parseFloat(clip?.lineHeight || 1.25) - 0.8) / 1.7) * 100}%` }} />
                        </div>
                        <span className="text-[6.5px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{clip?.lineHeight || 1.25}x</span>
                      </div>
                    </div>
                  </>
                )}

                {activeSubTab === 'shadow' && (
                  <>
                    <div className="flex flex-col gap-1.5 mt-1">
                      <span className="text-[6px] sm:text-[6.5px] font-semibold tracking-[0.15em] text-[#71717a] uppercase leading-none">SHADOW BLUR</span>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 flex items-center group h-2.5">
                          <input type="range" min="0" max="20" step="1" value={clip?.shadowBlur || 0} onChange={(e) => updateClip({ shadowBlur: parseInt(e.target.value) })} className="w-full absolute inset-0 z-10 opacity-0 cursor-pointer" />
                          <div className="w-full h-[1.5px] bg-[#27272a] rounded-full overflow-hidden pointer-events-none"><div className="h-full bg-[#f4f4f5]" style={{ width: `${((clip?.shadowBlur || 0) / 20) * 100}%` }} /></div>
                          <div className="absolute h-[7px] w-[7px] bg-[#ffffff] rounded-full shadow-[0_1px_3px_rgba(0,0,0,0.5)] pointer-events-none -ml-[3.5px] transition-transform group-hover:scale-110" style={{ left: `${((clip?.shadowBlur || 0) / 20) * 100}%` }} />
                        </div>
                        <span className="text-[6.5px] font-medium text-[#a1a1aa] w-[20px] text-right shrink-0">{clip?.shadowBlur || 0}px</span>
                      </div>
                    </div>
                  </>
                )}
              </div>

              {/* Right Side: Color Wheel Area */}
              <div className="flex items-center justify-center flex-1 shrink-0 h-[64px] w-full pt-1.5">
                {activeSubTab !== 'spacing' ? (
                  <div className="relative w-[50px] h-[50px] sm:w-[54px] sm:h-[54px] rounded-full cursor-pointer hover:scale-[1.02] active:scale-95 transition-transform duration-150 flex items-center justify-center">
                    {/* The Rainbow Ring */}
                    <div className="absolute inset-0 rounded-full" style={{ background: 'conic-gradient(from 180deg, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff, #ff00ff, #ff0000)' }} />
                    {/* The Inner Dark Cutout to make it a ring */}
                    <div className="absolute inset-[5.5px] sm:inset-[6.5px] rounded-full bg-[#18181b] z-10 shadow-[inner_0_2px_4px_rgba(0,0,0,0.6)]" />
                    {/* The Center Selected Color Circle */}
                    <div className="absolute w-[18px] h-[18px] sm:w-[20px] sm:h-[20px] rounded-full z-20 shadow-[0_2px_8px_rgba(0,0,0,0.4)] border-2 border-white/90" style={{ backgroundColor: getActiveColor() }} />
                    <input
                      type="color"
                      aria-label="Color Picker"
                      value={getActiveColor().substring(0, 7)}
                      onChange={(e) => setActiveColor(e.target.value)}
                      className="absolute inset-0 w-full h-full opacity-0 z-30 cursor-pointer p-0 m-0 border-none outline-none appearance-none bg-transparent"
                    />
                  </div>
                ) : (
                  <div className="flex items-center justify-center w-[50px] h-[50px] sm:w-[54px] sm:h-[54px] border border-white/[0.04] rounded-full bg-[#18181b] text-[12px] font-medium tracking-[0.1em] text-[#71717a]">
                    Aa
                  </div>
                )}
              </div>
            </motion.div>
          )}

          {activeTab === 'animation' && (
            <motion.div key="animation" initial={{ opacity: 0, scale: 0.98 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }} transition={{ duration: 0.15 }} className="grid grid-cols-2 gap-1.5 max-h-[76px] pt-1 overflow-y-auto scrollbar-hide auto-rows-min">
              {animations.map(a => {
                const isSelected = clip?.textAnimation === a || (!clip?.textAnimation && a === "None");
                return (
                  <button key={a} onClick={() => updateClip({ textAnimation: a })} className={`flex items-center justify-center p-1 rounded-lg transition-all outline-none border h-[26px] cursor-pointer ${isSelected ? 'bg-[#27272a] border-[#ef4444]/40 text-white font-medium' : 'border-white/[0.03] bg-zinc-950/20 text-[#a1a1aa] hover:text-white hover:bg-[#27272a]/80'}`}>
                    <span className="text-[6.5px] uppercase tracking-wider font-semibold">{a}</span>
                  </button>
                );
              })}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Bottom Sub-Tabs (TEXT, STROKE, GLOW, SPACING, SHADOW) */}
      {activeTab === 'style' && (
        <div className="flex items-center justify-between w-full mt-0.5 pb-1">
          {subTabs.map(tab => {
            const isActive = activeSubTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => { setActiveTab('style'); setActiveSubTab(tab.id); }}
                className={`relative flex items-center justify-center px-1.5 py-1.5 sm:px-2 transition-colors duration-200 cursor-pointer outline-none rounded-lg border ${
                  isActive ? 'border-[#3f3f46] bg-[#27272a]/60' : 'border-transparent hover:bg-[#27272a]/30'
                }`}
              >
                <span className={`text-[6px] sm:text-[6.5px] tracking-[0.1em] font-medium uppercase transition-colors whitespace-nowrap ${
                  isActive ? 'text-[#f4f4f5]' : 'text-[#71717a]'
                }`}>
                  {tab.label}
                </span>
                {isActive && (
                  <div className="absolute -bottom-[2px] left-[20%] right-[20%] h-[1.5px] bg-[#ef4444] rounded-t-sm" />
                )}
              </button>
            );
          })}
        </div>
      )}
    </motion.div>
  );
}
