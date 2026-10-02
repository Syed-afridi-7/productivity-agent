package com.syedafridi.productivity_agent.services

enum class ContentVerdict {
    PRODUCTIVE,
    UNPRODUCTIVE,
    UNKNOWN
}

object ContentIntelligenceEngine {

    // ─── Productive Keywords (all lowercase) ───
    private val PRODUCTIVE_KEYWORDS = setOf(
        // Programming / CS
        "dsa", "algorithm", "data structure", "sorting", "binary tree", "linked list",
        "dynamic programming", "recursion", "graph", "bfs", "dfs", "stack", "queue",
        "heap", "hash", "array", "two pointer", "sliding window", "leetcode",
        "codeforces", "hackerrank", "codechef", "competitive programming",
        "flutter", "react", "angular", "vue", "next.js", "nextjs", "node.js", "nodejs",
        "express", "django", "fastapi", "spring boot", "python", "java", "kotlin",
        "javascript", "typescript", "c++", "cpp", "rust", "golang", "swift",
        "sql", "mongodb", "postgresql", "mysql", "redis", "docker", "kubernetes",
        "linux", "git", "github", "api", "rest", "graphql", "backend", "frontend",
        "fullstack", "full stack", "devops", "ci/cd", "aws", "azure", "gcp",
        "firebase", "supabase", "machine learning", "deep learning", "neural network",
        "tensorflow", "pytorch", "computer science", "operating system", "networking",
        "compiler", "html", "css", "tailwind", "android development", "ios development",
        "xcode", "gradle", "programming", "coding", "tutorial", "course", "lecture",
        "software engineering", "web development", "app development", "code review",
        "system design", "microservices", "database", "object oriented",
        // Academics
        "math", "physics", "chemistry", "biology", "science", "engineering",
        "calculus", "algebra", "geometry", "trigonometry", "statistics", "probability",
        "mechanics", "thermodynamics", "optics", "organic chemistry", "inorganic",
        "biochemistry", "genetics", "ecology", "history", "geography", "economics",
        "political science", "sociology", "psychology", "philosophy",
        "exam prep", "upsc", "jee", "neet", "gre", "gate", "cat", "ielts", "toefl",
        "sat", "board exam", "ncert", "cbse", "class 10", "class 11", "class 12",
        "professor", "university", "syllabus", "textbook", "revision", "formula",
        "theorem", "proof", "derivation", "numericals",
        // Career
        "interview prep", "interview question", "resume", "portfolio", "career",
        "placement", "internship", "freelancing", "startup", "business",
        "entrepreneurship", "finance", "stock market", "investing", "mutual fund",
        "personal finance", "budgeting", "taxation", "salary negotiation",
        "soft skills", "communication skills", "leadership", "management",
        "project management", "agile", "scrum", "product management",
        // Health
        "workout", "exercise", "gym", "yoga", "meditation", "mindfulness",
        "nutrition", "mental health", "stress management", "home workout",
        "stretching", "cardio", "strength training",
        // Language
        "english speaking", "vocabulary", "grammar", "pronunciation",
        "public speaking", "debate", "writing skills", "essay writing",
        "communication", "fluency",
        // News / Current Affairs
        "current affairs", "news analysis", "geopolitics", "world news",
        "budget", "parliament", "policy", "governance", "editorial",
        "international relations"
    )

    // ─── Unproductive Keywords (all lowercase) ───
    private val UNPRODUCTIVE_KEYWORDS = setOf(
        // Music, Songs & Audio Entertainment
        "music", "song", "songs", "audio", "track", "tracks", "official video",
        "official music video", "music video", "lyrics", "lyric video", "album",
        "full album", "single", "singer", "artist", "remix", "mashup", "cover song",
        "acoustic cover", "concert", "live performance", "dj", "dj mix", "beat",
        "beats", "lofi", "lo-fi", "chillhop", "pop music", "hip hop", "hip-hop",
        "rap", "r&b", "rock music", "metal", "kpop", "k-pop", "edm", "trap beat",
        "soundtrack", "ost", "bgm", "tune", "playlist", "radio", "band",
        "guitar cover", "piano cover", "bass boosted", "slowed and reverb",
        // Entertainment / Comedy / Casual Vlogs
        "funny", "prank", "comedy", "standup", "roast", "reaction video", "reaction",
        "unboxing", "haul", "vlog", "daily vlog", "travel vlog", "asmr",
        "mukbang", "entertainment", "drama", "gossip", "celebrity",
        "bollywood", "hollywood", "tollywood", "movie trailer", "teaser", "movie scene",
        "dance", "compilation", "fail compilation", "blooper", "top 10", "top 5",
        "tier list", "ranking", "best of", "worst of", "challenge",
        "tiktok", "meme", "memes", "satisfying", "oddly satisfying", "relaxing",
        "aesthetic", "story time", "grwm", "get ready with me",
        "dating", "relationship advice", "crush", "breakup", "zodiac",
        "horoscope", "astrology", "tarot", "gaming", "gameplay",
        "walkthrough", "playthrough", "lets play", "stream highlight",
        "fortnite", "minecraft", "gta", "valorant", "pubg", "free fire",
        "bgmi", "cricket highlights", "ipl", "football highlights", "wwe",
        "ufc", "boxing", "match highlights", "fantasy", "dream11",
        "luxury", "millionaire", "billionaire", "house tour", "room tour",
        "setup tour", "gadget review", "phone review", "unbox",
        "prank call", "social experiment"
    )

    // ─── Browser Package Names ───
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.UCMobile.intl",
        "com.sec.android.app.sbrowser",
        "com.mi.globalbrowser",
        "com.vivaldi.browser"
    )

    // ─── YouTube activities / views that indicate searching ───
    private val YOUTUBE_SEARCH_INDICATORS = setOf(
        "searchactivity",
        "searchresultactivity",
        "search_edit_text",
        "search"
    )

    /**
     * Classify content as PRODUCTIVE, UNPRODUCTIVE, or UNKNOWN.
     *
     * Scoring: count productive keyword matches vs unproductive matches.
     * - If productive > 0 and unproductive == 0 → PRODUCTIVE
     * - If unproductive > 0 and productive == 0 → UNPRODUCTIVE
     * - If both > 0 → whichever has more matches wins; ties → UNKNOWN
     * - If neither → UNKNOWN
     */
    fun classify(
        packageName: String,
        contentDescription: String?,
        className: String?,
        text: String?,
        hierarchyText: String? = null
    ): ContentVerdict {
        val classLower = className?.lowercase() ?: ""

        // YouTube Search Activity / Search views are treated as productive (user is looking for a topic)
        if (packageName == "com.google.android.youtube" &&
            (YOUTUBE_SEARCH_INDICATORS.any { classLower.contains(it) })) {
            return ContentVerdict.PRODUCTIVE
        }

        // Combine all available text signals into one searchable string
        val combined = buildString {
            contentDescription?.let { append(it.lowercase()).append(" ") }
            text?.let { append(it.lowercase()).append(" ") }
            className?.let { append(it.lowercase()).append(" ") }
            hierarchyText?.let { append(it.lowercase()).append(" ") }
        }.trim()

        if (combined.isEmpty()) return ContentVerdict.UNKNOWN

        val productiveHits = PRODUCTIVE_KEYWORDS.count { combined.contains(it) }
        val unproductiveHits = UNPRODUCTIVE_KEYWORDS.count { combined.contains(it) }

        return when {
            productiveHits > 0 && unproductiveHits == 0 -> ContentVerdict.PRODUCTIVE
            unproductiveHits > 0 && productiveHits == 0 -> ContentVerdict.UNPRODUCTIVE
            productiveHits > unproductiveHits -> ContentVerdict.PRODUCTIVE
            unproductiveHits > productiveHits -> ContentVerdict.UNPRODUCTIVE
            productiveHits > 0 -> ContentVerdict.UNKNOWN // tie
            else -> ContentVerdict.UNKNOWN
        }
    }

    /** Check if a package is a monitored browser. */
    fun isMonitoredBrowser(packageName: String?): Boolean {
        return packageName in BROWSER_PACKAGES
    }

    /** Check if a package needs content intelligence (YouTube or browser). */
    fun isContentMonitoredApp(packageName: String?): Boolean {
        return packageName == "com.google.android.youtube" || isMonitoredBrowser(packageName)
    }
}
