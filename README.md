# Telegram bot to download tracks from YouTube/Spotify playlists
## Run
```shell
# create network
docker network create <network name>
# build
LOGS_VOLUME= \
  MONGO_VOLUME= \
  docker compose build
```
```shell
# run
LOGS_VOLUME= \
  MONGO_VOLUME= \
  SPOTIFY_CLIENT_ID= \
  SPOTIFY_CLIENT_SECRET= \
  YOUTUBE_API_KEY= \
  TRACKS_COLLECTOR_BOT_TOKEN= \
  NETWORK_NAME= \
  docker compose up -d 
```
