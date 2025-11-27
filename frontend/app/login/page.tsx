'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '../contexts/AuthContext';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [showMfa, setShowMfa] = useState(false);
  const [error, setError] = useState('');
  const { login, isLoading } = useAuth();
  const router = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!showMfa) {
      // First step: email/password
      if (!email || !password) {
        setError('Please enter email and password');
        return;
      }

      // Simulate MFA requirement for demo
      setShowMfa(true);
    } else {
      // Second step: MFA
      if (!mfaCode || mfaCode.length !== 6) {
        setError('Please enter a valid 6-digit MFA code');
        return;
      }

      const success = await login(email, password, mfaCode);
      if (success) {
        router.push('/dashboard');
      } else {
        setError('Invalid credentials or MFA code');
        setShowMfa(false);
      }
    }
  };

  const handleBackToLogin = () => {
    setShowMfa(false);
    setMfaCode('');
    setError('');
  };

  return (
    <div className="min-h-screen bg-sentinel-bg-primary flex items-center justify-center relative overflow-hidden">
      {/* Cyber-grid background */}
      <div className="absolute inset-0 opacity-10">
        <div className="absolute inset-0" style={{
          backgroundImage: `
            linear-gradient(rgba(60, 232, 255, 0.1) 1px, transparent 1px),
            linear-gradient(90deg, rgba(60, 232, 255, 0.1) 1px, transparent 1px)
          `,
          backgroundSize: '50px 50px'
        }} />
        <div className="absolute inset-0" style={{
          backgroundImage: `
            linear-gradient(rgba(60, 232, 255, 0.05) 1px, transparent 1px),
            linear-gradient(90deg, rgba(60, 232, 255, 0.05) 1px, transparent 1px)
          `,
          backgroundSize: '10px 10px'
        }} />
      </div>

      <div className="relative z-10 w-full max-w-md px-6">
        {/* SENTINEL Logo */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold tracking-wider text-sentinel-accent-primary mb-2">
            SENTINEL
          </h1>
          <p className="text-sentinel-text-secondary text-sm tracking-widest uppercase">
            RTGS Operations Center
          </p>
        </div>

        {/* Login Form */}
        <div className="bg-sentinel-bg-secondary border border-sentinel-border rounded-lg p-8">
          <div className="mb-6">
            <h2 className="text-xl font-semibold text-sentinel-text-primary mb-2">
              {showMfa ? 'Multi-Factor Authentication' : 'Command Center Access'}
            </h2>
            <p className="text-sentinel-text-muted text-sm">
              {showMfa
                ? 'Enter your 6-digit MFA code from your authenticator app'
                : 'Enter your credentials to access the operations dashboard'
              }
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-6">
            {!showMfa ? (
              <>
                {/* Email Field */}
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-sentinel-text-secondary mb-2">
                    Email Address
                  </label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full px-3 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded-md text-sentinel-text-primary placeholder-sentinel-text-muted focus:outline-none focus:ring-2 focus:ring-sentinel-accent-primary focus:border-transparent"
                    placeholder="operator@sentinel-rtgs.com"
                    required
                  />
                </div>

                {/* Password Field */}
                <div>
                  <label htmlFor="password" className="block text-sm font-medium text-sentinel-text-secondary mb-2">
                    Password
                  </label>
                  <input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full px-3 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded-md text-sentinel-text-primary placeholder-sentinel-text-muted focus:outline-none focus:ring-2 focus:ring-sentinel-accent-primary focus:border-transparent"
                    placeholder="Enter your password"
                    required
                  />
                </div>
              </>
            ) : (
              <>
                {/* MFA Code Field */}
                <div>
                  <label htmlFor="mfaCode" className="block text-sm font-medium text-sentinel-text-secondary mb-2">
                    MFA Code
                  </label>
                  <input
                    id="mfaCode"
                    type="text"
                    value={mfaCode}
                    onChange={(e) => {
                      const value = e.target.value.replace(/\D/g, '');
                      if (value.length <= 6) {
                        setMfaCode(value);
                      }
                    }}
                    className="w-full px-3 py-2 bg-sentinel-bg-tertiary border border-sentinel-border rounded-md text-sentinel-text-primary placeholder-sentinel-text-muted focus:outline-none focus:ring-2 focus:ring-sentinel-accent-primary focus:border-transparent text-center text-lg tracking-widest"
                    placeholder="000000"
                    maxLength={6}
                    required
                  />
                </div>

                <button
                  type="button"
                  onClick={handleBackToLogin}
                  className="text-sm text-sentinel-accent-primary hover:text-sentinel-accent-primary/80 transition-colors"
                >
                  ← Back to login
                </button>
              </>
            )}

            {/* Error Message */}
            {error && (
              <div className="text-sentinel-accent-danger text-sm text-center">
                {error}
              </div>
            )}

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-sentinel-accent-primary hover:bg-sentinel-accent-primary/90 disabled:opacity-50 disabled:cursor-not-allowed text-sentinel-bg-primary font-semibold py-3 px-4 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-sentinel-accent-primary focus:ring-offset-2 focus:ring-offset-sentinel-bg-secondary"
            >
              {isLoading ? (
                <span className="flex items-center justify-center">
                  <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-sentinel-bg-primary" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Authenticating...
                </span>
              ) : (
                showMfa ? 'Verify Code' : 'Enter Command Center'
              )}
            </button>
          </form>
        </div>

        {/* Footer */}
        <div className="text-center mt-8">
          <p className="text-sentinel-text-muted text-xs">
            Sentinel RTGS v1.6 • Professional Operations Dashboard
          </p>
        </div>
      </div>
    </div>
  );
}





