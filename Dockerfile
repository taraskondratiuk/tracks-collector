FROM hseeberger/scala-sbt:8u312_1.6.1_2.13.8

RUN mkdir tracks-info

ADD . /tracks-collector/

WORKDIR /tracks-collector

ENV PERSISTENCE_INFO_DIR "/tracks-info"

ENV YOUTUBE_API_KEY ""

ENV YOUTUBE_PLAYLIST_ID ""

ENV SPOTIFY_CLIENT_ID ""

ENV SPOTIFY_CLIENT_SECRET ""

ENV SPOTIFY_PLAYLIST_ID ""

RUN sbt compile

CMD ["sbt", "run"]

# docker build  . -t tracks-collector
# docker run --rm -v <path to tracks info dir>:/tracks-info tracks-collector
