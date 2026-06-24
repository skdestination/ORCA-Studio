export const KEYBOARD_SHORTCUTS = [
  { keys: ["Space"], desc: "Toggle Play / Pause", category: "Playback" },
  { keys: ["S"], desc: "Split Clip at Playhead", category: "Editing" },
  { keys: ["Delete"], desc: "Delete Selected Clip", category: "Editing" },
  { keys: ["Cmd/Ctrl", "Z"], desc: "Undo last change", category: "History" },
  { keys: ["Cmd/Ctrl", "Shift", "Z"], desc: "Redo change", category: "History" },
  { keys: ["←"], desc: "Seek backward 0.5s", category: "Navigation" },
  { keys: ["→"], desc: "Seek forward 0.5s", category: "Navigation" },
  { keys: ["Shift", "←"], desc: "Seek backward 5s", category: "Navigation" },
  { keys: ["Shift", "→"], desc: "Seek forward 5s", category: "Navigation" },
  { keys: ["+"], desc: "Zoom in Timeline", category: "View" },
  { keys: ["-"], desc: "Zoom out Timeline", category: "View" },
];

export const sampleMediaImages = [
  { name: "Mountain Peak", url: "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&q=80&w=600" },
  { name: "Forest Fog", url: "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&q=80&w=600" },
  { name: "Ocean Sunset", url: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&q=80&w=600" },
  { name: "Neon Downtown", url: "https://images.unsplash.com/photo-1515621061946-eff1c2a352bd?auto=format&fit=crop&q=80&w=600" },
  { name: "Retro Desert", url: "https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&q=80&w=600" },
  { name: "Cozy Study", url: "https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&q=80&w=600" },
];

export const sampleMediaVideos = [
  { name: "Alpine Peaks", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", duration: 15, thumbnail: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&q=80&w=400" },
  { name: "Sunset Drive", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4", duration: 12, thumbnail: "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&q=80&w=400" },
  { name: "Forest River", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4", duration: 14, thumbnail: "https://images.unsplash.com/photo-1425913397330-cf8af2ff40a1?auto=format&fit=crop&q=80&w=400" },
  { name: "City Lights", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", duration: 15, thumbnail: "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?auto=format&fit=crop&q=80&w=400" },
];

export const sampleMediaAudio = [
  { name: "Serene Nature", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", duration: 372 },
  { name: "Lofi Beats", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", duration: 184 },
  { name: "Luminous Synth", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", duration: 302 },
  { name: "Chill Groove", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3", duration: 251 },
];
