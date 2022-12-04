### Prerequisites
install docker

install pip
```
apt install python3-pip
```
### Run
edit crontab file
```shell
sudo crontab -e
```
add lines
```shell
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin:/snap/bin
0 * * * * export SPOTIFY_CLIENT_ID= && export SPOTIFY_CLIENT_SECRET= && export YOUTUBE_API_KEY= && export PERSISTENCE_DIR=<writeable empty dir> && export TRACKS_COLLECTOR_BOT_PORT=<any free port> && export TRACKS_COLLECTOR_BOT_TOKEN=<telegram bot token> && cd <path to tracks-collector dir> && flock -n /tmp/collector.lockfile ./start.sh >> <path to logfile> 2>&1
```
