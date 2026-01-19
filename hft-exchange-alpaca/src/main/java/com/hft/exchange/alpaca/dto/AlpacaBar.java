package com.hft.exchange.alpaca.dto;

import java.time.Instant;

/**
 * DTO for Alpaca bar (OHLCV) API response.
 */
public class AlpacaBar {
    private String o;  // open
    private String h;  // high
    private String l;  // low
    private String c;  // close
    private long v;    // volume
    private Instant t; // timestamp
    private long n;    // number of trades
    private String vw; // volume-weighted average price

    public String getO() {
        return o;
    }

    public void setO(String o) {
        this.o = o;
    }

    public String getH() {
        return h;
    }

    public void setH(String h) {
        this.h = h;
    }

    public String getL() {
        return l;
    }

    public void setL(String l) {
        this.l = l;
    }

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public long getV() {
        return v;
    }

    public void setV(long v) {
        this.v = v;
    }

    public Instant getT() {
        return t;
    }

    public void setT(Instant t) {
        this.t = t;
    }

    public long getN() {
        return n;
    }

    public void setN(long n) {
        this.n = n;
    }

    public String getVw() {
        return vw;
    }

    public void setVw(String vw) {
        this.vw = vw;
    }
}
