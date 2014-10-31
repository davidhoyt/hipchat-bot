FROM phusion/baseimage:0.9.15
MAINTAINER David Hoyt

EXPOSE 7789

ENV DEBIAN_FRONTEND noninteractive

#Add PPA for Oracle's Java
#Preemptively accept the Oracle license
#Install other dependencies
RUN echo '#!/bin/sh' "\nexit 0" >  /usr/sbin/policy-rc.d && \
    echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee /etc/apt/sources.list.d/webupd8team-java.list && \
    echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list && \
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys EEA14886 && \
    apt-get update && \
    echo "oracle-java8-installer	shared/accepted-oracle-license-v1-1	boolean	true" > /tmp/oracle-license-debconf && \
    /usr/bin/debconf-set-selections /tmp/oracle-license-debconf && \
    rm /tmp/oracle-license-debconf && \
    apt-get upgrade -y && \
    apt-get install -y wget curl vim nano && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN apt-get update && \
    apt-get install -y oracle-java8-installer && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Everything from this point should not require much, if any, customization.

# Add a run script to runit.
RUN mkdir /etc/service/hipchat-bot && \
    cd /etc/service/hipchat-bot && \
    echo '#!/bin/bash'               > run && \
    echo 'cd /opt/hipchat-bot' >> run && \
    echo 'exec /opt/hipchat-bot/bin/run -Dhostname=$HOSTNAME' >> run && \
    chmod 755 run

# Do as much work as possible first that's cacheable before proceeding with
# work that will change from build to build in order to keep the docker registry
# from filling up with additional, unnecessary layers.

ADD target/universal/ /opt/hipchat-bot

# Untar and rename scripts in bin/ to a known name ("run").
RUN cd /opt/hipchat-bot && \
    rm -rf ./tmp && \
    tar xzf *.tgz --strip 1 && \
    rm -f *.tgz && \
    cd bin/ && \
    bash -c 'for f in *; do base=${f%.[^.]*}; ext="${f:${#base}}"; next=run${ext}; [ -f "${next}" ] || { echo "Renaming \"$f\" to \"${next}\"..."; mv "$f" "${next}"; }  ; done'

CMD [ "/sbin/my_init" ]
