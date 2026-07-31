import { Clip, Layer } from "../types";

export const initialSnapshot = {
  layers: [
    { id: "L_6", order: 5, isMuted: false, isHidden: false, name: "Overlay 2" },
    { id: "L_5", order: 4, isMuted: false, isHidden: false, name: "Overlay 1" },
    { id: "L_4", order: 3, isMuted: false, isHidden: false, name: "Text Layer" },
    { id: "L_3", order: 2, isMuted: false, isHidden: false, name: "Main Video" },
    { id: "L_2", order: 1, isMuted: false, isHidden: false, name: "Audio 1" },
    { id: "L_1", order: 0, isMuted: false, isHidden: false, name: "Audio 2" }
  ] as Layer[],
  clips: [
    {
      id: "clip_default_1",
      layerId: "L_3",
      type: "video",
      src: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
      name: "Alpine Peaks",
      leftSeconds: 0.0,
      durationSeconds: 15.0,
      trimStartSeconds: 0.0,
      playbackSpeed: 1.0,
      volume: 1.0,
      opacity: 1.0,
      isReversed: false,
      additionalProperties: {}
    },
    {
      id: "clip_default_2",
      layerId: "L_3",
      type: "image",
      src: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&q=80&w=600",
      name: "Ocean Sunset",
      leftSeconds: 15.0,
      durationSeconds: 10.0,
      trimStartSeconds: 0.0,
      playbackSpeed: 1.0,
      volume: 1.0,
      opacity: 1.0,
      isReversed: false,
      additionalProperties: {}
    }
  ] as Clip[],
  currentTime: 0,
  zoomLevel: 1.0,
  scrollLeft: 0,
  selectedClipIds: ["clip_default_1"]
};
