FROM debian:stretch

ENV SPAMASSASSIN_VERSION 3.4.1

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y \
        gpg \
        libio-socket-ip-perl \
        libmail-dkim-perl \
        libnet-ident-perl \
        libsocket-getaddrinfo-perl \
        spamassassin=${SPAMASSASSIN_VERSION}* && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir /etc/mail/spamassassin/bayes_db && \
    chmod -R 777 /etc/mail/spamassassin/bayes_db

COPY spamd.sh /
COPY rule-update.sh /
COPY run.sh /
RUN chmod 755 /spamd.sh /rule-update.sh /run.sh

COPY local.cf /etc/spamassassin/

EXPOSE 783

ENTRYPOINT /spamd.sh
