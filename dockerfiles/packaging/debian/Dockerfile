FROM debian:8.1

RUN apt-get update
RUN apt-get install -y ruby-dev ruby build-essential
RUN gem install fpm

ADD . /debian

COPY package.sh /root/package.sh
COPY james.postinst /debian/james.postinst
COPY james.prerm /debian/james.prerm
COPY james.service /debian/james.service

ARG RELEASE
ENV RELEASE $RELEASE
ARG ITERATION
ENV ITERATION $ITERATION

WORKDIR /debian

ENTRYPOINT [ "sh", "-c", "/root/package.sh $RELEASE $ITERATION" ]
