// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';
import { TextEncoder, TextDecoder } from 'util';

// react-router v7 needs these; jsdom in CRA's Jest doesn't provide them.
Object.assign(global, { TextEncoder, TextDecoder });

// Mermaid is ESM-only and needs a real browser layout engine; tests use a stub.
jest.mock('mermaid', () => ({
  __esModule: true,
  default: { initialize: jest.fn(), render: jest.fn() },
}));

beforeEach(() => {
  localStorage.clear();
});
