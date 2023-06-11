
- [Basis](#basis)
- [Volumes](#volumes)
- [Customized image](#customized-image)
- [DockerCompose](#dockercompose)


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