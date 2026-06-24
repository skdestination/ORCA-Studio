import fs from 'fs';

const code = fs.readFileSync('src/App.tsx', 'utf8');

// Match useState declarations
const stateRegex = /const \[([a-zA-Z]+),\s*([a-zA-Z]+)\] = useState/g;
const vars = [];
let match;
while ((match = stateRegex.exec(code)) !== null) {
    vars.push(match[1]);
    vars.push(match[2]);
}

// Match function declarations that might be used
const funcRegex = /const ([a-zA-Z]+) = \(/g;
while ((match = funcRegex.exec(code)) !== null) {
    vars.push(match[1]);
}

// Also let's just cheat and stick `eval` or something? No, we just write a massive object:
const uniqueVars = [...new Set(vars)];
const propsObj = `const screenProps: any = {\n  ${uniqueVars.join(',\n  ')}\n};\n`;

fs.writeFileSync('src/App.tsx', code.replace('    return (', propsObj + '\n    return (').replace(/<HomeScreen \{\.\.\.props\}/g, '<HomeScreen {...screenProps}').replace(/<SettingsScreen \{\.\.\.props\}/g, '<SettingsScreen {...screenProps}').replace(/<EditorScreen \{\.\.\.props\}/g, '<EditorScreen {...screenProps}'));

console.log('Props object generated!');
