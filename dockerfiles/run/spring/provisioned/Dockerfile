# Run James
#
# VERSION	1.0

FROM linagora/james-jpa-spring

WORKDIR /root

RUN git clone https://github.com/vishnubob/wait-for-it.git wait-for-it
RUN cp /root/wait-for-it/wait-for-it.sh /usr/bin/wait-for-it.sh

COPY startup.sh /root/james-server-app-3.0.0-beta6-SNAPSHOT/bin
COPY initialdata.sh /root/james-server-app-3.0.0-beta6-SNAPSHOT/bin

RUN chmod +x /root/james-server-app-3.0.0-beta6-SNAPSHOT/bin/startup.sh
RUN chmod +x /root/james-server-app-3.0.0-beta6-SNAPSHOT/bin/initialdata.sh

WORKDIR /root/james-server-app-3.0.0-beta6-SNAPSHOT/bin

ENTRYPOINT ["./startup.sh"]
