package com.hft.api.service;

import com.hft.api.config.ExchangeProperties;
import com.hft.api.dto.ExchangeStatusDto;
import com.hft.api.dto.SymbolDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeServiceTest {

    private ExchangeService exchangeService;
    private ExchangeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ExchangeProperties();

        // Configure Alpaca in stub mode
        ExchangeProperties.AlpacaProperties alpaca = new ExchangeProperties.AlpacaProperties();
        alpaca.setEnabled(true);
        alpaca.setMode("stub");
        alpaca.setApiKey("");
        alpaca.setSecretKey("");
        properties.setAlpaca(alpaca);

        // Configure Binance in stub mode
        ExchangeProperties.BinanceProperties binance = new ExchangeProperties.BinanceProperties();
        binance.setEnabled(true);
        binance.setMode("stub");
        binance.setApiKey("");
        binance.setSecretKey("");
        properties.setBinance(binance);

        exchangeService = new ExchangeService(properties);
        exchangeService.initialize();
    }

    @Test
    void getExchangeStatus_returnsAllExchanges() {
        List<ExchangeStatusDto> statuses = exchangeService.getExchangeStatus();

        assertEquals(2, statuses.size());

        boolean hasAlpaca = statuses.stream().anyMatch(s -> s.exchange().equals("ALPACA"));
        boolean hasBinance = statuses.stream().anyMatch(s -> s.exchange().equals("BINANCE"));

        assertTrue(hasAlpaca, "Should have ALPACA exchange");
        assertTrue(hasBinance, "Should have BINANCE exchange");
    }

    @Test
    void getExchangeStatus_alpacaInStubMode_isConnected() {
        ExchangeStatusDto alpaca = exchangeService.getExchangeStatus("ALPACA");

        assertNotNull(alpaca);
        assertEquals("ALPACA", alpaca.exchange());
        assertTrue(alpaca.name().contains("Stub"));
        assertEquals("stub", alpaca.mode());
        assertTrue(alpaca.connected());
        assertTrue(alpaca.authenticated());
        assertNull(alpaca.errorMessage());
    }

    @Test
    void getExchangeStatus_binanceInStubMode_isConnected() {
        ExchangeStatusDto binance = exchangeService.getExchangeStatus("BINANCE");

        assertNotNull(binance);
        assertEquals("BINANCE", binance.exchange());
        assertTrue(binance.name().contains("Stub"));
        assertEquals("stub", binance.mode());
        assertTrue(binance.connected());
        assertTrue(binance.authenticated());
        assertNull(binance.errorMessage());
    }

    @Test
    void getExchangeStatus_unknownExchange_returnsNull() {
        ExchangeStatusDto unknown = exchangeService.getExchangeStatus("UNKNOWN");
        assertNull(unknown);
    }

    @Test
    void getExchangeStatus_caseInsensitive() {
        ExchangeStatusDto alpaca = exchangeService.getExchangeStatus("alpaca");
        assertNotNull(alpaca);
        assertEquals("ALPACA", alpaca.exchange());
    }

    @Test
    void getSymbols_alpaca_returnsStubSymbols() {
        List<SymbolDto> symbols = exchangeService.getSymbols("ALPACA");

        assertFalse(symbols.isEmpty());
        assertTrue(symbols.stream().anyMatch(s -> s.symbol().equals("AAPL")));
        assertTrue(symbols.stream().anyMatch(s -> s.symbol().equals("GOOGL")));
        assertTrue(symbols.stream().anyMatch(s -> s.symbol().equals("MSFT")));

        // Verify equity properties
        SymbolDto aapl = symbols.stream()
                .filter(s -> s.symbol().equals("AAPL"))
                .findFirst()
                .orElseThrow();
        assertEquals("Apple Inc.", aapl.name());
        assertEquals("ALPACA", aapl.exchange());
        assertEquals("equity", aapl.assetClass());
        assertEquals("USD", aapl.quoteAsset());
        assertTrue(aapl.tradable());
    }

    @Test
    void getSymbols_binance_returnsStubSymbols() {
        List<SymbolDto> symbols = exchangeService.getSymbols("BINANCE");

        assertFalse(symbols.isEmpty());
        assertTrue(symbols.stream().anyMatch(s -> s.symbol().equals("BTCUSDT")));
        assertTrue(symbols.stream().anyMatch(s -> s.symbol().equals("ETHUSDT")));

        // Verify crypto properties
        SymbolDto btc = symbols.stream()
                .filter(s -> s.symbol().equals("BTCUSDT"))
                .findFirst()
                .orElseThrow();
        assertEquals("BTC/USDT", btc.name());
        assertEquals("BINANCE", btc.exchange());
        assertEquals("crypto", btc.assetClass());
        assertEquals("BTC", btc.baseAsset());
        assertEquals("USDT", btc.quoteAsset());
        assertTrue(btc.tradable());
    }

    @Test
    void getSymbols_unknownExchange_returnsEmptyList() {
        List<SymbolDto> symbols = exchangeService.getSymbols("UNKNOWN");
        assertTrue(symbols.isEmpty());
    }

    @Test
    void getSymbols_caseInsensitive() {
        List<SymbolDto> symbols = exchangeService.getSymbols("alpaca");
        assertFalse(symbols.isEmpty());
    }

    @Test
    void getSymbols_cachesResults() {
        // First call should populate cache
        List<SymbolDto> symbols1 = exchangeService.getSymbols("ALPACA");
        // Second call should use cache
        List<SymbolDto> symbols2 = exchangeService.getSymbols("ALPACA");

        assertSame(symbols1, symbols2, "Should return cached instance");
    }

    @Test
    void refreshSymbols_clearsCache() {
        // Populate cache
        List<SymbolDto> symbols1 = exchangeService.getSymbols("ALPACA");

        // Refresh should clear cache
        List<SymbolDto> symbols2 = exchangeService.refreshSymbols("ALPACA");

        // Should be a new list (not the same reference)
        assertNotSame(symbols1, symbols2, "Should return new instance after refresh");
        assertEquals(symbols1.size(), symbols2.size());
    }

    @Test
    void updateConnectionStatus_updatesExchange() {
        // Initial state - connected
        ExchangeStatusDto initial = exchangeService.getExchangeStatus("ALPACA");
        assertTrue(initial.connected());

        // Update to disconnected
        exchangeService.updateConnectionStatus("ALPACA", false, false, "Connection lost");

        ExchangeStatusDto updated = exchangeService.getExchangeStatus("ALPACA");
        assertFalse(updated.connected());
        assertFalse(updated.authenticated());
        assertEquals("Connection lost", updated.errorMessage());
    }

    @Test
    void disabledExchange_showsDisabledStatus() {
        // Create new service with disabled Alpaca
        ExchangeProperties disabledProps = new ExchangeProperties();

        ExchangeProperties.AlpacaProperties alpaca = new ExchangeProperties.AlpacaProperties();
        alpaca.setEnabled(false);
        disabledProps.setAlpaca(alpaca);

        ExchangeProperties.BinanceProperties binance = new ExchangeProperties.BinanceProperties();
        binance.setEnabled(true);
        binance.setMode("stub");
        disabledProps.setBinance(binance);

        ExchangeService service = new ExchangeService(disabledProps);
        service.initialize();

        ExchangeStatusDto alpacaStatus = service.getExchangeStatus("ALPACA");

        assertFalse(alpacaStatus.connected());
        assertFalse(alpacaStatus.authenticated());
        assertTrue(alpacaStatus.errorMessage().contains("Disabled"));
    }

    @Test
    void switchMode_alpacaStubToSandbox_reinitializes() {
        ExchangeStatusDto before = exchangeService.getExchangeStatus("ALPACA");
        assertEquals("stub", before.mode());

        ExchangeStatusDto after = exchangeService.switchMode("ALPACA", "sandbox");

        assertNotNull(after);
        assertEquals("ALPACA", after.exchange());
        assertEquals("sandbox", after.mode());
    }

    @Test
    void switchMode_binanceStubToTestnet_reinitializes() {
        ExchangeStatusDto before = exchangeService.getExchangeStatus("BINANCE");
        assertEquals("stub", before.mode());

        ExchangeStatusDto after = exchangeService.switchMode("BINANCE", "testnet");

        assertNotNull(after);
        assertEquals("BINANCE", after.exchange());
        assertEquals("testnet", after.mode());
    }

    @Test
    void switchMode_backToStub_restoresSymbols() {
        // Switch away from stub
        exchangeService.switchMode("ALPACA", "sandbox");

        // Switch back to stub
        exchangeService.switchMode("ALPACA", "stub");

        ExchangeStatusDto status = exchangeService.getExchangeStatus("ALPACA");
        assertEquals("stub", status.mode());
        assertTrue(status.connected());

        List<SymbolDto> symbols = exchangeService.getSymbols("ALPACA");
        assertFalse(symbols.isEmpty());
        assertTrue(symbols.stream().anyMatch(s -> s.symbol().equals("AAPL")));
    }

    @Test
    void switchMode_unknownExchange_returnsNull() {
        ExchangeStatusDto result = exchangeService.switchMode("UNKNOWN", "stub");
        assertNull(result);
    }

    @Test
    void switchMode_clearsCacheForExchange() {
        // Populate cache
        List<SymbolDto> before = exchangeService.getSymbols("ALPACA");
        assertFalse(before.isEmpty());

        // Switch mode - cache should be cleared
        exchangeService.switchMode("ALPACA", "sandbox");

        // After switching to sandbox (no API keys), fetching symbols returns stub fallback
        List<SymbolDto> after = exchangeService.getSymbols("ALPACA");
        assertNotSame(before, after, "Cache should have been cleared");
    }

    @Test
    void symbolDto_equityFactoryMethod_setsCorrectFields() {
        SymbolDto equity = SymbolDto.equity("TEST", "Test Company", "EXCHANGE", true, true, false);

        assertEquals("TEST", equity.symbol());
        assertEquals("Test Company", equity.name());
        assertEquals("EXCHANGE", equity.exchange());
        assertEquals("equity", equity.assetClass());
        assertEquals("TEST", equity.baseAsset());
        assertEquals("USD", equity.quoteAsset());
        assertTrue(equity.tradable());
        assertTrue(equity.marginable());
        assertFalse(equity.shortable());
    }

    @Test
    void symbolDto_cryptoFactoryMethod_setsCorrectFields() {
        SymbolDto crypto = SymbolDto.crypto("BTCETH", "BTC/ETH", "EXCHANGE", "BTC", "ETH", true);

        assertEquals("BTCETH", crypto.symbol());
        assertEquals("BTC/ETH", crypto.name());
        assertEquals("EXCHANGE", crypto.exchange());
        assertEquals("crypto", crypto.assetClass());
        assertEquals("BTC", crypto.baseAsset());
        assertEquals("ETH", crypto.quoteAsset());
        assertTrue(crypto.tradable());
        assertFalse(crypto.marginable());
        assertFalse(crypto.shortable());
    }
}
