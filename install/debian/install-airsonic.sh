#!/bin/bash
set -euo pipefail

# Airsonic-Pulse installer for Debian / Ubuntu and derivatives

GITHUB_REPO="litebito/airsonic-pulse"
AIRSONIC_HOME="/var/airsonic"
AIRSONIC_USER="airsonic"
SYSTEMD_UNIT="/etc/systemd/system/airsonic.service"
DEFAULT_FILE="/etc/default/airsonic"

# ---------------------------------------------------------------------------
# Color helpers (degrade gracefully when not a terminal)
# ---------------------------------------------------------------------------
if [[ -t 1 ]]; then
    _GREEN='\033[0;32m'
    _YELLOW='\033[1;33m'
    _RED='\033[0;31m'
    _RESET='\033[0m'
else
    _GREEN=''
    _YELLOW=''
    _RED=''
    _RESET=''
fi

ok()   { echo -e "${_GREEN}[OK]${_RESET}    $*"; }
warn() { echo -e "${_YELLOW}[WARN]${_RESET}  $*"; }
err()  { echo -e "${_RED}[ERROR]${_RESET} $*" >&2; }
die()  { err "$*"; exit 1; }

ask() {
    # ask <prompt> — returns 0 for yes, 1 for no
    local answer
    read -r -p "$1 [y/N] " answer
    [[ "${answer,,}" == "y" || "${answer,,}" == "yes" ]]
}

# ---------------------------------------------------------------------------
# Root check
# ---------------------------------------------------------------------------
[[ "${EUID}" -eq 0 ]] || die "This script must be run as root. Try: sudo $0 $*"

# ---------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------
VERSION="${1:-}"

if [[ -z "${VERSION}" ]]; then
    echo "Querying GitHub API for latest release..."
    VERSION="$(curl -fsSL "https://api.github.com/repos/${GITHUB_REPO}/releases/latest" \
        | grep '"tag_name"' | head -1 | sed 's/.*"tag_name": *"\([^"]*\)".*/\1/')"
    [[ -n "${VERSION}" ]] || die "Could not determine latest version from GitHub API."
    ok "Latest release: ${VERSION}"
fi

# ---------------------------------------------------------------------------
# a) Java check
# ---------------------------------------------------------------------------
echo
echo "=== Checking Java ==="

JAVA_21_PKG="openjdk-21-jre-headless"
JAVA_CURRENT_MAJOR=""

if command -v java &>/dev/null; then
    JAVA_CURRENT_MAJOR="$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')"
fi

_find_java21_path() {
    # Resolve the Java 21 binary path for update-alternatives
    local arch
    arch="$(dpkg --print-architecture 2>/dev/null || uname -m)"
    case "${arch}" in
        amd64|x86_64)  echo "/usr/lib/jvm/java-21-openjdk-amd64/bin/java" ;;
        arm64|aarch64) echo "/usr/lib/jvm/java-21-openjdk-arm64/bin/java" ;;
        *)
            # Fallback: find any java-21 alternative registered
            update-alternatives --list java 2>/dev/null | grep "java-21" | head -1
            ;;
    esac
}

if [[ "${JAVA_CURRENT_MAJOR}" == "21" ]]; then
    ok "Java 21 is already the default JVM."
elif [[ -n "${JAVA_CURRENT_MAJOR}" ]]; then
    warn "Java ${JAVA_CURRENT_MAJOR} is currently the default JVM. Airsonic-Pulse requires Java 21."
    echo "  1) Install Java 21 and set it as the default"
    echo "  2) Install Java 21 but keep the current default"
    echo "  3) Abort"
    read -r -p "Choice [1/2/3]: " java_choice
    case "${java_choice}" in
        1)
            apt-get update -qq
            apt-get install -y "${JAVA_21_PKG}"
            JAVA_21_PATH="$(_find_java21_path)"
            if [[ -n "${JAVA_21_PATH}" && -x "${JAVA_21_PATH}" ]]; then
                update-alternatives --set java "${JAVA_21_PATH}"
                ok "Java 21 installed and set as default."
            else
                warn "Could not auto-configure alternatives for Java 21. Set it manually with: update-alternatives --config java"
            fi
            ;;
        2)
            apt-get update -qq
            apt-get install -y "${JAVA_21_PKG}"
            warn "Java 21 installed but NOT set as default. The systemd unit uses /usr/bin/java — ensure it resolves to Java 21 before starting Airsonic."
            ;;
        *)
            die "Aborted by user."
            ;;
    esac
else
    echo "Installing ${JAVA_21_PKG}..."
    apt-get update -qq
    apt-get install -y "${JAVA_21_PKG}"
    JAVA_21_PATH="$(_find_java21_path)"
    if [[ -n "${JAVA_21_PATH}" && -x "${JAVA_21_PATH}" ]]; then
        update-alternatives --set java "${JAVA_21_PATH}"
    fi
    ok "Java 21 installed."
fi

# ---------------------------------------------------------------------------
# b) System user
# ---------------------------------------------------------------------------
echo
echo "=== System user ==="

if id "${AIRSONIC_USER}" &>/dev/null; then
    ok "User '${AIRSONIC_USER}' already exists — skipping."
else
    useradd --system --no-create-home --shell /usr/sbin/nologin "${AIRSONIC_USER}"
    ok "User '${AIRSONIC_USER}' created."
fi

# ---------------------------------------------------------------------------
# c) Data directory
# ---------------------------------------------------------------------------
echo
echo "=== Data directory ==="

mkdir -p "${AIRSONIC_HOME}"
chown "${AIRSONIC_USER}:${AIRSONIC_USER}" "${AIRSONIC_HOME}"
ok "Directory ${AIRSONIC_HOME} ready."

# ---------------------------------------------------------------------------
# d) Download WAR
# ---------------------------------------------------------------------------
echo
echo "=== Downloading WAR (${VERSION}) ==="

WAR_URL="https://github.com/${GITHUB_REPO}/releases/download/${VERSION}/airsonic.war"
WAR_DEST="${AIRSONIC_HOME}/airsonic.war"

if ! curl -fsSL "${WAR_URL}" -o "${WAR_DEST}"; then
    err "Failed to download WAR from: ${WAR_URL}"
    err "If the repository is private, download airsonic.war manually and place it at ${WAR_DEST},"
    err "then re-run this script or complete the remaining steps by hand."
    exit 1
fi
chown "${AIRSONIC_USER}:${AIRSONIC_USER}" "${WAR_DEST}"
ok "WAR downloaded to ${WAR_DEST}."

# ---------------------------------------------------------------------------
# e) Systemd unit
# ---------------------------------------------------------------------------
echo
echo "=== Installing systemd unit ==="

cat > "${SYSTEMD_UNIT}" << 'UNIT_EOF'
# Airsonic-Pulse systemd service unit
#
# Install:
#   sudo cp airsonic.service /etc/systemd/system/
#   sudo systemctl daemon-reload
#   sudo systemctl enable --now airsonic
#
# Override defaults without editing this file:
#   sudo systemctl edit airsonic          (drop-in, survives package updates)
#   or use /etc/sysconfig/airsonic        (RHEL/CentOS/Alma/Rocky/Fedora)
#   or use /etc/default/airsonic          (Debian/Ubuntu)
#   These environment overrides are loaded automatically.
#
# Java 21 / virtual threads note:
#   The SystemCallFilter includes `mincore` explicitly.  This syscall is used by
#   Lucene's MMapDirectory when memory-mapping index files, and is blocked by
#   systemd's default @system-service set on some kernels.  Without it, Lucene
#   will throw AccessControlException on startup when virtual threads are enabled
#   (spring.threads.virtual.enabled=true).

[Unit]
Description=Airsonic-Pulse media server
After=remote-fs.target network.target
AssertPathExists=/var/airsonic

[Service]
Type=simple

# --- Paths and ports ---------------------------------------------------------
Environment="JAVA_JAR=/var/airsonic/airsonic.war"
Environment="AIRSONIC_HOME=/var/airsonic"
Environment="PORT=4040"
Environment="CONTEXT_PATH=/"

# --- JVM tuning --------------------------------------------------------------
# Increase -Xmx for large libraries (>50k tracks consider 2048m or more).
Environment="JAVA_OPTS=-Xmx1024m -Xms512m"

# --- Extra application arguments (optional) ----------------------------------
Environment="JAVA_ARGS="

# Site-specific overrides (PORT, JAVA_OPTS, etc.) go in /etc/sysconfig/airsonic.
# The file is optional; a missing file is silently ignored.
EnvironmentFile=-/etc/sysconfig/airsonic
EnvironmentFile=-/etc/default/airsonic

ExecStart=/usr/bin/java \
          $JAVA_OPTS \
          -Dairsonic.home=${AIRSONIC_HOME} \
          -Dserver.servlet.context-path=${CONTEXT_PATH} \
          -Dserver.port=${PORT} \
          -Dfile.encoding=UTF-8 \
          -Dserver.forward-headers-strategy=framework \
          -Dsun.jnu.encoding=UTF-8 \
          -jar ${JAVA_JAR} $JAVA_ARGS

User=airsonic
Group=airsonic

# --- Sandboxing / hardening --------------------------------------------------
DevicePolicy=closed
NoNewPrivileges=yes
PrivateDevices=yes
PrivateTmp=yes
PrivateUsers=yes
ProtectControlGroups=yes
ProtectKernelModules=yes
ProtectKernelTunables=yes
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
RestrictNamespaces=yes
RestrictRealtime=yes

# mincore is required: Lucene MMapDirectory uses it for index file advising.
# Removing it will cause startup failures when virtual threads are enabled.
SystemCallFilter=@basic-io @file-system @chown @network-io @sync @timer @signal @process @system-service mincore

ProtectSystem=full

# --- Writable paths ----------------------------------------------------------
# Always include AIRSONIC_HOME.  Add each media folder the server needs to
# read transcoded output from or write uploads/podcasts to.
#
# Example (uncomment and adjust):
#   ReadWritePaths=/var/airsonic /srv/music /srv/podcasts
ReadWritePaths=/var/airsonic

[Install]
WantedBy=multi-user.target
UNIT_EOF

systemctl daemon-reload
ok "Systemd unit installed and daemon reloaded."

# ---------------------------------------------------------------------------
# f) Override file
# ---------------------------------------------------------------------------
echo
echo "=== Override file ==="

if [[ -f "${DEFAULT_FILE}" ]]; then
    ok "${DEFAULT_FILE} already exists — skipping."
else
    cat > "${DEFAULT_FILE}" << 'DEFAULT_EOF'
# Airsonic-Pulse environment overrides
# Uncomment and adjust values as needed, then restart: systemctl restart airsonic

# JAVA_JAR=/var/airsonic/airsonic.war
# AIRSONIC_HOME=/var/airsonic
# PORT=4040
# CONTEXT_PATH=/
# JAVA_OPTS="-Xmx1024m -Xms512m"
DEFAULT_EOF
    ok "${DEFAULT_FILE} created."
fi

# ---------------------------------------------------------------------------
# g) Optional: ffmpeg
# ---------------------------------------------------------------------------
echo
echo "=== Transcoding (optional) ==="

if ask "Install ffmpeg for transcoding support?"; then
    apt-get install -y ffmpeg
    ok "ffmpeg installed."
else
    warn "Skipping ffmpeg. Transcoding will not be available until ffmpeg is installed."
fi

# ---------------------------------------------------------------------------
# h) Optional: firewall
# ---------------------------------------------------------------------------
echo
echo "=== Firewall (optional) ==="

if command -v ufw &>/dev/null && ufw status 2>/dev/null | grep -q "^Status: active"; then
    if ask "Open port 4040 in ufw?"; then
        ufw allow 4040/tcp
        ok "Port 4040 allowed in ufw."
    fi
else
    warn "ufw is not active — skipping firewall configuration."
fi

# ---------------------------------------------------------------------------
# i) Enable and start
# ---------------------------------------------------------------------------
echo
echo "=== Enabling service ==="

if ask "Start Airsonic now?"; then
    systemctl enable --now airsonic
    ok "Airsonic enabled and started."
else
    systemctl enable airsonic
    ok "Airsonic enabled. Start it manually with: systemctl start airsonic"
fi

# ---------------------------------------------------------------------------
# j) Summary
# ---------------------------------------------------------------------------
HOSTNAME_FQDN="$(hostname -f 2>/dev/null || hostname)"
echo
echo "======================================================"
ok "Airsonic-Pulse ${VERSION} installation complete."
echo
echo "  Data directory : ${AIRSONIC_HOME}"
echo "  Config file    : ${AIRSONIC_HOME}/airsonic.properties  (created on first run)"
echo "  Log file       : ${AIRSONIC_HOME}/airsonic.log"
echo "  Overrides      : ${DEFAULT_FILE}"
echo "  Web interface  : http://${HOSTNAME_FQDN}:4040"
echo
echo "  Follow logs    : journalctl -u airsonic -f"
echo "======================================================"
