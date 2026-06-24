import fs from 'fs';
let code = fs.readFileSync('src/App.tsx', 'utf8');
const searchString = 'function MinusIcon({ size = 24 }: { size?: number }) {';
const index = code.indexOf(searchString);
if (index !== -1) {
    code = code.substring(0, index);
    fs.writeFileSync('src/App.tsx', code);
    console.log('Successfully trimmed file');
} else {
    console.log('Could not find MinusIcon function');
}
