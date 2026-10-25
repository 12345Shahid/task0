package com.rcl.androidstreamcontrol.simulation;

import java.util.Random;

/**
 * Defines authentic behavioral archetypes and per-session dynamics for portfolio visitors.
 * Prevents robotic uniformity by generating realistic variations in dwell times,
 * scroll velocities, reading speeds, and interaction targets.
 */
public class BehaviorProfile {

    public enum Archetype {
        FAST_SKIMMER("Fast Skimmer"),
        SLOW_READER("Slow Reader"),
        LINK_CLICKER("Recruiter / Client"),
        PASSIVE_BROWSER("Casual Browser");

        private final String displayName;

        Archetype(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final Random RANDOM = new Random();

    public final Archetype archetype;
    public final int heroHesitationSeconds;
    public final int scrollStepPx;
    public final long pauseBetweenScrollsMs;
    public final long paragraphReadingDwellMs;
    public final int targetScrollDepthPercent;
    public final int targetPagesCount;
    public final boolean shouldBounce;
    public final boolean shouldClickProjectLink;
    public final int externalTabStaySeconds;
    public final int postReturnBrowseSeconds;
    public final int totalTargetSessionDurationSeconds;
    public final int allocatedClicks;
    public final boolean isReturningVisitor;
    public final TrafficSourceProfile trafficSource;

    public BehaviorProfile(
            Archetype archetype,
            int heroHesitationSeconds,
            int scrollStepPx,
            long pauseBetweenScrollsMs,
            long paragraphReadingDwellMs,
            int targetScrollDepthPercent,
            int targetPagesCount,
            boolean shouldBounce,
            boolean shouldClickProjectLink,
            int externalTabStaySeconds,
            int postReturnBrowseSeconds,
            int totalTargetSessionDurationSeconds,
            int allocatedClicks,
            boolean isReturningVisitor,
            TrafficSourceProfile trafficSource
    ) {
        this.archetype = archetype;
        this.heroHesitationSeconds = heroHesitationSeconds;
        this.scrollStepPx = scrollStepPx;
        this.pauseBetweenScrollsMs = pauseBetweenScrollsMs;
        this.paragraphReadingDwellMs = paragraphReadingDwellMs;
        this.targetScrollDepthPercent = targetScrollDepthPercent;
        this.targetPagesCount = targetPagesCount;
        this.shouldBounce = shouldBounce;
        this.shouldClickProjectLink = shouldClickProjectLink;
        this.externalTabStaySeconds = externalTabStaySeconds;
        this.postReturnBrowseSeconds = postReturnBrowseSeconds;
        this.totalTargetSessionDurationSeconds = totalTargetSessionDurationSeconds;
        this.allocatedClicks = allocatedClicks;
        this.isReturningVisitor = isReturningVisitor;
        this.trafficSource = trafficSource != null ? trafficSource : TrafficSourceProfile.generateRandom(isReturningVisitor);
    }

    public BehaviorProfile(
            Archetype archetype,
            int heroHesitationSeconds,
            int scrollStepPx,
            long pauseBetweenScrollsMs,
            long paragraphReadingDwellMs,
            int targetScrollDepthPercent,
            int targetPagesCount,
            boolean shouldBounce,
            boolean shouldClickProjectLink,
            int externalTabStaySeconds,
            int postReturnBrowseSeconds,
            int totalTargetSessionDurationSeconds,
            int allocatedClicks,
            boolean isReturningVisitor
    ) {
        this(archetype, heroHesitationSeconds, scrollStepPx, pauseBetweenScrollsMs, paragraphReadingDwellMs,
                targetScrollDepthPercent, targetPagesCount, shouldBounce, shouldClickProjectLink,
                externalTabStaySeconds, postReturnBrowseSeconds, totalTargetSessionDurationSeconds,
                allocatedClicks, isReturningVisitor, TrafficSourceProfile.generateRandom(isReturningVisitor));
    }

    /**
     * Generates an authentic behavioral profile based on population statistics:
     * - 30-50% Bounce Rate
     * - 15-20% External Project Click Rate
     * - 60-120s Average Full Session
     * - 30-45s Returning Visitor Session
     */
    public static BehaviorProfile createRandom(boolean isReturningVisitor, int allocatedClicks) {
        Archetype archetype;

        if (allocatedClicks > 0) {
            // Visitors with allocated clicks are weighted toward Recruiter / Slow Reader
            archetype = (RANDOM.nextInt(100) < 65) ? Archetype.LINK_CLICKER : Archetype.SLOW_READER;
        } else {
            int roll = RANDOM.nextInt(100);
            if (roll < 35) {
                archetype = Archetype.FAST_SKIMMER;
            } else if (roll < 70) {
                archetype = Archetype.PASSIVE_BROWSER;
            } else {
                archetype = Archetype.SLOW_READER;
            }
        }

        TrafficSourceProfile trafficSource = TrafficSourceProfile.generateRandom(isReturningVisitor);

        // Returning visitors have shorter, focused sessions (30-45s, 1-2 pages)
        if (isReturningVisitor) {
            int hesitation = 3 + RANDOM.nextInt(4); // 3-6s
            int scrollStep = 220 + RANDOM.nextInt(120); // 220-340px
            long pauseBetweenScrolls = 1500 + RANDOM.nextInt(1500); // 1.5-3.0s
            long readingDwell = 2500 + RANDOM.nextInt(2000); // 2.5-4.5s
            int scrollDepth = 45 + RANDOM.nextInt(25); // 45-70%
            int pages = (RANDOM.nextInt(100) < 60) ? 1 : 2;
            boolean bounce = (pages == 1);
            boolean clickProject = (allocatedClicks > 0);
            int extStay = 10 + RANDOM.nextInt(8); // 10-17s
            int postBrowse = 8 + RANDOM.nextInt(7); // 8-14s
            int sessionDuration = 30 + RANDOM.nextInt(16); // 30-45s

            return new BehaviorProfile(
                    archetype, hesitation, scrollStep, pauseBetweenScrolls, readingDwell,
                    scrollDepth, pages, bounce, clickProject, extStay, postBrowse,
                    sessionDuration, allocatedClicks, true, trafficSource
            );
        }

        // Fresh Visitor Configuration
        // Bounce rate: 35% chance of bouncing on the landing page if allocatedClicks == 0
        boolean bounce = (allocatedClicks == 0) && (RANDOM.nextInt(100) < 38);

        // Hesitation: 5-15 seconds on initial hero section
        int hesitation = 5 + RANDOM.nextInt(11);

        // Scroll step: 200-400px per swipe
        int scrollStep = 200 + RANDOM.nextInt(201);

        // Pause between scrolls: 2000-5000ms
        long pauseBetweenScrolls = 2000 + RANDOM.nextInt(3001);

        // Reading dwell per paragraph: 3000-8000ms
        long readingDwell = 3000 + RANDOM.nextInt(5001);

        // Scroll depth: 50-70%
        int scrollDepth = 50 + RANDOM.nextInt(21);

        // Pages per session: 1 if bounce, otherwise 2 to 4
        int pages = bounce ? 1 : (2 + RANDOM.nextInt(3));

        // External project link click: 100% if allocatedClicks > 0, otherwise ~15% for LinkClicker
        boolean clickProject = (allocatedClicks > 0) || (archetype == Archetype.LINK_CLICKER && RANDOM.nextInt(100) < 20);

        // External tab stay duration: 10-20 seconds
        int extStay = 10 + RANDOM.nextInt(11);

        // Post return browse: 10-15 seconds
        int postBrowse = 10 + RANDOM.nextInt(6);

        // Total session duration: 60-120 seconds (clamped to realistic window)
        int sessionDuration = bounce ? (35 + RANDOM.nextInt(25)) : (60 + RANDOM.nextInt(61));

        return new BehaviorProfile(
                archetype, hesitation, scrollStep, pauseBetweenScrolls, readingDwell,
                scrollDepth, pages, bounce, clickProject, extStay, postBrowse,
                sessionDuration, allocatedClicks, false, trafficSource
        );
    }
}
