**Table of contents**
- [Basis](#basis)
- [Volumes](#volumes)
- [Customized image](#customized-image)
- [DockerCompose](#dockercompose)
  - [Demo(deploy a microservices with dockercompose)](#demodeploy-a-microservices-with-dockercompose)
- [Private Docker Registry](#private-docker-registry)
  - [Without UI](#without-ui)
  - [With UI](#with-ui)


# Basis

**Architecture**
![docker arch](./images/Screenshot%202023-06-10%20at%201.32.13%20PM.png)

**How does Docker solve compatibility between different components of a large system?**

1. Docker packs application, dependency, library and configuration into a single **image**.
2. Docker runs the images in a seperated **container**.

**How does Docker resolve differences between the development, test and production environments?**

Docker image contains a complete operating environment, including system function libraries, and only depends on the Linux kernel of the system, so it can run on any Linux operating system.

**Image related commands**
1. docker images
2. docker rmi
3. docker build
4. docker push
5. docker pull
6. docker save
7. docker load

![Image related commands](./images/Screenshot%202023-06-10%20at%204.02.21%20PM.png)

**Container related commands**
1. docker run
```shell
docker run --name containerName -p 80:80 -d nginx

--name: assign a name to the container
-p: publish a container’s port(s) to the host
80:80: left is host's port, right is container's port
-d: run in background 
```
2. docker pause
3. docker unpause
4. docker start
5. docker stop
6. docker exec: enter the container
```shell
docker exec -it containerName bash 

-it: std input and output for communication
bash: executed command after entering the container
```
7. docker logs: check container's logs
8. docker ps: check all running containers' status
9.  docker delete: delete particular container

![Container related commands](./images/Screenshot%202023-06-10%20at%204.14.51%20PM.png)

[Docker Commands Doc](https://docs.docker.com/engine/reference/commandline/cli/)

# Volumes

Volumes is a virtual directory pointing to a real directory existing in host file system.

![Volumes](./images/Screenshot%202023-06-10%20at%205.00.39%20PM.png)

**Commands**

[Volumes doc](https://docs.docker.com/storage/volumes/)

docker volume [COMMAND]
* create: create a new volume.
* inspect: show the info of volumes.
* ls: list all the volumes.
* prune: delete unused volume.
* rm: delete assigned volumes.

**Start a container with a volume**

```shell
Example: load the volume "html" into the container /usr/share/nginx/html

docker run --name containerName -v html:/usr/share/nginx/html -p 8080:80 nginx 
```

**Customized volume**

![customized volume](./images/Screenshot%202023-06-10%20at%206.18.43%20PM.png)

```txt
Example: 

docker run \
  --name mq \ 
  -e MYSQL_ROOT_PASSWORD=123 \
  -p 3306:3306 \
  -v /tmp/mysql/conf/hmy.cnf:/etc/mysql/conf.d \
  -v /tmp/mysql/data:/var/lib/mysql \
  -d \
  mysql
  ```

# Customized image

Image can be divided into multiple layers:
* Entrypoint: program starting point.
* Layers: pakages, dependency, and configuration.
* BaseImage: base system library, env, and file sysytem.

**Dockerfile instruction**

[Dockerfile reference](https://docs.docker.com/engine/reference/builder/)

| instruction | detail | example | 
| ------ | ------ | ------ |
| FROM | specified base image | FROM centos:6 |
| ENV | set env | ENV key value |
| COPY | copy local file to image directory | COPY ./mysql-5.7.rpm /tmp |
| RUN | run linux shell command | RUN yum install gcc |
| EXPOSE | image listen port | EXPOSE 8080 |
| ENTRYPOINT | image application run command | ENTRYPOINT java -jar xx.jar |

Example: 

Image needed files:
* docker-demo/docker-demo.jar
* docker-demo/jdk8.tar.gz
* docker-demo/Dockerfile

```dockerfile
# base image(M1,M2 macbook)
FROM --platform=linux/amd64 ubuntu:20.04
# env variable, JDK installed path
ENV JAVA_DIR=/usr/local

# copy jdk to target path
COPY ./jdk8.tar.gz $JAVA_DIR/

# install JDK
RUN cd $JAVA_DIR \
 && tar -xf ./jdk8.tar.gz \
 && mv ./jdk1.8.0_144 ./java8

# set env variables
ENV JAVA_HOME=$JAVA_DIR/java8
ENV PATH=$PATH:$JAVA_HOME/bin

# the above can be built as a base layer, is equal to
# FROM java:8-alpine

# microservices build can begin here
# copy java to target path
COPY ./docker-demo.jar /tmp/app.jar
# expose port
EXPOSE 8090
# entrypoint，start java application
ENTRYPOINT java -jar /tmp/app.jar
```

Run command in docker-demo
```docker
docker build -t javaweb:1.0 .
```

# DockerCompose

[Compose doc](https://docs.docker.com/compose/compose-file/)

Docker compose helps to start distributed applications automatically instead of starting every microservice one by one.

Example:
```yml
version: "3.8" # compose version
services:

# e1: start from an existing image, doesn't need to expose ports here, since they are organized by docker container. It equals to

# docker run \
#  --name mq \ 
#  -e MYSQL_ROOT_PASSWORD=123 \
#  -p 3306:3306 \
#  -v /tmp/mysql/conf/hmy.cnf:/etc/mysql/conf.d \
#  -v /tmp/mysql/data:/var/lib/mysql \
#  -d \
#  mysql:5.7.25

  mysql: # container name
    image: mysql:5.7.25 # image 
    environment: # env variable
     MYSQL_ROOT_PASSWORD: 123 
    volumes: 
     - "/tmp/mysql/data:/var/lib/mysql"
     -  "/tmp/mysql/conf/hmy.cnf:/etc/mysql/conf.d/hmy.cnf"

# e2: build an image first, and then start from it, it equals to
# docker build -t web:1.0 . (need dockerfile to build)
# docker run --name web -p 8090:8090 -d web:1.0
  web: # image's name
    build: .
    ports:
     - "8090:8090"
```

## Demo(deploy a microservices with dockercompose)

Suppose our microservices's architecture begins at:
* demo/gateway/Dockerfile
* demo/order-service/Dockerfile
* demo/user-service/Dockerfile
* demo/mysql
* docker-compose.yml

1. Prepare docker-compose file(example).
```yml
version: "3.2"

services:
  nacos: # service name
    image: nacos/nacos-server
    environment:
      MODE: standalone
    ports:
      - "8848:8848"
  mysql:
    image: mysql:5.7.25
    environment:
      MYSQL_ROOT_PASSWORD: 123
    volumes:
      - "$PWD/mysql/data:/var/lib/mysql"
      - "$PWD/mysql/conf:/etc/mysql/conf.d/"
  userservice:
    build: ./user-service
  orderservice:
    build: ./order-service
  # microservices... if you have more
  gateway:
    build: ./gateway
    ports:
      - "10010:10010" # only expose gateway port
```
2. Modify the mysql and nacos's addresses to the service names in docker-compose.
3. Pack every microservice into **jar** files, and put them into the corresponding directories.
4. upload demo directory to docker, cd into this demo and type
```sh
docker-compose up -d
```

# Private Docker Registry

[Docker registry doc](https://hub.docker.com/_/registry)

##  Without UI

```sh
docker run -d \
    --restart=always \
    --name registry	\
    -p 5000:5000 \
    -v registry-data:/var/lib/registry \
    registry
```

Private image was stored at this mapping "registry-data:/var/lib/registry", can check the images at http://YourIp:5000/v2/_catalog.

## With UI

[Doc](https://github.com/Joxit/docker-registry-ui)

```yaml
version: '3.8'

services:
  registry-ui:
    image: joxit/docker-registry-ui:main
    restart: always
    ports:
      - 80:80
    environment:
      - SINGLE_REGISTRY=true
      - REGISTRY_TITLE=Docker Registry UI
      - DELETE_IMAGES=true
      - SHOW_CONTENT_DIGEST=true
      - NGINX_PROXY_PASS_URL=http://registry-server:5000
      - SHOW_CATALOG_NB_TAGS=true
      - CATALOG_MIN_BRANCHES=1
      - CATALOG_MAX_BRANCHES=1
      - TAGLIST_PAGE_SIZE=100
      - REGISTRY_SECURED=false
      - CATALOG_ELEMENTS_LIMIT=1000
    container_name: registry-ui

  registry-server:
    image: registry:2.8.2
    restart: always
    environment:
      REGISTRY_HTTP_HEADERS_Access-Control-Origin: '[http://registry.example.com]'
      REGISTRY_HTTP_HEADERS_Access-Control-Allow-Methods: '[HEAD,GET,OPTIONS,DELETE]'
      REGISTRY_HTTP_HEADERS_Access-Control-Credentials: '[true]'
      REGISTRY_HTTP_HEADERS_Access-Control-Allow-Headers: '[Authorization,Accept,Cache-Control]'
      REGISTRY_HTTP_HEADERS_Access-Control-Expose-Headers: '[Docker-Content-Digest]'
      REGISTRY_STORAGE_DELETE_ENABLED: 'true'
    volumes:
      - ./registry/data:/var/lib/registry
    container_name: registry-server
```
