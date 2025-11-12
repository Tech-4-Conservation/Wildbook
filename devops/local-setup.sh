#!/bin/bash

# Wildbook Local Development Setup Script
# This script automates the setup process for local Wildbook development

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

print_section() {
    echo ""
    echo "========================================"
    echo "$1"
    echo "========================================"
}

# Get the script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Define directories
WILDBOOK_DEV_DIR="$HOME/wildbook-dev"
WEBAPPS_DIR="$WILDBOOK_DEV_DIR/webapps/wildbook"
DATA_DIR="$WILDBOOK_DEV_DIR/webapps/wildbook_data_dir"
LOGS_DIR="$WILDBOOK_DEV_DIR/logs"
ENV_TEMPLATE="$SCRIPT_DIR/development/_env.template"
ENV_FILE="$SCRIPT_DIR/development/.env"

# Check prerequisites
check_prerequisites() {
    print_section "Checking Prerequisites"
    
    # Check if Maven is installed
    if command -v mvn &> /dev/null; then
        MVN_VERSION=$(mvn -version | head -n 1)
        print_success "Maven is installed: $MVN_VERSION"
    else
        print_error "Maven is not installed"
        print_info "Please install Maven:"
        echo "  - On Linux: sudo apt update && sudo apt install maven"
        echo "  - On Mac: brew install maven"
        exit 1
    fi
    
    # Check if Docker is installed
    if command -v docker &> /dev/null; then
        print_success "Docker is installed"
    else
        print_error "Docker is not installed"
        print_info "Please install Docker from https://www.docker.com/get-started"
        exit 1
    fi
    
    # Check if Docker Compose is available
    if docker compose version &> /dev/null; then
        print_success "Docker Compose is available"
    else
        print_error "Docker Compose is not available"
        exit 1
    fi
}

# Setup directory structure
setup_directories() {
    print_section "Setting up Directory Structure"
    
    mkdir -p "$WEBAPPS_DIR"
    print_success "Created: $WEBAPPS_DIR"
    
    mkdir -p "$DATA_DIR"
    print_success "Created: $DATA_DIR"
    
    mkdir -p "$LOGS_DIR"
    print_success "Created: $LOGS_DIR"
}

# Setup environment file
setup_environment() {
    print_section "Setting up Environment File"
    
    if [ -f "$ENV_FILE" ]; then
        print_info "Environment file already exists at: $ENV_FILE"
        read -p "Do you want to overwrite it? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Skipping environment file creation"
            return
        fi
    fi
    
    if [ ! -f "$ENV_TEMPLATE" ]; then
        print_error "Template file not found: $ENV_TEMPLATE"
        exit 1
    fi
    
    cp "$ENV_TEMPLATE" "$ENV_FILE"
    print_success "Created environment file: $ENV_FILE"
    print_info "Please edit $ENV_FILE and configure:"
    echo "  - WILDBOOK_DB_NAME"
    echo "  - WILDBOOK_DB_USER"
    echo "  - WILDBOOK_DB_PASSWORD"
    echo "  - WILDBOOK_DB_CONNECTION_URL"
}

# Build the project
build_project() {
    print_section "Building the Project"
    
    cd "$PROJECT_ROOT"
    
    print_info "Running Maven build script..."
    ./mavenBuild.sh
    
    print_success "Build completed successfully"
}

# Deploy the application
deploy_application() {
    print_section "Deploying the Application"
    
    # Find the WAR file
    WAR_FILE=$(find "$PROJECT_ROOT/target" -name "wildbook-*.war" | head -n 1)
    
    if [ -z "$WAR_FILE" ]; then
        print_error "WAR file not found in $PROJECT_ROOT/target"
        print_info "Please run the build first"
        exit 1
    fi
    
    WAR_FILENAME=$(basename "$WAR_FILE")
    print_info "Found WAR file: $WAR_FILENAME"
    
    # Clean deployment directory
    print_info "Cleaning deployment directory..."
    rm -rf "$WEBAPPS_DIR"
    mkdir -p "$WEBAPPS_DIR"
    
    # Copy WAR file
    print_info "Copying WAR file..."
    cp "$WAR_FILE" "$WEBAPPS_DIR/"
    
    # Extract WAR file
    print_info "Extracting WAR file..."
    cd "$WEBAPPS_DIR"
    jar -xf "$WAR_FILENAME"
    
    print_success "Application deployed to: $WEBAPPS_DIR"
}

# Start Docker containers
start_docker() {
    print_section "Starting Docker Containers"
    
    cd "$SCRIPT_DIR/development"
    
    if [ ! -f "$ENV_FILE" ]; then
        print_error "Environment file not found: $ENV_FILE"
        print_info "Please run the environment setup first"
        exit 1
    fi
    
    print_info "Starting Docker Compose..."
    docker compose up -d --remove-orphans
    
    print_success "Docker containers started"
}

# Stop Docker containers
stop_docker() {
    print_section "Stopping Docker Containers"
    
    cd "$SCRIPT_DIR/development"
    
    print_info "Stopping Docker Compose..."
    docker compose down
    
    print_success "Docker containers stopped"
}

# Stop Docker containers and remove volumes
stop_docker_volumes() {
    print_section "Stopping Docker Containers and Removing Volumes"
    
    cd "$SCRIPT_DIR/development"
    
    print_info "Stopping Docker Compose and removing volumes..."
    docker compose down -v
    
    print_success "Docker containers stopped and volumes removed"
}

# Open database terminal
db_shell() {
    print_section "Opening Database Terminal"
    
    cd "$SCRIPT_DIR/development"
    
    # Load environment variables
    if [ ! -f "$ENV_FILE" ]; then
        print_error "Environment file not found: $ENV_FILE"
        print_info "Please run the environment setup first"
        exit 1
    fi
    source "$ENV_FILE"
    
    print_info "Connecting to database: $WILDBOOK_DB_NAME as user: $WILDBOOK_DB_USER"
    print_info "Type 'exit' or press Ctrl+D to exit the database terminal"
    echo ""
    
    docker compose exec db psql -U "$WILDBOOK_DB_USER" -d "$WILDBOOK_DB_NAME"
}

# Initialize database with required data
init_database() {
    print_section "Initializing Database"
    
    cd "$SCRIPT_DIR/development"
    
    # Load environment variables
    if [ ! -f "$ENV_FILE" ]; then
        print_error "Environment file not found: $ENV_FILE"
        return 1
    fi
    source "$ENV_FILE"
    
    print_info "Waiting for database to be ready..."
    local max_attempts=60
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if docker compose exec -T db pg_isready -U postgres &> /dev/null; then
            print_success "Database is ready"
            break
        fi
        attempt=$((attempt + 1))
        if [ $attempt -eq $max_attempts ]; then
            print_error "Database did not become ready in time"
            return 1
        fi
        sleep 2
    done
    
    print_info "Waiting for SYSTEMVALUE table to be created (this may take a while)..."
    attempt=0
    max_attempts=120
    
    while [ $attempt -lt $max_attempts ]; do
        if docker compose exec -T db psql -U "$WILDBOOK_DB_USER" -d "$WILDBOOK_DB_NAME" -c "\dt public.\"SYSTEMVALUE\"" 2>/dev/null | grep -q "SYSTEMVALUE"; then
            print_success "SYSTEMVALUE table exists"
            break
        fi
        attempt=$((attempt + 1))
        if [ $attempt -eq $max_attempts ]; then
            print_error "SYSTEMVALUE table was not created in time"
            print_info "The table may be created later by the application. You can run: $0 init-db"
            return 1
        fi
        sleep 5
    done
    
    print_info "Inserting SERVER_INFO configuration..."
    docker compose exec -T db psql -U "$WILDBOOK_DB_USER" -d "$WILDBOOK_DB_NAME" << 'EOF'
INSERT INTO public."SYSTEMVALUE" ("KEY", "VALUE", "VERSION")
VALUES('SERVER_INFO', '{"type":"JSONObject","value":{"scheme":"http","contextPath":"","context":"context0","serverName":"localhost","serverPort":81,"timestamp":1584020902808}}', 1584020902808)
ON CONFLICT ("KEY") DO NOTHING;
EOF
    
    if [ $? -eq 0 ]; then
        print_success "Database initialized successfully"
    else
        print_error "Failed to insert SERVER_INFO"
        return 1
    fi
}

# Full setup
full_setup() {
    check_prerequisites
    setup_directories
    setup_environment
    
    print_section "Initial Setup Complete"
    print_info "Next steps:"
    echo "  1. Edit the environment file: $ENV_FILE"
    echo "  2. Run: $0 build-and-run"
}

# Build and run
build_and_run() {
    check_prerequisites
    setup_directories
    setup_environment
    build_project
    deploy_application
    start_docker
    init_database
    
    print_section "Wildbook is Running!"
    print_success "Access the application at: http://localhost:81/"
    echo ""
    print_info "Post-Setup Tasks:"
    echo "  1. Update API URLs in: devops/development/.dockerfiles/tomcat/IA-wbia.json"
    echo "     Change http://172.17.0.1:5000 to your arguswild-api endpoint"
}

# Show usage
show_usage() {
    echo "Wildbook Local Development Setup Script"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  setup           - Run initial setup (directories and environment)"
    echo "  build           - Build the project with Maven"
    echo "  deploy          - Deploy the built WAR file"
    echo "  start           - Start Docker containers"
    echo "  stop            - Stop Docker containers"
    echo "  stop-volumes    - Stop Docker containers and remove volumes"
    echo "  init-db         - Initialize database with required configuration"
    echo "  db-shell        - Open PostgreSQL terminal in database container"
    echo "  build-and-run   - Build, deploy, and start everything"
    echo "  help            - Show this help message"
    echo ""
    echo "Examples:" 
    echo "  $0 setup          # First time setup"
    echo "  $0 build-and-run  # Build and start everything"
    echo "  $0 init-db        # Initialize database (run if build-and-run was interrupted)"
    echo "  $0 db-shell       # Open PostgreSQL terminal"
    echo "  $0 stop           # Stop the containers"
}

# Main script logic
case "${1:-}" in
    setup)
        full_setup
        ;;
    build)
        build_project
        ;;
    deploy)
        deploy_application
        ;;
    start)
        start_docker
        ;;
    stop)
        stop_docker
        ;;
    stop-volumes)
        stop_docker_volumes
        ;;
    init-db)
        init_database
        ;;
    db-shell)
        db_shell
        ;;
    build-and-run)
        build_and_run
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        if [ -z "${1:-}" ]; then
            show_usage
        else
            print_error "Unknown command: $1"
            echo ""
            show_usage
            exit 1
        fi
        ;;
esac

