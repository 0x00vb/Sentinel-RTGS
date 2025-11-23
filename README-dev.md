# Development Setup

This setup allows you to develop with hot reloading, avoiding the need to rebuild Docker containers for every code change.

## Quick Start

1. **Start development environment:**
   ```bash
   ./dev.sh start
   ```

2. **Make code changes** - they'll be automatically reloaded!

3. **Stop when done:**
   ```bash
   ./dev.sh stop
   ```

## What's Different in Development

### Backend (Spring Boot)
- **Hot Reloading**: Changes to Java files trigger automatic restarts
- **Volume Mounting**: Source code is mounted into the container
- **Debug Port**: Available on port 5005 for remote debugging
- **DevTools**: Enhanced logging and development features

### Frontend (Next.js)
- **Hot Module Replacement**: Instant updates without page refresh
- **Volume Mounting**: Source code changes are reflected immediately
- **Development Server**: Runs with `npm run dev`

## Development Commands

```bash
./dev.sh start      # Start all services
./dev.sh stop       # Stop all services
./dev.sh restart    # Restart all services
./dev.sh logs       # Show all logs
./dev.sh logs backend  # Show backend logs only
./dev.sh shell      # Open shell in backend container
./dev.sh build      # Rebuild containers
./dev.sh clean      # Clean up containers and volumes
```

## File Structure

```
ledger/
├── docker-compose.yml          # Production configuration
├── docker-compose.override.yml  # Development overrides (auto-loaded)
├── dev.sh                       # Development helper script
├── backend/
│   ├── Dockerfile              # Multi-stage (production + development)
│   └── src/main/resources/
│       └── application-development.properties  # Dev config
└── frontend/
    └── Dockerfile              # Multi-stage (production + development)
```

## Ports

- **Backend**: http://localhost:8080
- **Frontend**: http://localhost:3000
- **Database**: localhost:5432
- **Debug**: localhost:5005 (backend remote debugging)

## Making Changes

### Backend Changes
- Edit Java files in `backend/src/main/java/`
- Changes trigger automatic restart via Spring DevTools
- Check logs: `./dev.sh logs backend`

### Frontend Changes
- Edit files in `frontend/`
- Changes appear instantly via HMR
- Check logs: `./dev.sh logs frontend`

### Configuration Changes
- Edit `backend/src/main/resources/application-development.properties`
- Container will restart automatically

## Debugging

### Backend Debugging
Connect your IDE to port 5005 for remote debugging:
- **Host**: localhost
- **Port**: 5005

### Frontend Debugging
- Open browser DevTools
- Source maps are enabled for debugging

## Troubleshooting

### Backend not reloading?
1. Check logs: `./dev.sh logs backend`
2. Verify file is saved and syntax is correct
3. Try manual restart: `./dev.sh restart`

### Frontend not updating?
1. Check logs: `./dev.sh logs frontend`
2. Clear browser cache
3. Restart: `./dev.sh restart`

### Permission issues?
```bash
# Fix file permissions
sudo chown -R $USER:$USER .
```

## Production Deployment

For production, use the regular `docker-compose.yml` without the override:

```bash
docker compose -f docker-compose.yml up --build
```

The override file is automatically ignored in production environments.
