#!/bin/bash
set -euo pipefail

# format-release-notes.sh — Generate formatted release notes from merged PRs
#
# Usage:
#   ./scripts/format-release-notes.sh <release-tag> [since-tag]
#
# Examples:
#   ./scripts/format-release-notes.sh v13.1.0 v13.0.0
#   ./scripts/format-release-notes.sh v13.1.0              # auto-detects previous tag
#   ./scripts/format-release-notes.sh v13.1.0 v13.0.0 | gh release edit v13.1.0 --notes-file -
#
# Categorization priority:
#   1. Issue labels (if PR references an issue via "fixes #N" or "closes #N")
#   2. PR title pattern (fallback for PRs without issue references)
#
# Label-to-category mapping (in priority order — highest wins):
#   bug                  → Bug Fixes
#   hardening, security  → Hardening
#   infrastructure       → Infrastructure & CI
#   documentation        → Documentation
#   enhancement          → Features & Enhancements
#   dependencies, chore  → Maintenance
#
# Requires: gh CLI (authenticated), jq

RELEASE_TAG="${1:-}"
SINCE_TAG="${2:-}"

if [[ -z "${RELEASE_TAG}" ]]; then
    echo "Usage: $0 <release-tag> [since-tag]" >&2
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

# Get tag dates for filtering
MERGE_DATE_SINCE="$(git log -1 --format=%aI "${SINCE_TAG}" 2>/dev/null || echo "")"
MERGE_DATE_UNTIL="$(git log -1 --format=%aI "${RELEASE_TAG}" 2>/dev/null || echo "")"

if [[ -z "${MERGE_DATE_SINCE}" ]]; then
    echo "Error: Tag '${SINCE_TAG}' not found." >&2
    exit 1
fi

# Collect merged PRs via gh CLI
PRS_JSON="$(gh pr list \
    --state merged \
    --base main \
    --limit 200 \
    --json number,title,author,mergedAt,body \
    --jq "sort_by(.number)")"

# Filter PRs merged between the two tags
if [[ -n "${MERGE_DATE_UNTIL}" ]]; then
    PRS_FILTERED="$(echo "${PRS_JSON}" | jq -r \
        --arg since "${MERGE_DATE_SINCE}" \
        --arg until "${MERGE_DATE_UNTIL}" \
        '[.[] | select(.mergedAt > $since and .mergedAt <= $until)]')"
else
    # Tag might not exist yet (preparing notes before tagging)
    PRS_FILTERED="$(echo "${PRS_JSON}" | jq -r \
        --arg since "${MERGE_DATE_SINCE}" \
        '[.[] | select(.mergedAt > $since)]')"
fi

PR_COUNT="$(echo "${PRS_FILTERED}" | jq 'length')"
echo "Found ${PR_COUNT} merged PRs" >&2

if [[ "${PR_COUNT}" -eq 0 ]]; then
    echo "No merged PRs found between ${SINCE_TAG} and ${RELEASE_TAG}." >&2
    exit 0
fi

# ---------------------------------------------------------------------------
# Categorization
# ---------------------------------------------------------------------------

# Label priority order (highest priority first).
# When an issue has multiple labels, the highest-priority match wins.
# E.g. an issue labeled "bug" + "hardening" → Bug Fixes (bug is higher priority).
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

# Map a label name to a category key
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

# Given a newline-separated list of labels, return the category for the
# highest-priority label that maps to a category.
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

# Fallback: guess category from PR title (for PRs without issue references)
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

# Arrays for categorized output
declare -a CAT_FEATURES=()
declare -a CAT_DOCUMENTATION=()
declare -a CAT_BUGFIXES=()
declare -a CAT_HARDENING=()
declare -a CAT_INFRASTRUCTURE=()
declare -a CAT_MAINTENANCE=()

# Process each PR
while IFS= read -r pr_line; do
    PR_NUM="$(echo "${pr_line}" | jq -r '.number')"
    PR_TITLE="$(echo "${pr_line}" | jq -r '.title')"
    PR_AUTHOR="$(echo "${pr_line}" | jq -r '.author.login')"
    PR_BODY="$(echo "${pr_line}" | jq -r '.body // ""')"

    # Extract issue number from PR title or body ("fixes #N", "closes #N")
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

    # Build the formatted line
    CATEGORY=""
    if [[ -n "${ISSUE_NUM}" ]]; then
        # Fetch issue title and labels
        ISSUE_DATA="$(gh issue view "${ISSUE_NUM}" --json title,labels 2>/dev/null || echo "")"
        if [[ -n "${ISSUE_DATA}" ]]; then
            ISSUE_TITLE="$(echo "${ISSUE_DATA}" | jq -r '.title')"
            # Strip common prefixes from issue titles
            ISSUE_TITLE="${ISSUE_TITLE#\[Feature Request\]: }"
            ISSUE_TITLE="${ISSUE_TITLE#\[Bug Report\]: }"

            LINE="* #${ISSUE_NUM} ${ISSUE_TITLE} by @${PR_AUTHOR} in #${PR_NUM}"

            # Categorize from labels (highest-priority label wins)
            LABELS="$(echo "${ISSUE_DATA}" | jq -r '[.labels[].name] | join("\n")')"
            CATEGORY="$(labels_to_category "${LABELS}")"
        else
            # Issue not found — use PR title
            CLEAN_TITLE="$(echo "${PR_TITLE}" | sed -E 's/[[:space:]]*[-—]+[[:space:]]*[Ff]ixes[[:space:]]+#[0-9]+//')"
            LINE="* #${ISSUE_NUM} ${CLEAN_TITLE} by @${PR_AUTHOR} in #${PR_NUM}"
        fi
    else
        # No issue reference — use PR title as-is
        LINE="* ${PR_TITLE} by @${PR_AUTHOR} in #${PR_NUM}"
    fi

    # Fallback to title-based categorization if labels didn't match
    if [[ -z "${CATEGORY}" ]]; then
        CATEGORY="$(title_to_category "${PR_TITLE}")"
    fi

    # Add to appropriate category array
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
done < <(echo "${PRS_FILTERED}" | jq -c '.[]')

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

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

echo "**Full Changelog**: https://github.com/litebito/airsonic-pulse/compare/${SINCE_TAG}...${RELEASE_TAG}"