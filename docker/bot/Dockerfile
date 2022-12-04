FROM hseeberger/scala-sbt:11.0.13_1.6.1_2.13.8

RUN mkdir tracks-info

RUN chmod 777 tracks-info

ADD . /tracks-collector/

WORKDIR /tracks-collector

ENV PERSISTENCE_DIR "/tracks-info"

ENV TRACKS_DIR "/tracks"

ENV YOUTUBE_API_KEY ""

ENV SPOTIFY_CLIENT_ID ""

ENV SPOTIFY_CLIENT_SECRET ""

ENV TRACKS_COLLECTOR_BOT_TOKEN ""

RUN sbt bot/compile

CMD ["sbt", "~bot/run"]

# docker build . -f docker/bot/Dockerfile -t tracks-collector-bot
# docker run -d \
#  --restart=unless-stopped \
#  -v <path to tracks info dir>:/tracks-info \
#  -v <path to tracks dir>:/tracks \
#  -e SPOTIFY_CLIENT_ID=<spotify client id> \
#  -e SPOTIFY_CLIENT_SECRET=<spotify client secret> \
#  -e YOUTUBE_API_KEY=<youtube api key> \
#  -e TRACKS_COLLECTOR_BOT_TOKEN=<telegram bot token> \
#  -p <any available port>:8080 \
#  --name tracks-collector-bot \
#  tracks-collector-bot
