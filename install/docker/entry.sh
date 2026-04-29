#!/bin/bash

set -e

ue=$(id -u)
echo "Docker USER id: $ue"
echo "Docker PUID env: $PUID"
ge=$(id -g)
echo "Docker USER group: $ge"
echo "Docker PGID env: $PGID"

# Ensure data directories exist and are writable (runs as root before gosu)
if [ "$ue" = '0' ]; then
  mkdir -p $AIRSONIC_DIR/airsonic/transcode $AIRSONIC_DIR/music $AIRSONIC_DIR/playlists $AIRSONIC_DIR/podcasts
  [[ ! -f $AIRSONIC_DIR/airsonic/transcode/ffmpeg ]] && ln -fs /usr/bin/ffmpeg $AIRSONIC_DIR/airsonic/transcode/ffmpeg
  [[ ! -f $AIRSONIC_DIR/airsonic/transcode/lame ]] && ln -fs /usr/bin/lame $AIRSONIC_DIR/airsonic/transcode/lame
fi

if [ $ue != '0' ] || [ $ge != '0' ]; then
  # specified from USER directive, run as is
  run.sh "$@"
else
  # No USER specified, guaranteed to be root
  gn=$(getent group $PGID | cut -d":" -f1)
  if [ -z $gn ]; then
    # group doesn't exist, create it
    gn=ag
    groupadd -r -g $PGID $gn
  fi
  un=$(getent passwd $PUID | cut -d":" -f1)
  if [ -z $un ]; then
    # user doesn't exist, create it
    un=au
    useradd -r -u $PUID $un
  fi
  # add user to group
  usermod -g $gn $un
  # chown data directories to target user
  chown -R $PUID:$PGID $AIRSONIC_DIR/airsonic $AIRSONIC_DIR/music $AIRSONIC_DIR/playlists $AIRSONIC_DIR/podcasts 2>/dev/null || true
  # execute as user
  exec gosu $un run.sh "$@"
fi
