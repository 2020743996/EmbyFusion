const fs = require("fs");
const assert = require("assert/strict");

const calls = [];
const storage = {};

global.Widget = {
  http: {
    get: async (url) => {
      calls.push(url);
      throw new Error("Should not call http.get for AI recommend");
    },
    post: async (url, body, opts) => {
      calls.push({ url, body, opts });
      if (url.includes("/chat/completions")) {
        return {
          data: {
            choices: [
              {
                message: {
                  content: JSON.stringify([
                    { tmdbId: 550, mediaType: "movie", title: "搏击俱乐部", reason: "经典悬疑片" },
                    { tmdbId: 13, mediaType: "tv", title: "辛普森一家", reason: "经典动画" },
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
      if (api.includes("movie/550")) {
        return {
          id: 550,
          title: "Fight Club",
          poster_path: "/x.jpg",
          backdrop_path: "/bg.jpg",
          vote_average: 8.4,
          release_date: "1999-10-15",
          overview: "A fighter",
          genres: [{ id: 18, name: "剧情" }],
        };
      }
      if (api.includes("tv/13")) {
        return {
          id: 13,
          name: "The Simpsons",
          poster_path: "/s.jpg",
          backdrop_path: "/sb.jpg",
          vote_average: 8.0,
          first_air_date: "1989-12-17",
          overview: "Animated series",
          genres: [{ id: 35, name: "喜剧" }],
        };
      }
      throw new Error("unmocked tmdb: " + api);
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

(async () => {
  console.log("Testing AI recommend widget...\n");

  const list = await loadList({
    apiKey: "test-key",
    apiBase: "https://api.openai.com",
    model: "gpt-4o-mini",
    page: 1,
  });

  console.log("loadList results:", list.length, "items");
  assert.equal(list.length, 2, "Should return 2 recommended items");

  assert.equal(list[0].id, 550);
  assert.equal(list[0].type, "tmdb");
  assert.equal(list[0].mediaType, "movie");
  assert.ok(list[0].title);
  assert.ok(list[0].posterPath);
  assert.ok(list[0].rating);

  assert.equal(list[1].id, 13);
  assert.equal(list[1].mediaType, "tv");

  const aiCall = calls.find((c) => c.url && c.url.includes("/chat/completions"));
  assert.ok(aiCall, "Should call OpenAI API");
  assert.equal(aiCall.opts.headers.Authorization, "Bearer test-key");

  const history = await loadHistory();
  console.log("loadHistory results:", history.length, "items");
  assert.equal(history.length, 2);

  const detail = await loadDetail("detail:movie-550");
  console.log("loadDetail result:", detail?.title);
  assert.equal(detail.id, "detail:movie-550");
  assert.equal(detail.type, "url");
  assert.ok(Array.isArray(detail.backdropPaths));

  try {
    await loadList({ apiKey: "" });
    assert.fail("Should throw when no API key");
  } catch (e) {
    assert.ok(e.message.includes("API Key"));
  }

  const modelsNoKey = await loadModels({ apiKey: "" });
  console.log("loadModels (no key):", modelsNoKey[0].title);
  assert.ok(modelsNoKey[0].title.includes("API Key"));

  global.Widget.http.get = async (url, opts) => {
    calls.push({ url, opts });
    if (url.includes("/v1/models")) {
      return {
        data: {
          data: [
            { id: "gpt-4o", owned_by: "openai" },
            { id: "gpt-4o-mini", owned_by: "openai" },
            { id: "claude-3", owned_by: "anthropic" },
            { id: "text-embedding-ada", owned_by: "openai" },
          ],
        },
      };
    }
    throw new Error("unmocked: " + url);
  };

  const models = await loadModels({ apiKey: "test-key", apiBase: "https://api.openai.com" });
  console.log("loadModels results:", models.length, "models");
  assert.equal(models.length, 3, "Should filter out embedding models");
  assert.equal(models[0].type, "url");
  assert.ok(models[0].title.startsWith("gpt") || models[0].title.startsWith("claude"));
  assert.ok(models[0].id.startsWith("model:"));

  console.log("\n✅ All tests passed");
  console.log("Calls:", calls.length);
})().catch((e) => {
  console.error("❌", e);
  process.exit(1);
});
