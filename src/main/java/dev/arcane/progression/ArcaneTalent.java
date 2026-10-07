package dev.arcane.progression;

import java.util.List;

/**
 * The talent tree, bought with spell mastery points. Three branches of three ranks each, priced 1, 2 and 3 points; each
 * branch, once complete, opens its capstone; and the three capstones together open Archmage at the crown. Only ever
 * appended to: the order is the network id.
 */
public enum ArcaneTalent {
    /** +20 max mana per rank. */
    RESERVOIR(3, 0),
    /** +0.5 mana per second per rank. */
    RECOVERY(3, 0),
    /** New cooldowns 5% shorter per rank. */
    FOCUS(3, 0),
    /** Every spell costs 5% less mana. */
    WELLSPRING(1, 4, RESERVOIR),
    /** A tenth of every spell's mana comes back. */
    ECHO(1, 4, RECOVERY),
    /** Astral spells' new cooldowns are 10% shorter. */
    TEMPO(1, 4, FOCUS),
    /** Affinity XP comes twice as fast. */
    ARCHMAGE(1, 6, WELLSPRING, ECHO, TEMPO);

    private final int maxRank;
    private final int price;
    private final List<ArcaneTalent> requires;

    /** {@code price} 0 means each rank costs one more than the last: 1, 2, 3. */
    ArcaneTalent(int maxRank, int price, ArcaneTalent... requires) {
        this.maxRank = maxRank;
        this.price = price;
        this.requires = List.of(requires);
    }

    public int maxRank() {
        return maxRank;
    }

    public int costForNextRank(int currentRank) {
        return price > 0 ? price : currentRank + 1;
    }

    /** Points spent on the first {@code rank} ranks. */
    public int totalCost(int rank) {
        int total = 0;
        for (int i = 0; i < rank; i++) total += costForNextRank(i);
        return total;
    }

    /** The talents that must be complete, every rank, before this one can be learned. */
    public List<ArcaneTalent> requires() {
        return requires;
    }
}
