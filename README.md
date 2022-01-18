### Prerequisites
install docker

install and log into telegram-cli (esiest way is with snap)
```shell
sudo apt install --assume-yes snapd
export PATH=$PATH:/snap/bin

sudo snap install telegram-cli

# this command opens shell, where u need to log into telegram
# after logging it can be exited
telegram-cli
```
set env vars
```shell
export SPOTIFY_CLIENT_ID=
export SPOTIFY_CLIENT_SECRET=
export SPOTIFY_PLAYLIST_ID=
export YOUTUBE_API_KEY=
export YOUTUBE_PLAYLIST_ID=
# telegram chat name to send tracks into
export MUSIC_CHANNEL=
# dir for song links to be downloaded 
export SONG_INFO_DIR=
# dir for song links that were already downloaded 
export SONG_INFO_SAVED_DIR=
```
### Run

```shell
bash start.sh
```
