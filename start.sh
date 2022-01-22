if [[ -z "$SPOTIFY_CLIENT_ID" ]]; then
  echo "----------SPOTIFY_CLIENT_ID env var is not set!----------"
  exit 32
fi
if [[ -z "$SPOTIFY_CLIENT_SECRET" ]]; then
  echo "----------SPOTIFY_CLIENT_SECRET env var is not set!----------"
  exit 32
fi
if [[ -z "$SPOTIFY_PLAYLIST_ID" ]]; then
  echo "----------SPOTIFY_PLAYLIST_ID env var is not set!----------"
  exit 32
fi
if [[ -z "$YOUTUBE_API_KEY" ]]; then
  echo "----------YOUTUBE_API_KEY env var is not set!----------"
  exit 32
fi
if [[ -z "$YOUTUBE_PLAYLIST_ID" ]]; then
  echo "----------YOUTUBE_PLAYLIST_ID env var is not set!----------"
  exit 32
fi
if [[ -z "$MUSIC_CHANNEL" ]]; then
  echo "----------MUSIC_CHANNEL env var is not set!----------"
  exit 32
fi
if [[ -z "$SONG_INFO_DIR" ]]; then
  echo "----------SONG_INFO_DIR env var is not set!----------"
  exit 32
fi
if [[ -z "$SONG_INFO_SAVED_DIR" ]]; then
  echo "----------SONG_INFO_SAVED_DIR env var is not set!----------"
  exit 32
fi

if [[ "$(docker images -q tracks-collector 2> /dev/null)" == "" ]]; then
  echo "----------$(date +"%T") building tracks-collector img----------"
  docker build . -t tracks-collector
fi

if [[ "$(docker images -q savify 2> /dev/null)" == "" ]]; then
  echo "----------$(date +"%T") building savify----------"
  git clone git@github.com:LaurenceRawlings/savify.git
  cd savify || exit 1
  docker build . -t savify
  cd ..
  rm savify -r
fi

if ! command -v yt-dlp &> /dev/null
then
  echo "----------$(date +"%T") installing yt-dlp----------"
  python3 -m pip install -U yt-dlp
  export PATH=$PATH:~/.local/bin
fi

echo "----------$(date +"%T") running track collector----------"
docker run --rm -v "$SONG_INFO_DIR":/tracks-info \
  -e SPOTIFY_CLIENT_ID="$SPOTIFY_CLIENT_ID" \
  -e SPOTIFY_CLIENT_SECRET="$SPOTIFY_CLIENT_SECRET" \
  -e SPOTIFY_PLAYLIST_ID="$SPOTIFY_PLAYLIST_ID" \
  -e YOUTUBE_API_KEY="$YOUTUBE_API_KEY" \
  -e YOUTUBE_PLAYLIST_ID="$YOUTUBE_PLAYLIST_ID" \
  tracks-collector

mkdir tracks
chmod 777 tracks
export TRACKS_DIR=$PWD/tracks

for file in $(diff -q "$SONG_INFO_DIR" "$SONG_INFO_SAVED_DIR" | grep "$SONG_INFO_DIR" | grep -E "^Only in*" | sed -n 's/[^:]*: //p')
do
  while IFS= read -r trackUrl
  do
    echo "----------$(date +"%T") downloading $trackUrl----------"
    if [[ "$trackUrl" == *spotify* ]]; then
     docker run --rm -v "$TRACKS_DIR"":/root/.local/share/Savify/downloads" \
           -e SPOTIPY_CLIENT_ID="$SPOTIFY_CLIENT_ID" \
           -e SPOTIPY_CLIENT_SECRET="$SPOTIFY_CLIENT_SECRET" \
             savify -q best "$trackUrl"
    fi
    if [[ $"$trackUrl" == *youtube* ]]; then
      yt-dlp -f 'bestaudio[ext=m4a]' --output "$TRACKS_DIR/%(title)s.mp3" "$trackUrl"
    fi
  done < "$SONG_INFO_DIR"/"$file"
  cp "$SONG_INFO_DIR"/"$file" "$SONG_INFO_SAVED_DIR"
done

cd "$TRACKS_DIR" || exit 2

for track in $(ls)
do
  echo "----------$(date +"%T") sending $track to telegram----------"
  telegram-cli -W -e "send_audio $MUSIC_CHANNEL $track"
done
cd ..
# rm "$TRACKS_DIR" -rf
unset TRACKS_DIR

echo "----------$(date +"%T") script finished----------"
