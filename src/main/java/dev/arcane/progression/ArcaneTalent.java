package dev.arcane.progression;

/** Small, permanent branches bought with spell mastery points. */
public enum ArcaneTalent {
    RESERVOIR, RECOVERY, FOCUS;

    public int maxRank() {
        return 3;
    }

    public int costForNextRank(int currentRank) {
        return currentRank + 1;
    }
}
