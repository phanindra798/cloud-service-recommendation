import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import "./App.css";
import { getAuth } from "./lib/auth";
import AppShell from "./components/AppShell";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";

function ProtectedRoute({ children }) {
  return getAuth() ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login"    element={<AppShell><LoginPage /></AppShell>} />
        <Route path="/register" element={<AppShell><RegisterPage /></AppShell>} />
        <Route path="/" element={
          <ProtectedRoute>
            <AppShell><HomePage /></AppShell>
          </ProtectedRoute>
        } />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
