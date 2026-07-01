WidgetMetadata = {
  id: "forward.ai-recommend",
  title: "AI 推荐",
  version: "1.1.0",
  requiredVersion: "0.0.1",
  description: "基于观看记录的 AI 影片推荐",
  author: "Forward",
  site: "https://github.com/InchStudio/ForwardWidgets",
  icon: "https://assets.vvebo.vip/scripts/icon.png",
  detailCacheDuration: 60,
  globalParams: [
    { name: "apiKey", title: "API Key", type: "input", value: "" },
    {
      name: "apiBase",
      title: "API 地址",
      type: "input",
      value: "https://api.openai.com",
      placeholders: [
        { title: "OpenAI", value: "https://api.openai.com" },
        { title: "Azure", value: "https://your-resource.openai.azure.com" },
        { title: "自定义", value: "https://your-api.com" },
      ],
    },
    {
      name: "model",
      title: "模型",
      type: "input",
      value: "gpt-4o-mini",
      placeholders: [
        { title: "GPT-4o Mini", value: "gpt-4o-mini" },
        { title: "GPT-4o", value: "gpt-4o" },
        { title: "GPT-3.5", value: "gpt-3.5-turbo" },
      ],
    },
  ],
  modules: [
    {
      id: "loadList",
      title: "AI 推荐",
      functionName: "loadList",
      cacheDuration: 3600,
      requiresWebView: false,
      params: [
        { name: "page", title: "页码", type: "page" },
        {
          name: "genre",
          title: "偏好类型",
          type: "enumeration",
          enumOptions: [
            { title: "不限", value: "" },
            { title: "动作", value: "action" },
            { title: "喜剧", value: "comedy" },
            { title: "科幻", value: "sci-fi" },
            { title: "恐怖", value: "horror" },
            { title: "爱情", value: "romance" },
            { title: "悬疑", value: "mystery" },
            { title: "纪录片", value: "documentary" },
          ],
        },
      ],
    },
    {
      id: "history",
      title: "观看记录",
      functionName: "loadHistory",
      cacheDuration: 0,
      requiresWebView: false,
      params: [],
    },
    {
      id: "loadModels",
      title: "可用模型",
      functionName: "loadModels",
      cacheDuration: 3600,
      requiresWebView: false,
      params: [],
    },
    {
      id: "loadResource",
      title: "加载资源",
      functionName: "loadResource",
      type: "stream",
      cacheDuration: 0,
      params: [],
    },
  ],
};

const HISTORY_KEY = "ai-recommend-history";
const CACHE_KEY = "ai-recommend-cache";
const MAX_HISTORY = 50;
const CACHE_TTL = 30 * 60 * 1000;

function getHistory() {
  try {
    return Widget.storage.get(HISTORY_KEY) || [];
  } catch {
    return [];
  }
}

function saveHistory(history) {
  Widget.storage.set(HISTORY_KEY, history.slice(-MAX_HISTORY));
}

function addToHistory(item) {
  if (!item || !item.id) return;
  const history = getHistory();
  const exists = history.find((h) => h.id === item.id && h.mediaType === item.mediaType);
  if (exists) return;
  history.push({
    id: item.id,
    mediaType: item.mediaType,
    title: item.title,
    posterPath: item.posterPath,
    rating: item.rating,
    timestamp: Date.now(),
  });
  saveHistory(history);
}

function recordFromTmdb(tmdbId, mediaType, tmdbRes) {
  if (!tmdbRes) return;
  addToHistory({
    id: Number(tmdbId),
    mediaType: mediaType || "movie",
    title: tmdbRes.title || tmdbRes.name,
    posterPath: tmdbRes.poster_path,
    rating: tmdbRes.vote_average,
  });
}

function getCached(key) {
  try {
    const cache = Widget.storage.get(CACHE_KEY) || {};
    const item = cache[key];
    if (item && Date.now() - item.ts < CACHE_TTL) return item.data;
    return null;
  } catch {
    return null;
  }
}

function setCache(key, data) {
  try {
    const cache = Widget.storage.get(CACHE_KEY) || {};
    cache[key] = { data, ts: Date.now() };
    Widget.storage.set(CACHE_KEY, cache);
  } catch {}
}

function clearCache() {
  try {
    Widget.storage.set(CACHE_KEY, {});
  } catch {}
}

async function callOpenAI(apiKey, apiBase, model, messages) {
  const url = `${apiBase}/v1/chat/completions`;
  const controller = typeof AbortController !== "undefined" ? new AbortController() : null;
  const timeout = controller ? setTimeout(() => controller.abort(), 15000) : null;

  try {
    const opts = {
      headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    };
    if (controller) opts.signal = controller.signal;

    const res = await Widget.http.post(url, { model, messages, temperature: 0.7, max_tokens: 1500 }, opts);
    return res.data;
  } finally {
    if (timeout) clearTimeout(timeout);
  }
}

function parseAiJson(content) {
  let cleaned = content.trim().replace(/```json\s*/g, "").replace(/```\s*/g, "").replace(/^\s*[\r\n]+/gm, "");

  const arrayMatch = cleaned.match(/\[[\s\S]*?\]/g);
  if (arrayMatch) {
    for (const match of arrayMatch) {
      try {
        const parsed = JSON.parse(match);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      } catch {}
    }
  }

  const start = cleaned.indexOf("[");
  const end = cleaned.lastIndexOf("]");
  if (start !== -1 && end > start) {
    try {
      const parsed = JSON.parse(cleaned.slice(start, end + 1));
      if (Array.isArray(parsed)) return parsed;
    } catch {}
  }

  throw new Error("无法解析 AI 返回的 JSON");
}

function buildPrompt(history, genre, page) {
  const historyText =
    history.length > 0
      ? history
          .slice(-20)
          .map((h) => `- ${h.title} (${h.mediaType === "movie" ? "电影" : "剧集"}, 评分: ${h.rating || "未知"})`)
          .join("\n")
      : "暂无观看记录";

  const genreHint = genre ? `\n用户偏好类型: ${genre}` : "";
  const count = Math.min(10, page * 10 - 9 + 4);

  return [
    {
      role: "system",
      content: `你是影视推荐助手。根据观看历史推荐影片。

输出格式: 纯 JSON 数组，不要 markdown、不要解释、不要文字。
每个元素: {"tmdbId":数字,"mediaType":"movie或tv","title":"名称","reason":"一句话理由"}

示例:
[{"tmdbId":550,"mediaType":"movie","title":"搏击俱乐部","reason":"烧脑悬疑经典"}]`,
    },
    {
      role: "user",
      content: `观看记录:\n${historyText}\n${genreHint}\n\n推荐${count}部新影片，返回JSON数组。`,
    },
  ];
}

async function fetchTmdbBatch(items) {
  const results = await Promise.all(
    items.map(async (rec) => {
      try {
        const res = await Widget.tmdb.get(`${rec.mediaType}/${rec.tmdbId}`, { params: { language: "zh-CN" } });
        if (!res) return null;
        return {
          id: rec.tmdbId,
          type: "tmdb",
          mediaType: rec.mediaType,
          title: res.title || res.name,
          posterPath: res.poster_path,
          backdropPath: res.backdrop_path,
          rating: res.vote_average,
          releaseDate: res.release_date || res.first_air_date,
          description: rec.reason,
        };
      } catch {
        return null;
      }
    })
  );
  return results.filter(Boolean);
}

async function loadList(params = {}) {
  try {
    const apiKey = params.apiKey;
    const apiBase = params.apiBase || "https://api.openai.com";
    const model = params.model || "gpt-4o-mini";

    if (!apiKey) throw new Error("请先设置 API Key");

    const page = Number(params.page || 1);
    const genre = params.genre || "";
    const cacheKey = `recommend:${genre}:${page}`;

    const cached = getCached(cacheKey);
    if (cached) return cached;

    const history = getHistory();
    const messages = buildPrompt(history, genre, page);
    const aiRes = await callOpenAI(apiKey, apiBase, model, messages);

    const content = aiRes.choices?.[0]?.message?.content || "";
    if (!content) throw new Error("AI 返回为空");

    const recommendations = parseAiJson(content);
    const valid = recommendations.filter((r) => r.tmdbId && ["movie", "tv"].includes(r.mediaType));
    if (valid.length === 0) throw new Error("AI 返回数据无效");

    const results = await fetchTmdbBatch(valid.slice(0, 10));

    for (const item of results) {
      addToHistory(item);
    }

    setCache(cacheKey, results);
    return results;
  } catch (error) {
    console.error("[loadList] 失败:", error.message || error);
    throw error;
  }
}

async function loadHistory() {
  const history = getHistory();
  return [...history].reverse().map((h) => ({
    id: h.id,
    type: "tmdb",
    mediaType: h.mediaType,
    title: h.title,
    posterPath: h.posterPath,
    rating: h.rating,
  }));
}

async function loadDetail(link) {
  try {
    const key = String(link).split(":")[1];
    if (!key) return null;

    const [mediaType, idStr] = key.split("-");
    if (!mediaType || !idStr) return null;

    const res = await Widget.tmdb.get(`${mediaType}/${idStr}`, { params: { language: "zh-CN" } });
    if (!res) return null;

    recordFromTmdb(idStr, mediaType, res);

    return {
      id: link,
      type: "url",
      mediaType,
      title: res.title || res.name,
      posterPath: res.poster_path,
      backdropPath: res.backdrop_path,
      backdropPaths: res.backdrop_path ? [res.backdrop_path] : [],
      rating: res.vote_average,
      releaseDate: res.release_date || res.first_air_date,
      description: res.overview,
      genreItems: (res.genres || []).map((g) => ({ id: String(g.id), title: g.name })),
    };
  } catch (error) {
    console.error("[loadDetail] 失败:", error.message || error);
    throw error;
  }
}

async function loadResource(params = {}) {
  try {
    const { tmdbId, type: mediaType } = params;
    if (!tmdbId) return [];

    const res = await Widget.tmdb.get(`${mediaType || "movie"}/${tmdbId}`, { params: { language: "zh-CN" } });
    recordFromTmdb(tmdbId, mediaType, res);

    return [];
  } catch (error) {
    console.error("[loadResource] 失败:", error.message || error);
    return [];
  }
}

async function loadModels(params = {}) {
  const apiKey = params.apiKey;
  const apiBase = params.apiBase || "https://api.openai.com";

  if (!apiKey) {
    return [{ id: "no-api-key", type: "url", title: "请先在设置中填写 API Key", description: "填写后刷新查看可用模型", posterPath: "https://assets.vvebo.vip/scripts/icon.png" }];
  }

  try {
    const res = await Widget.http.get(`${apiBase}/v1/models`, {
      headers: { Authorization: `Bearer ${apiKey}` },
    });

    const models = (res.data?.data || [])
      .filter((m) => m.id && !m.id.includes("embedding"))
      .sort((a, b) => a.id.localeCompare(b.id));

    if (models.length === 0) {
      return [{ id: "no-models", type: "url", title: "未找到可用模型", description: "请检查 API 地址和 Key", posterPath: "https://assets.vvebo.vip/scripts/icon.png" }];
    }

    return models.map((m) => ({
      id: `model:${m.id}`,
      type: "url",
      title: m.id,
      description: m.owned_by ? `提供商: ${m.owned_by}` : "",
      posterPath: "https://assets.vvebo.vip/scripts/icon.png",
    }));
  } catch (error) {
    console.error("[loadModels] 失败:", error.message || error);
    return [{ id: "error", type: "url", title: "获取模型列表失败", description: error.message || "请检查网络连接", posterPath: "https://assets.vvebo.vip/scripts/icon.png" }];
  }
}
