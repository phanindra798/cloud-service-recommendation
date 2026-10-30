import { render, screen } from '@testing-library/react';
import App from './App';
import { signIn } from './test/utils';

test('redirects unauthenticated users to the login page', () => {
  render(<App />);
  expect(screen.getByText(/sign in to your/i)).toBeInTheDocument();
});

test('shows the recommendation page to signed-in users', () => {
  signIn('alice@example.com');
  global.fetch = jest.fn();
  render(<App />);
  expect(screen.getByRole('button', { name: /get recommendation/i })).toBeInTheDocument();
  expect(screen.getByText('alice@example.com')).toBeInTheDocument();
});
