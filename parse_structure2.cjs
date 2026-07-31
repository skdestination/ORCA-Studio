const fs = require('fs');
const content = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', 'utf-8');

let depth = 0;
let lines = content.split('\n');

for (let lineNum = 0; lineNum < lines.length; lineNum++) {
    let line = lines[lineNum];
    let stripped = line.trim();
    
    let oldDepth = depth;
    for (let char of line) {
        if (char === '{') depth++;
        if (char === '}') depth--;
    }
    
    if (oldDepth === 0 || depth === 0) {
        if (stripped !== '') console.log(`Line ${lineNum + 1} (Depth ${oldDepth}->${depth}): ${stripped}`);
    }
}
