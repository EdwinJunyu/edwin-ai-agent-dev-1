/* Previous implementation commented out per workspace rule. */
// #NEW CODE#
package com.edwin.edwin_ai_agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-accuracy Tavily search with hybrid ranking and optional verification.
 */
public class WebSearchTool {

    private static final String TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int OFFICIAL_ROUND_MAX_RESULTS = 4;
    private static final int MAX_RETURNED_RESULTS = 10;
    private static final int MAX_VERIFICATION_CANDIDATES = 3;
    private static final int MAX_RESULT_CONTENT_LENGTH = 420;
    private static final Set<String> EVENT_KEYWORDS = Set.of(
            "event", "events", "activity", "activities", "calendar", "schedule", "agenda", "semester", "welcome",
            "orientation", "student life", "announcement", "announcements", "festival", "workshop", "program",
            "活动", "日历", "校历", "日程", "安排", "欢迎", "迎新", "公告", "讲座", "项目"
    );
    private static final Set<String> NEWS_KEYWORDS = Set.of(
            "latest", "today", "news", "breaking", "recent", "update", "updates", "headline", "current"
    );
    private static final Set<String> FINANCE_KEYWORDS = Set.of(
            "stock", "stocks", "share price", "market cap", "earnings", "ticker", "nasdaq", "nyse", "crypto", "etf"
    );
    private static final Set<String> INSTITUTION_KEYWORDS = Set.of(
            "university", "college", "school", "campus", "faculty", "department", "office", "government", "ministry",
            "city", "county", "state", "official", "student", "society", "association", "committee",
            "大学", "学校", "学院", "校区", "办公室", "部门", "政府", "官方", "学生"
    );
    private static final Set<String> EVENT_SIGNAL_KEYWORDS = Set.of(
            "event", "events", "calendar", "schedule", "student", "welcome", "orientation", "announcement",
            "活动", "日历", "安排", "欢迎", "迎新", "学生", "公告"
    );
    private static final Set<String> INSTITUTION_SIGNAL_KEYWORDS = Set.of(
            "university", "college", "school", "student services", "student affairs", "official", "department",
            "ministry", "government", "faculty", "admissions", "registrar",
            "大学", "学院", "学校", "官方", "办公室", "部门", "政府", "学生事务"
    );
    private static final Set<String> LOW_AUTHORITY_DOMAINS = Set.of(
            "instagram.com", "facebook.com", "x.com", "twitter.com", "youtube.com", "tiktok.com",
            "reddit.com", "medium.com", "pinterest.com"
    );
    private static final Set<String> ENGLISH_STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "for", "to", "in", "on", "with", "by", "at", "from",
            "latest", "official", "site", "event", "events", "activity", "activities", "calendar", "schedule"
    );
    // Broaden intent recognition so more real-world campus, public-service, and business queries map to the right plan.
    private static final Set<String> ADDITIONAL_EVENT_KEYWORDS = Set.of(
            "webinar", "webinars", "meetup", "meetups", "seminar", "seminars", "conference", "conferences",
            "summit", "summits", "expo", "fair", "hackathon", "lecture", "lectures", "forum", "forums",
            "town hall", "open house", "open day", "info session", "information session", "deadline", "deadlines",
            "registration", "registrations", "register", "ceremony", "convocation", "graduation", "bootcamp",
            "competition", "training", "class schedule", "timetable"
    );
    private static final Set<String> ADDITIONAL_NEWS_KEYWORDS = Set.of(
            "press release", "press releases", "statement", "statements", "bulletin", "bulletins", "notice",
            "notices", "advisory", "advisories", "briefing", "briefings", "report", "reports", "memo", "memos",
            "communique", "communiques"
    );
    private static final Set<String> ADDITIONAL_FINANCE_KEYWORDS = Set.of(
            "dividend", "dividends", "forecast", "forecasts", "valuation", "valuations", "revenue", "revenues",
            "profit", "profits", "margin", "margins", "analyst", "analysts", "price target", "quarterly results",
            "fiscal", "10-k", "10-q", "sec filing", "sec filings"
    );
    private static final Set<String> ADDITIONAL_INSTITUTION_KEYWORDS = Set.of(
            "institute", "institution", "academy", "center", "centre", "clinic", "hospital", "agency", "bureau",
            "commission", "authority", "council", "board", "foundation", "museum", "library", "laboratory", "lab",
            "registry", "registrar", "student union", "union", "guild"
    );
    private static final Set<String> ADDITIONAL_EVENT_SIGNAL_KEYWORDS = Set.of(
            "webinar", "seminar", "conference", "summit", "lecture", "registration", "deadline", "open house",
            "info session", "hackathon", "fair", "expo", "forum", "town hall", "graduation", "convocation"
    );
    private static final Set<String> ADDITIONAL_INSTITUTION_SIGNAL_KEYWORDS = Set.of(
            "institute", "academy", "center", "centre", "clinic", "hospital", "authority", "agency", "bureau",
            "commission", "council", "board", "foundation", "library", "museum", "registry", "registrar",
            "office of", "student union", "visitor services"
    );
    private static final Set<String> ADDITIONAL_RECENCY_KEYWORDS = Set.of(
            "newest", "fresh", "upcoming", "this week", "this month", "this year", "just announced"
    );
    private static final Set<String> ADDITIONAL_STOPWORDS = Set.of(
            "webinar", "webinars", "meetup", "meetups", "seminar", "seminars", "conference", "conferences",
            "registration", "registrations", "announcement", "announcements", "bulletin", "bulletins", "notice",
            "notices", "press", "release", "releases", "student", "students", "university", "college", "school",
            "government", "department", "office", "agency", "board", "council", "clinic", "hospital"
    );
    private static final Set<String> PAGE_TYPE_BOOST_KEYWORDS = Set.of(
            "calendar", "announcement", "welcome", "webinar", "seminar", "conference", "registration",
            "bulletin", "notice", "press release", "open house", "deadline"
    );
    private static final Set<String> ALL_EVENT_KEYWORDS = mergeKeywordSets(EVENT_KEYWORDS, ADDITIONAL_EVENT_KEYWORDS);
    private static final Set<String> ALL_NEWS_KEYWORDS = mergeKeywordSets(NEWS_KEYWORDS, ADDITIONAL_NEWS_KEYWORDS);
    private static final Set<String> ALL_FINANCE_KEYWORDS = mergeKeywordSets(FINANCE_KEYWORDS, ADDITIONAL_FINANCE_KEYWORDS);
    private static final Set<String> ALL_INSTITUTION_KEYWORDS = mergeKeywordSets(INSTITUTION_KEYWORDS, ADDITIONAL_INSTITUTION_KEYWORDS);
    private static final Set<String> ALL_EVENT_SIGNAL_KEYWORDS = mergeKeywordSets(EVENT_SIGNAL_KEYWORDS, ADDITIONAL_EVENT_SIGNAL_KEYWORDS);
    private static final Set<String> ALL_INSTITUTION_SIGNAL_KEYWORDS = mergeKeywordSets(INSTITUTION_SIGNAL_KEYWORDS, ADDITIONAL_INSTITUTION_SIGNAL_KEYWORDS);
    private static final Set<String> ALL_RECENCY_KEYWORDS = mergeKeywordSets(NEWS_KEYWORDS, ADDITIONAL_RECENCY_KEYWORDS);
    private static final Set<String> ALL_ENGLISH_STOPWORDS = mergeKeywordSets(ENGLISH_STOPWORDS, ADDITIONAL_STOPWORDS);
    // General-purpose data requests need a stricter evidence gate before the agent can safely finalize.
    private static final Set<String> DATA_INTENSIVE_KEYWORDS = Set.of(
            "data", "stats", "statistics", "number", "numbers", "figure", "figures", "table", "breakdown",
            "summary", "price", "prices", "change", "changes", "performance", "trend", "trends", "market cap",
            "volume", "amount", "count", "counts", "percent", "percentage", "\u6570\u636e", "\u7edf\u8ba1",
            "\u5217\u8868", "\u6e05\u5355", "\u4ef7\u683c", "\u5e02\u503c", "\u6da8\u8dcc", "\u6da8\u5e45",
            "\u8dcc\u5e45", "\u8d8b\u52bf", "\u767e\u5206\u6bd4"
    );
    private static final Set<String> RANKING_INTENT_KEYWORDS = Set.of(
            "top", "ranking", "rank", "ranked", "leaderboard", "list", "best", "worst", "highest", "lowest",
            "\u6392\u540d", "\u699c", "\u699c\u5355", "\u524d\u4e94", "\u524d\u5341", "\u524d5", "\u524d10"
    );
    private static final Set<String> PERFORMANCE_INTENT_KEYWORDS = Set.of(
            "change", "changes", "performance", "gain", "gains", "loss", "losses", "gainer", "gainers",
            "loser", "losers", "rise", "fall", "return", "returns", "\u6da8\u8dcc", "\u6da8\u8dcc\u60c5\u51b5",
            "\u6da8\u5e45", "\u8dcc\u5e45", "\u8d70\u52bf", "\u8868\u73b0"
    );
    private static final Set<String> QUANTITATIVE_INTENT_KEYWORDS = Set.of(
            "data", "stats", "statistics", "number", "numbers", "figure", "figures", "price", "prices",
            "amount", "count", "counts", "percent", "percentage", "market cap", "volume", "rate", "rates",
            "\u6570\u636e", "\u7edf\u8ba1", "\u6570\u91cf", "\u91d1\u989d", "\u6bd4\u4f8b", "\u767e\u5206\u6bd4",
            "\u4ef7\u683c", "\u5e02\u503c"
    );
    private static final Set<String> ALL_DATA_INTENSIVE_KEYWORDS = mergeKeywordSets(
            DATA_INTENSIVE_KEYWORDS,
            RANKING_INTENT_KEYWORDS,
            PERFORMANCE_INTENT_KEYWORDS,
            QUANTITATIVE_INTENT_KEYWORDS
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b20\\d{2}[-/]\\d{1,2}(?:[-/]\\d{1,2})?\\b|20\\d{2}年\\d{1,2}月(?:\\d{1,2}日)?|(?i)\\b(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\s+\\d{1,2},\\s*20\\d{2}\\b"
    );
    private static final Pattern YEAR_MONTH_HYPHEN_PATTERN = Pattern.compile("\\b(20\\d{2})-(\\d{1,2})\\b");
    private static final Pattern YEAR_MONTH_CHINESE_PATTERN = Pattern.compile("(20\\d{2})年\\s*(\\d{1,2})月");
    private static final Pattern MONTH_NAME_YEAR_PATTERN = Pattern.compile("(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(20\\d{2})\\b");
    private static final Pattern YEAR_MONTH_NAME_PATTERN = Pattern.compile("(?i)\\b(20\\d{2})\\s+(january|february|march|april|may|june|july|august|september|october|november|december)\\b");
    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("\\b(?:site:)?([a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+)\\b");
    private static final Pattern TOP_STYLE_PATTERN = Pattern.compile("(?i)\\btop\\s*\\d+\\b|\\b(?:highest|lowest|largest|smallest|best|worst)\\s+\\d+\\b|\\u524d\\s*[0-9\\u4e00-\\u5341]+");
    private static final Pattern NUMERIC_EVIDENCE_PATTERN = Pattern.compile("(?i)(?:\\b\\d+(?:\\.\\d+)?%?\\b|\\$\\s*\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?\\s?(?:million|billion|trillion))");
    private static final Map<String, Integer> MONTH_NAME_TO_NUMBER = Map.ofEntries(
            Map.entry("january", 1),
            Map.entry("february", 2),
            Map.entry("march", 3),
            Map.entry("april", 4),
            Map.entry("may", 5),
            Map.entry("june", 6),
            Map.entry("july", 7),
            Map.entry("august", 8),
            Map.entry("september", 9),
            Map.entry("october", 10),
            Map.entry("november", 11),
            Map.entry("december", 12)
    );

    private final String apiKey;
    private final WebSearchHttpClient webSearchHttpClient;
    private final WebScrapingTool webScrapingTool;

    public WebSearchTool(String apiKey) {
        this(apiKey, new WebScrapingTool(), (url, headers, requestBody) -> {
            HttpRequest request = HttpUtil.createPost(url);
            headers.forEach(request::header);
            return request.body(requestBody).execute().body();
        });
    }

    public WebSearchTool(String apiKey, WebScrapingTool webScrapingTool) {
        this(apiKey, webScrapingTool, (url, headers, requestBody) -> {
            HttpRequest request = HttpUtil.createPost(url);
            headers.forEach(request::header);
            return request.body(requestBody).execute().body();
        });
    }

    WebSearchTool(String apiKey, WebScrapingTool webScrapingTool, WebSearchHttpClient webSearchHttpClient) {
        this.apiKey = apiKey;
        this.webSearchHttpClient = webSearchHttpClient;
        this.webScrapingTool = webScrapingTool;
    }

    public String searchWeb(String query) {
        return searchWeb(query, null, null, null, null, null, null);
    }

    @Tool(description = "Search the web with official-source preference, fallback coverage, and optional page verification")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query,
            @ToolParam(required = false, description = "Preferred domains to prioritize, for example [\"sfu.ca\", \"canada.ca\"]") List<String> preferredDomains,
            @ToolParam(required = false, description = "Domains to exclude from search results") List<String> excludedDomains,
            @ToolParam(required = false, description = "Optional time hint such as 2026-04, 2026, day, week, month, or year") String timeHint,
            @ToolParam(required = false, description = "Whether to prioritize official or authoritative sources first") Boolean officialFirst,
            @ToolParam(required = false, description = "Whether to verify top candidates by scraping their pages") Boolean needVerification,
            @ToolParam(required = false, description = "Maximum number of results to return") Integer maxResults) {
        try {
            SearchOptions options = resolveSearchOptions(
                    query,
                    preferredDomains,
                    excludedDomains,
                    timeHint,
                    officialFirst,
                    needVerification,
                    maxResults
            );
            SearchResponse response = executeHighAccuracySearch(options);
            return JSONUtil.toJsonStr(response.toMap());
        } catch (Exception e) {
            return "Error searching web: " + e.getMessage();
        }
    }

    // Build an explicit search plan so request construction, fallback, and ranking stay testable.
    private SearchOptions resolveSearchOptions(
            String query,
            List<String> preferredDomains,
            List<String> excludedDomains,
            String timeHint,
            Boolean officialFirst,
            Boolean needVerification,
            Integer maxResults
    ) {
        String normalizedQuery = normalizeWhitespace(query);
        if (!StringUtils.hasText(normalizedQuery)) {
            throw new IllegalArgumentException("query must not be blank");
        }

        SearchSignals signals = detectSignals(normalizedQuery, timeHint);
        List<String> resolvedPreferredDomains = mergeDomains(
                normalizeDomains(preferredDomains),
                extractDomainsFromQuery(normalizedQuery)
        );
        List<String> resolvedExcludedDomains = normalizeDomains(excludedDomains);
        boolean resolvedOfficialFirst = officialFirst != null ? officialFirst : (signals.institutionLike() || signals.eventLike());
        boolean resolvedNeedVerification = needVerification != null ? needVerification : (resolvedOfficialFirst || signals.eventLike());
        int resolvedMaxResults = clamp(maxResults != null ? maxResults : DEFAULT_MAX_RESULTS, 1, MAX_RETURNED_RESULTS);
        TimeConstraint timeConstraint = resolveTimeConstraint(normalizedQuery, timeHint, signals);

        return new SearchOptions(
                normalizedQuery,
                buildOfficialQuery(normalizedQuery, signals, timeConstraint),
                buildBroadQuery(normalizedQuery, signals, timeConstraint),
                resolvedPreferredDomains,
                resolvedExcludedDomains,
                timeConstraint,
                signals,
                resolvedOfficialFirst,
                resolvedNeedVerification,
                resolvedMaxResults,
                extractRankingTokens(normalizedQuery)
        );
    }

    private SearchResponse executeHighAccuracySearch(SearchOptions options) {
        List<SearchCandidate> officialCandidates = new ArrayList<>();
        int roundsUsed = 0;

        if (options.officialFirst()) {
            SearchRoundRequest officialRound = buildRoundRequest(options, true);
            officialCandidates.addAll(executeRound(officialRound, options));
            roundsUsed++;
            rerankCandidates(officialCandidates, options);
            verifyTopCandidates(officialCandidates, options, Math.min(2, officialCandidates.size()));
            rerankCandidates(officialCandidates, options);
        }

        List<SearchCandidate> broadCandidates = new ArrayList<>();
        if (!options.officialFirst() || shouldRunBroadFallback(officialCandidates, options)) {
            SearchRoundRequest broadRound = buildRoundRequest(options, false);
            broadCandidates.addAll(executeRound(broadRound, options));
            roundsUsed++;
        }

        List<SearchCandidate> mergedCandidates = mergeCandidates(officialCandidates, broadCandidates);
        rerankCandidates(mergedCandidates, options);
        verifyTopCandidates(mergedCandidates, options, Math.min(MAX_VERIFICATION_CANDIDATES, mergedCandidates.size()));
        rerankCandidates(mergedCandidates, options);

        List<SearchCandidate> topCandidates = mergedCandidates.stream()
                .sorted(Comparator.comparingDouble(SearchCandidate::score).reversed())
                .limit(options.maxResults())
                .toList();

        SearchEvidenceSummary evidenceSummary = buildEvidenceSummary(options, topCandidates);

        return new SearchResponse(
                options.originalQuery(),
                roundsUsed,
                options.officialFirst(),
                options.needVerification(),
                resolveTopic(options.signals()),
                options.timeConstraint().queryHint(),
                evidenceSummary,
                topCandidates
        );
    }

    private SearchEvidenceSummary buildEvidenceSummary(SearchOptions options, List<SearchCandidate> candidates) {
        int authoritativeResultCount = (int) candidates.stream()
                .filter(SearchCandidate::isAuthoritative)
                .count();
        int verifiedResultCount = (int) candidates.stream()
                .filter(candidate -> "verified".equals(candidate.verificationStatus()))
                .count();
        int directAnswerCount = (int) candidates.stream()
                .filter(candidate -> candidate.isDirectAnswer(options))
                .count();
        int thresholdQualifiedCount = (int) candidates.stream()
                .filter(candidate -> candidate.meetsEvidenceThreshold(options))
                .count();
        boolean evidenceThresholdMet = thresholdQualifiedCount > 0;

        String evidenceThresholdReason;
        if (evidenceThresholdMet) {
            evidenceThresholdReason = "\u5f53\u524d\u5df2\u83b7\u53d6\u5230\u8db3\u4ee5\u652f\u6491\u7ed3\u8bba\u7684\u76f4\u63a5\u8bc1\u636e\u3002";
        } else if (options.signals().dataIntensive()) {
            evidenceThresholdReason = "\u5f53\u524d\u68c0\u7d22\u7ed3\u679c\u8fd8\u4e0d\u8db3\u4ee5\u7a33\u5b9a\u652f\u6301\u7cbe\u786e\u7684\u6570\u636e/\u6392\u540d/\u6da8\u8dcc\u7c7b\u7ed3\u8bba\u3002";
        } else if (authoritativeResultCount > 0 || verifiedResultCount > 0) {
            evidenceThresholdReason = "\u5df2\u627e\u5230\u5019\u9009\u7ed3\u679c\uff0c\u4f46\u8fd8\u7f3a\u5c11\u80fd\u76f4\u63a5\u56de\u7b54\u95ee\u9898\u7684\u6709\u6548\u8bc1\u636e\u3002";
        } else {
            evidenceThresholdReason = "\u5f53\u524d\u7ed3\u679c\u4ecd\u4ee5\u5019\u9009\u9875\u9762\u4e3a\u4e3b\uff0c\u8fd8\u6ca1\u6709\u5f62\u6210\u53ef\u76f4\u63a5\u5f15\u7528\u7684\u8bc1\u636e\u3002";
        }

        return new SearchEvidenceSummary(
                options.signals().dataIntensive(),
                authoritativeResultCount,
                verifiedResultCount,
                directAnswerCount,
                thresholdQualifiedCount,
                evidenceThresholdMet,
                evidenceThresholdReason
        );
    }

    private SearchRoundRequest buildRoundRequest(SearchOptions options, boolean officialRound) {
        String query = officialRound ? options.officialQuery() : options.broadQuery();
        String topic = resolveTopic(options.signals());
        String searchDepth = resolveSearchDepth(options.signals(), officialRound);
        int maxResults = officialRound
                ? Math.min(OFFICIAL_ROUND_MAX_RESULTS, options.maxResults())
                : options.maxResults();

        List<String> includeDomains = officialRound ? options.preferredDomains() : List.of();
        return new SearchRoundRequest(
                officialRound ? "official" : "broad",
                query,
                topic,
                searchDepth,
                maxResults,
                includeDomains,
                options.excludedDomains(),
                options.timeConstraint().publishTimeRange(),
                options.timeConstraint().publishStartDate(),
                options.timeConstraint().publishEndDate()
        );
    }

    private List<SearchCandidate> executeRound(SearchRoundRequest roundRequest, SearchOptions options) {
        JSONObject requestBody = JSONUtil.createObj()
                .set("query", roundRequest.query())
                .set("topic", roundRequest.topic())
                .set("search_depth", roundRequest.searchDepth())
                .set("max_results", roundRequest.maxResults())
                .set("include_answer", false)
                .set("include_raw_content", false)
                .set("auto_parameters", false);

        if (!roundRequest.includeDomains().isEmpty()) {
            requestBody.set("include_domains", roundRequest.includeDomains());
        }
        if (!roundRequest.excludeDomains().isEmpty()) {
            requestBody.set("exclude_domains", roundRequest.excludeDomains());
        }
        if (StringUtils.hasText(roundRequest.publishTimeRange())) {
            requestBody.set("time_range", roundRequest.publishTimeRange());
        }
        if (StringUtils.hasText(roundRequest.publishStartDate())) {
            requestBody.set("start_date", roundRequest.publishStartDate());
        }
        if (StringUtils.hasText(roundRequest.publishEndDate())) {
            requestBody.set("end_date", roundRequest.publishEndDate());
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");

        String response = webSearchHttpClient.post(
                TAVILY_SEARCH_API_URL,
                headers,
                JSONUtil.toJsonStr(requestBody)
        );

        JSONObject responseJson = JSONUtil.parseObj(response);
        return normalizeCandidates(responseJson.getJSONArray("results"), roundRequest.round(), options);
    }

    private List<SearchCandidate> normalizeCandidates(JSONArray tavilyResults, String round, SearchOptions options) {
        List<SearchCandidate> candidates = new ArrayList<>();
        if (tavilyResults == null) {
            return candidates;
        }

        for (int index = 0; index < tavilyResults.size(); index++) {
            JSONObject tavilyResult = tavilyResults.getJSONObject(index);
            String title = normalizeWhitespace(tavilyResult.getStr("title", ""));
            String url = normalizeWhitespace(tavilyResult.getStr("url", ""));
            if (!StringUtils.hasText(url)) {
                continue;
            }

            SearchCandidate candidate = new SearchCandidate(
                    title,
                    url,
                    abbreviate(normalizeWhitespace(tavilyResult.getStr("content", "")), MAX_RESULT_CONTENT_LENGTH),
                    round,
                    tavilyResult.getDouble("score", 0.0d)
            );
            candidate.setSourceType(classifySourceType(candidate.domain(), candidate.title(), candidate.content(), options));
            candidate.addDateHints(extractDateHints(candidate.title() + "\n" + candidate.content()));
            candidate.setScore(calculateScore(candidate, options));
            candidates.add(candidate);
        }
        return candidates;
    }

    private void verifyTopCandidates(List<SearchCandidate> candidates, SearchOptions options, int maxToVerify) {
        if (!options.needVerification() || candidates.isEmpty() || maxToVerify <= 0) {
            return;
        }

        int verifiedCount = 0;
        List<SearchCandidate> sortedCandidates = candidates.stream()
                .sorted(Comparator.comparingDouble(SearchCandidate::score).reversed())
                .toList();

        for (SearchCandidate candidate : sortedCandidates) {
            if (verifiedCount >= maxToVerify) {
                break;
            }
            if (!candidate.needsVerification()) {
                continue;
            }

            WebScrapingTool.ScrapeResult scrapeResult = webScrapingTool.scrapePage(candidate.url());
            candidate.applyVerification(scrapeResult, options);
            candidate.setScore(calculateScore(candidate, options));
            verifiedCount++;
        }
    }

    private boolean shouldRunBroadFallback(List<SearchCandidate> officialCandidates, SearchOptions options) {
        if (officialCandidates.isEmpty()) {
            return true;
        }

        long authoritativeCount = officialCandidates.stream()
                .filter(SearchCandidate::isAuthoritative)
                .count();
        boolean hasDirectEvidence = officialCandidates.stream()
                .anyMatch(candidate -> candidate.isDirectAnswer(options));

        return authoritativeCount < 2 || !hasDirectEvidence;
    }

    private List<SearchCandidate> mergeCandidates(List<SearchCandidate> officialCandidates, List<SearchCandidate> broadCandidates) {
        Map<String, SearchCandidate> merged = new LinkedHashMap<>();

        for (SearchCandidate candidate : officialCandidates) {
            merged.put(candidate.normalizedUrl(), candidate.copy());
        }
        for (SearchCandidate candidate : broadCandidates) {
            SearchCandidate existing = merged.get(candidate.normalizedUrl());
            if (existing == null) {
                merged.put(candidate.normalizedUrl(), candidate.copy());
            } else {
                existing.mergeFrom(candidate);
            }
        }

        return new ArrayList<>(merged.values());
    }

    private void rerankCandidates(List<SearchCandidate> candidates, SearchOptions options) {
        for (SearchCandidate candidate : candidates) {
            candidate.setScore(calculateScore(candidate, options));
        }
        candidates.sort(Comparator.comparingDouble(SearchCandidate::score).reversed());
    }

    private double calculateScore(SearchCandidate candidate, SearchOptions options) {
        double score = candidate.rawScore() * 10.0;
        score += authorityWeight(candidate, options);
        score += keywordMatchWeight(candidate, options.queryTokens());
        score += dateHintWeight(candidate, options);
        score += pageTypeWeight(candidate, options);
        score += verificationWeight(candidate);
        if ("official".equals(candidate.round()) && options.officialFirst()) {
            score += 0.6;
        }
        return roundScore(score);
    }

    private double authorityWeight(SearchCandidate candidate, SearchOptions options) {
        if ("official".equals(candidate.sourceType())) {
            return matchesPreferredDomain(candidate.domain(), options.preferredDomains()) ? 5.0 : 3.5;
        }
        if ("authoritative".equals(candidate.sourceType())) {
            return 1.8;
        }
        return LOW_AUTHORITY_DOMAINS.contains(candidate.domain()) ? -1.5 : 0.2;
    }

    private double keywordMatchWeight(SearchCandidate candidate, List<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }

        String titleText = candidate.title().toLowerCase(Locale.ROOT);
        String bodyText = (candidate.content() + " " + candidate.verificationSnippet()).toLowerCase(Locale.ROOT);
        double score = 0.0;

        for (String token : queryTokens) {
            String normalizedToken = token.toLowerCase(Locale.ROOT);
            if (titleText.contains(normalizedToken)) {
                score += 0.9;
            }
            if (bodyText.contains(normalizedToken)) {
                score += 0.45;
            }
        }

        return Math.min(score, 4.0);
    }

    private double dateHintWeight(SearchCandidate candidate, SearchOptions options) {
        if (!options.signals().timeSensitive()) {
            return 0.0;
        }
        if (candidate.matchedDateHints().isEmpty()) {
            return options.signals().eventLike() ? -0.4 : 0.0;
        }
        if (candidate.matchesRequestedTime(options.timeConstraint().requestedDateTokens())) {
            return 1.8;
        }
        return 0.8;
    }

    //页面类型加权
    private double pageTypeWeight(SearchCandidate candidate, SearchOptions options) {
        String combinedText = (candidate.title() + " " + candidate.content() + " " + candidate.verificationSnippet()).toLowerCase(Locale.ROOT);
        double score = 0.0;

        // if (options.signals().eventLike() && containsAny(combinedText, EVENT_SIGNAL_KEYWORDS)) {
        //     score += 1.2;
        // }
        // if (options.signals().institutionLike() && containsAny(combinedText, INSTITUTION_SIGNAL_KEYWORDS)) {
        //     score += 0.8;
        // }
        // if (combinedText.contains("calendar") || combinedText.contains("announcement") || combinedText.contains("welcome")) {
        //     score += 0.5;
        // }
        // #NEW CODE#
        // Reuse the broader marker sets in ranking so newly supported query phrasings also boost the right pages.
        if (options.signals().eventLike() && containsAny(combinedText, ALL_EVENT_SIGNAL_KEYWORDS)) {
            score += 1.2;
        }
        if (options.signals().institutionLike() && containsAny(combinedText, ALL_INSTITUTION_SIGNAL_KEYWORDS)) {
            score += 0.8;
        }
        if (containsAny(combinedText, PAGE_TYPE_BOOST_KEYWORDS)) {
            score += 0.5;
        }

        return score;
    }

    private double verificationWeight(SearchCandidate candidate) {
        return switch (candidate.verificationStatus()) {
            case "verified" -> 4.0;
            case "content_found" -> 1.2;
            case "weak_match" -> -0.6;
            case "fetch_error" -> -1.2;
            case "insufficient_content" -> -0.8;
            default -> 0.0;
        };
    }

    private String resolveTopic(SearchSignals signals) {
        if (signals.financeLike()) {
            return "finance";
        }
        if (signals.newsLike() && !signals.eventLike()) {
            return "news";
        }
        return "general";
    }

    private String resolveSearchDepth(SearchSignals signals, boolean officialRound) {
        // if (officialRound || signals.eventLike() || signals.newsLike()) {
        // #NEW CODE#
        if (officialRound || signals.eventLike() || signals.newsLike() || signals.dataIntensive()) {
            return "advanced";
        }
        return "basic";
    }

    //查询意图识
    private SearchSignals detectSignals(String query, String timeHint) {
        String loweredQuery = query.toLowerCase(Locale.ROOT);
        // boolean eventLike = containsAny(loweredQuery, EVENT_KEYWORDS);
        // boolean newsLike = containsAny(loweredQuery, NEWS_KEYWORDS);
        // boolean financeLike = containsAny(loweredQuery, FINANCE_KEYWORDS);
        // boolean institutionLike = containsAny(loweredQuery, INSTITUTION_KEYWORDS) || hasAcronymToken(query);
        // boolean timeSensitive = eventLike || newsLike || hasDateHint(query) || hasDateHint(timeHint) || containsLatestKeyword(loweredQuery);
        // #NEW CODE#
        // Merge baseline keywords with broader synonyms so common phrasings trigger the intended search strategy.
        // boolean eventLike = containsAny(loweredQuery, ALL_EVENT_KEYWORDS);
        // boolean newsLike = containsAny(loweredQuery, ALL_NEWS_KEYWORDS);
        // boolean financeLike = containsAny(loweredQuery, ALL_FINANCE_KEYWORDS);
        // boolean institutionLike = containsAny(loweredQuery, ALL_INSTITUTION_KEYWORDS) || hasAcronymToken(query);
        // boolean timeSensitive = eventLike || newsLike || hasDateHint(query) || hasDateHint(timeHint) || containsLatestKeyword(loweredQuery);
        // boolean cjkQuery = containsCjk(query);
        // return new SearchSignals(eventLike, newsLike, financeLike, institutionLike, timeSensitive, cjkQuery);
        // #NEW CODE#
        boolean eventLike = containsAny(loweredQuery, ALL_EVENT_KEYWORDS);
        boolean newsLike = containsAny(loweredQuery, ALL_NEWS_KEYWORDS);
        boolean financeLike = containsAny(loweredQuery, ALL_FINANCE_KEYWORDS);
        boolean institutionLike = containsAny(loweredQuery, ALL_INSTITUTION_KEYWORDS) || hasAcronymToken(query);
        boolean rankingLike = containsAny(loweredQuery, RANKING_INTENT_KEYWORDS) || matchesTopStylePattern(query);
        boolean performanceLike = containsAny(loweredQuery, PERFORMANCE_INTENT_KEYWORDS);
        boolean dataIntensive = rankingLike || performanceLike || containsAny(loweredQuery, ALL_DATA_INTENSIVE_KEYWORDS);
        boolean timeSensitive = eventLike || newsLike || hasDateHint(query) || hasDateHint(timeHint) || containsLatestKeyword(loweredQuery);
        boolean cjkQuery = containsCjk(query);
        return new SearchSignals(eventLike, newsLike, financeLike, institutionLike, timeSensitive, cjkQuery, dataIntensive, rankingLike, performanceLike);
    }

    private TimeConstraint resolveTimeConstraint(String query, String timeHint, SearchSignals signals) {
        String normalizedTimeHint = normalizeWhitespace(timeHint);
        List<String> requestedDateTokens = new ArrayList<>(extractDateHints(query + " " + normalizedTimeHint));
        String queryHint = normalizedTimeHint;

        if (!StringUtils.hasText(queryHint) && !requestedDateTokens.isEmpty()) {
            queryHint = requestedDateTokens.get(0);
        }

        if (StringUtils.hasText(normalizedTimeHint) && isRelativeTimeRange(normalizedTimeHint) && !signals.eventLike()) {
            return new TimeConstraint(normalizedTimeHint.toLowerCase(Locale.ROOT), "", "", queryHint, requestedDateTokens);
        }

        DateRange explicitRange = parseDateRange(normalizedTimeHint);
        if (explicitRange == null && !signals.eventLike()) {
            explicitRange = parseDateRange(query);
        }

        if (explicitRange != null && !signals.eventLike() && signals.newsLike()) {
            return new TimeConstraint("", explicitRange.startDate(), explicitRange.endDate(), queryHint, requestedDateTokens);
        }

        return new TimeConstraint("", "", "", queryHint, requestedDateTokens);
    }

    private String buildOfficialQuery(String query, SearchSignals signals, TimeConstraint timeConstraint) {
        String rewrittenQuery = appendIfMissing(query, timeConstraint.queryHint());
        if (signals.cjkQuery()) {
            rewrittenQuery = appendIfMissing(rewrittenQuery, "官网");
            rewrittenQuery = appendIfMissing(rewrittenQuery, "官方");
            if (signals.eventLike()) {
                rewrittenQuery = appendIfMissing(rewrittenQuery, "活动");
                rewrittenQuery = appendIfMissing(rewrittenQuery, "日历");
            }
        } else {
            rewrittenQuery = appendIfMissing(rewrittenQuery, "official");
            if (signals.eventLike()) {
                rewrittenQuery = appendIfMissing(rewrittenQuery, "events");
                rewrittenQuery = appendIfMissing(rewrittenQuery, "calendar");
            }
        }
        return normalizeWhitespace(rewrittenQuery);
    }

    private String buildBroadQuery(String query, SearchSignals signals, TimeConstraint timeConstraint) {
        String rewrittenQuery = appendIfMissing(query, timeConstraint.queryHint());
        if (!signals.cjkQuery() && signals.eventLike()) {
            rewrittenQuery = appendIfMissing(rewrittenQuery, "events");
        }
        return normalizeWhitespace(rewrittenQuery);
    }

    private String classifySourceType(String domain, String title, String content, SearchOptions options) {
        if (!StringUtils.hasText(domain)) {
            return "third_party";
        }
        if (matchesPreferredDomain(domain, options.preferredDomains())) {
            return "official";
        }
        if (LOW_AUTHORITY_DOMAINS.contains(domain)) {
            return "third_party";
        }
        if (looksOfficialDomain(domain)) {
            return "official";
        }
        if (domainMatchesQueryIdentity(domain, options.queryTokens()) && pageLooksInstitutional(title + " " + content)) {
            return "official";
        }
        if (domain.endsWith(".org")) {
            return "authoritative";
        }
        return "third_party";
    }

    private boolean looksOfficialDomain(String domain) {
        return domain.contains(".gov")
                || domain.endsWith(".edu")
                || domain.contains(".edu.")
                || domain.contains(".ac.")
                || domain.endsWith("canada.ca")
                || domain.endsWith("gc.ca");
    }

    //机构页判
    private boolean pageLooksInstitutional(String text) {
        // return containsAny(text.toLowerCase(Locale.ROOT), INSTITUTION_SIGNAL_KEYWORDS);
        // #NEW CODE#
        return containsAny(text.toLowerCase(Locale.ROOT), ALL_INSTITUTION_SIGNAL_KEYWORDS);
    }

    private boolean domainMatchesQueryIdentity(String domain, List<String> queryTokens) {
        String rootDomain = rootDomain(domain);
        if (!StringUtils.hasText(rootDomain)) {
            return false;
        }
        for (String token : queryTokens) {
            if (token.length() >= 2 && rootDomain.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPreferredDomain(String domain, List<String> preferredDomains) {
        for (String preferredDomain : preferredDomains) {
            if (domain.equals(preferredDomain) || domain.endsWith("." + preferredDomain)) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeDomains(List<String> domains) {
        LinkedHashSet<String> normalizedDomains = new LinkedHashSet<>();
        if (domains == null) {
            return List.of();
        }
        for (String domainEntry : domains) {
            if (!StringUtils.hasText(domainEntry)) {
                continue;
            }
            for (String domainPart : domainEntry.split(",")) {
                String normalizedDomain = normalizeDomain(domainPart);
                if (StringUtils.hasText(normalizedDomain)) {
                    normalizedDomains.add(normalizedDomain);
                }
            }
        }
        return new ArrayList<>(normalizedDomains);
    }

    private List<String> extractDomainsFromQuery(String query) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        Matcher matcher = DOMAIN_PATTERN.matcher(query);
        while (matcher.find()) {
            String normalizedDomain = normalizeDomain(matcher.group(1));
            if (StringUtils.hasText(normalizedDomain) && normalizedDomain.contains(".")) {
                domains.add(normalizedDomain);
            }
        }
        return new ArrayList<>(domains);
    }

    private String normalizeDomain(String rawDomain) {
        if (!StringUtils.hasText(rawDomain)) {
            return "";
        }

        String trimmed = rawDomain.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed.replaceFirst("^site:", "");
        }
        try {
            String host = URI.create(trimmed).getHost();
            if (!StringUtils.hasText(host)) {
                return "";
            }
            return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return rawDomain.trim().toLowerCase(Locale.ROOT).replaceFirst("^site:", "").replaceFirst("^www\\.", "");
        }
    }

    private List<String> mergeDomains(List<String> firstDomains, List<String> secondDomains) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(firstDomains);
        merged.addAll(secondDomains);
        return new ArrayList<>(merged);
    }

    // Keep merged keyword sets deterministic and reusable across ranking, verification, and query planning.
    private static Set<String> mergeKeywordSets(Set<String>... keywordGroups) {
        LinkedHashSet<String> mergedKeywords = new LinkedHashSet<>();
        for (Set<String> keywordGroup : keywordGroups) {
            mergedKeywords.addAll(keywordGroup);
        }
        return Set.copyOf(mergedKeywords);
    }

    private List<String> extractRankingTokens(String query) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String loweredQuery = query.toLowerCase(Locale.ROOT);

        for (String part : loweredQuery.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            // if (part.length() < 2 || ENGLISH_STOPWORDS.contains(part) || part.matches("20\\d{2}") || part.matches("\\d+")) {
            // #NEW CODE#
            if (part.length() < 2 || ALL_ENGLISH_STOPWORDS.contains(part) || part.matches("20\\d{2}") || part.matches("\\d+")) {
                continue;
            }
            tokens.add(part);
        }

        Matcher cjkMatcher = Pattern.compile("[\\p{IsHan}]{2,}").matcher(query);
        while (cjkMatcher.find()) {
            tokens.add(cjkMatcher.group());
        }

        return new ArrayList<>(tokens);
    }

    private List<String> extractDateHints(String text) {
        LinkedHashSet<String> dateHints = new LinkedHashSet<>();
        Matcher matcher = DATE_PATTERN.matcher(normalizeWhitespace(text));
        while (matcher.find() && dateHints.size() < 5) {
            dateHints.add(matcher.group());
        }
        return new ArrayList<>(dateHints);
    }

    private boolean hasDateHint(String value) {
        return StringUtils.hasText(value) && DATE_PATTERN.matcher(value).find();
    }

    private boolean containsLatestKeyword(String loweredQuery) {
        // return loweredQuery.contains("latest") || loweredQuery.contains("today") || loweredQuery.contains("recent");
        // #NEW CODE#
        return containsAny(loweredQuery, ALL_RECENCY_KEYWORDS);
    }

    private static boolean matchesTopStylePattern(String value) {
        return StringUtils.hasText(value) && TOP_STYLE_PATTERN.matcher(value).find();
    }

    private static int countNumericEvidence(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        int count = 0;
        Matcher matcher = NUMERIC_EVIDENCE_PATTERN.matcher(value);
        while (matcher.find() && count < 8) {
            count++;
        }
        return count;
    }

    private boolean isRelativeTimeRange(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Set.of("day", "week", "month", "year", "d", "w", "m", "y").contains(normalized);
    }

    private DateRange parseDateRange(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = YEAR_MONTH_HYPHEN_PATTERN.matcher(text);
        if (matcher.find()) {
            return fromYearMonth(matcher.group(1), matcher.group(2));
        }

        matcher = YEAR_MONTH_CHINESE_PATTERN.matcher(text);
        if (matcher.find()) {
            return fromYearMonth(matcher.group(1), matcher.group(2));
        }

        matcher = MONTH_NAME_YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            return fromYearMonth(matcher.group(2), String.valueOf(MONTH_NAME_TO_NUMBER.get(matcher.group(1).toLowerCase(Locale.ROOT))));
        }

        matcher = YEAR_MONTH_NAME_PATTERN.matcher(text);
        if (matcher.find()) {
            return fromYearMonth(matcher.group(1), String.valueOf(MONTH_NAME_TO_NUMBER.get(matcher.group(2).toLowerCase(Locale.ROOT))));
        }

        matcher = YEAR_ONLY_PATTERN.matcher(text);
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            return new DateRange(
                    LocalDate.of(year, 1, 1).toString(),
                    LocalDate.of(year, 12, 31).toString()
            );
        }

        return null;
    }

    private DateRange fromYearMonth(String yearPart, String monthPart) {
        int year = Integer.parseInt(yearPart);
        int month = Integer.parseInt(monthPart);
        YearMonth yearMonth = YearMonth.of(year, month);
        return new DateRange(
                yearMonth.atDay(1).toString(),
                yearMonth.atEndOfMonth().toString()
        );
    }

    // private boolean containsAny(String text, Set<String> keywords) {
    // #NEW CODE#
    private static boolean containsAny(String text, Set<String> keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            String loweredKeyword = keyword.toLowerCase(Locale.ROOT);
            if (loweredKeyword.chars().allMatch(character -> Character.isLetterOrDigit(character) || Character.isWhitespace(character))) {
                if (Pattern.compile("\\b" + Pattern.quote(loweredKeyword) + "\\b").matcher(lowered).find()) {
                    return true;
                }
            } else if (lowered.contains(loweredKeyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAcronymToken(String query) {
        return Pattern.compile("\\b[A-Z]{2,6}\\b").matcher(query).find();
    }

    private boolean containsCjk(String value) {
        return StringUtils.hasText(value) && Pattern.compile("[\\p{IsHan}]").matcher(value).find();
    }

    private String appendIfMissing(String base, String addition) {
        if (!StringUtils.hasText(addition)) {
            return base;
        }
        if (base.toLowerCase(Locale.ROOT).contains(addition.toLowerCase(Locale.ROOT))) {
            return base;
        }
        return normalizeWhitespace(base + " " + addition);
    }

    private String rootDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return "";
        }
        String[] parts = domain.split("\\.");
        return parts.length == 0 ? domain : parts[0];
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit - 3) + "...";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double roundScore(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    private record SearchSignals(
            boolean eventLike,
            boolean newsLike,
            boolean financeLike,
            boolean institutionLike,
            boolean timeSensitive,
            boolean cjkQuery,
            boolean dataIntensive,
            boolean rankingLike,
            boolean performanceLike
    ) {
    }

    private record DateRange(String startDate, String endDate) {
    }

    private record TimeConstraint(
            String publishTimeRange,
            String publishStartDate,
            String publishEndDate,
            String queryHint,
            List<String> requestedDateTokens
    ) {
    }

    private record SearchOptions(
            String originalQuery,
            String officialQuery,
            String broadQuery,
            List<String> preferredDomains,
            List<String> excludedDomains,
            TimeConstraint timeConstraint,
            SearchSignals signals,
            boolean officialFirst,
            boolean needVerification,
            int maxResults,
            List<String> queryTokens
    ) {
    }

    private record SearchRoundRequest(
            String round,
            String query,
            String topic,
            String searchDepth,
            int maxResults,
            List<String> includeDomains,
            List<String> excludeDomains,
            String publishTimeRange,
            String publishStartDate,
            String publishEndDate
    ) {
    }

    private record SearchResponse(
            String query,
            int roundsUsed,
            boolean officialFirst,
            boolean needVerification,
            String topic,
            String resolvedTimeHint,
            SearchEvidenceSummary evidenceSummary,
            List<SearchCandidate> results
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> strategy = new LinkedHashMap<>();
            strategy.put("officialFirst", officialFirst);
            strategy.put("needVerification", needVerification);
            strategy.put("roundsUsed", roundsUsed);
            strategy.put("topic", topic);
            strategy.put("resolvedTimeHint", resolvedTimeHint);
            strategy.putAll(evidenceSummary.toMap());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", query);
            response.put("strategy", strategy);
            response.put("results", results.stream().map(SearchCandidate::toMap).toList());
            return response;
        }
    }

    private record SearchEvidenceSummary(
            boolean dataIntensive,
            int authoritativeResultCount,
            int verifiedResultCount,
            int directAnswerCount,
            int thresholdQualifiedCount,
            boolean evidenceThresholdMet,
            String evidenceThresholdReason
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("dataIntensive", dataIntensive);
            map.put("authoritativeResultCount", authoritativeResultCount);
            map.put("verifiedResultCount", verifiedResultCount);
            map.put("directAnswerCount", directAnswerCount);
            map.put("thresholdQualifiedCount", thresholdQualifiedCount);
            map.put("evidenceThresholdMet", evidenceThresholdMet);
            map.put("evidenceThresholdReason", evidenceThresholdReason);
            return map;
        }
    }

    private static final class SearchCandidate {
        private final String title;
        private final String url;
        private final String normalizedUrl;
        private final String domain;
        private final String content;
        private final String round;
        private final double rawScore;
        private final LinkedHashSet<String> matchedDateHints = new LinkedHashSet<>();
        private String sourceType = "third_party";
        private double score = 0.0d;
        private String verificationStatus = "not_requested";
        private String verificationSnippet = "";

        SearchCandidate(String title, String url, String content, String round, double rawScore) {
            this.title = title;
            this.url = url;
            this.normalizedUrl = normalizeUrl(url);
            this.domain = normalizeHost(url);
            this.content = content;
            this.round = round;
            this.rawScore = rawScore;
        }

        SearchCandidate copy() {
            SearchCandidate copy = new SearchCandidate(title, url, content, round, rawScore);
            copy.matchedDateHints.addAll(matchedDateHints);
            copy.sourceType = sourceType;
            copy.score = score;
            copy.verificationStatus = verificationStatus;
            copy.verificationSnippet = verificationSnippet;
            return copy;
        }

        void mergeFrom(SearchCandidate other) {
            matchedDateHints.addAll(other.matchedDateHints);
            if (sourcePriority(other.sourceType) > sourcePriority(this.sourceType)) {
                this.sourceType = other.sourceType;
            }
            if ("verified".equals(other.verificationStatus) || "content_found".equals(other.verificationStatus)) {
                this.verificationStatus = other.verificationStatus;
                this.verificationSnippet = other.verificationSnippet;
            }
            this.score = Math.max(this.score, other.score);
        }

        void addDateHints(List<String> dateHints) {
            matchedDateHints.addAll(dateHints);
        }

        void applyVerification(WebScrapingTool.ScrapeResult scrapeResult, SearchOptions options) {
            addDateHints(scrapeResult.matchedDateHints());
            verificationSnippet = scrapeResult.verificationSnippet();

            if ("fetch_error".equals(scrapeResult.verificationStatus())) {
                verificationStatus = "fetch_error";
                return;
            }
            if (!StringUtils.hasText(scrapeResult.summary())) {
                verificationStatus = "insufficient_content";
                return;
            }

            boolean entityMatched = matchesQueryIdentity(scrapeResult, options.queryTokens()) || isAuthoritative();
            // boolean eventMatched = !options.signals().eventLike()
            //         || containsAny((scrapeResult.title() + " " + scrapeResult.summary()).toLowerCase(Locale.ROOT), EVENT_SIGNAL_KEYWORDS);
            // #NEW CODE#
            boolean eventMatched = !options.signals().eventLike()
                    || containsAny((scrapeResult.title() + " " + scrapeResult.summary()).toLowerCase(Locale.ROOT), ALL_EVENT_SIGNAL_KEYWORDS);
            boolean dateMatched = !options.signals().timeSensitive()
                    || matchedDateHints.isEmpty()
                    || matchesRequestedTime(options.timeConstraint().requestedDateTokens());

            if (entityMatched && eventMatched && dateMatched) {
                verificationStatus = "verified";
            } else if (entityMatched) {
                verificationStatus = "content_found";
            } else {
                verificationStatus = "weak_match";
            }
        }

        boolean needsVerification() {
            return "not_requested".equals(verificationStatus);
        }

        boolean isAuthoritative() {
            return "official".equals(sourceType) || "authoritative".equals(sourceType);
        }

        boolean isDirectAnswer(SearchOptions options) {
            if (options.signals().eventLike() || options.signals().timeSensitive()) {
                return "verified".equals(verificationStatus)
                        && !matchedDateHints.isEmpty()
                        // && containsAny((title + " " + content + " " + verificationSnippet).toLowerCase(Locale.ROOT), EVENT_SIGNAL_KEYWORDS);
                        // #NEW CODE#
                        && containsAny((title + " " + content + " " + verificationSnippet).toLowerCase(Locale.ROOT), ALL_EVENT_SIGNAL_KEYWORDS);
            }
            return isAuthoritative() && ("verified".equals(verificationStatus) || "content_found".equals(verificationStatus));
        }

        boolean meetsEvidenceThreshold(SearchOptions options) {
            if (!isDirectAnswer(options)) {
                return false;
            }
            if (!options.signals().dataIntensive()) {
                return true;
            }

            String combinedText = (title + " " + content + " " + verificationSnippet).toLowerCase(Locale.ROOT);
            boolean rankingMatched = !options.signals().rankingLike()
                    || containsAny(combinedText, RANKING_INTENT_KEYWORDS)
                    || matchesTopStylePattern(combinedText);
            boolean performanceMatched = !options.signals().performanceLike()
                    || containsAny(combinedText, PERFORMANCE_INTENT_KEYWORDS);
            boolean quantitativeMatched = containsAny(combinedText, QUANTITATIVE_INTENT_KEYWORDS)
                    || countNumericEvidence(combinedText) > 0;

            return rankingMatched && performanceMatched && quantitativeMatched;
        }

        boolean matchesRequestedTime(List<String> requestedDateTokens) {
            if (requestedDateTokens == null || requestedDateTokens.isEmpty()) {
                return !matchedDateHints.isEmpty();
            }
            for (String requestedDateToken : requestedDateTokens) {
                String requestedFingerprint = normalizeDateFingerprint(requestedDateToken);
                for (String matchedDateHint : matchedDateHints) {
                    String loweredRequested = requestedDateToken.toLowerCase(Locale.ROOT);
                    String loweredMatched = matchedDateHint.toLowerCase(Locale.ROOT);
                    if (loweredMatched.contains(loweredRequested) || loweredRequested.contains(loweredMatched)) {
                        return true;
                    }
                    String matchedFingerprint = normalizeDateFingerprint(matchedDateHint);
                    if (StringUtils.hasText(requestedFingerprint) && requestedFingerprint.equals(matchedFingerprint)) {
                        return true;
                    }
                    if (StringUtils.hasText(requestedFingerprint)
                            && StringUtils.hasText(matchedFingerprint)
                            && (requestedFingerprint.startsWith(matchedFingerprint) || matchedFingerprint.startsWith(requestedFingerprint))) {
                        return true;
                    }
                }
            }
            return false;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("title", title);
            map.put("url", url);
            map.put("content", content);
            map.put("sourceType", sourceType);
            map.put("score", score);
            map.put("matchedDateHints", new ArrayList<>(matchedDateHints));
            map.put("verificationStatus", verificationStatus);
            map.put("verificationSnippet", verificationSnippet);
            return map;
        }

        String title() {
            return title;
        }

        String url() {
            return url;
        }

        String normalizedUrl() {
            return normalizedUrl;
        }

        String domain() {
            return domain;
        }

        String content() {
            return content;
        }

        String round() {
            return round;
        }

        double rawScore() {
            return rawScore;
        }

        List<String> matchedDateHints() {
            return new ArrayList<>(matchedDateHints);
        }

        String sourceType() {
            return sourceType;
        }

        void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        double score() {
            return score;
        }

        void setScore(double score) {
            this.score = score;
        }

        String verificationStatus() {
            return verificationStatus;
        }

        String verificationSnippet() {
            return verificationSnippet;
        }

        private boolean matchesQueryIdentity(WebScrapingTool.ScrapeResult scrapeResult, List<String> queryTokens) {
            String combinedText = (scrapeResult.title() + " " + scrapeResult.summary() + " " + url).toLowerCase(Locale.ROOT);
            int matchCount = 0;
            for (String queryToken : queryTokens) {
                String normalizedToken = queryToken.toLowerCase(Locale.ROOT);
                if (combinedText.contains(normalizedToken)) {
                    matchCount++;
                }
                if (matchCount >= 2) {
                    return true;
                }
            }
            return matchCount > 0;
        }

        private static int sourcePriority(String sourceType) {
            return switch (sourceType) {
                case "official" -> 3;
                case "authoritative" -> 2;
                default -> 1;
            };
        }

        private static String normalizeUrl(String url) {
            try {
                URI uri = URI.create(url);
                String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                String path = uri.getPath() == null ? "" : uri.getPath();
                return scheme + "://" + host + path;
            } catch (Exception ignored) {
                return url;
            }
        }

        private static String normalizeHost(String url) {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            } catch (Exception ignored) {
                return "";
            }
        }

        private static String normalizeDateFingerprint(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }

            Matcher matcher = YEAR_MONTH_HYPHEN_PATTERN.matcher(value);
            if (matcher.find()) {
                return matcher.group(1) + "-" + String.format("%02d", Integer.parseInt(matcher.group(2)));
            }

            matcher = YEAR_MONTH_CHINESE_PATTERN.matcher(value);
            if (matcher.find()) {
                return matcher.group(1) + "-" + String.format("%02d", Integer.parseInt(matcher.group(2)));
            }

            matcher = MONTH_NAME_YEAR_PATTERN.matcher(value);
            if (matcher.find()) {
                Integer monthNumber = MONTH_NAME_TO_NUMBER.get(matcher.group(1).toLowerCase(Locale.ROOT));
                if (monthNumber != null) {
                    return matcher.group(2) + "-" + String.format("%02d", monthNumber);
                }
            }

            matcher = YEAR_MONTH_NAME_PATTERN.matcher(value);
            if (matcher.find()) {
                Integer monthNumber = MONTH_NAME_TO_NUMBER.get(matcher.group(2).toLowerCase(Locale.ROOT));
                if (monthNumber != null) {
                    return matcher.group(1) + "-" + String.format("%02d", monthNumber);
                }
            }

            matcher = YEAR_ONLY_PATTERN.matcher(value);
            if (matcher.find()) {
                return matcher.group(1);
            }

            return "";
        }
    }

    @FunctionalInterface
    interface WebSearchHttpClient {
        String post(String url, Map<String, String> headers, String requestBody);
    }
}
