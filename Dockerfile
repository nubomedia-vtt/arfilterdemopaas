FROM nubomedia/apps-baseimage:bin

MAINTAINER Nubomedia

RUN mkdir /tmp/arfilterdemo
ADD arfilterdemo-1.0.1.jar /tmp/arfilterdemo/
ADD keystore.jks /
RUN mkdir -p ~/.ssh 
ADD ~/.ssh/authorized_keys
EXPOSE 8080 8443 443

ENTRYPOINT java -jar /tmp/arfilterdemo/arfilterdemo-1.0.1.jar
