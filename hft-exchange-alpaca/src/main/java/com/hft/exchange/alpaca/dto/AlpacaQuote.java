package com.hft.exchange.alpaca.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for Alpaca quote (NBBO) API response.
 */
public class AlpacaQuote {
    private String ap; // ask price
    private int as;    // ask size
    private String ax; // ask exchange
    private String bp; // bid price
    private int bs;    // bid size
    private String bx; // bid exchange
    private List<String> c;  // condition flags (array in API response)
    private Instant t; // timestamp
    private String z;  // tape

    public String getAp() {
        return ap;
    }

    public void setAp(String ap) {
        this.ap = ap;
    }

    public int getAs() {
        return as;
    }

    public void setAs(int as) {
        this.as = as;
    }

    public String getAx() {
        return ax;
    }

    public void setAx(String ax) {
        this.ax = ax;
    }

    public String getBp() {
        return bp;
    }

    public void setBp(String bp) {
        this.bp = bp;
    }

    public int getBs() {
        return bs;
    }

    public void setBs(int bs) {
        this.bs = bs;
    }

    public String getBx() {
        return bx;
    }

    public void setBx(String bx) {
        this.bx = bx;
    }

    public List<String> getC() {
        return c;
    }

    public void setC(List<String> c) {
        this.c = c;
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
