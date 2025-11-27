'use client';

import { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import * as topojson from 'topojson-client';
import type { FeatureCollection, Geometry } from 'geojson';

interface WorldMapProps {
  className?: string;
  countryData?: { [key: string]: number }; // ISO_A3 country code -> transaction count
}

export default function WorldMap({ className = '', countryData }: WorldMapProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [worldData, setWorldData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Load world topojson data
    fetch('https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json')
      .then(response => response.json())
      .then(data => {
        setWorldData(data);
        setLoading(false);
      })
      .catch(error => {
        console.error('Error loading world data:', error);
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    if (!worldData || !svgRef.current) return;

    const svg = d3.select(svgRef.current);
    const width = 400;
    const height = 250;

    // Clear previous content
    svg.selectAll('*').remove();

    // Set up projection
    const projection = d3.geoNaturalEarth1()
      .scale(100)
      .translate([width / 2, height / 2]);

    const path = d3.geoPath().projection(projection);

    // Use provided data or fallback to empty (no activity)
    const transferData = countryData || {};
    
    // Calculate max value for normalization (use 1 if no data to avoid division by zero)
    const values = Object.values(transferData);
    const maxValue = values.length > 0 ? Math.max(...values) : 1;

    // Create color scale based on transfer activity
    const colorScale = d3.scaleSequential()
      .domain([0, maxValue])
      .interpolator(d3.interpolateRgbBasis([
        '#1a1a2e', // Very low activity - dark background
        '#0f3460', // Low activity
        '#16213e', // Medium-low
        '#533483', // Medium
        '#fca311', // Medium-high (warning color)
        '#e63946', // High activity (danger color)
      ]));

    // Get countries data
    const countries = topojson.feature(worldData, worldData.objects.countries) as unknown as FeatureCollection<Geometry>;

    // Create the main group
    const g = svg.append('g');

    // Draw countries
    g.selectAll('path')
      .data(countries.features)
      .enter()
      .append('path')
      .attr('d', path)
      .attr('fill', (d: any) => {
        const countryCode = d.properties.ISO_A3;
        const activity = transferData[countryCode] || 0;
        return activity > 0 ? colorScale(activity) : '#1a1a2e';
      })
      .attr('stroke', '#2a2a3e')
      .attr('stroke-width', 0.5)
      .attr('class', 'country')
      .style('cursor', 'pointer')
      .on('mouseover', function(event, d: any) {
        const countryCode = d.properties.ISO_A3;
        const activity = transferData[countryCode] || 0;
        const countryName = d.properties.NAME || 'Unknown';

        // Highlight on hover
        d3.select(this)
          .attr('stroke', '#fca311')
          .attr('stroke-width', 1.5);

        // Show tooltip
        const tooltip = d3.select('body').append('div')
          .attr('class', 'worldmap-tooltip')
          .style('position', 'absolute')
          .style('background', 'rgba(26, 26, 46, 0.95)')
          .style('color', 'white')
          .style('padding', '8px 12px')
          .style('border-radius', '6px')
          .style('font-size', '12px')
          .style('pointer-events', 'none')
          .style('border', '1px solid #fca311')
          .style('z-index', '1000')
          .html(`
            <div style="font-weight: 600;">${countryName}</div>
            <div style="color: #fca311;">Transactions: ${activity > 0 ? activity.toLocaleString() : 'No data'}</div>
          `);

        tooltip
          .style('left', (event.pageX + 10) + 'px')
          .style('top', (event.pageY - 10) + 'px');
      })
      .on('mouseout', function() {
        // Remove highlight
        d3.select(this)
          .attr('stroke', '#2a2a3e')
          .attr('stroke-width', 0.5);

        // Remove tooltip
        d3.select('.worldmap-tooltip').remove();
      });

    // Add subtle glow effect to active countries
    g.selectAll('.active-country')
      .data(countries.features.filter((d: any) => transferData[d.properties.ISO_A3] > 0))
      .enter()
      .append('path')
      .attr('d', path)
      .attr('fill', 'none')
      .attr('stroke', (d: any) => {
        const activity = transferData[d.properties.ISO_A3] || 0;
        const threshold70 = maxValue * 0.7;
        const threshold40 = maxValue * 0.4;
        return activity > threshold70 ? '#e63946' : activity > threshold40 ? '#fca311' : '#533483';
      })
      .attr('stroke-width', 0.8)
      .attr('opacity', 0.6)
      .attr('filter', 'blur(0.5px)');

    // Add legend
    const legendWidth = 100;
    const legendHeight = 8;

    const legend = svg.append('g')
      .attr('transform', `translate(${width - legendWidth - 20}, ${height - 40})`);

    // Create gradient for legend
    const defs = svg.append('defs');
    const gradient = defs.append('linearGradient')
      .attr('id', 'legend-gradient')
      .attr('x1', '0%')
      .attr('x2', '100%');

    gradient.append('stop')
      .attr('offset', '0%')
      .attr('stop-color', '#1a1a2e');

    gradient.append('stop')
      .attr('offset', '25%')
      .attr('stop-color', '#533483');

    gradient.append('stop')
      .attr('offset', '50%')
      .attr('stop-color', '#fca311');

    gradient.append('stop')
      .attr('offset', '75%')
      .attr('stop-color', '#e63946');

    gradient.append('stop')
      .attr('offset', '100%')
      .attr('stop-color', '#e63946');

    // Legend bar
    legend.append('rect')
      .attr('width', legendWidth)
      .attr('height', legendHeight)
      .style('fill', 'url(#legend-gradient)')
      .attr('rx', 2);

    // Legend labels
    legend.append('text')
      .attr('x', 0)
      .attr('y', legendHeight + 12)
      .attr('fill', '#fca311')
      .attr('font-size', '10px')
      .text('Low');

    legend.append('text')
      .attr('x', legendWidth)
      .attr('y', legendHeight + 12)
      .attr('fill', '#e63946')
      .attr('font-size', '10px')
      .attr('text-anchor', 'end')
      .text('High');

    legend.append('text')
      .attr('x', legendWidth / 2)
      .attr('y', legendHeight + 24)
      .attr('fill', '#888')
      .attr('font-size', '9px')
      .attr('text-anchor', 'middle')
      .text('Activity Level');

  }, [worldData, countryData]);

  if (loading) {
    return (
      <div className={`flex items-center justify-center ${className}`}>
        <div className="animate-pulse text-sentinel-text-secondary">Loading world map...</div>
      </div>
    );
  }

  return (
    <div className={`relative ${className}`}>
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox="0 0 400 250"
        className="rounded-lg overflow-hidden"
      />
      {/* Thin neon border effect */}
      <div className="absolute inset-0 border border-sentinel-accent-primary/20 rounded-lg pointer-events-none"></div>
    </div>
  );
}
