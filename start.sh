if [[ "$(docker images -q tracks-collector 2> /dev/null)" == "" ]]; then
  docker build . -t tracks-collector
fi

if [[ "$(docker images -q savify 2> /dev/null)" == "" ]]; then
  git clone git@github.com:LaurenceRawlings/savify.git
  cd savify || exit 1
  docker build . -t savify
  cd ..
  rm savify -r
fi

if ! command -v yt-dlp &> /dev/null
then
  python3 -m pip install -U yt-dlp
  export PATH=$PATH:~/.local/bin
fi

if ! command -v telegram-cli &> /dev/null
then
  if ! command -v snap &> /dev/null
  then
    sudo apt install --assume-yes snapd
    export PATH=$PATH:/snap/bin
  fi
  sudo snap install telegram-cli
fi

docker run --rm -v "$SONG_INFO_DIR":/tracks-info \
  -e SPOTIPY_CLIENT_ID="$SPOTIPY_CLIENT_ID" \
  -e SPOTIPY_CLIENT_SECRET="$SPOTIPY_CLIENT_SECRET" \
  -e SPOTIPY_PLAYLIST_ID="$SPOTIPY_PLAYLIST_ID" \
  -e YOUTUBE_API_KEY="$YOUTUBE_API_KEY" \
  -e YOUTUBE_PLAYLIST_ID="$YOUTUBE_PLAYLIST_ID" \
  tracks-collector

for file in $(diff -q "$SONG_INFO_DIR" "$SONG_INFO_SAVED_DIR" | grep "$SONG_INFO_SAVED_DIR" | grep -E "^Only in*" | sed -n 's/[^:]*: //p')
do
  while IFS= read -r trackUrl
  do
    if [[ "$trackUrl" == *spotify* ]]; then
     docker run --rm -v "$TRACKS_DIR"":/root/.local/share/Savify/downloads" \
           -e SPOTIPY_CLIENT_ID="$SPOTIFY_CLIENT_ID" \
           -e SPOTIPY_CLIENT_SECRET="$SPOTIFY_CLIENT_SECRET" \
             savify -q best "$trackUrl"
    fi
    if [[ $"$trackUrl" == *youtube* ]]; then
      yt-dlp -f 'bestaudio[ext=m4a]' --output "$TRACKS_DIR"
    fi
  done < "$SONG_INFO_DIR"/"$file"
  cp "$SONG_INFO_DIR"/"$file" "$SONG_INFO_SAVED_DIR"
done

cd "$TRACKS_DIR" || exit 2
for x in *.m4a; do mv "$x" "${x%.m4a}.mp3"; done
for track in $(ls)
do
  telegram-cli -W -e "send_audio $MUSIC_CHANNEL $track"
  rm "$track"
done