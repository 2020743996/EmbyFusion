const fs = require("fs");

global.Widget = {
  http: { get: async () => ({}), post: async () => ({}) },
  tmdb: { get: async () => null },
  storage: { _m: {}, get(k) { return this._m[k]; }, set(k, v) { this._m[k] = v; } },
  html: { load: () => ({}) },
};
global.WidgetMetadata = {};
eval(fs.readFileSync("./widgets/ai-recommend.js", "utf8"));

const testCases = [
  {
    name: "纯 JSON",
    input: '[{"tmdbId":550,"mediaType":"movie","title":"Fight Club","reason":"test"}]',
    expect: true,
  },
  {
    name: "带 markdown 代码块",
    input: '```json\n[{"tmdbId":550,"mediaType":"movie","title":"Fight Club","reason":"test"}]\n```',
    expect: true,
  },
  {
    name: "带前后文字",
    input: '以下是推荐：\n[{"tmdbId":550,"mediaType":"movie","title":"Fight Club","reason":"test"}]\n希望你喜欢！',
    expect: true,
  },
  {
    name: "带换行和空格",
    input: '\n\n[{"tmdbId":550,"mediaType":"movie","title":"Fight Club","reason":"test"}]\n\n',
    expect: true,
  },
  {
    name: "无有效数据",
    input: '抱歉，我无法推荐',
    expect: false,
  },
  {
    name: "空数组",
    input: '[]',
    expect: false,
  },
];

console.log("=== JSON 解析测试 ===\n");

let pass = 0;
let fail = 0;

for (const tc of testCases) {
  try {
    const result = parseAiJson(tc.input);
    const ok = Array.isArray(result) && result.length > 0;
    if (ok === tc.expect) {
      console.log(`✅ ${tc.name}`);
      pass++;
    } else {
      console.log(`❌ ${tc.name} - 期望 ${tc.expect}，得到 ${ok}`);
      fail++;
    }
  } catch (e) {
    if (!tc.expect) {
      console.log(`✅ ${tc.name} (正确抛出错误)`);
      pass++;
    } else {
      console.log(`❌ ${tc.name} - ${e.message}`);
      fail++;
    }
  }
}

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
if (fail > 0) process.exit(1);
