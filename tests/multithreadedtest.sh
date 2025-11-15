#!/bin/bash

# Multithreaded File Server Test Script
# Spawns multiple clients simultaneously to stress the server and summarize results

set -euo pipefail
shopt -s nullglob

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENT_DIR="$SCRIPT_DIR/../FileClient"
LOG_DIR="$SCRIPT_DIR/test_logs"
CLIENT_CLASS="ca.concordia.Main"
CLIENT_CLASSPATH="target/classes"

# Configuration (override via CLI for client count or env vars for host/port)
NUM_CLIENTS="${1:-5}"
SERVER_HOST="${SERVER_HOST:-localhost}"
SERVER_PORT="${SERVER_PORT:-12345}"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}${CYAN}Multi-threaded File Server Test${NC}"
printf "${BLUE}Configuration:${NC}\n"
printf "  • Concurrent clients: ${BOLD}%s${NC}\n" "$NUM_CLIENTS"
printf "  • Server: ${BOLD}%s:%s${NC}\n\n" "$SERVER_HOST" "$SERVER_PORT"

# Omit saving logs to disk: no log directory or file cleanup

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo -e "${RED}Missing required command: $1${NC}"
        echo "Install it and re-run the script."
        exit 1
    fi
}

ensure_client_built() {
    if [ -d "$CLIENT_DIR/$CLIENT_CLASSPATH" ]; then
        return
    fi

    require_command mvn
    echo -e "${YELLOW}⚙ Compiling FileClient...${NC}"
    (cd "$CLIENT_DIR" && mvn clean compile -q)
    echo -e "${GREEN}✓ Compilation complete${NC}\n"
}

wait_for_pids() {
    local pid
    for pid in "$@"; do
        [ -n "$pid" ] || continue
        wait "$pid" 2>/dev/null || true
    done
}

boxed_section() {
    # Simplified: no decorative boxes; keep minimal marker if needed
    :
}

run_client() {
    local client_id=$1
    local num_files=$((3 + RANDOM % 3))

    echo -e "${YELLOW}[Client $client_id] Starting (creating $num_files files)...${NC}" >&2

    (
        cd "$CLIENT_DIR" || exit 1
        {
            local f
            for f in $(seq 1 "$num_files"); do
                echo "CREATE c${client_id}f${f}.txt"
                sleep 0.1
            done

            for f in $(seq 1 "$num_files"); do
                echo "WRITE c${client_id}f${f}.txt Data_C${client_id}_F${f}"
                sleep 0.1
            done

            for f in $(seq 1 "$num_files"); do
                echo "READ c${client_id}f${f}.txt"
                sleep 0.1
            done

            echo "LIST"
            sleep 0.2

            for f in $(seq 1 "$num_files"); do
                echo "DELETE c${client_id}f${f}.txt"
                sleep 0.1
            done

            echo "QUIT"
        } | java -cp "$CLIENT_CLASSPATH" "$CLIENT_CLASS"
    ) 2>&1 &

    echo $!
}

prime_file() {
    local filename=$1
    local content=${2-}
    local attempts=3
    local ok=0
    while (( attempts > 0 )); do
        local out
        out=$(
            cd "$CLIENT_DIR" || exit 1
            {
                printf 'CREATE %s\n' "$filename"
                sleep 0.2
                if [ -n "$content" ]; then
                    printf 'WRITE %s %s\n' "$filename" "$content"
                    sleep 0.2
                fi
                echo "QUIT"
            } | java -cp "$CLIENT_CLASSPATH" "$CLIENT_CLASS" 2>/dev/null
        )
        if echo "$out" | grep -q "Response from server: SUCCESS:"; then
            ok=1
            break
        fi
        attempts=$((attempts - 1))
        sleep 0.3
    done
    if (( ok == 0 )); then
        echo -e "${YELLOW}Warning: prime_file ${filename} may have failed${NC}"
    fi
}

teardown_file() {
    local filename=$1
    (
        cd "$CLIENT_DIR" || exit 1
        {
            printf 'DELETE %s\n' "$filename"
            sleep 0.2
            echo "QUIT"
        } | java -cp "$CLIENT_CLASSPATH" "$CLIENT_CLASS"
    ) >/dev/null 2>&1
}

run_concurrent_write_test() {
    boxed_section "${GREEN}" "Test 2: Concurrent Writes"

    prime_file "shared.txt"
    echo "Testing $NUM_CLIENTS clients writing to shared.txt simultaneously..."

    local -a pids=()
    local i
    for i in $(seq 1 "$NUM_CLIENTS"); do
        (
            cd "$CLIENT_DIR" || exit 1
            {
                printf 'WRITE shared.txt Data_Client_%s\n' "$i"
                sleep 0.2
                echo "QUIT"
            } | java -cp "$CLIENT_CLASSPATH" "$CLIENT_CLASS"
    ) 2>&1 &
        pids+=("$!")
    done

    wait_for_pids "${pids[@]}"

    # Read back the shared file once to ensure content exists after writes
    echo "Verifying content of shared.txt after concurrent writes..."
    local readback
    readback=$(
        cd "$CLIENT_DIR" || exit 1
        {
            echo "READ shared.txt"
            sleep 0.2
            echo "QUIT"
        } | java -cp "$CLIENT_CLASSPATH" "$CLIENT_CLASS" 2>/dev/null | grep -m1 '^Response from server: CONTENT:' || true
    )
    if [ -n "$readback" ]; then
        # Strip the leading label for a concise print
        echo "Content read: ${readback#Response from server: }"
    else
        echo -e "${YELLOW}Warning: Could not verify content of shared.txt${NC}"
    fi

    # Do not delete shared.txt here; it will be used by the concurrent read test
    echo -e "${GREEN}✓ Concurrent write test completed${NC}\n"
}

run_concurrent_read_test() {
    boxed_section "${GREEN}" "Test 3: Concurrent Reads"

    # Read the file created in the previous write test
    echo "Testing $NUM_CLIENTS clients reading shared.txt simultaneously..."

    local -a pids=()
    local i
    for i in $(seq 1 "$NUM_CLIENTS"); do
        (
            cd "$CLIENT_DIR" || exit 1
            {
                echo "READ shared.txt"
                sleep 0.2
                echo "QUIT"
            } | java -cp "$CLIENT_CLASSPATH" "$CLIENT_CLASS"
    ) 2>&1 &
        pids+=("$!")
    done

    wait_for_pids "${pids[@]}"
    teardown_file "shared.txt"
    echo -e "${GREEN}✓ Concurrent read test completed and cleaned up${NC}\n"
}

check_server() {
    require_command nc
    echo -e "${YELLOW}Checking if server is running...${NC}"
    if nc -z "$SERVER_HOST" "$SERVER_PORT" 2>/dev/null; then
        echo -e "${GREEN}Server is running on $SERVER_HOST:$SERVER_PORT${NC}"
        return 0
    fi

    echo -e "${RED}Server is NOT running on $SERVER_HOST:$SERVER_PORT${NC}"
    cat <<EOF
Please start the server first:
  cd ../FileServer
  mvn exec:java -Dexec.mainClass="ca.concordia.Main"
EOF
    return 1
}

count_pattern() {
    local pattern=$1
    shift
    ((${#@} == 0)) && { echo 0; return; }
    grep -h -- "$pattern" "$@" 2>/dev/null | wc -l | tr -d ' '
}

summarize_logs() {
    # Simplified summary since logs are not saved to disk
    echo -e "${BOLD}Tests completed.${NC}"
}

main() {
    require_command java
    ensure_client_built

    if ! check_server; then
        exit 1
    fi

    boxed_section "${GREEN}" "Test 1: Independent File Operations"
    echo "Spawning $NUM_CLIENTS clients..." && echo

    local -a client_pids=()
    local i
    local pid
    for i in $(seq 1 "$NUM_CLIENTS"); do
        pid="$(run_client "$i")"
        client_pids+=("$pid")
        sleep 0.15
    done

    echo -e "${YELLOW}⏳ Waiting for all clients to complete...${NC}"
    wait_for_pids "${client_pids[@]}"
    echo -e "${BOLD}${GREEN}✓ All clients completed!${NC}\n"

    sleep 1
    run_concurrent_write_test
    sleep 1
    run_concurrent_read_test

    summarize_logs
}

main "$@"
