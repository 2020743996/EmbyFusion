const fs = require("fs");
const assert = require("assert/strict");

const calls = [];
const storage = {};

global.Widget = {
  http: {
    get: async (url, opts) => {
      calls.push({ url, opts });
      if (url.includes("/v1/models")) {
        return {
          data: {
            data: [
              { id: "gpt-4o-mini", owned_by: "openai" },
              { id: "gpt-4o", owned_by: "openai" },
            ],
          },
        };
      }
      throw new Error("unmocked get: " + url);
    },
    post: async (url, body, opts) => {
      calls.push({ url, body, opts });
      if (url.includes("/chat/completions")) {
        await new Promise((r) => setTimeout(r, 100));
        return {
          data: {
            choices: [
              {
                message: {
                  content: JSON.stringify([
                    { tmdbId: 550, mediaType: "movie", title: "搏击俱乐部", reason: "经典悬疑" },
                    { tmdbId: 13, mediaType: "tv", title: "辛普森一家", reason: "轻松喜剧" },
                    { tmdbId: 27205, mediaType: "movie", title: "盗梦空间", reason: "烧脑神作" },
                    { tmdbId: 1396, mediaType: "tv", title: "绝命毒师", reason: "高分美剧" },
                  ]),
                },
              },
            ],
          },
        };
      }
      throw new Error("unmocked post: " + url);
    },
  },
  tmdb: {
    get: async (api) => {
      calls.push("tmdb:" + api);
      await new Promise((r) => setTimeout(r, 50));
      const db = {
        "movie/550": { id: 550, title: "Fight Club", poster_path: "/x.jpg", backdrop_path: "/bg.jpg", vote_average: 8.4, release_date: "1999-10-15", overview: "A fighter" },
        "tv/13": { id: 13, name: "The Simpsons", poster_path: "/s.jpg", backdrop_path: "/sb.jpg", vote_average: 8.0, first_air_date: "1989-12-17", overview: "Animated" },
        "movie/27205": { id: 27205, title: "Inception", poster_path: "/i.jpg", backdrop_path: "/ib.jpg", vote_average: 8.8, release_date: "2010-07-16", overview: "Dreams" },
        "tv/1396": { id: 1396, name: "Breaking Bad", poster_path: "/b.jpg", backdrop_path: "/bb.jpg", vote_average: 9.5, first_air_date: "2008-01-20", overview: "Chemistry" },
        "tv/1399": { id: 1399, name: "Game of Thrones", poster_path: "/g.jpg", backdrop_path: "/gb.jpg", vote_average: 9.3, first_air_date: "2011-04-17", overview: "Fantasy" },
      };
      return db[api] || null;
    },
  },
  storage: {
    _m: {},
    get(k) { return this._m[k]; },
    set(k, v) { this._m[k] = v; },
  },
  html: { load: () => ({}) },
};

global.WidgetMetadata = {};
eval(fs.readFileSync("./widgets/ai-recommend.js", "utf8"));

async function test() {
  console.log("=== AI 推荐模块测试 ===\n");

  const t0 = Date.now();
  const list1 = await loadList({ apiKey: "test-key", apiBase: "https://api.openai.com", model: "gpt-4o-mini", page: 1 });
  const t1 = Date.now();
  console.log(`首次加载: ${list1.length} 部影片, 耗时 ${t1 - t0}ms`);
  list1.forEach((m) => console.log(`  - ${m.title} (${m.mediaType}) ⭐${m.rating}`));

  const t2 = Date.now();
  const list2 = await loadList({ apiKey: "test-key", apiBase: "https://api.openai.com", model: "gpt-4o-mini", page: 1 });
  const t3 = Date.now();
  console.log(`\n缓存命中: ${list2.length} 部影片, 耗时 ${t3 - t2}ms`);

  const aiCalls = calls.filter((c) => c.url && c.url.includes("/chat/completions"));
  console.log(`\nAI 调用次数: ${aiCalls.length} (应为 1，第二次走缓存)`);

  const history = await loadHistory();
  console.log(`\n观看记录: ${history.length} 条`);
  history.forEach((h) => console.log(`  - ${h.title}`));

  const models = await loadModels({ apiKey: "test-key", apiBase: "https://api.openai.com" });
  console.log(`\n可用模型: ${models.length} 个`);
  models.forEach((m) => console.log(`  - ${m.title}`));

  const detail = await loadDetail("detail:movie-550");
  console.log(`\n详情页: ${detail.title}`);
  console.log(`  backdropPaths: ${detail.backdropPaths.length} 张`);

  const historyAfterDetail = await loadHistory();
  console.log(`\n打开详情后观看记录: ${historyAfterDetail.length} 条`);

  const resource = await loadResource({ tmdbId: 1399, type: "tv", seriesName: "Game of Thrones", season: 1, episode: 1 });
  console.log(`\n播放资源: ${resource.length} 条`);

  const historyAfterPlay = await loadHistory();
  console.log(`播放后观看记录: ${historyAfterPlay.length} 条`);

  console.log("\n✅ 测试完成");
}

test().catch((e) => {
  console.error("❌", e);
  process.exit(1);
});
