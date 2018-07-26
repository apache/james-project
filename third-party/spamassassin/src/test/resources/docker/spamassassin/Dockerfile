FROM postgres:10.3

ENV SPAMASSASSIN_VERSION 3.4.1

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y \
        gpg \
        libio-socket-ip-perl \
        libmail-dkim-perl \
        libnet-ident-perl \
        libsocket-getaddrinfo-perl \
        pyzor \
        razor \
        libdbi-perl \
        libdbd-pg-perl \
        spamassassin=${SPAMASSASSIN_VERSION}* && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /etc/spamassassin/sa-update-keys && \
    chmod 700 /etc/spamassassin/sa-update-keys && \
    chown debian-spamd:debian-spamd /etc/spamassassin/sa-update-keys && \
    mkdir -p /var/lib/spamassassin/.pyzor && \
    chmod 700 /var/lib/spamassassin/.pyzor && \
    echo "public.pyzor.org:24441" > /var/lib/spamassassin/.pyzor/servers && \
    chmod 600 /var/lib/spamassassin/.pyzor/servers && \
    chown -R debian-spamd:debian-spamd /var/lib/spamassassin/.pyzor

RUN sed -i 's/^logfile = .*$/logfile = \/dev\/stderr/g' /etc/razor/razor-agent.conf

COPY spamd.sh /
COPY rule-update.sh /
COPY run.sh /
RUN chmod 755 /spamd.sh /rule-update.sh /run.sh

COPY local.cf /etc/spamassassin/

# Bayes database will be created automatically by Postres
COPY bayes_pg.sql /docker-entrypoint-initdb.d/

EXPOSE 783

ENTRYPOINT ["/spamd.sh"]
