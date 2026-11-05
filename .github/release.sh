#!/usr/bin/env bash
# Publishes the jars built by the CI (in dist/redo-<Minecraft version>/) on the GitHub Release
# of the current mod version, replacing it if it already exists. Run by build.yml on main.
set -euo pipefail
VERSION=$(sed -nE 's/^version *= *"([^"]+)".*/\1/p' core/build.gradle.kts)
TAG="v$VERSION"
REPO="${GITHUB_REPOSITORY:-ORA-KUN/Redo}"
mkdir -p release
for d in dist/redo-*/; do
    mc=$(basename "$d"); mc=${mc#redo-}
    jar=$(ls "$d"*.jar | grep -vE -- '-(sources|dev)\.jar$' | head -1)
    cp "$jar" "release/redo-$mc.jar"
done
ls -l release

link() { echo "https://github.com/$REPO/releases/latest/download/redo-$1.jar"; }
cat > notes.md <<NOTES
Redo $VERSION, construit et testé en jeu par la CI (commit ${GITHUB_SHA:-local}).

| Minecraft | Chargeur | Jar |
|---|---|---|
| 1.8.9 | Forge 1.8.9 | [redo-1.8.9.jar]($(link 1.8.9)) |
| 1.21.11 | Fabric Loader 0.17+ | [redo-1.21.11.jar]($(link 1.21.11)) |
| 26.1 | Fabric Loader 0.17+ | [redo-26.1.jar]($(link 26.1)) |
| 26.2 | Fabric Loader 0.17+ | [redo-26.2.jar]($(link 26.2)) |
| 26.3 | Fabric Loader 0.17+ | [redo-26.3.jar]($(link 26.3)) |

Mets le jar de ta version dans \`.minecraft/mods\`. Fabric API n'est pas nécessaire.
Ces liens pointent toujours vers la dernière version publiée.
NOTES

# A merge without a version bump replaces the Release (and its tag) of that version
gh release delete "$TAG" --repo "$REPO" --cleanup-tag --yes 2>/dev/null || true
gh release create "$TAG" release/*.jar --repo "$REPO" --target "${GITHUB_SHA:-main}" \
    --title "Redo $VERSION" --notes-file notes.md --latest
