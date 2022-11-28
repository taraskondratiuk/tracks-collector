### Prerequisites
install docker

install pip
```
apt install python3-pip
```
set env vars
```shell
export SPOTIFY_CLIENT_ID=
export SPOTIFY_CLIENT_SECRET=
export YOUTUBE_API_KEY=
# writeable empty dir
export PERSISTENCE_DIR=
# any free port
export TRACKS_COLLECTOR_BOT_PORT=
# your telegram bot token
export TRACKS_COLLECTOR_BOT_TOKEN=
```
### Run
edit root crontab file
```shell
sudo crontab -e
```
add line
```shell
0 * * * * $(cd <path to tracks-collector dir> && flock -n /tmp/collector.lockfile bash prekol.sh >> <path to logfile>
```
