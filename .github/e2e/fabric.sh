#!/usr/bin/env bash
# In-game test of the Fabric build, run by the CI (see E2E.java).
# Starts a vanilla server, launches the client under a virtual display with -Dredo.e2e=true,
# animates the world from the server console while the client records, then checks the log.
# Usage: .github/e2e/fabric.sh <minecraft version>
set -u
MC="${1:-1.21.11}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRV="$ROOT/e2e-server"
OUT="$ROOT/e2e-out"
RUN="$ROOT/fabric/run"
mkdir -p "$SRV" "$OUT" "$RUN"

cleanup() {
    [ -n "${CONSOLE_FD:-}" ] && { echo "stop" >&3 2>/dev/null; sleep 5; }
    [ -n "${SERVER_PID:-}" ] && kill "$SERVER_PID" 2>/dev/null
    [ -n "${DIRECTOR_PID:-}" ] && kill "$DIRECTOR_PID" 2>/dev/null
    cp "$SRV/server.log" "$OUT/server.log" 2>/dev/null
    mkdir -p "$OUT/client"
    for d in screenshots logs crash-reports redo; do
        [ -d "$RUN/$d" ] && cp -r "$RUN/$d" "$OUT/client/"
    done
    return 0
}
trap cleanup EXIT

echo "== Vanilla $MC server"
cd "$SRV"
VERSION_URL=$(curl -fsSL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
    | jq -r --arg v "$MC" '.versions[] | select(.id == $v) | .url')
[ -n "$VERSION_URL" ] || { echo "unknown Minecraft version $MC"; exit 1; }
SERVER_URL=$(curl -fsSL "$VERSION_URL" | jq -r '.downloads.server.url')
curl -fsSL -o server.jar "$SERVER_URL" || { echo "server download failed"; exit 1; }
# The test server only runs inside the CI job, for this test.
echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
enforce-secure-profile=false
level-type=minecraft\:flat
generate-structures=false
view-distance=6
simulation-distance=6
spawn-protection=0
spawn-monsters=false
motd=Redo E2E
EOF
rm -f console && mkfifo console
java -Xmx1G -jar server.jar nogui < console > server.log 2>&1 &
SERVER_PID=$!
exec 3> console
CONSOLE_FD=3
for i in $(seq 1 240); do
    grep -q "Done (" server.log && break
    kill -0 "$SERVER_PID" 2>/dev/null || { echo "server died"; cat server.log; exit 1; }
    sleep 1
done
grep -q "Done (" server.log || { echo "server did not start"; tail -50 server.log; exit 1; }
echo "server ready"

# Server-side "director": once the player is in, spawn animals and build a tower block by
# block so the recording has entities, block changes and chat.
(
    for i in $(seq 1 400); do
        NAME=$(grep -oE '[A-Za-z0-9_]+ joined the game' server.log | head -1 | cut -d' ' -f1)
        [ -n "$NAME" ] && break
        sleep 1
    done
    [ -n "$NAME" ] || exit 0
    sleep 2
    {
        echo "op $NAME"
        echo "time set day"
        echo "weather clear"
        for m in pig pig cow sheep chicken villager; do
            echo "execute at $NAME run summon minecraft:$m ~$((RANDOM % 9 - 4)) ~ ~$((RANDOM % 5 + 3))"
        done
        echo "execute at $NAME run summon minecraft:armor_stand ~-3 ~ ~4"
    } >&3
    sleep 2
    echo "say Bonjour depuis le serveur de test" >&3
    for y in 0 1 2 3 4 5 6 7; do
        echo "execute at $NAME run setblock ~3 ~$y ~6 minecraft:gold_block" >&3
        sleep 1
    done
    echo "execute at $NAME run setblock ~3 ~8 ~6 minecraft:glowstone" >&3
    sleep 1
    echo "execute at $NAME run summon minecraft:lightning_bolt ~6 ~ ~8" >&3
) &
DIRECTOR_PID=$!

echo "== Client $MC"
cd "$ROOT/fabric"
cat > "$RUN/options.txt" <<'EOF'
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
tutorialStep:none
renderDistance:6
simulationDistance:6
soundCategory_master:0.0
EOF
chmod +x gradlew
REDO_E2E=1 timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" \
    ./gradlew ":$MC:runClient" --console=plain \
    --args="--width 1280 --height 720 --quickPlayMultiplayer localhost:25565"
echo "client exited with $?"

LOG="$RUN/logs/latest.log"
if grep -q "REDO E2E OK" "$LOG" 2>/dev/null; then
    echo "== REDO E2E OK"
    grep "\[Redo E2E\]" "$LOG"
    exit 0
fi
echo "== REDO E2E FAILED"
grep -E "\[Redo E2E\]|REDO E2E FAIL|Mixin|mixin|Exception|Error" "$LOG" 2>/dev/null | head -150
exit 1
