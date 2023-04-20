# Telegram bot to download tracks from YouTube/Spotify playlists
## Run
```shell
# build
docker-compose build

# run
LOGS_VOLUME= \
  MONGO_VOLUME= \
  SPOTIFY_CLIENT_ID= \
  SPOTIFY_CLIENT_SECRET= \
  YOUTUBE_API_KEY= \
  TRACKS_COLLECTOR_BOT_TOKEN= \
  docker-compose up -d 
```
