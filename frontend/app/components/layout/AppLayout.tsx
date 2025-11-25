'use client';

import { useState, ReactNode } from 'react';
import TopBar from './TopBar';
import Navigation from './Navigation';

interface AppLayoutProps {
  children: ReactNode;
}

export default function AppLayout({ children }: AppLayoutProps) {
  const [navCollapsed, setNavCollapsed] = useState(false);

  return (
    <div className="min-h-screen bg-sentinel-bg-primary flex">
      {/* Navigation Sidebar */}
      <Navigation
        collapsed={navCollapsed}
        onToggleCollapse={() => setNavCollapsed(!navCollapsed)}
      />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 relative">
        {/* Top Bar - positioned relative to main content */}
        <div className="absolute top-0 left-0 right-0 z-50">
          <TopBar />
        </div>

        {/* Main Content */}
        <main className="flex-1 overflow-auto pt-12">
          {children}
        </main>
      </div>
    </div>
  );
}
