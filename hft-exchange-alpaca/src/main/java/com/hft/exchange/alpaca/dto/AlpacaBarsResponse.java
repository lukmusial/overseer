package com.hft.exchange.alpaca.dto;

import java.util.List;

/**
 * DTO for Alpaca bars API response.
 * Alpaca returns: { "bars": [...], "next_page_token": "..." }
 */
public class AlpacaBarsResponse {
    private List<AlpacaBar> bars;
    private String nextPageToken;

    public List<AlpacaBar> getBars() {
        return bars;
    }

    public void setBars(List<AlpacaBar> bars) {
        this.bars = bars;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }
}
