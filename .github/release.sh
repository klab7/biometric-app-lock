#!/usr/bin/env bash
set -euo pipefail

VERSION_INPUT=${1:-}

if [[ -z "$VERSION_INPUT" ]]; then
	echo "Usage: ./.github/release.sh <version>"
	echo "Example: ./.github/release.sh 0.3.0"
	echo "Example: ./.github/release.sh v0.3.0"
	exit 1
fi

if [[ "$VERSION_INPUT" == vv* ]]; then
	echo "Error: invalid version '$VERSION_INPUT'"
	echo "Example: ./.github/release.sh v0.3.0"
	exit 1
fi

VERSION="${VERSION_INPUT#v}"

SEMVER_RE='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'
if [[ ! "$VERSION" =~ $SEMVER_RE ]]; then
	echo "Error: invalid version '$VERSION_INPUT'"
	echo "Example: ./.github/release.sh 0.3.0"
	exit 1
fi

TAG="v$VERSION"

if [[ -n "$(git status --porcelain)" ]]; then
	echo "Error: working tree not clean"
	exit 1
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
	echo "Error: not on main branch (currently on $BRANCH)"
	exit 1
fi

git pull --ff-only

if ! command -v git-cliff &>/dev/null; then
	echo "Error: git-cliff not installed"
	exit 1
fi

if [[ ! -f ".github/cliff.toml" ]]; then
	echo "Error: .github/cliff.toml not found"
	exit 1
fi

if [[ ! -f "tools/cliff_to_changelog.py" ]]; then
	echo "Error: tools/cliff_to_changelog.py not found"
	exit 1
fi

PREV_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "none")
COMMIT_COUNT=$(git rev-list "${PREV_TAG}..HEAD" --count 2>/dev/null || git rev-list HEAD --count)

echo "Releasing ${PREV_TAG} → ${TAG} (${COMMIT_COUNT} commits)"
echo ""
git cliff --config .github/cliff.toml --tag "$TAG" --unreleased
echo ""

read -rp "Push and release? [y/N] " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
	echo "Aborted"
	exit 1
fi

# Bump version.name + version.code in gradle.properties
# Scheme: MAJOR * 10000 + MINOR * 100 + PATCH (matches existing 0.1.0=100, 0.2.0=200)
IFS='.' read -r MAJOR MINOR PATCH _ <<<"$VERSION"
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))

sed -i "s/^version\.name=.*/version.name=${VERSION}/" gradle.properties
sed -i "s/^version\.code=.*/version.code=${VERSION_CODE}/" gradle.properties

# Generate CHANGELOG.json from cliff context (last 5 versions)
git cliff --config .github/cliff.toml --context --tag "$TAG" \
	| python3 tools/cliff_to_changelog.py "$TAG" >CHANGELOG.json

git add gradle.properties CHANGELOG.json
git commit -s -S -m "build: bump version to ${VERSION}"

git tag -s "$TAG" -m "Release $TAG"
git push origin main "$TAG"

echo ""
echo "Released $TAG"
