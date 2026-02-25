const fs = require('fs');
const jsdom = require('jsdom');
const { JSDOM } = jsdom;

const html = fs.readFileSync('c:\\Users\\PracticasSoftware2\\Desktop\\TPV\\electrobazar\\src\\main\\resources\\templates\\admin\\index.html', 'utf8');
const dom = new JSDOM(html, { runScripts: "dangerously" });

// This will output any syntax errors or execution errors caught by jsdom
console.log("JSDOM initialization complete.");
