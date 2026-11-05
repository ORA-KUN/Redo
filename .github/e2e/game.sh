#!/usr/bin/env bash
# In-game test of a Redo build, run by the CI (see E2E.java and LegacyE2E.java).
# Starts a vanilla server, launches the client under a virtual display with -Dredo.e2e=true,
# animates the world from the server console while the client records, then checks the log.
# Usage: .github/e2e/game.sh fabric 1.21.11 | .github/e2e/game.sh forge 1.8.9
set -u
LOADER="${1:-fabric}"
MC="${2:-1.21.11}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRV="$ROOT/e2e-server"
OUT="$ROOT/e2e-out"
if [ "$LOADER" = "forge" ]; then
    CLIENT_DIR="$ROOT/forge-1.8.9"
    TASK="runE2E"
    CLIENT_ARGS="--width 1280 --height 720 --server localhost --port 25565"
    SERVER_JAVA="${JAVA_HOME_8_X64:+$JAVA_HOME_8_X64/bin/}java"
    LEVEL_TYPE="FLAT"
    LEGACY=1
else
    CLIENT_DIR="$ROOT/fabric"
    TASK=":$MC:runClient"
    CLIENT_ARGS="--width 1280 --height 720 --quickPlayMultiplayer localhost:25565"
    SERVER_JAVA="java"
    LEVEL_TYPE="minecraft\\:flat"
    LEGACY=0
fi
RUN="$CLIENT_DIR/run"
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
cat > server.properties <<EOF
online-mode=false
enforce-secure-profile=false
level-type=$LEVEL_TYPE
generate-structures=false
view-distance=6
simulation-distance=6
spawn-protection=0
white-list=false
enforce-whitelist=false
spawn-monsters=false
motd=Redo E2E
EOF
rm -f console && mkfifo console
"$SERVER_JAVA" -Xmx1G -jar server.jar nogui < console > server.log 2>&1 &
SERVER_PID=$!
exec 3> console
CONSOLE_FD=3
for i in $(seq 1 240); do
    grep -q "Done (" server.log && break
    kill -0 "$SERVER_PID" 2>/dev/null || { echo "server died"; cat server.log; exit 1; }
    sleep 1
done
grep -q "Done (" server.log || { echo "server did not start"; tail -50 server.log; exit 1; }
# 26.3 servers start with the whitelist on: the test player must be let in
echo "whitelist off" >&3
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
    if [ "$LEGACY" = 1 ]; then
        # 1.8 command syntax
        AT="execute $NAME ~ ~ ~"
        MOBS="Pig Pig Cow Sheep Chicken Villager"; STAND="ArmorStand"; BOLT="LightningBolt"
        GOLD="gold_block"; GLOW="glowstone"
    else
        AT="execute at $NAME run"
        MOBS="minecraft:pig minecraft:pig minecraft:cow minecraft:sheep minecraft:chicken minecraft:villager"
        STAND="minecraft:armor_stand"; BOLT="minecraft:lightning_bolt"
        GOLD="minecraft:gold_block"; GLOW="minecraft:glowstone"
    fi
    {
        echo "op $NAME"
        echo "time set day"
        echo "weather clear"
        for m in $MOBS; do
            echo "$AT summon $m ~$((RANDOM % 9 - 4)) ~ ~$((RANDOM % 5 + 3))"
        done
        echo "$AT summon $STAND ~-3 ~ ~4"
    } >&3
    sleep 2
    echo "say Bonjour depuis le serveur de test" >&3
    for y in 0 1 2 3 4 5 6 7; do
        echo "$AT setblock ~3 ~$y ~6 $GOLD" >&3
        sleep 1
    done
    echo "$AT setblock ~3 ~8 ~6 $GLOW" >&3
    sleep 1
    echo "$AT summon $BOLT ~6 ~ ~8" >&3
) &
DIRECTOR_PID=$!

echo "== Client $MC ($LOADER)"
cd "$CLIENT_DIR"
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
# 26.3 opens its window with SDL: let it pick an EGL config (its strict GLX visual match finds
# none on the virtual display); the Vulkan fallback runs on Mesa's software driver.
export SDL_VIDEO_FORCE_EGL=1
REDO_E2E=1 timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" \
    ./gradlew "$TASK" --console=plain --args="$CLIENT_ARGS" 2>&1 | tee "$OUT/client-console.log"
echo "client exited with ${PIPESTATUS[0]}"

# The game log, or the console output when the run has no log file
LOG="$RUN/logs/latest.log"
[ -f "$LOG" ] || LOG="$OUT/client-console.log"
if grep -q "REDO E2E OK" "$LOG" "$OUT/client-console.log" 2>/dev/null; then
    echo "== REDO E2E OK"
    grep "\[Redo E2E\]" "$LOG"
    exit 0
fi
echo "== REDO E2E FAILED"
grep -E "\[Redo E2E\]|REDO E2E FAIL|Mixin|mixin|Exception|Error" "$OUT/client-console.log" 2>/dev/null | head -200
echo "== Server log (end)"
tail -60 "$SRV/server.log"
exit 1
