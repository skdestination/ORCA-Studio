const fs = require('fs');
const content = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', 'utf-8');

let depth = 0;
let inString = false;
let inChar = false;
let inLineComment = false;
let inBlockComment = false;

let lines = content.split('\n');

for (let lineNum = 0; lineNum < lines.length; lineNum++) {
    let line = lines[lineNum];
    for (let i = 0; i < line.length; i++) {
        let char = line[i];
        let nextChar = line[i+1];

        if (inLineComment) continue;

        if (inBlockComment) {
            if (char === '*' && nextChar === '/') {
                inBlockComment = false;
                i++;
            }
            continue;
        }

        if (inString) {
            if (char === '\\') i++; // Skip escaped char
            else if (char === '"') inString = false;
            continue;
        }
        
        if (inChar) {
            if (char === '\\') i++;
            else if (char === "'") inChar = false;
            continue;
        }

        if (char === '/' && nextChar === '/') {
            inLineComment = true;
            break; // rest of line is comment
        }
        
        if (char === '/' && nextChar === '*') {
            inBlockComment = true;
            i++;
            continue;
        }
        
        if (char === '"') {
            inString = true;
            continue;
        }
        
        if (char === "'") {
            inChar = true;
            continue;
        }

        if (char === '{') {
            depth++;
        } else if (char === '}') {
            depth--;
            if (depth < 0) {
                console.log(`Unmatched closing brace at line ${lineNum + 1}`);
                break;
            }
        }
    }
    inLineComment = false;
    
    if (depth < 0) break;
}
console.log(`Final depth: ${depth}`);
