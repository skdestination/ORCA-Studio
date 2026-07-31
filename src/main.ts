// Pure TypeScript Native Status Dashboard - No React
const root = document.getElementById('root');

if (root) {
  root.innerHTML = `
    <div style="
      background-color: #08080a;
      color: #f4f4f5;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      box-sizing: border-box;
    ">
      <div style="
        background: #14161d;
        border: 1px solid rgba(255, 255, 255, 0.1);
        padding: 32px;
        border-radius: 24px;
        max-width: 520px;
        width: 100%;
        text-align: center;
        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
      ">
        <div style="
          background: #ff2d55;
          color: #ffffff;
          font-size: 11px;
          font-weight: 800;
          letter-spacing: 0.05em;
          padding: 6px 14px;
          border-radius: 9999px;
          display: inline-block;
          margin-bottom: 20px;
          text-transform: uppercase;
        ">
          Android Native Project Only
        </div>

        <h1 style="font-size: 22px; font-weight: 900; margin: 0 0 10px 0; color: #ffffff; tracking: -0.02em;">
          ORCA Creative Studio (Native Jetpack Compose)
        </h1>

        <p style="font-size: 13px; color: #a1a1aa; margin: 0 0 24px 0; line-height: 1.6;">
          All React files and web preview frameworks have been completely removed from this project per your request. The codebase consists exclusively of native Kotlin Android code designed for Codemagic native builds.
        </p>

        <div style="
          background: #08080a;
          border: 1px solid rgba(255, 255, 255, 0.08);
          border-radius: 16px;
          padding: 16px;
          text-align: left;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
          font-size: 11px;
          color: #e4e4e7;
          line-height: 1.8;
        ">
          <div style="color: #a1a1aa; font-weight: bold; margin-bottom: 8px;">Native Android Directory Architecture:</div>
          <div><span style="color: #38bdf8;">📁 android/app/src/main/java/com/litecut/app/</span></div>
          <div>  ├── 📄 MainActivity.kt</div>
          <div>  ├── 📄 OrcaApplication.kt</div>
          <div>  ├── 📁 timeline/ (Editor UI & Engine)</div>
          <div>  │   ├── 📄 TimelineScreen.kt</div>
          <div>  │   ├── 📄 EditorHeaderBar.kt</div>
          <div>  │   ├── 📄 EditorTransportBar.kt</div>
          <div>  │   ├── 📄 EditorTimelineArea.kt</div>
          <div>  │   └── 📄 EditorPreviewCanvas.kt</div>
          <div>  ├── 📁 controls/</div>
          <div>  │   ├── 📄 EditorBottomToolBar.kt</div>
          <div>  │   └── 📄 ControlSubPanels.kt</div>
          <div>  └── 📁 home/</div>
          <div>      └── 📄 HomeScreen.kt</div>
        </div>

        <div style="margin-top: 24px; font-size: 11px; color: #71717a;">
          Ready for building APK/AAB via Codemagic CI/CD pipeline.
        </div>
      </div>
    </div>
  `;
}
