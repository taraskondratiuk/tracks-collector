FROM hseeberger/scala-sbt:11.0.13_1.6.1_2.13.8

RUN mkdir tracks-info

RUN chmod 777 tracks-info

ADD . /tracks-collector/

WORKDIR /tracks-collector

ENV PERSISTENCE_DIR "/tracks-info"

ENV YOUTUBE_API_KEY ""

ENV SPOTIFY_CLIENT_ID ""

ENV SPOTIFY_CLIENT_SECRET ""

RUN sbt collector/compile

CMD ["sbt", "collector/run"]

# docker build  . -f docker/collector/Dockerfile -t tracks-collector
# docker run --rm -v <path to tracks info dir>:/tracks-info \
#  -e SPOTIFY_CLIENT_ID="<spotify client id>" \
#  -e SPOTIFY_CLIENT_SECRET="<spotify client secret>" \
#  -e YOUTUBE_API_KEY="<youtube api key>" \
#  --name tracks-collector \
#  tracks-collector
