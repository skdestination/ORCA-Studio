import cv2
import numpy as np
import argparse
import sys
import os

def optical_flow_interpolation(frame1, frame2, t):
    # Convert frames to grayscale for optical flow
    prev_gray = cv2.cvtColor(frame1, cv2.COLOR_BGR2GRAY)
    next_gray = cv2.cvtColor(frame2, cv2.COLOR_BGR2GRAY)
    
    # Calculate dense optical flow (Farneback)
    flow = cv2.calcOpticalFlowFarneback(
        prev_gray, next_gray, None, 
        pyr_scale=0.5, levels=3, winsize=15, 
        iterations=3, poly_n=5, poly_sigma=1.2, flags=0
    )
    
    # We want to interpolate at time 't' (between 0 and 1)
    # Forward warp approximation
    h, w = prev_gray.shape
    flow_x = flow[..., 0] * t
    flow_y = flow[..., 1] * t
    
    # Create coordinate grid
    y_coords, x_coords = np.mgrid[0:h, 0:w]
    
    # Map coordinates
    new_x = np.clip(x_coords + flow_x, 0, w - 1).astype(np.float32)
    new_y = np.clip(y_coords + flow_y, 0, h - 1).astype(np.float32)
    
    # Remap the first frame towards the second frame
    warped_frame1 = cv2.remap(frame1, new_x, new_y, cv2.INTER_LINEAR)
    
    # Reverse mapping for the second frame
    flow_back = cv2.calcOpticalFlowFarneback(
        next_gray, prev_gray, None, 
        pyr_scale=0.5, levels=3, winsize=15, 
        iterations=3, poly_n=5, poly_sigma=1.2, flags=0
    )
    
    flow_back_x = flow_back[..., 0] * (1 - t)
    flow_back_y = flow_back[..., 1] * (1 - t)
    
    new_x_back = np.clip(x_coords + flow_back_x, 0, w - 1).astype(np.float32)
    new_y_back = np.clip(y_coords + flow_back_y, 0, h - 1).astype(np.float32)
    
    warped_frame2 = cv2.remap(frame2, new_x_back, new_y_back, cv2.INTER_LINEAR)
    
    # Blend the two warped frames based on time t
    blended = cv2.addWeighted(warped_frame1, 1 - t, warped_frame2, t, 0)
    return blended

def process_video(input_path, output_path, start_time, end_time, factor=4):
    if not os.path.exists(input_path):
        print(f"Error: Input file {input_path} not found.")
        sys.exit(1)
        
    cap = cv2.VideoCapture(input_path)
    
    fps = cap.get(cv2.CAP_PROP_FPS)
    if fps <= 0: fps = 30.0 # Default if unreadable
    
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    # Video compression configurations
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    
    # We enforce target fps to emulate 120fps (e.g. 30 * 4)
    target_fps = fps * factor
    out = cv2.VideoWriter(output_path, fourcc, target_fps, (width, height))
    
    # Calculate start and trim frames
    start_frame = int(start_time * fps)
    end_frame = int(end_time * fps) if end_time > 0 else float('inf')
    
    cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
    current_frame_idx = start_frame
    
    ret, prev_frame = cap.read()
    if not ret:
        print("Error: Could not read starting frame.")
        sys.exit(1)
        
    print(f"Starting Smooth SlowMo processing from {start_time}s to {end_time if end_time > 0 else 'end'}...")
    out.write(prev_frame)
    
    while True:
        ret, next_frame = cap.read()
        if not ret or current_frame_idx >= end_frame:
            break
            
        # Generate intermediate frames
        for i in range(1, factor):
            t = i / float(factor)
            interpolated = optical_flow_interpolation(prev_frame, next_frame, t)
            out.write(interpolated)
            
        out.write(next_frame)
        prev_frame = next_frame.copy()
        current_frame_idx += 1
        
        sys.stdout.write(f"\rProcessed frames up to {current_frame_idx}...")
        sys.stdout.flush()

    print("\nProcessing complete! Saved to:", output_path)
    cap.release()
    out.release()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Smooth Slow-Motion using OpenCV Optical Flow")
    parser.add_argument("--input", required=True, help="Path to input video")
    parser.add_argument("--output", required=True, help="Path to output video")
    parser.add_argument("--factor", type=int, default=4, help="Interpolation factor (default 4 for 4x slower/120fps)")
    # Using trim boundaries logic
    parser.add_argument("--start", type=float, default=0.0, help="Trim start time in seconds (only apply slowmo to half trimed part)")
    parser.add_argument("--end", type=float, default=-1.0, help="Trim end time in seconds (-1 for full track)")
    
    args = parser.parse_args()
    
    process_video(args.input, args.output, args.start, args.end, args.factor)
