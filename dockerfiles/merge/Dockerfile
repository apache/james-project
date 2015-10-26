FROM debian:8.1

ENV GIT_VERSION 1:2.1.4-2.1

RUN apt-get update
RUN apt-get install -y git="$GIT_VERSION"

RUN git config --global user.email \"merge@localhost\" \
    && git config --global user.name \"Merge\"

ADD . /origin
WORKDIR /origin

ENTRYPOINT ["/origin/dockerfiles/merge/merge.sh"]
