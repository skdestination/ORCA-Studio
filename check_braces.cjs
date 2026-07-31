const fs = require('fs');
const content = fs.readFileSync('android/app/src/main/java/com/litecut/app/timeline/TimelineScreen.kt', 'utf-8');
let depth = 0;
for (let i = 0; i < content.length; i++) {
  if (content[i] === '{') depth++;
  else if (content[i] === '}') {
    depth--;
    if (depth < 0) {
      console.log(`Unmatched closing brace at index ${i}`);
      break;
    }
  }
}
console.log(`Final depth: ${depth}`);
