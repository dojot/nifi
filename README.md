# nifi:

This is a PoC to integrate NiFi with Dojot


# Table of Contents
- [nifi:](#nifi)
- [Table of Contents](#table-of-contents)
  - [NiFi](#nifi-1)
    - [What is NiFi?](#what-is-nifi)
    - [What aready do?](#what-aready-do)
      - [Install](#install)
    - [What will do?](#what-will-do)

Exemplo:
```bash
    ...
    container_name: emsp_web
    environment:
    - DOCKER_URL_API=https://cs3060api.cpqd.com.br/emsp
    volumes:
    ...
```

## NiFi

### What is NiFi?

NiFi it's a surname for [Apache Nifi](https://nifi.apache.org/).

We intend integrate nifi with Dojot for substitute flowbroker service.


### What aready do?

The target of this PoC is integrate Dojot with PlatIA. We aready make a [template](./templates/template-clamper.xml) with some processors who process some information consumed by kafka processor nifi's and send a HTTP Post with a payload that contains a message tranformed whit other processors.

Another advance we make is a custom processor that we called ["MyProcessor"](./processors/surto-processor/) using some java libs. This is a powerful example for future implementations of custom processors.

#### Install

To install you will need some tools:

    - maven
    - jdk 11

Then, execute this steps:


1 -  Gerenare the NAR file of custom processor.

In processors/surto-processor execute

```shell
mvn clean install
```

This will generate a NAR file in target folder [here](./processors/surto-processor/nifi-br.com.cpqd-nar/target/)



2 - Execute docker-compose using [docker-compose.yml](./docker-compose.yml).

```shell
docker-compose up -d
```


### What will do?