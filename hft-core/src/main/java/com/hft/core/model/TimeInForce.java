package com.hft.core.model;

/**
 * Time in force specifies how long an order remains active.
 */
public enum TimeInForce {
    /** Day order - expires at end of trading day */
    DAY,

    /** Good till cancelled - remains until filled or cancelled */
    GTC,

    /** Immediate or cancel - fill immediately or cancel */
    IOC,

    /** Fill or kill - fill entirely immediately or cancel */
    FOK,

    /** Good till date - remains until specified date */
    GTD,

    /** At the open - execute at market open */
    OPG,

    /** At the close - execute at market close */
    CLS
}
