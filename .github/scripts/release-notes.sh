#!/bin/bash
set -euo pipefail

# release-notes.sh — Generate formatted release notes and write to file
#
# Usage:
#   ./scripts/release-notes.sh <release-tag> [since-tag] [--dry-run]
#
# Behavior:
#   - Generates the auto-categorized "What's Changed" section from the PRs whose
#     squash commits are reachable from the release ref: the <release-tag> if it
#     already exists, otherwise HEAD (run it from the release branch before
#     tagging). PR numbers come from the trailing "(#N)" in each commit subject;
#     for cherry-picked squashes carrying two references, the last one is the PR
#     that landed on this ref. Commits without a "(#N)" are skipped with a
#     warning. This makes the listing correct on release branches, where
#     selecting merged PRs by date listed main-only PRs (#340).
#   - Writes to docs/release-notes/<tag>.md
#   - Also emits the auto section to stdout for piping/preview
#   - --dry-run: print the generated section to stdout only; no file is
#     created or modified
#   - On first run for a tag, creates the file with a title header and marker:
#       # Airsonic-Pulse <tag>
#
#       <!-- AUTO-GENERATED-BELOW -->
#       ## What's Changed
#       [... auto content ...]
#   - On subsequent runs, finds the <!-- AUTO-GENERATED-BELOW --> marker and
#     replaces everything from that line down with freshly-generated content.
#     Content above the marker (Highlights, Upgrade notes, etc.) is preserved.
#   - If the file exists but has no marker line, appends marker + auto content
#     to the end (safety fallback).
#
# Examples:
#   ./scripts/release-notes.sh v13.1.0 v13.0.0
#   ./scripts/release-notes.sh v13.1.0              # auto-detects previous tag
#   ./scripts/release-notes.sh v13.1.0 | less       # preview while writing
#
# Categorization priority:
#   1. Issue labels (if PR references an issue via "fixes #N" or "closes #N")
#   2. PR title pattern (fallback for PRs without issue references)
#
# Label-to-category mapping (highest priority first):
#   bug                  → Bug Fixes
#   hardening, security  → Hardening & Security
#   infrastructure       → Infrastructure & CI
#   documentation        → Documentation
#   enhancement          → Features & Enhancements
#   dependencies, chore  → Maintenance
#
# Requires: gh CLI (authenticated), jq

RELEASE_TAG=""
SINCE_TAG=""
DRY_RUN=0
for arg in "$@"; do
    if [[ "${arg}" == "--dry-run" ]]; then
        DRY_RUN=1
    elif [[ -z "${RELEASE_TAG}" ]]; then
        RELEASE_TAG="${arg}"
    elif [[ -z "${SINCE_TAG}" ]]; then
        SINCE_TAG="${arg}"
    else
        echo "Error: unexpected argument '${arg}'" >&2
        exit 1
    fi
done

MARKER="<!-- AUTO-GENERATED-BELOW -->"
NOTES_DIR="docs/release-notes"
NOTES_FILE="${NOTES_DIR}/${RELEASE_TAG}.md"

if [[ -z "${RELEASE_TAG}" ]]; then
    echo "Usage: $0 <release-tag> [since-tag] [--dry-run]" >&2
    echo "  e.g. $0 v13.1.0 v13.0.0" >&2
    exit 1
fi

# Auto-detect previous tag if not provided
if [[ -z "${SINCE_TAG}" ]]; then
    SINCE_TAG="$(git tag --sort=-v:refname | grep -v "^${RELEASE_TAG}$" | head -1)"
    if [[ -z "${SINCE_TAG}" ]]; then
        echo "Error: Could not determine previous tag. Specify it explicitly." >&2
        exit 1
    fi
    echo "Auto-detected previous tag: ${SINCE_TAG}" >&2
fi

echo "Generating release notes: ${SINCE_TAG} → ${RELEASE_TAG}" >&2

if ! git rev-parse --verify --quiet "${SINCE_TAG}^{commit}" > /dev/null; then
    echo "Error: Tag '${SINCE_TAG}' not found." >&2
    exit 1
fi

# The release tag usually doesn't exist yet when notes are being prepared; in
# that case use HEAD (run the script from the release branch). Once the tag
# exists the script is runnable from anywhere.
if git rev-parse --verify --quiet "${RELEASE_TAG}^{commit}" > /dev/null; then
    TARGET_REF="${RELEASE_TAG}"
else
    TARGET_REF="HEAD"
    echo "Tag '${RELEASE_TAG}' does not exist yet — using HEAD as the release ref." >&2
fi

# Collect the PR number for every commit reachable from the release ref but not
# from the since tag. The subject's LAST "(#N)" is the PR that landed on this
# ref (cherry-picked squashes carry the original main PR number earlier in the
# subject and the branch pick PR last).
PR_NUMBERS=()
while IFS=$'\t' read -r commit_hash commit_subject; do
    LAST_REF="$(grep -oE '\(#[0-9]+\)' <<< "${commit_subject}" | tail -1 || true)"
    if [[ -z "${LAST_REF}" ]]; then
        echo "  skipping ${commit_hash} (no PR reference in subject): ${commit_subject}" >&2
        continue
    fi
    LAST_REF="${LAST_REF#(#}"
    PR_NUMBERS+=("${LAST_REF%)}")
done < <(git log --format='%h%x09%s' "${SINCE_TAG}..${TARGET_REF}")

if [[ ${#PR_NUMBERS[@]} -eq 0 ]]; then
    echo "No PR-referencing commits found between ${SINCE_TAG} and ${TARGET_REF}." >&2
    exit 0
fi

# Deduplicate and sort ascending (the existing output ordering convention)
mapfile -t PR_NUMBERS < <(printf '%s\n' "${PR_NUMBERS[@]}" | sort -n -u)
echo "Found ${#PR_NUMBERS[@]} PRs on ${TARGET_REF} since ${SINCE_TAG}" >&2

# ---------------------------------------------------------------------------
# Categorization
# ---------------------------------------------------------------------------

# Label priority order (highest priority first).
LABEL_PRIORITY=(
    bug
    hardening
    security
    infrastructure
    documentation
    enhancement
    dependencies
    chore
)

label_to_category() {
    local label="$1"
    case "${label}" in
        enhancement)     echo "features" ;;
        documentation)   echo "documentation" ;;
        bug)             echo "bugfixes" ;;
        hardening)       echo "hardening" ;;
        security)        echo "hardening" ;;
        infrastructure)  echo "infrastructure" ;;
        dependencies)    echo "maintenance" ;;
        chore)           echo "maintenance" ;;
        *)               echo "" ;;
    esac
}

labels_to_category() {
    local labels="$1"
    local priority_label
    for priority_label in "${LABEL_PRIORITY[@]}"; do
        if echo "${labels}" | grep -qx "${priority_label}"; then
            label_to_category "${priority_label}"
            return
        fi
    done
    echo ""
}

title_to_category() {
    local title_lower
    title_lower="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
    if [[ "${title_lower}" =~ ^feat ]]; then
        echo "features"
    elif [[ "${title_lower}" =~ ^fix || "${title_lower}" =~ "rebrand" ]]; then
        echo "bugfixes"
    elif [[ "${title_lower}" =~ ^hardening || "${title_lower}" =~ "audit" ]]; then
        echo "hardening"
    elif [[ "${title_lower}" =~ ^chore || "${title_lower}" =~ "workflow" || \
            "${title_lower}" =~ "docker" || "${title_lower}" =~ "ci" || \
            "${title_lower}" =~ "matrix" || "${title_lower}" =~ "release" ]]; then
        echo "infrastructure"
    elif [[ "${title_lower}" =~ "bump" || "${title_lower}" =~ "deps" || \
            "${title_lower}" =~ "dependabot" ]]; then
        echo "maintenance"
    elif [[ "${title_lower}" =~ ^docs || "${title_lower}" =~ "documentation" || \
            "${title_lower}" =~ "install" ]]; then
        echo "documentation"
    else
        echo "features"
    fi
}

declare -a CAT_FEATURES=()
declare -a CAT_DOCUMENTATION=()
declare -a CAT_BUGFIXES=()
declare -a CAT_HARDENING=()
declare -a CAT_INFRASTRUCTURE=()
declare -a CAT_MAINTENANCE=()

for PR_NUM in "${PR_NUMBERS[@]}"; do
    # gh pr view resolves the PR regardless of its base branch, so PRs targeting
    # release/* directly are handled the same as PRs merged to main.
    if ! pr_json="$(gh pr view "${PR_NUM}" --json number,title,author,body 2>/dev/null)"; then
        echo "  skipping #${PR_NUM}: gh pr view failed (not a PR?)" >&2
        continue
    fi
    PR_TITLE="$(echo "${pr_json}" | jq -r '.title')"
    PR_AUTHOR="$(echo "${pr_json}" | jq -r '.author.login')"
    PR_BODY="$(echo "${pr_json}" | jq -r '.body // ""')"

    ISSUE_NUM=""
    if [[ "${PR_TITLE}" =~ [Ff]ixes[[:space:]]+#([0-9]+) ]]; then
        ISSUE_NUM="${BASH_REMATCH[1]}"
    elif [[ "${PR_TITLE}" =~ [Cc]loses[[:space:]]+#([0-9]+) ]]; then
        ISSUE_NUM="${BASH_REMATCH[1]}"
    elif [[ "${PR_BODY}" =~ [Ff]ixes[[:space:]]+#([0-9]+) ]]; then
        ISSUE_NUM="${BASH_REMATCH[1]}"
    elif [[ "${PR_BODY}" =~ [Cc]loses[[:space:]]+#([0-9]+) ]]; then
        ISSUE_NUM="${BASH_REMATCH[1]}"
    fi

    CATEGORY=""
    if [[ -n "${ISSUE_NUM}" ]]; then
        ISSUE_DATA="$(gh issue view "${ISSUE_NUM}" --json title,labels 2>/dev/null || echo "")"
        if [[ -n "${ISSUE_DATA}" ]]; then
            ISSUE_TITLE="$(echo "${ISSUE_DATA}" | jq -r '.title')"
            ISSUE_TITLE="${ISSUE_TITLE#\[Feature Request\]: }"
            ISSUE_TITLE="${ISSUE_TITLE#\[Bug Report\]: }"

            LINE="* #${ISSUE_NUM} ${ISSUE_TITLE} by @${PR_AUTHOR} in #${PR_NUM}"

            LABELS="$(echo "${ISSUE_DATA}" | jq -r '[.labels[].name] | join("\n")')"
            CATEGORY="$(labels_to_category "${LABELS}")"
        else
            CLEAN_TITLE="$(echo "${PR_TITLE}" | sed -E 's/[[:space:]]*[-—]+[[:space:]]*[Ff]ixes[[:space:]]+#[0-9]+//')"
            LINE="* #${ISSUE_NUM} ${CLEAN_TITLE} by @${PR_AUTHOR} in #${PR_NUM}"
        fi
    else
        LINE="* ${PR_TITLE} by @${PR_AUTHOR} in #${PR_NUM}"
    fi

    if [[ -z "${CATEGORY}" ]]; then
        CATEGORY="$(title_to_category "${PR_TITLE}")"
    fi

    case "${CATEGORY}" in
        features)       CAT_FEATURES+=("${LINE}") ;;
        documentation)  CAT_DOCUMENTATION+=("${LINE}") ;;
        bugfixes)       CAT_BUGFIXES+=("${LINE}") ;;
        hardening)      CAT_HARDENING+=("${LINE}") ;;
        infrastructure) CAT_INFRASTRUCTURE+=("${LINE}") ;;
        maintenance)    CAT_MAINTENANCE+=("${LINE}") ;;
        *)              CAT_FEATURES+=("${LINE}") ;;
    esac

    echo "  PR #${PR_NUM} → ${CATEGORY}${ISSUE_NUM:+ (issue #${ISSUE_NUM})}" >&2
done

# ---------------------------------------------------------------------------
# Build the auto section
# ---------------------------------------------------------------------------

AUTO_CONTENT="$(mktemp --suffix=.md)"
trap 'rm -f "${AUTO_CONTENT}"' EXIT

{
    echo "## What's Changed"
    echo ""

    if [[ ${#CAT_FEATURES[@]} -gt 0 ]]; then
        echo "### Features & Enhancements"
        printf '%s\n' "${CAT_FEATURES[@]}"
        echo ""
    fi

    if [[ ${#CAT_DOCUMENTATION[@]} -gt 0 ]]; then
        echo "### Documentation"
        printf '%s\n' "${CAT_DOCUMENTATION[@]}"
        echo ""
    fi

    if [[ ${#CAT_BUGFIXES[@]} -gt 0 ]]; then
        echo "### Bug Fixes"
        printf '%s\n' "${CAT_BUGFIXES[@]}"
        echo ""
    fi

    if [[ ${#CAT_HARDENING[@]} -gt 0 ]]; then
        echo "### Hardening & Security"
        printf '%s\n' "${CAT_HARDENING[@]}"
        echo ""
    fi

    if [[ ${#CAT_INFRASTRUCTURE[@]} -gt 0 ]]; then
        echo "### Infrastructure & CI"
        printf '%s\n' "${CAT_INFRASTRUCTURE[@]}"
        echo ""
    fi

    if [[ ${#CAT_MAINTENANCE[@]} -gt 0 ]]; then
        echo "### Maintenance"
        printf '%s\n' "${CAT_MAINTENANCE[@]}"
        echo ""
    fi

    echo "**Full Changelog**: https://github.com/Airsonic-Pulse/airsonic-pulse/compare/${SINCE_TAG}...${RELEASE_TAG}"
} > "${AUTO_CONTENT}"

# ---------------------------------------------------------------------------
# Write to file (creating, updating, or appending as appropriate)
# ---------------------------------------------------------------------------

if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "Dry run — no file written." >&2
    cat "${AUTO_CONTENT}"
    exit 0
fi

mkdir -p "${NOTES_DIR}"

if [[ ! -f "${NOTES_FILE}" ]]; then
    # First run for this tag — create the file with title, marker, and auto content
    {
        echo "# Airsonic-Pulse ${RELEASE_TAG}"
        echo ""
        echo "${MARKER}"
        cat "${AUTO_CONTENT}"
    } > "${NOTES_FILE}"
    echo "Created ${NOTES_FILE}" >&2
    echo "Add Highlights / Upgrade notes between the title and the ${MARKER} marker," >&2
    echo "then commit and push before tagging." >&2

elif grep -qF "${MARKER}" "${NOTES_FILE}"; then
    # File exists with marker — preserve everything up to and including the marker,
    # replace everything below with fresh auto content
    PRESERVED="$(mktemp --suffix=.md)"
    trap 'rm -f "${AUTO_CONTENT}" "${PRESERVED}"' EXIT

    # Extract content up to and including the marker line
    awk -v marker="${MARKER}" '
        { print }
        index($0, marker) { exit }
    ' "${NOTES_FILE}" > "${PRESERVED}"

    {
        cat "${PRESERVED}"
        cat "${AUTO_CONTENT}"
    } > "${NOTES_FILE}"

    echo "Updated ${NOTES_FILE} (regenerated auto section below ${MARKER})" >&2

else
    # File exists but no marker — append marker + auto content as safety fallback
    {
        echo ""
        echo "${MARKER}"
        cat "${AUTO_CONTENT}"
    } >> "${NOTES_FILE}"
    echo "Appended marker + auto content to ${NOTES_FILE} (no existing marker found)" >&2
fi

# Always emit to stdout for piping/preview
cat "${AUTO_CONTENT}"