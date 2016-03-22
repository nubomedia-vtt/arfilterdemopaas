ROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/arfilterdemo
ADD arfilterdemo-1.0.1.jar /tmp/arfilterdemo/
ADD keystore.jks /

EXPOSE 8080 8080

ENTRYPOINT java -jar /tmp/arfilterdemo/arfilterdemo-1.0.1.jar
