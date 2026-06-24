import fs from 'fs';

let code = fs.readFileSync('src/App.tsx', 'utf8');

const renderSettingsStart = code.indexOf('const renderSettings = () => (');
const renderHomeStart = code.indexOf('const renderHome = () => (');

if (renderSettingsStart !== -1 && renderHomeStart !== -1) {
    const settingsCode = code.substring(renderSettingsStart, renderHomeStart);
    fs.mkdirSync('src/components', { recursive: true });
    fs.writeFileSync('src/components/SettingsScreen.txt', settingsCode);
}
