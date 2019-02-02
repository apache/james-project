FROM debian:8.11

RUN apt-get update
RUN apt-get install -y git

RUN git config --global user.email \"merge@localhost\" \
    && git config --global user.name \"Merge\"

ADD . /origin
WORKDIR /origin

ENTRYPOINT ["/origin/dockerfiles/merge/merge.sh"]
