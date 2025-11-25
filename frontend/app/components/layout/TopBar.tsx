'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';

export default function TopBar() {
  const { user, logout, environment } = useAuth();
  const [currentTime, setCurrentTime] = useState<string>('');
  const [showUserMenu, setShowUserMenu] = useState(false);

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(now.toISOString().replace('T', ' ').substring(0, 19) + ' UTC');
    };

    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  const getEnvironmentColor = () => {
    switch (environment) {
      case 'PROD':
        return 'bg-sentinel-accent-danger';
      case 'DEV':
        return 'bg-sentinel-accent-warning';
      default:
        return 'bg-sentinel-bg-tertiary';
    }
  };

  return (
    <div className="h-12 bg-sentinel-bg-secondary border-b border-sentinel-border flex items-center justify-between px-6">
      {/* Left side - Environment Badge */}
      <div className="flex items-center">
        <span className={`px-2 py-1 text-xs font-mono font-semibold text-sentinel-bg-primary rounded ${getEnvironmentColor()}`}>
          {environment}
        </span>
      </div>

      {/* Center - UTC Time */}
      <div className="flex items-center">
        <span className="text-sentinel-text-secondary font-mono text-sm">
          {currentTime}
        </span>
      </div>

      {/* Right side - User Menu */}
      <div className="flex items-center relative">
        <button
          onClick={() => setShowUserMenu(!showUserMenu)}
          className="flex items-center space-x-2 text-sentinel-text-primary hover:text-sentinel-accent-primary transition-colors"
        >
          <div className="w-8 h-8 bg-sentinel-accent-primary rounded-full flex items-center justify-center text-sentinel-bg-primary font-semibold text-sm">
            {user?.name?.charAt(0).toUpperCase() || 'U'}
          </div>
          <span className="text-sm font-medium">{user?.name || 'User'}</span>
          <svg
            className={`w-4 h-4 transition-transform ${showUserMenu ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>

        {/* User Dropdown Menu */}
        {showUserMenu && (
          <div className="absolute top-full right-0 mt-2 w-48 bg-sentinel-bg-tertiary border border-sentinel-border rounded-md shadow-lg z-50">
            <div className="px-4 py-3 border-b border-sentinel-border">
              <p className="text-sm font-medium text-sentinel-text-primary">{user?.name}</p>
              <p className="text-xs text-sentinel-text-muted">{user?.email}</p>
              <p className="text-xs text-sentinel-accent-primary capitalize mt-1">{user?.role}</p>
            </div>
            <div className="py-1">
              <button className="w-full text-left px-4 py-2 text-sm text-sentinel-text-secondary hover:bg-sentinel-bg-secondary hover:text-sentinel-text-primary transition-colors">
                Profile Settings
              </button>
              <button className="w-full text-left px-4 py-2 text-sm text-sentinel-text-secondary hover:bg-sentinel-bg-secondary hover:text-sentinel-text-primary transition-colors">
                Preferences
              </button>
              <div className="border-t border-sentinel-border my-1"></div>
              <button
                onClick={logout}
                className="w-full text-left px-4 py-2 text-sm text-sentinel-accent-danger hover:bg-sentinel-bg-secondary transition-colors"
              >
                Sign Out
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
