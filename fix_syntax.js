import fs from 'fs';

['src/screens/SettingsScreen.tsx', 'src/screens/HomeScreen.tsx', 'src/screens/EditorScreen.tsx'].forEach(file => {
    let code = fs.readFileSync(file, 'utf8');
    // Remove the last ");" if it precedes "};"
    code = code.replace(/\);\n\s*\);\n\};/, ');\n};'); // That might not match.
    // Instead just replace the exact text
    code = code.replace('  );\n  );\n};', '  );\n};');
    code = code.replace('    </div>\n  );\n  );\n};', '    </div>\n  );\n};');
    
    // Fallback regex: remove extra `);` before `};`
    code = code.replace(/\);\s*\r?\n\s*\};\s*$/, '};');
    fs.writeFileSync(file, code);
})
console.log('Fixed syntax errors at bottom of screens');
