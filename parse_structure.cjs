const fs = require('fs');
const content = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', 'utf-8');

let depth = 0;
let lines = content.split('\n');

for (let lineNum = 0; lineNum < lines.length; lineNum++) {
    let line = lines[lineNum];
    let stripped = line.trim();
    
    // Count braces naive
    for (let char of line) {
        if (char === '{') depth++;
        if (char === '}') depth--;
    }
    
    if (stripped.startsWith('fun ') || stripped.startsWith('class ') || stripped.startsWith('object ')) {
        console.log(`Line ${lineNum + 1} (Depth ${depth}): ${stripped}`);
    }
}
