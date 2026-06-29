import React from "react";
import { ChevronLeft, ArrowUp, ArrowDown } from "lucide-react";

interface SettingsPanelProps {
  setCurrentScreen: (screen: string) => void;
  flowBarOrder: string[];
  getFlowBarItemLabel: (key: string) => string;
  handleMoveFlowBarItem: (index: number, direction: "up" | "down") => void;
  appScale: number;
  handleAppScaleChange: (scale: number) => void;
  snappingEnabled: boolean;
  handleToggleSnapping: (enabled: boolean) => void;
}

export const SettingsPanel: React.FC<SettingsPanelProps> = ({
  setCurrentScreen,
  flowBarOrder,
  getFlowBarItemLabel,
  handleMoveFlowBarItem,
  appScale,
  handleAppScaleChange,
  snappingEnabled,
  handleToggleSnapping,
}) => {
  return (
    <div className="flex flex-col h-screen w-full bg-[#0c0c0e] overflow-hidden relative">
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 scrollbar-hide">
        <div className="min-h-full flex flex-col pb-[150px]">
          {/* Header */}
          <div className="pt-32 pb-8 flex justify-between items-end mt-auto">
            <h1 className="text-[52px] font-extrabold tracking-tight leading-none text-white">
              Settings
            </h1>
            <button
              className="w-10 h-10 rounded-full hover:bg-zinc-800 flex items-center justify-center transition-colors mb-2 text-zinc-400 hover:text-white"
              onClick={() => setCurrentScreen("home")}
            >
              <ChevronLeft size={24} />
            </button>
          </div>

          {/* Settings Content */}
          <div className="flex flex-col gap-6 max-w-2xl mx-auto w-full mt-4">
            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                Flow Bar Order
              </h3>
              <p className="text-sm text-zinc-400 mb-4 font-medium">Customize the order of tools in the floating action menu.</p>
              <div className="flex flex-col gap-2">
                {flowBarOrder.map((key, index) => (
                  <div key={key} className="flex items-center justify-between bg-zinc-800/50 rounded-xl px-4 py-3 border border-white/5">
                    <span className="text-zinc-200 font-medium text-sm">{getFlowBarItemLabel(key)}</span>
                    <div className="flex items-center gap-1">
                      <button 
                        onClick={() => handleMoveFlowBarItem(index, 'up')}
                        disabled={index === 0}
                        className={`p-1.5 rounded-lg transition-colors ${index === 0 ? 'opacity-30' : 'hover:bg-zinc-700 text-zinc-400 hover:text-white'}`}
                      >
                        <ArrowUp size={16} />
                      </button>
                      <button 
                        onClick={() => handleMoveFlowBarItem(index, 'down')}
                        disabled={index === flowBarOrder.length - 1}
                        className={`p-1.5 rounded-lg transition-colors ${index === flowBarOrder.length - 1 ? 'opacity-30' : 'hover:bg-zinc-700 text-zinc-400 hover:text-white'}`}
                      >
                        <ArrowDown size={16} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                App Layout Scale
              </h3>
              <p className="text-sm text-zinc-400 mb-6 font-medium">
                Adjust the overall size of the app interface independently from your device display.
              </p>
              <div className="flex flex-col gap-4">
                <div className="flex justify-between items-center px-1">
                  <span className="text-zinc-300 font-medium text-sm">Scale</span>
                  <span className="text-white font-mono font-bold">{Math.round(appScale * 100)}%</span>
                </div>
                <input
                  type="range"
                  min="0.5"
                  max="2"
                  step="0.05"
                  value={appScale}
                  onChange={(e) => handleAppScaleChange(parseFloat(e.target.value))}
                  className="w-full accent-white h-2 rounded-lg appearance-none bg-zinc-800"
                />
                <div className="flex justify-between w-full px-1 mt-1">
                  <span className="text-[10px] text-zinc-500 font-bold">50%</span>
                  <span className="text-[10px] text-zinc-500 font-bold">100%</span>
                  <span className="text-[10px] text-zinc-500 font-bold">200%</span>
                </div>
                <button
                  onClick={() => handleAppScaleChange(1)}
                  className="mt-2 text-xs font-bold text-zinc-400 hover:text-white transition-colors bg-zinc-800/50 hover:bg-zinc-800 py-2 rounded-xl border border-white/5"
                >
                  Reset Default
                </button>
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                Timeline Snapping
              </h3>
              <p className="text-sm text-zinc-400 mb-6 font-medium">
                Toggle interactive grid alignment and edge snapping for layers and timeline playhead.
              </p>
              <div className="flex items-center justify-between bg-zinc-800/50 rounded-xl px-4 py-3 border border-white/5">
                <span className="text-zinc-200 font-medium text-sm">Enable Snapping</span>
                <button
                  type="button"
                  onClick={() => handleToggleSnapping(!snappingEnabled)}
                  className={`w-12 h-6 flex items-center rounded-full p-1 cursor-pointer transition-colors duration-200 outline-none ${snappingEnabled ? 'bg-indigo-600' : 'bg-zinc-700'}`}
                >
                  <div
                    className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform duration-200 ${snappingEnabled ? 'translate-x-[24px]' : 'translate-x-[0px]'}`}
                  />
                </button>
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">
                Export Preferences
              </h3>
              <div className="flex flex-col gap-5">
                <div className="flex justify-between items-center">
                  <span className="text-zinc-300 font-medium text-sm">
                    Default Resolution
                  </span>
                  <select className="bg-zinc-800 text-white rounded-xl px-4 py-2 outline-none border border-white/10 text-sm focus:border-white/20 transition-colors">
                    <option>1080p</option>
                    <option>4K</option>
                    <option>720p</option>
                  </select>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-zinc-300 font-medium text-sm">
                    Default FPS
                  </span>
                  <select className="bg-zinc-800 text-white rounded-xl px-4 py-2 outline-none border border-white/10 text-sm focus:border-white/20 transition-colors">
                    <option>30 fps</option>
                    <option>60 fps</option>
                  </select>
                </div>
              </div>
            </div>

            <div className="bg-zinc-900 border border-white/5 rounded-3xl p-6">
              <h3 className="text-white font-bold mb-4 text-xl">App Info</h3>
              <div className="flex flex-col gap-2">
                <div className="flex justify-between items-center">
                  <span className="text-zinc-400 text-sm font-medium">
                    Version
                  </span>
                  <span className="text-zinc-500 font-mono text-sm">1.0.0</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-zinc-400 text-sm font-medium">
                    Developer
                  </span>
                  <span className="text-zinc-500 text-sm">AI Studio</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
