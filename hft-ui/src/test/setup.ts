import '@testing-library/jest-dom';

// Mock fetch globally for tests
global.fetch = vi.fn();

// Reset mocks before each test
beforeEach(() => {
  vi.resetAllMocks();
});
