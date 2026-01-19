package com.hft.exchange.alpaca.dto;

import java.time.Instant;

/**
 * DTO for Alpaca trade API response.
 */
public class AlpacaTrade {
    private String p;  // price
    private long s;    // size
    private String x;  // exchange
    private String c;  // condition flags
    private long i;    // trade ID
    private Instant t; // timestamp
    private String z;  // tape

    public String getP() {
        return p;
    }

    public void setP(String p) {
        this.p = p;
    }

    public long getS() {
        return s;
    }

    public void setS(long s) {
        this.s = s;
    }

    public String getX() {
        return x;
    }

    public void setX(String x) {
        this.x = x;
    }

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public long getI() {
        return i;
    }

    public void setI(long i) {
        this.i = i;
    }

    public Instant getT() {
        return t;
    }

    public void setT(Instant t) {
        this.t = t;
    }

    public String getZ() {
        return z;
    }

    public void setZ(String z) {
        this.z = z;
    }
}
