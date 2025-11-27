'use client';

import { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import * as topojson from 'topojson-client';
import type { FeatureCollection, Geometry } from 'geojson';

interface WorldMapProps {
  className?: string;
  countryData?: { [key: string]: number }; // ISO_A3 country code -> transaction count
}

// Mapping from common country names to ISO_A3 codes (fallback if dataset doesn't have ISO codes)
const countryNameToISO: { [key: string]: string } = {
  'Germany': 'DEU',
  'United Kingdom': 'GBR',
  'France': 'FRA',
  'Spain': 'ESP',
  'Italy': 'ITA',
  'Netherlands': 'NLD',
  'Switzerland': 'CHE',
  'Belgium': 'BEL',
  'Austria': 'AUT',
  'Sweden': 'SWE',
  'Norway': 'NOR',
  'Denmark': 'DNK',
  'Finland': 'FIN',
  'Poland': 'POL',
  'Portugal': 'PRT',
  'Ireland': 'IRL',
  'Greece': 'GRC',
  'Czech Republic': 'CZE',
  'Hungary': 'HUN',
  'Romania': 'ROU',
  'United States of America': 'USA',
  'United States': 'USA',
  'Canada': 'CAN',
  'Australia': 'AUS',
  'New Zealand': 'NZL',
  'Japan': 'JPN',
  'China': 'CHN',
  'South Korea': 'KOR',
  'Singapore': 'SGP',
  'Hong Kong': 'HKG',
  'India': 'IND',
  'Brazil': 'BRA',
  'Mexico': 'MEX',
  'Argentina': 'ARG',
  'South Africa': 'ZAF',
  'Russia': 'RUS',
  'Turkey': 'TUR',
  'United Arab Emirates': 'ARE',
  'Saudi Arabia': 'SAU',
  'Thailand': 'THA',
  'Malaysia': 'MYS',
  'Indonesia': 'IDN',
  'Philippines': 'PHL',
};

export default function WorldMap({ className = '', countryData }: WorldMapProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [worldData, setWorldData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Load world topojson data with ISO codes
    // Using a dataset that includes ISO_A3 codes
    const loadWorldData = async () => {
      const urls = [
        'https://cdn.jsdelivr.net/npm/world-atlas@3/countries-110m.json',
        'https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json',
        'https://unpkg.com/world-atlas@3/countries-110m.json',
        'https://unpkg.com/world-atlas@2/countries-110m.json'
      ];

      for (const url of urls) {
        try {
          const response = await fetch(url);
          
          // Check if response is OK
          if (!response.ok) {
            console.warn(`Failed to fetch ${url}: ${response.status} ${response.statusText}`);
            continue;
          }

          // Check Content-Type
          const contentType = response.headers.get('content-type');
          if (!contentType || !contentType.includes('application/json')) {
            console.warn(`Unexpected content type for ${url}: ${contentType}`);
            continue;
          }

          const data = await response.json();
          
          // Validate that we got valid TopoJSON data
          if (data && data.type === 'Topology' && data.objects) {
            setWorldData(data);
            setLoading(false);
            return;
          } else {
            console.warn(`Invalid TopoJSON data from ${url}`);
            continue;
          }
        } catch (error) {
          console.error(`Error loading world data from ${url}:`, error);
          continue;
        }
      }

      // If all URLs failed, log error and set loading to false
      console.error('Failed to load world data from all sources');
      setLoading(false);
    };

    loadWorldData();
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
    
    // Log data for debugging
    if (Object.keys(transferData).length > 0) {
      console.log('WorldMap rendering with data:', transferData);
    } else {
      console.log('WorldMap: No country data provided');
    }
    
    // Calculate max value for normalization (use 1 if no data to avoid division by zero)
    const values = Object.values(transferData) as number[];
    const maxValue = values.length > 0 ? Math.max(...values) : 1;

    // Create color scale based on transfer activity
    // Use a much brighter and more visible color scale
    // For countries with data, use bright colors; for no data, use dark background
    const colorScale = d3.scaleSequential()
      .domain([0, maxValue])
      .interpolator(d3.interpolateRgbBasis([
        '#2d3a5a', // Low activity - visible blue-gray
        '#4a5a8a', // Medium-low - lighter blue
        '#6b7aaa', // Medium - purple-blue
        '#8b7aca', // Medium-high - purple
        '#fca311', // High - orange (warning color)
        '#e63946', // Very high - red (danger color)
      ]));
    
    // Debug: Log color scale test
    console.log('Color scale test:', {
      maxValue,
      minColor: colorScale(0),
      midColor: colorScale(maxValue / 2),
      maxColor: colorScale(maxValue),
      testValues: Object.entries(transferData).slice(0, 3).map(([code, val]) => ({
        code,
        value: val,
        color: colorScale(val as number)
      }))
    });

    // Get countries data
    const countries = topojson.feature(worldData, worldData.objects.countries) as unknown as FeatureCollection<Geometry>;

    // Debug: Check what properties are available in the first country
    if (countries.features.length > 0) {
      const firstCountry = countries.features[0] as any;
      console.log('Sample country properties:', firstCountry.properties);
      console.log('Available property keys:', Object.keys(firstCountry.properties || {}));
      
      // Try to find a country that matches our data
      const testCodes = ['DEU', 'GBR', 'FRA', 'ESP', 'ITA', 'NLD', 'CHE'];
      for (const code of testCodes) {
        const matching = countries.features.find((f: any) => {
          const props = f.properties || {};
          return props.ISO_A3 === code || props.iso_a3 === code || 
                 props.ISO_A3_EH === code || props.ISO_A3_CD === code;
        });
        if (matching) {
          console.log(`Found matching country for ${code}:`, (matching as any).properties);
        }
      }
    }

    // Create the main group
    const g = svg.append('g');

    // Draw countries
    g.selectAll('path')
      .data(countries.features)
      .enter()
      .append('path')
      .attr('d', path)
      .attr('fill', (d: any) => {
        // Try multiple possible property names for ISO_A3
        const props = d.properties || {};
        let countryCode = props.ISO_A3 || props.iso_a3 || props.ISO_A3_EH || props.ISO_A3_CD || null;
        
        // Fallback: try to map from country name if ISO code not found
        if (!countryCode) {
          const countryName = props.NAME || props.name || props.NAME_LONG || '';
          countryCode = countryNameToISO[countryName] || null;
        }
        
        const activity = countryCode ? (transferData[countryCode] || 0) : 0;
        
        if (activity > 0) {
          const color = colorScale(activity);
          // Log first few matches for debugging
          if (activity > 100) {
            console.log(`Country ${countryCode} (${props.NAME || 'unknown'}): activity=${activity}, color=${color}`);
          }
          return color;
        }
        return '#1a1a2e';
      })
      .attr('stroke', '#2a2a3e')
      .attr('stroke-width', 0.5)
      .attr('class', 'country')
      .style('cursor', 'pointer')
      .on('mouseover', function(event, d: any) {
        const props = d.properties || {};
        let countryCode = props.ISO_A3 || props.iso_a3 || props.ISO_A3_EH || props.ISO_A3_CD || null;
        
        // Fallback: try to map from country name if ISO code not found
        if (!countryCode) {
          const countryName = props.NAME || props.name || props.NAME_LONG || '';
          countryCode = countryNameToISO[countryName] || null;
        }
        
        const activity = countryCode ? (transferData[countryCode] || 0) : 0;
        const countryName = props.NAME || props.name || props.NAME_LONG || 'Unknown';

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
      .data(countries.features.filter((d: any) => {
        const props = d.properties || {};
        let countryCode = props.ISO_A3 || props.iso_a3 || props.ISO_A3_EH || props.ISO_A3_CD || null;
        
        // Fallback: try to map from country name if ISO code not found
        if (!countryCode) {
          const countryName = props.NAME || props.name || props.NAME_LONG || '';
          countryCode = countryNameToISO[countryName] || null;
        }
        
        return countryCode && (transferData[countryCode] || 0) > 0;
      }))
      .enter()
      .append('path')
      .attr('d', path)
      .attr('fill', 'none')
      .attr('stroke', (d: any) => {
        const props = d.properties || {};
        let countryCode = props.ISO_A3 || props.iso_a3 || props.ISO_A3_EH || props.ISO_A3_CD || null;
        
        // Fallback: try to map from country name if ISO code not found
        if (!countryCode) {
          const countryName = props.NAME || props.name || props.NAME_LONG || '';
          countryCode = countryNameToISO[countryName] || null;
        }
        
        const activity = countryCode ? (transferData[countryCode] || 0) : 0;
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

  if (!worldData) {
    return (
      <div className={`flex items-center justify-center ${className}`}>
        <div className="text-sentinel-text-secondary text-sm">
          Unable to load world map data. Please check your network connection.
        </div>
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
