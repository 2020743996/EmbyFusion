WidgetMetadata = {
  id: "forward.demo",
  title: "示例",
  version: "1.0.0",
  requiredVersion: "0.0.1",
  description: "ForwardWidget 示例模块，展示所有模块类型和 VideoItem 字段",
  author: "Forward",
  site: "https://github.com/InchStudio/ForwardWidgets",
  detailCacheDuration: 60,
  globalParams: [
    {
      name: "language",
      title: "语言",
      type: "language",
      value: "zh-CN",
    },
  ],
  modules: [
    {
      id: "loadList",
      title: "热门影片",
      functionName: "loadList",
      cacheDuration: 3600,
      requiresWebView: false,
      params: [
        { name: "page", title: "页码", type: "page" },
        {
          name: "category",
          title: "分类",
          type: "enumeration",
          enumOptions: [
            { title: "全部", value: "all" },
            { title: "电影", value: "movie" },
            { title: "剧集", value: "tv" },
          ],
        },
      ],
    },
    {
      id: "searchDanmu",
      title: "弹幕搜索",
      functionName: "searchDanmu",
      type: "danmu",
      params: [],
    },
    {
      id: "getDetail",
      title: "弹幕剧集",
      functionName: "getDetailById",
      type: "danmu",
      params: [],
    },
    {
      id: "getComments",
      title: "弹幕评论",
      functionName: "getCommentsById",
      type: "danmu",
      params: [],
    },
  ],
  search: {
    title: "搜索",
    functionName: "search",
    params: [
      { name: "keyword", title: "关键词", type: "input" },
      { name: "page", title: "页码", type: "page" },
    ],
  },
};

const TMDB_BASE = "https://api.themoviedb.org/3";
const DANMU_SERVER = "https://api.dandanplay.net";

async function loadList(params = {}) {
  try {
    const page = Number(params.page || 1);
    const language = params.language || "zh-CN";
    const category = params.category || "all";

    let apiPath;
    if (category === "movie") {
      apiPath = "movie/popular";
    } else if (category === "tv") {
      apiPath = "tv/popular";
    } else {
      apiPath = "trending/all/week";
    }

    const res = await Widget.tmdb.get(apiPath, {
      params: { page, language },
    });

    if (!res || !res.results) throw new Error("空响应");

    return res.results.map((item) => ({
      id: item.id,
      type: "tmdb",
      mediaType: item.media_type || (category === "movie" ? "movie" : "tv"),
      title: item.title || item.name,
      posterPath: item.poster_path,
      backdropPath: item.backdrop_path,
      rating: item.vote_average,
      releaseDate: item.release_date || item.first_air_date,
      description: item.overview,
    }));
  } catch (error) {
    console.error("[loadList] 失败:", error.message || error);
    throw error;
  }
}

async function loadDetail(link) {
  try {
    const key = String(link).split(":")[1];
    if (!key) return null;

    const [mediaType, idStr] = key.split("-");
    if (!mediaType || !idStr) return null;

    const res = await Widget.tmdb.get(`${mediaType}/${idStr}`, {
      params: { language: "zh-CN" },
    });

    if (!res) return null;

    const creditsRes = await Widget.tmdb.get(`${mediaType}/${idStr}/credits`, {
      params: { language: "zh-CN" },
    });

    const similarRes = await Widget.tmdb.get(`${mediaType}/${idStr}/similar`, {
      params: { page: 1 },
    });

    const peoples = (creditsRes?.cast || []).slice(0, 10).map((p) => ({
      id: String(p.id),
      title: p.name,
      avatar: p.profile_path ? `https://image.tmdb.org/t/p/w185${p.profile_path}` : null,
      role: p.character || "Actor",
    }));

    const relatedItems = (similarRes?.results || []).slice(0, 6).map((item) => ({
      id: item.id,
      type: "tmdb",
      mediaType: item.media_type || mediaType,
      title: item.title || item.name,
      posterPath: item.poster_path,
      rating: item.vote_average,
    }));

    const backdropPaths = [];
    if (res.backdrop_path) {
      backdropPaths.push(res.backdrop_path);
    }

    const trailers = [];
    const videosRes = await Widget.tmdb.get(`${mediaType}/${idStr}/videos`, {
      params: { language: "zh-CN" },
    });
    if (videosRes?.results) {
      for (const v of videosRes.results) {
        if (v.site === "YouTube" && v.type === "Trailer") {
          trailers.push({
            coverUrl: `https://img.youtube.com/vi/${v.key}/hqdefault.jpg`,
            url: `https://www.youtube.com/watch?v=${v.key}`,
          });
        }
      }
    }

    const genreItems = (res.genres || []).map((g) => ({
      id: String(g.id),
      title: g.name,
    }));

    return {
      id: link,
      type: "url",
      mediaType,
      title: res.title || res.name,
      posterPath: res.poster_path,
      backdropPath: res.backdrop_path,
      backdropPaths,
      rating: res.vote_average,
      releaseDate: res.release_date || res.first_air_date,
      description: res.overview,
      genreItems,
      peoples,
      relatedItems,
      trailers,
      duration: res.runtime || 0,
    };
  } catch (error) {
    console.error("[loadDetail] 失败:", error.message || error);
    throw error;
  }
}

async function search(params = {}) {
  try {
    const keyword = params.keyword || "";
    const page = Number(params.page || 1);

    if (!keyword) return [];

    const res = await Widget.tmdb.get("search/multi", {
      params: {
        query: keyword,
        page,
        language: "zh-CN",
      },
    });

    if (!res || !res.results) return [];

    return res.results
      .filter((item) => item.media_type === "movie" || item.media_type === "tv")
      .map((item) => ({
        id: item.id,
        type: "tmdb",
        mediaType: item.media_type,
        title: item.title || item.name,
        posterPath: item.poster_path,
        backdropPath: item.backdrop_path,
        rating: item.vote_average,
        releaseDate: item.release_date || item.first_air_date,
        description: item.overview,
      }));
  } catch (error) {
    console.error("[search] 失败:", error.message || error);
    throw error;
  }
}

async function searchDanmu(params = {}) {
  try {
    const kw = params.seriesName || params.title || "";
    if (!kw) return { animes: [] };

    const res = await Widget.http.get(
      `${params.server || DANMU_SERVER}/api/v2/search/anime?keyword=${encodeURIComponent(kw)}`
    );

    const animes = (res.data?.animes || []).map((a) => ({
      animeId: a.bangumiId || a.animeId,
      animeTitle: a.animeTitle,
      type: a.type,
    }));

    return { animes };
  } catch (error) {
    console.error("[searchDanmu] 失败:", error.message || error);
    throw error;
  }
}

async function getDetailById(params = {}) {
  try {
    const animeId = params.animeId;
    if (!animeId) return [];

    const res = await Widget.http.get(
      `${params.server || DANMU_SERVER}/api/v2/bangumi/${animeId}`
    );

    const eps = res.data?.bangumi?.episodes || [];
    return eps.map((e) => ({
      episodeId: e.episodeId,
      episodeTitle: e.episodeTitle,
    }));
  } catch (error) {
    console.error("[getDetailById] 失败:", error.message || error);
    throw error;
  }
}

async function getCommentsById(params = {}) {
  try {
    const commentId = params.commentId;
    if (!commentId) return { count: 0, comments: [] };

    const res = await Widget.http.get(
      `${params.server || DANMU_SERVER}/api/v2/comment/${commentId}?withRelated=true&chConvert=1`
    );

    return res.data;
  } catch (error) {
    console.error("[getCommentsById] 失败:", error.message || error);
    throw error;
  }
}
