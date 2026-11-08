// Minimal Markdown -> DOCX converter for the blog post (headings, paragraphs, bullets, tables, code, links).
// Usage: node md2docx.js <in.md> <out.docx>
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, ExternalHyperlink, HeadingLevel, Table, TableRow, TableCell,
  WidthType, BorderStyle, TableLayoutType, ShadingType, LevelFormat, AlignmentType, Footer, PageNumber,
} = require("docx");

const [, , inPath, outPath] = process.argv;
const lines = fs.readFileSync(inPath, "utf8").split("\n");
const FONT = "Calibri";
const MONO = "Consolas";
const CONTENT_WIDTH = 11906 - 2 * 1300; // A4 width minus margins (DXA)

// ---------- inline parsing ----------
function inline(text, base = {}) {
  const out = [];
  const re = /(\*\*([^*]+)\*\*)|(\*([^*]+)\*)|(`([^`]+)`)|(\[([^\]]+)\]\(([^)]+)\))|(<(https?:\/\/[^>]+)>)/g;
  let last = 0, m;
  const push = (t, o = {}) => { if (t) out.push(new TextRun({ text: t, font: FONT, ...base, ...o })); };
  while ((m = re.exec(text)) !== null) {
    push(text.slice(last, m.index));
    if (m[1]) out.push(...inline(m[2], { ...base, bold: true }));
    else if (m[3]) out.push(...inline(m[4], { ...base, italics: true }));
    else if (m[5]) push(m[6], { font: MONO, size: (base.size || 22) - 2, color: "8A3B12" });
    else if (m[7]) {
      const url = m[9];
      if (/^https?:/.test(url)) out.push(link(m[8], url, base));
      else push(m[8], { italics: true }); // repository-relative doc: keep the name, no dead link
    } else if (m[10]) out.push(link(m[11], m[11], base));
    last = re.lastIndex;
  }
  push(text.slice(last));
  return out;
}
function link(text, url, base) {
  return new ExternalHyperlink({ link: url, children: [new TextRun({ text, font: FONT, ...base, style: "Hyperlink" })] });
}

// ---------- block parsing ----------
const children = [];
let i = 0;
const isBlockStart = (l) => /^(#{1,6} |\* |- |\||```)/.test(l) || l.trim() === "";

function table(rows) {
  const cells = rows.map((r) => r.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim()));
  const head = cells[0];
  const body = cells.slice(2); // skip |---| row
  const n = head.length;
  const len = (s) => s.replace(/\*\*|`|\*/g, "").length;
  const weights = head.map((_, c) => Math.min(60, Math.max(8, ...[head, ...body].map((r) => len(r[c] || "")))));
  const total = weights.reduce((a, b) => a + b, 0);
  const widths = weights.map((w) => Math.floor((w / total) * CONTENT_WIDTH));
  widths[n - 1] += CONTENT_WIDTH - widths.reduce((a, b) => a + b, 0);
  const border = { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" };
  const borders = { top: border, bottom: border, left: border, right: border };
  const mkRow = (r, header) => new TableRow({
    tableHeader: header,
    children: widths.map((w, c) => new TableCell({
      width: { size: w, type: WidthType.DXA },
      borders,
      shading: header ? { type: ShadingType.CLEAR, color: "auto", fill: "1F3A5F" } : undefined,
      margins: { top: 60, bottom: 60, left: 100, right: 100 },
      children: [new Paragraph({
        spacing: { before: 0, after: 0 },
        children: inline(r[c] || "", header ? { bold: true, color: "FFFFFF", size: 18 } : { size: 18 }),
      })],
    })),
  });
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: widths,
    layout: TableLayoutType.FIXED,
    rows: [mkRow(head, true), ...body.map((r) => mkRow(r, false))],
  });
}

while (i < lines.length) {
  const l = lines[i];
  if (l.trim() === "") { i++; continue; }
  if (l.startsWith("```")) {
    i++;
    const code = [];
    while (i < lines.length && !lines[i].startsWith("```")) code.push(lines[i++]);
    i++;
    code.forEach((c, k) => children.push(new Paragraph({
      shading: { type: ShadingType.CLEAR, color: "auto", fill: "F2F2F2" },
      spacing: { before: k === 0 ? 120 : 0, after: k === code.length - 1 ? 160 : 0 },
      indent: { left: 200, right: 200 },
      children: [new TextRun({ text: c || " ", font: MONO, size: 18 })],
    })));
    continue;
  }
  const h = l.match(/^(#{1,6}) (.*)/);
  if (h) {
    const level = h[1].length;
    children.push(level === 1
      ? new Paragraph({ heading: HeadingLevel.TITLE, children: inline(h[2]) })
      : new Paragraph({ heading: level === 2 ? HeadingLevel.HEADING_1 : HeadingLevel.HEADING_2, children: inline(h[2]) }));
    i++;
    continue;
  }
  if (l.startsWith("|")) {
    const rows = [];
    while (i < lines.length && lines[i].startsWith("|")) rows.push(lines[i++]);
    children.push(table(rows));
    children.push(new Paragraph({ spacing: { before: 0, after: 120 }, children: [] }));
    continue;
  }
  if (/^(\* |- )/.test(l)) {
    while (i < lines.length && /^(\* |- )/.test(lines[i])) {
      let item = lines[i++].slice(2);
      while (i < lines.length && /^ {2,}\S/.test(lines[i])) item += " " + lines[i++].trim();
      children.push(new Paragraph({ numbering: { reference: "bullets", level: 0 }, spacing: { after: 60 }, children: inline(item) }));
    }
    continue;
  }
  let para = l;
  i++;
  while (i < lines.length && !isBlockStart(lines[i])) para += " " + lines[i++].trim();
  children.push(new Paragraph({ spacing: { after: 160, line: 288 }, children: inline(para) }));
}

const heading = (size, color, before, after) => ({
  run: { font: FONT, size, bold: true, color },
  paragraph: { spacing: { before, after }, keepNext: true },
});
const doc = new Document({
  creator: "Beyond the LLM benchmark",
  title: "Do We Really Need an LLM for That?",
  styles: {
    default: { document: { run: { font: FONT, size: 22 } } },
    paragraphStyles: [
      { id: "Title", name: "Title", basedOn: "Normal", next: "Normal", run: { font: FONT, size: 44, bold: true, color: "1F3A5F" }, paragraph: { spacing: { before: 0, after: 240 }, keepNext: true } },
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, ...heading(30, "1F3A5F", 360, 140), paragraph: { spacing: { before: 360, after: 140 }, keepNext: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, ...heading(26, "2E5C8A", 240, 100), paragraph: { spacing: { before: 240, after: 100 }, keepNext: true, outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [{ reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] }],
  },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1200, bottom: 1200, left: 1300, right: 1300 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18, color: "808080" })] })] }) },
    children,
  }],
});
Packer.toBuffer(doc).then((b) => { fs.writeFileSync(outPath, b); console.log("wrote", outPath); });
