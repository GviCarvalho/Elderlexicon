package com.elderlexicon.mod.ligabis;

import java.util.UUID;

/** The people who own links and pay for them. */
public interface Owners {

    /** A link whose owner is offline is suspended: nothing is reflected and nothing is charged. */
    boolean isOnline(UUID owner);

    /**
     * Charges the owner. Returns false only when the owner has nothing left to pay with, which breaks
     * the link. Paying with the owner's own body counts as paid, even if it kills them.
     */
    boolean pay(UUID owner, double umu);
}
