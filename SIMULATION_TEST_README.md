# Simulation Engine Test Script

This script provides comprehensive testing for the Fortress-Settlement traffic simulation engine.

## Quick Start

```bash
# Run basic functionality tests
./test_simulation.sh basic

# Run comprehensive tests (recommended)
./test_simulation.sh full

# Run load/performance tests only
./test_simulation.sh load
```

## Test Coverage

### Basic Tests
- ✅ Single message sending
- ✅ Simulation start/stop/status
- ✅ Data integrity testing
- ✅ Audit chain verification

### Load Tests
- ✅ 5 msg/sec for 10 seconds
- ✅ 10 msg/sec for 5 seconds
- ✅ Performance validation

### Service Management
- ✅ Automatic service startup
- ✅ Health checks
- ✅ Clean shutdown

## Command Reference

| Command | Description |
|---------|-------------|
| `basic` | Basic functionality tests |
| `load` | Load and performance tests |
| `full` | Complete test suite |
| `start` | Start services only |
| `stop` | Stop services only |
| `status` | Check service health |
| `help` | Show usage information |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND_URL` | `http://localhost:8080` | Backend API URL |
| `FRONTEND_URL` | `http://localhost:3000` | Frontend URL |

## Example Usage

```bash
# Test with custom backend URL
BACKEND_URL=http://my-server:8080 ./test_simulation.sh basic

# Start services and check status
./test_simulation.sh start
./test_simulation.sh status

# Run load tests only
./test_simulation.sh load

# Stop everything
./test_simulation.sh stop
```

## Test Output

The script provides colored output with clear success/error indicators:

- 🟢 **SUCCESS**: Tests passed
- 🟡 **WARNING**: Tests completed but with minor issues
- 🔴 **ERROR**: Tests failed

## Prerequisites

- Docker and Docker Compose
- curl
- jq (for JSON parsing)
- bash

## Safety Features

- Automatic cleanup on script exit
- Service health verification
- Timeout protection for long-running tests
- Non-destructive testing (dev mode only)
