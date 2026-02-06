import '@testing-library/jest-dom';

// Mock fetch globally for tests
global.fetch = vi.fn();

// Mock ResizeObserver (not available in jsdom)
global.ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
} as unknown as typeof ResizeObserver;

// Reset mocks before each test
beforeEach(() => {
  vi.resetAllMocks();
});
