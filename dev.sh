#!/bin/bash

# Development helper script for the Ledger project

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$PROJECT_ROOT"

case "${1:-help}" in
    "start"|"up")
        echo "🚀 Starting development environment..."
        docker compose up --build
        ;;
    "stop"|"down")
        echo "🛑 Stopping development environment..."
        docker compose down
        ;;
    "restart")
        echo "🔄 Restarting development environment..."
        docker compose down
        docker compose up --build
        ;;
    "logs")
        echo "📋 Showing logs..."
        if [ -n "$2" ]; then
            docker compose logs -f "$2"
        else
            docker compose logs -f
        fi
        ;;
    "build")
        echo "🔨 Building development images..."
        docker compose build
        ;;
    "clean")
        echo "🧹 Cleaning up..."
        docker compose down -v
        docker system prune -f
        ;;
    "shell")
        echo "🐚 Opening shell in backend container..."
        docker compose exec backend bash
        ;;
    "help"|*)
        echo "Ledger Development Helper Script"
        echo ""
        echo "Usage: $0 <command>"
        echo ""
        echo "Commands:"
        echo "  start|up     Start development environment"
        echo "  stop|down    Stop development environment"
        echo "  restart      Restart development environment"
        echo "  logs [svc]   Show logs (optionally for specific service)"
        echo "  build        Build development images"
        echo "  clean        Clean up containers and volumes"
        echo "  shell        Open shell in backend container"
        echo "  help         Show this help message"
        echo ""
        echo "Examples:"
        echo "  $0 start           # Start all services"
        echo "  $0 logs backend    # Show backend logs"
        echo "  $0 shell           # Open shell in backend"
        ;;
esac
