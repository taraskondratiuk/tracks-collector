#!/bin/bash
echo "----------$(date +"%T") script start----------"

if [[ -z "$SPOTIFY_CLIENT_ID" ]]; then
  echo "----------SPOTIFY_CLIENT_ID env var is not set!----------"
  exit 32
fi
if [[ -z "$SPOTIFY_CLIENT_SECRET" ]]; then
  echo "----------SPOTIFY_CLIENT_SECRET env var is not set!----------"
  exit 32
fi
if [[ -z "$YOUTUBE_API_KEY" ]]; then
  echo "----------YOUTUBE_API_KEY env var is not set!----------"
  exit 32
fi
if [[ -z "$PERSISTENCE_DIR" ]]; then
  echo "----------PERSISTENCE_DIR env var is not set!----------"
  exit 32
fi
if [[ -z "$TRACKS_COLLECTOR_BOT_PORT" ]]; then
  echo "----------TRACKS_COLLECTOR_BOT_PORT env var is not set!----------"
  exit 32
fi
if [[ -z "$TRACKS_COLLECTOR_BOT_TOKEN" ]]; then
  echo "----------TRACKS_COLLECTOR_BOT_TOKEN env var is not set!----------"
  exit 32
fi

if [[ "$(docker images -q tracks-collector-bot 2> /dev/null)" == "" ]]; then
  echo "----------$(date +"%T") building tracks-collector-bot img----------"
  docker build . -f docker/bot/Dockerfile -t tracks-collector-bot
fi

if [[ "$(docker images -q tracks-collector 2> /dev/null)" == "" ]]; then
  echo "----------$(date +"%T") building tracks-collector img----------"
  docker build . -f docker/collector/Dockerfile -t tracks-collector
fi

if [[ "$(docker images -q savify 2> /dev/null)" == "" ]]; then
  echo "----------$(date +"%T") building savify----------"
  git clone https://github.com/LaurenceRawlings/savify.git
  cd savify || exit 1
  docker build . -t savify
  cd ..
  rm savify -rf
fi

mkdir -p tracks
chmod 777 tracks
export TRACKS_DIR=$PWD/tracks
export PATH=$PATH:~/.local/bin
if ! command -v yt-dlp &> /dev/null
then
  echo "----------$(date +"%T") installing yt-dlp----------"
  python3 -m pip install -U yt-dlp
fi

if [[ "$(docker ps | grep tracks-collector-bot 2> /dev/null)" == "" ]]; then
  echo "----------$(date +"%T") starting tracks-collector-bot----------"
  docker run -d \
   --restart=unless-stopped \
   -v "$PERSISTENCE_DIR":/tracks-info \
   -v "$TRACKS_DIR":/tracks \
   -e SPOTIFY_CLIENT_ID="$SPOTIFY_CLIENT_ID" \
   -e SPOTIFY_CLIENT_SECRET="$SPOTIFY_CLIENT_SECRET" \
   -e YOUTUBE_API_KEY="$YOUTUBE_API_KEY" \
   -e TRACKS_COLLECTOR_BOT_TOKEN="$TRACKS_COLLECTOR_BOT_TOKEN" \
   -p $TRACKS_COLLECTOR_BOT_PORT:8080 \
   --name tracks-collector-bot \
   tracks-collector-bot
fi

echo "----------$(date +"%T") running track collector----------"
docker run --rm -d -v "$PERSISTENCE_DIR":/tracks-info \
  -e SPOTIFY_CLIENT_ID="$SPOTIFY_CLIENT_ID" \
  -e SPOTIFY_CLIENT_SECRET="$SPOTIFY_CLIENT_SECRET" \
  -e YOUTUBE_API_KEY="$YOUTUBE_API_KEY" \
  --name tracks-collector \
  tracks-collector

docker wait tracks-collector

chmod 777 -R $PERSISTENCE_DIR
for chatIdDir in $(ls "$PERSISTENCE_DIR")
do
  echo "---------sending tracks for chat id $chatIdDir----------"
  for file in $(diff -q "$PERSISTENCE_DIR/$chatIdDir/should_be_saved" "$PERSISTENCE_DIR/$chatIdDir/saved" | grep "$PERSISTENCE_DIR/$chatIdDir/should_be_saved" | grep -E "^Only in*" | sed -n 's/[^:]*: //p')
  do
    while IFS= read -r trackUrl
    do
      if [ "$trackUrl" != "" ]; then
        echo "----------$(date +"%T") downloading $trackUrl----------"
        if [[ "$trackUrl" == *spotify* ]]; then
         docker run --rm -v "$TRACKS_DIR/$chatIdDir"":/root/.local/share/Savify/downloads" \
               -e SPOTIPY_CLIENT_ID="$SPOTIFY_CLIENT_ID" \
               -e SPOTIPY_CLIENT_SECRET="$SPOTIFY_CLIENT_SECRET" \
               --name savify \
                 savify -q best "$trackUrl"
        fi
        if [[ $"$trackUrl" == *youtube* ]]; then
          yt-dlp -f 'bestaudio[ext=m4a]' --output "$TRACKS_DIR/$chatIdDir/%(title)s.mp3" "$trackUrl"
        fi
      fi
    done < "$PERSISTENCE_DIR/$chatIdDir/should_be_saved/$file"
    cp "$PERSISTENCE_DIR/$chatIdDir/should_be_saved/$file" "$PERSISTENCE_DIR/$chatIdDir/saved"
  done

  chmod 777 -R $TRACKS_DIR
  for track in $TRACKS_DIR/$chatIdDir/*
  do
    test -f "$track" || continue
    [[ "$track" =~ .*lock ]] && continue
    trackFile=$(echo "$track" | sed 's:.*/::')
    echo "----------$(date +"%T") sending $trackFile to telegram chat id $chatIdDir----------"
    curl -X POST -d '{"chatId": "'"$chatIdDir"'", "trackPath": "'"/tracks/$chatIdDir/$trackFile"'"}' localhost:$TRACKS_COLLECTOR_BOT_PORT/sendTrack
    sleep 1
  done

  while [[ $(find $TRACKS_DIR/$chatIdDir -name *.lock) ]]
  do
    echo "----------$(date +"%T") lock files present----------"
    sleep 5
  done
done

rm "$TRACKS_DIR"/* -rf
unset TRACKS_DIR

echo "----------$(date +"%T") script finished----------"
