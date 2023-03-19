FROM hseeberger/scala-sbt:11.0.13_1.6.1_2.13.8

RUN apt-get update

RUN apt-get install python3 -y

RUN apt-get install python3-pip -y

RUN python3 -m pip install -U yt-dlp

RUN python3 -m pip install --use-pep517 git+https://github.com/stschake/savify@feature/use-yt-dlp

RUN apt-get install ffmpeg -y

RUN apt-get install frei0r-plugins -y

RUN mkdir "/tracks-collector-log"

RUN mkdir "/tracks"

RUN chmod 777 "/tracks"

ADD . /tracks-collector/

WORKDIR /tracks-collector

ENV TRACKS_DIR "/tracks"

ENV TRACKS_COLLECTOR_LOG_DIR "/tracks-collector-log"

ENV YOUTUBE_API_KEY ""

ENV SPOTIFY_CLIENT_ID ""

ENV SPOTIFY_CLIENT_SECRET ""

ENV SPOTIPY_CLIENT_ID ""

ENV SPOTIPY_CLIENT_SECRET ""

ENV TRACKS_COLLECTOR_BOT_TOKEN ""

ENV MONGO_URI ""

RUN sbt tracksCollector/compile

CMD ["sbt", "tracksCollector/run"]

# docker build . -t tracks-collector-bot
# docker run -d \
#  --restart=unless-stopped \
#  -v <path to log dir>:/tracks-collector-log \
#  -e SPOTIFY_CLIENT_ID=<spotify client id> \
#  -e SPOTIFY_CLIENT_SECRET=<spotify client secret> \
#  -e SPOTIPY_CLIENT_ID=<spotify client id> \
#  -e SPOTIPY_CLIENT_SECRET=<spotify client secret> \
#  -e YOUTUBE_API_KEY=<youtube api key> \
#  -e TRACKS_COLLECTOR_BOT_TOKEN=<telegram bot token> \
#  -e MONGO_URI=<mongo uri in format host:port> \
#  --name tracks-collector-bot \
#  tracks-collector-bot
