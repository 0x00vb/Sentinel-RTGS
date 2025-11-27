#!/bin/bash

# Fortress-Settlement Simulation Engine Test Script
# Tests all simulation functionality including traffic generation and data integrity

set -e  # Exit on any error

# Configuration
BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:3000"
COMPOSE_FILE="docker-compose.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Health check functions
check_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1

    log_info "Waiting for $service_name to be ready..."

    while [ $attempt -le $max_attempts ]; do
        if curl -s --max-time 5 "$url" > /dev/null 2>&1; then
            log_success "$service_name is ready!"
            return 0
        fi

        log_info "Attempt $attempt/$max_attempts - $service_name not ready yet..."
        sleep 2
        ((attempt++))
    done

    log_error "$service_name failed to start after $max_attempts attempts"
    return 1
}

check_backend_health() {
    check_service "$BACKEND_URL/api/v1/simulation/status" "Backend API"
}

check_frontend_health() {
    check_service "$FRONTEND_URL" "Frontend"
}

# API test functions
test_api_endpoint() {
    local method=$1
    local url=$2
    local expected_status=${3:-200}
    local description=$4

    log_info "Testing: $description"
    log_info "  $method $url"

    local response
    if [ "$method" = "POST" ]; then
        response=$(curl -s -X POST -w "\nHTTP_STATUS:%{http_code}" "$url" 2>/dev/null)
    else
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$url" 2>/dev/null)
    fi

    local body=$(echo "$response" | head -n -1)
    local status=$(echo "$response" | tail -n 1 | cut -d: -f2)

    if [ "$status" = "$expected_status" ]; then
        log_success "✓ $description - Status: $status"
        echo "$body" | jq . 2>/dev/null || echo "$body"
        return 0
    else
        log_error "✗ $description - Expected: $expected_status, Got: $status"
        echo "Response: $body"
        return 1
    fi
}

# Simulation tests
test_single_message() {
    log_info "=== Testing Single Message Sending ==="
    test_api_endpoint "POST" "$BACKEND_URL/api/v1/simulation/send-test-message" 200 "Send single test message"
}

test_simulation_start() {
    log_info "=== Testing Simulation Start ==="
    test_api_endpoint "POST" "$BACKEND_URL/api/v1/simulation/start?messagesPerSecond=2" 200 "Start simulation at 2 msg/sec"
}

test_simulation_status() {
    log_info "=== Testing Simulation Status ==="
    local response=$(curl -s "$BACKEND_URL/api/v1/simulation/status" 2>/dev/null)
    local running=$(echo "$response" | jq -r '.running' 2>/dev/null)
    local messages_sent=$(echo "$response" | jq -r '.messagesSent' 2>/dev/null)

    if [ "$running" = "true" ] || [ "$running" = "false" ]; then
        log_success "✓ Simulation status check - Running: $running, Messages sent: $messages_sent"
    else
        log_error "✗ Simulation status check failed - Invalid response: $response"
        return 1
    fi
}

test_simulation_stop() {
    log_info "=== Testing Simulation Stop ==="
    test_api_endpoint "POST" "$BACKEND_URL/api/v1/simulation/stop" 200 "Stop simulation"
}

test_data_integrity() {
    log_info "=== Testing Data Integrity ==="
    test_api_endpoint "GET" "$BACKEND_URL/api/v1/compliance/integrity/status" 200 "Check integrity testing status"
    test_api_endpoint "POST" "$BACKEND_URL/api/v1/compliance/integrity/test" 200 "Execute integrity test"
}

test_audit_verification() {
    log_info "=== Testing Audit Chain Verification ==="
    test_api_endpoint "GET" "$BACKEND_URL/api/v1/compliance/audit/transfer/1/verify" 200 "Verify audit chain for transfer 1"
}

# Load testing functions
test_load_simulation() {
    local rate=$1
    local duration=$2

    log_info "=== Testing Load Simulation: $rate msg/sec for $duration seconds ==="

    # Start simulation
    test_api_endpoint "POST" "$BACKEND_URL/api/v1/simulation/start?messagesPerSecond=$rate" 200 "Start load simulation"

    # Wait for the specified duration
    log_info "Running simulation for $duration seconds..."
    sleep $duration

    # Check status during load
    local mid_response=$(curl -s "$BACKEND_URL/api/v1/simulation/status" 2>/dev/null)
    local mid_count=$(echo "$mid_response" | jq -r '.messagesSent' 2>/dev/null)
    log_info "Messages sent during load test: $mid_count"

    # Stop simulation
    test_api_endpoint "POST" "$BACKEND_URL/api/v1/simulation/stop" 200 "Stop load simulation"

    # Final count
    local final_response=$(curl -s "$BACKEND_URL/api/v1/simulation/status" 2>/dev/null)
    local final_count=$(echo "$final_response" | jq -r '.messagesSent' 2>/dev/null)

    local total_sent=$((final_count - mid_count))
    local expected_min=$((rate * duration * 8 / 10))  # 80% of expected
    local expected_max=$((rate * duration * 12 / 10))  # 120% of expected

    if [ $total_sent -ge $expected_min ] && [ $total_sent -le $expected_max ]; then
        log_success "✓ Load test successful - Sent: $total_sent messages (expected range: $expected_min-$expected_max)"
    else
        log_warning "! Load test completed - Sent: $total_sent messages (expected range: $expected_min-$expected_max)"
    fi
}

# Service management
start_services() {
    log_info "=== Starting Services ==="
    if command -v docker-compose &> /dev/null; then
        docker-compose -f "$COMPOSE_FILE" up -d postgres rabbitmq backend frontend
    elif command -v docker &> /dev/null && docker compose version &> /dev/null; then
        docker compose -f "$COMPOSE_FILE" up -d postgres rabbitmq backend frontend
    else
        log_error "Neither docker-compose nor docker compose found"
        exit 1
    fi

    sleep 5  # Initial wait
}

stop_services() {
    log_info "=== Stopping Services ==="
    if command -v docker-compose &> /dev/null; then
        docker-compose -f "$COMPOSE_FILE" down
    elif command -v docker &> /dev/null && docker compose version &> /dev/null; then
        docker compose -f "$COMPOSE_FILE" down
    fi
}

cleanup() {
    log_info "=== Cleanup ==="
    # Stop any running simulations
    curl -s -X POST "$BACKEND_URL/api/v1/simulation/stop" > /dev/null 2>&1 || true
    stop_services
}

# Main test functions
run_basic_tests() {
    log_info "=== Running Basic Functionality Tests ==="

    test_single_message
    echo

    test_simulation_start
    sleep 3
    test_simulation_status
    test_simulation_stop
    echo

    test_data_integrity
    echo

    test_audit_verification
    echo
}

run_load_tests() {
    log_info "=== Running Load Tests ==="

    # Quick load test - 5 msg/sec for 10 seconds
    test_load_simulation 5 10
    echo

    # Higher load test - 10 msg/sec for 5 seconds
    test_load_simulation 10 5
    echo
}

run_comprehensive_tests() {
    log_info "=== Running Comprehensive Test Suite ==="

    run_basic_tests
    run_load_tests

    log_success "🎉 Comprehensive test suite completed!"
}

# Command line interface
show_usage() {
    echo "Fortress-Settlement Simulation Engine Test Script"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  basic      - Run basic functionality tests"
    echo "  load       - Run load/performance tests"
    echo "  full       - Run comprehensive test suite (basic + load)"
    echo "  start      - Start services only"
    echo "  stop       - Stop services only"
    echo "  status     - Check service status"
    echo "  help       - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 basic     # Run basic tests"
    echo "  $0 full      # Run all tests"
    echo "  $0 start     # Start services"
    echo ""
    echo "Environment variables:"
    echo "  BACKEND_URL  - Backend API URL (default: http://localhost:8080)"
    echo "  FRONTEND_URL - Frontend URL (default: http://localhost:3000)"
}

main() {
    # Set traps for cleanup
    trap cleanup EXIT INT TERM

    # Override defaults with environment variables
    BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
    FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"

    local command=${1:-full}

    case $command in
        basic)
            log_info "Running basic tests..."
            start_services
            check_backend_health
            run_basic_tests
            ;;
        load)
            log_info "Running load tests..."
            start_services
            check_backend_health
            run_load_tests
            ;;
        full)
            log_info "Running comprehensive tests..."
            start_services
            check_backend_health
            run_comprehensive_tests
            ;;
        start)
            start_services
            check_backend_health
            check_frontend_health
            log_success "Services started successfully!"
            ;;
        stop)
            stop_services
            ;;
        status)
            log_info "Checking service status..."
            check_backend_health && log_success "Backend is healthy" || log_error "Backend is not healthy"
            check_frontend_health && log_success "Frontend is healthy" || log_error "Frontend is not healthy"
            ;;
        help|--help|-h)
            show_usage
            exit 0
            ;;
        *)
            log_error "Unknown command: $command"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
