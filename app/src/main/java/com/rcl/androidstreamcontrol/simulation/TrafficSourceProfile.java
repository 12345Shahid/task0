package com.rcl.androidstreamcontrol.simulation;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Manages authentic traffic acquisition channels, referrer headers, and campaign UTM telemetry.
 * Prevents "100% Direct" detection red flags by producing an authentic blended traffic profile:
 * - 40% Social (Twitter/t.co, Reddit, LinkedIn)
 * - 25% Direct / Bookmarks
 * - 20% Search Engines (DuckDuckGo, Bing)
 * - 15% Industry Aggregators & Newsletters (HackerNews, Substack, Medium)
 */
public class TrafficSourceProfile {

    public enum ChannelType {
        SOCIAL_TWITTER("Twitter (t.co)"),
        SOCIAL_REDDIT("Reddit Community"),
        SOCIAL_LINKEDIN("LinkedIn Post"),
        SEARCH_DUCKDUCKGO("DuckDuckGo Organic"),
        SEARCH_BING("Bing Search"),
        AGGREGATOR_HACKERNEWS("Hacker News"),
        AGGREGATOR_NEWSLETTER("Substack Newsletter"),
        DIRECT_VISIT("Direct / Bookmarks");

        private final String displayName;

        ChannelType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final Random RANDOM = new Random();

    private static final String[] TWITTER_SLUGS = {
            "x8Km9L", "v3Np7Q", "k9Rt2W", "m4Pq8Y", "j2Xw6Z", "h7Bv4K"
    };

    private static final String[] REDDIT_SUBREDDITS = {
            "https://www.reddit.com/r/personalfinance/",
            "https://www.reddit.com/r/investing/",
            "https://www.reddit.com/r/CryptoCurrency/",
            "https://www.reddit.com/r/webdev/",
            "https://www.reddit.com/r/programming/"
    };

    private static final String[] SEARCH_QUERIES = {
            "developer+portfolio+showcase",
            "fullstack+engineer+crypto+projects",
            "fintech+developer+profile",
            "senior+software+architect+portfolio",
            "react+android+remote+control+system"
    };

    private static final String[] CAMPAIGN_NAMES = {
            "showcase_2026", "tech_profile", "dev_spotlight", "fin_architecture", "portfolio_review"
    };

    public final ChannelType channelType;
    public final String referrerUrl;
    public final String utmSource;
    public final String utmMedium;
    public final String utmCampaign;

    public TrafficSourceProfile(
            ChannelType channelType,
            String referrerUrl,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {
        this.channelType = channelType;
        this.referrerUrl = referrerUrl != null ? referrerUrl : "";
        this.utmSource = utmSource;
        this.utmMedium = utmMedium;
        this.utmCampaign = utmCampaign;
    }

    /**
     * Generates an authentic blended traffic source profile.
     * Returning visitors are weighted toward Direct/Bookmarks (70%).
     * Fresh visitors follow the 40/25/20/15 natural acquisition curve.
     */
    public static TrafficSourceProfile generateRandom(boolean isReturningVisitor) {
        if (isReturningVisitor) {
            // 70% Direct / Bookmarks for returning visits, 30% Social
            if (RANDOM.nextInt(100) < 70) {
                return new TrafficSourceProfile(ChannelType.DIRECT_VISIT, "", null, null, null);
            } else {
                String slug = TWITTER_SLUGS[RANDOM.nextInt(TWITTER_SLUGS.length)];
                String campaign = CAMPAIGN_NAMES[RANDOM.nextInt(CAMPAIGN_NAMES.length)];
                return new TrafficSourceProfile(
                        ChannelType.SOCIAL_TWITTER,
                        "https://t.co/" + slug,
                        "twitter", "social", campaign
                );
            }
        }

        int roll = RANDOM.nextInt(100);
        String campaign = CAMPAIGN_NAMES[RANDOM.nextInt(CAMPAIGN_NAMES.length)];

        // 1. Social (40% Total: Twitter 18%, Reddit 14%, LinkedIn 8%)
        if (roll < 18) {
            String slug = TWITTER_SLUGS[RANDOM.nextInt(TWITTER_SLUGS.length)];
            return new TrafficSourceProfile(
                    ChannelType.SOCIAL_TWITTER,
                    "https://t.co/" + slug,
                    "twitter", "social", campaign
            );
        } else if (roll < 32) {
            String sub = REDDIT_SUBREDDITS[RANDOM.nextInt(REDDIT_SUBREDDITS.length)];
            return new TrafficSourceProfile(
                    ChannelType.SOCIAL_REDDIT,
                    sub,
                    "reddit", "community", campaign
            );
        } else if (roll < 40) {
            return new TrafficSourceProfile(
                    ChannelType.SOCIAL_LINKEDIN,
                    "https://www.linkedin.com/",
                    "linkedin", "social", campaign
            );
        }

        // 2. Direct / Bookmarks (25%)
        if (roll < 65) {
            return new TrafficSourceProfile(ChannelType.DIRECT_VISIT, "", null, null, null);
        }

        // 3. Search Engines (20%: DuckDuckGo 12%, Bing 8%)
        if (roll < 77) {
            String query = SEARCH_QUERIES[RANDOM.nextInt(SEARCH_QUERIES.length)];
            return new TrafficSourceProfile(
                    ChannelType.SEARCH_DUCKDUCKGO,
                    "https://duckduckgo.com/?q=" + query,
                    "duckduckgo", "organic", null
            );
        } else if (roll < 85) {
            String query = SEARCH_QUERIES[RANDOM.nextInt(SEARCH_QUERIES.length)];
            return new TrafficSourceProfile(
                    ChannelType.SEARCH_BING,
                    "https://www.bing.com/search?q=" + query,
                    "bing", "organic", null
            );
        }

        // 4. Aggregators & Newsletters (15%: HackerNews 8%, Substack 7%)
        if (roll < 93) {
            return new TrafficSourceProfile(
                    ChannelType.AGGREGATOR_HACKERNEWS,
                    "https://news.ycombinator.com/",
                    "hackernews", "aggregator", campaign
            );
        } else {
            return new TrafficSourceProfile(
                    ChannelType.AGGREGATOR_NEWSLETTER,
                    "https://substack.com/",
                    "newsletter", "email", campaign
            );
        }
    }

    /**
     * Appends campaign UTM parameters cleanly to the target landing URL.
     */
    public String appendTrackingParameters(String baseTargetUrl) {
        if (baseTargetUrl == null) return "https://duckduckgo.com";
        if (utmSource == null || utmSource.isEmpty()) {
            return baseTargetUrl;
        }

        try {
            Uri parsed = Uri.parse(baseTargetUrl);
            Uri.Builder builder = parsed.buildUpon();
            builder.appendQueryParameter("utm_source", utmSource);
            if (utmMedium != null && !utmMedium.isEmpty()) {
                builder.appendQueryParameter("utm_medium", utmMedium);
            }
            if (utmCampaign != null && !utmCampaign.isEmpty()) {
                builder.appendQueryParameter("utm_campaign", utmCampaign);
            }
            return builder.build().toString();
        } catch (Exception e) {
            return baseTargetUrl;
        }
    }

    /**
     * Constructs authentic W3C Fetch Metadata headers matching genuine browser navigation physics.
     */
    public Map<String, String> getInitialHttpHeaders() {
        Map<String, String> headers = new HashMap<>();

        if (referrerUrl != null && !referrerUrl.isEmpty()) {
            headers.put("Referer", referrerUrl);
            headers.put("Sec-Fetch-Site", "cross-site");
        } else {
            headers.put("Sec-Fetch-Site", "none");
        }

        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Upgrade-Insecure-Requests", "1");

        return headers;
    }
}
