import fs from 'fs';

const code = fs.readFileSync('src/App.tsx', 'utf8');

const getBlock = (start, end) => {
    return code.substring(code.indexOf(start), code.indexOf(end));
}

const renderSettingsBlock = getBlock('const renderSettings = () => (', 'const renderHome = () => (');
const renderHomeBlock = getBlock('const renderHome = () => (', 'const renderEditor = () => (');
const renderEditorBlock = getBlock('const renderEditor = () => (', '  return (\n    <div \n      className="min-h-screen');

function writeScreen(filename, name, content) {
    fs.writeFileSync(`src/${filename}`, `import React from 'react';\n\nexport const ${name} = (props: any) => {\n  const { \n    // TODO: Destructure props here\n  } = props;\n\n  return (\n    ${content.replace(/const render\w+ = \(\) => \(/g, '').trim()}\n  );\n};\n`);
}

fs.mkdirSync('src/screens', { recursive: true });
writeScreen('screens/SettingsScreen.tsx', 'SettingsScreen', renderSettingsBlock);
writeScreen('screens/HomeScreen.tsx', 'HomeScreen', renderHomeBlock);
writeScreen('screens/EditorScreen.tsx', 'EditorScreen', renderEditorBlock);

let newApp = code.replace(renderSettingsBlock, '')
                 .replace(renderHomeBlock, '')
                 .replace(renderEditorBlock, '');

newApp = `import { SettingsScreen } from './screens/SettingsScreen';\nimport { HomeScreen } from './screens/HomeScreen';\nimport { EditorScreen } from './screens/EditorScreen';\n` + newApp;

newApp = newApp.replace('{currentScreen === "home" && renderHome()}', '{currentScreen === "home" && <HomeScreen {...props} /> /* TODO: define props object */}')
               .replace('{currentScreen === "settings" && renderSettings()}', '{currentScreen === "settings" && <SettingsScreen {...props} /> /* TODO: define props object */}')
               .replace('{currentScreen === "editor" && renderEditor()}', '{currentScreen === "editor" && <EditorScreen {...props} /> /* TODO: define props object */}');

fs.writeFileSync('src/App.tsx', newApp);
console.log('Successfully refactored screens!');
