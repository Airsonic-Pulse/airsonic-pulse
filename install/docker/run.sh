#!/bin/bash

set -e

echo "Process will run as:"
echo "User: $(id -u)"
echo "Group: $(id -g)"

# Create transcode directory and symlinks if they don't exist
# These may already exist from the Dockerfile build
mkdir -p $AIRSONIC_DIR/airsonic/transcode 2>/dev/null || true
[[ ! -f $AIRSONIC_DIR/airsonic/transcode/ffmpeg ]] && ln -fs /usr/bin/ffmpeg $AIRSONIC_DIR/airsonic/transcode/ffmpeg 2>/dev/null || true
[[ ! -f $AIRSONIC_DIR/airsonic/transcode/lame ]] && ln -fs /usr/bin/lame $AIRSONIC_DIR/airsonic/transcode/lame 2>/dev/null || true

java --version
echo "JAVA_OPTS=$JAVA_OPTS"
$AIRSONIC_DIR/airsonic/transcode/ffmpeg -version || echo "WARNING: ffmpeg not available"
curl --version || echo "WARNING: curl not available"
echo "PATH=$PATH"
echo "CONTEXT_PATH=$CONTEXT_PATH"
if [[ $# -lt 1 ]] || [[ ! "$1" == "java"* ]]; then

    java_opts_array=()
    while IFS= read -r -d '' item; do
        java_opts_array+=( "$item" )
    done < <([[ $JAVA_OPTS ]] && xargs printf '%s\0' <<<"$JAVA_OPTS")
    exec java -Xmx1024m -Xms512m \
     -cp /app/WEB-INF/classes:/app/WEB-INF/lib/*:/app/WEB-INF/lib-provided/* \
     -Dserver.address=0.0.0.0 \
     -Dserver.port=$AIRSONIC_PORT \
     -Dserver.servlet.context-path=$CONTEXT_PATH \
     -Dairsonic.home=$AIRSONIC_DIR/airsonic \
     -Dairsonic.defaultMusicFolder=$AIRSONIC_DIR/music \
     -Dairsonic.defaultPodcastFolder=$AIRSONIC_DIR/podcasts \
     -Dairsonic.defaultPlaylistFolder=$AIRSONIC_DIR/playlists \
     -DUPnpPort=$UPNP_PORT \
     -Djava.awt.headless=true \
     "${java_opts_array[@]}" \
     org.airsonic.player.Application "$@"
fi

exec "$@"
