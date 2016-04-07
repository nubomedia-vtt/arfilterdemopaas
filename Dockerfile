#FROM nubomedia/apps-baseimage:bin
FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/arfilterdemo
#ADD arfilterdemo-1.0.1.jar /tmp/arfilterdemo/
#ADD keystore.jks /
ADD ar3d /tmp/ar3d
RUN cd /tmp/ar3d && mvn compile 
RUN adduser -D tteyli
RUN mkdir -p /home/tteyli/.ssh 
ADD authorized_keys /home/tteyli/.ssh/
ADD .kurento ~/.kurento
#RUN less /etc/apt/sources.list
#RUN 'echo "deb [arch=amd64] http://ssi.vtt.fi/ubuntu trusty main" >> /etc/apt/sources.list'
#RUN apt-get update
#RUN apt-get install ar-markerdetector
#RUN ldconfig
#RUN /etc/init.d/kurento-media-server-6.0 restart
EXPOSE 8080 8443 443

#ENTRYPOINT java -jar /tmp/arfilterdemo/arfilterdemo-1.0.1.jar
ENTRYPOINT cd /tmp/ar3d && mvn exec:java
