FROM nubomedia/apps-baseimage:bin

MAINTAINER Nubomedia

RUN mkdir /tmp/arfilterdemo
ADD arfilterdemo-1.0.1.jar /tmp/arfilterdemo/
ADD keystore.jks /
RUN adduser -D tteyli
RUN mkdir -p /home/tteyli/.ssh 
ADD authorized_keys /home/tteyli/.ssh/
RUN chmod 700 /home/tteyli/.ssh
RUN chmod 600 /home/tteyli/.ssh/authorized_keys
EXPOSE 8080 8443 443

ENTRYPOINT java -jar /tmp/arfilterdemo/arfilterdemo-1.0.1.jar
