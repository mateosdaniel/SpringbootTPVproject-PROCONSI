const fs = require('fs');
const html = fs.readFileSync('c:\\Users\\PracticasSoftware2\\Desktop\\TPV\\electrobazar\\src\\main\\resources\\templates\\admin\\index.html', 'utf8');

// Extract all <script> tags
const scriptRegex = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
let match;
while ((match = scriptRegex.exec(html)) !== null) {
    const scriptContent = match[1];
    if (scriptContent.trim()) {
        try {
            // Test if it parses as valid JS
            new Function(scriptContent);
        } catch (e) {
            console.error("Syntax Error found in script tag:");
            console.error(e.message);
            // Print a snippet where it failed
            console.error("Script snippet:");
            console.error(scriptContent.substring(0, 200) + "...");
            process.exit(1);
        }
    }
}
console.log("No syntax errors found in any inline scripts.");
