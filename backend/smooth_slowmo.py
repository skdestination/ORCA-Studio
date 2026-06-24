import cv2
import numpy as np
import argparse
import sys
import os

def generate_interpolated_frame(f1, f2, flow, t):
    """
    Generates an intermediate frame using dense optical flow.
    t is between 0 and 1.
    """
    h, w = f1.shape[:2]
    
    # Create coordinate grid
    y, x = np.mgrid[0:h, 0:w].reshape(2, -1)
    
    # Scale flow by t
    fx, fy = flow[..., 0].flatten() * t, flow[..., 1].flatten() * t
    
    # Calculate new coordinates
    new_x = np.clip(x + fx, 0, w - 1).astype(np.int32)
    new_y = np.clip(y + fy, 0, h - 1).astype(np.int32)
    
    # Remap pixels
    new_frame = np.zeros_like(f1)
    new_frame[y, x] = f1[new_y, new_x] * (1 - t) + f2[y, x] * t
    
    return new_frame

def process_video(input_path, output_path, start_time, end_time, multiplier=4):
    """
    Applies smooth slow-motion using optical flow.
    Operates ONLY on the trimmed segment [start_time, end_time].
    multiplier: How many times slower the video should be (e.g. 4x for 30fps -> 120fps equivalent).
    """
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        print(f"Error: Could not open {input_path}")
        sys.exit(1)
        
    fps = cap.get(cv2.CAP_PROP_FPS)
    start_frame = int(start_time * fps)
    end_frame = int(end_time * fps)
    
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    # The output video will have the same FPS but more frames, so it plays back slower
    out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
    
    # Seek to start
    cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
    
    ret, prev_frame = cap.read()
    if not ret:
        print("Error reading first frame.")
        return
        
    out.write(prev_frame)
    prev_gray = cv2.cvtColor(prev_frame, cv2.COLOR_BGR2GRAY)
    
    current_frame_idx = start_frame + 1
    
    while current_frame_idx <= end_frame:
        ret, current_frame = cap.read()
        if not ret:
            break
            
        current_gray = cv2.cvtColor(current_frame, cv2.COLOR_BGR2GRAY)
        
        # Calculate dense optical flow using Farneback algorithm
        flow = cv2.calcOpticalFlowFarneback(
            prev_gray, current_gray, None, 
            0.5, 3, 15, 3, 5, 1.2, 0
        )
        
        # Interpolate frames
        for i in range(1, multiplier):
            t = i / multiplier
            interp_frame = generate_interpolated_frame(prev_frame, current_frame, flow, t)
            out.write(interp_frame)
            
        out.write(current_frame)
        
        prev_frame = current_frame
        prev_gray = current_gray
        current_frame_idx += 1
        
    cap.release()
    out.release()
    print(f"Smooth slow motion applied successfully to {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="True Smooth Slow-Motion using Optical Flow")
    parser.add_argument("--input", required=True, help="Input video path")
    parser.add_argument("--output", required=True, help="Output video path")
    parser.add_argument("--start", type=float, required=True, help="Trim start time in seconds")
    parser.add_argument("--end", type=float, required=True, help="Trim end time in seconds")
    parser.add_argument("--multiplier", type=int, default=4, help="Slowmo multiplier (e.g. 4 for 4x slower)")
    
    args = parser.parse_args()
    
    if not os.path.exists(args.input):
        print(f"Input file {args.input} does not exist.")
        sys.exit(1)
        
    process_video(args.input, args.output, args.start, args.end, args.multiplier)
