package com.huntmaster;

import net.runelite.api.gameval.VarPlayerID;

final class BossRegistry
{
    private BossRegistry()
    {
        // Utility class
    }

    public static BossDetector[] createDetectors()
    {
        return new BossDetector[]
                {
                        // ==================================================
                        // PROVEN STANDARD NPC DETECTORS
                        // ==================================================

                        new BossDetector(
                                new BossDefinition(
                                        "Vardorvis",
                                        "vardorvis",
                                        "Your Vardorvis kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Brutus",
                                        "brutus",
                                        "Your Brutus kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Zulrah",
                                        "zulrah",
                                        "Your Zulrah kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        // ==================================================
                        // STANDARD NPC BOSSES
                        // ==================================================

                        new BossDetector(
                                new BossDefinition(
                                        "Duke Sucellus",
                                        "duke sucellus",
                                        "Your Duke Sucellus kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "The Leviathan",
                                        "leviathan",
                                        "Your Leviathan kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "The Whisperer",
                                        "whisperer",
                                        "Your Whisperer kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Vorkath",
                                        "vorkath",
                                        "Your Vorkath kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Phantom Muspah",
                                        "phantom muspah",
                                        "Your Phantom Muspah kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Phosani's Nightmare",
                                        "phosani's nightmare",
                                        "Your Phosani's Nightmare kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Amoxliatl",
                                        "amoxliatl",
                                        "Your Amoxliatl kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Obor",
                                        "obor",
                                        "Your Obor kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Bryophyta",
                                        "bryophyta",
                                        "Your Bryophyta kill count is:",
                                        10,
                                        "v1"
                                )
                        ),

// ==================================================
// COMPLETION-BASED ENCOUNTERS
// ==================================================

                        new BossDetector(
                                new BossDefinition(
                                        "The Gauntlet",
                                        "gauntlet",
                                        "Your Gauntlet completion count is:",
                                        10,
                                        "v1",
                                        BossDetectorType.COMPLETION,
                                        VarPlayerID.TOTAL_COMPLETED_GAUNTLET
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "The Corrupted Gauntlet",
                                        "corrupted gauntlet",
                                        "Your Corrupted Gauntlet completion count is:",
                                        10,
                                        "v1",
                                        BossDetectorType.COMPLETION,
                                        VarPlayerID.TOTAL_COMPLETED_GAUNTLET_HM
                                )
                        ),

// ==================================================
// ACTIVITY-BASED ENCOUNTERS
// ==================================================

                        new BossDetector(
                                new BossDefinition(
                                        "Wintertodt",
                                        "wintertodt",
                                        "Your subdued Wintertodt count is:",
                                        10,
                                        "v1",
                                        BossDetectorType.ACTIVITY,
                                        VarPlayerID.TOTAL_WINTERTODT_KILLS
                                )
                        ),

                        new BossDetector(
                                new BossDefinition(
                                        "Tempoross",
                                        "tempoross",
                                        "Your Tempoross kill count is:",
                                        10,
                                        "v1",
                                        BossDetectorType.ACTIVITY,
                                        VarPlayerID.TOTAL_TEMPOROSS_KILLS
                                )
                        )
                };
    }

    public static void validateDetectors(
            BossDetector[] detectors)
    {
        if (detectors == null)
        {
            throw new IllegalStateException(
                    "Huntmaster boss detector registry is null"
            );
        }

        for (int i = 0; i < detectors.length; i++)
        {
            BossDetector detector = detectors[i];

            if (detector == null)
            {
                throw new IllegalStateException(
                        "Huntmaster boss detector at index "
                                + i
                                + " is null"
                );
            }

            validateRequiredField(
                    detector.getName(),
                    "boss name",
                    i
            );

            validateRequiredField(
                    detector.getProfileKey(),
                    "RuneLite profile key",
                    i
            );

            validateRequiredField(
                    detector.getKcMessagePrefix(),
                    "KC message prefix",
                    i
            );

            validateRequiredField(
                    detector.getDetectorVersion(),
                    "detector version",
                    i
            );

            if (detector.getPendingWindowTicks() <= 0 || detector.getPendingWindowTicks() > EncounterObservation.MAX_WINDOW_TICKS)
            {
                throw new IllegalStateException(
                        "Invalid pending window for "
                                + detector.getName()
                );
            }

            if (detector.getDetectorType() == null
                    || !detector.getDetectorVersion().matches("[A-Za-z0-9._-]{1,64}"))
            {
                throw new IllegalStateException("Invalid detector type or version for " + detector.getName());
            }

            if ((detector.getDetectorType()
                    == BossDetectorType.COMPLETION
                    || detector.getDetectorType()
                    == BossDetectorType.ACTIVITY)
                    && detector.getCompletionVarpId() == null)
            {
                throw new IllegalStateException(
                        detector.getDetectorType()
                                + " detector "
                                + detector.getName()
                                + " is missing a dedicated total-count varp"
                );
            }

            for (int j = i + 1; j < detectors.length; j++)
            {
                BossDetector other = detectors[j];

                if (other == null)
                {
                    continue;
                }

                if (detector.getName().equalsIgnoreCase(
                        other.getName()))
                {
                    throw new IllegalStateException(
                            "Duplicate Huntmaster boss name: "
                                    + detector.getName()
                    );
                }

                if (detector.getProfileKey().equalsIgnoreCase(
                        other.getProfileKey()))
                {
                    throw new IllegalStateException(
                            "Duplicate Huntmaster RuneLite profile key: "
                                    + detector.getProfileKey()
                    );
                }

                if (detector.getKcMessagePrefix().equalsIgnoreCase(
                        other.getKcMessagePrefix()))
                {
                    throw new IllegalStateException(
                            "Duplicate Huntmaster KC message prefix: "
                                    + detector.getKcMessagePrefix()
                    );
                }

                String prefix = detector.getKcMessagePrefix().toLowerCase(java.util.Locale.ROOT);
                String otherPrefix = other.getKcMessagePrefix().toLowerCase(java.util.Locale.ROOT);
                if (prefix.startsWith(otherPrefix) || otherPrefix.startsWith(prefix))
                {
                    throw new IllegalStateException("Overlapping Huntmaster KC prefixes for "
                            + detector.getName() + " and " + other.getName());
                }
            }
        }
    }

    private static void validateRequiredField(
            String value,
            String fieldName,
            int detectorIndex)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalStateException(
                    "Huntmaster detector at index "
                            + detectorIndex
                            + " has missing "
                            + fieldName
            );
        }
    }
}
