**Table of contents**

- [Redis](#redis)
  - [Setup](#setup)
    - [Master-Slave Cluster](#master-slave-cluster)
    - [Sentinel Cluster](#sentinel-cluster)
    - [Sharding Clusters](#sharding-clusters)
  - [Persistence](#persistence)
    - [RDB(Snapshot)](#rdbsnapshot)
      - [bgsave](#bgsave)
    - [AOF(Log)](#aoflog)
    - [Tradeoff](#tradeoff)
  - [Master-Slave Replication](#master-slave-replication)
    - [Full Sync](#full-sync)
    - [Incremental Sync](#incremental-sync)
    - [Optimal](#optimal)
  - [Sentinel](#sentinel)
    - [Responsibility](#responsibility)
      - [Monitor](#monitor)
      - [Failure Recover](#failure-recover)
      - [Notice](#notice)
    - [RedisTemplate](#redistemplate)
  - [Sharding Clusters](#sharding-clusters-1)
    - [Slot](#slot)
    - [Cluster Scaling](#cluster-scaling)
    - [Failure Recover](#failure-recover-1)
    - [RedisTemplate](#redistemplate-1)


# Redis

**Single Node Redis Problem**

* Data lost after restart - Persist data
* Can't handle high concurrency scenario - Build a master-slave cluster to achieve read-write separation
* Failure recovery is required - Use Redis sentinel to realize health detection and automatic recovery
* Hard to meet the demand for massive data - Build a fragmented cluster and use the slot mechanism to achieve dynamic expansion

## Setup

### Master-Slave Cluster

Assume we are going to build:

|       IP        | PORT |  role  |
| :-------------: | :--: | :----: |
| 192.168.150.101 | 7001 | master |
| 192.168.150.101 | 7002 | slave  |
| 192.168.150.101 | 7003 | slave  |

1. Prepare 3 copy of redis.conf
2. Modify port, dir ./, and replica-announce-ip in redis.conf
3. Run the command in three terminals
```sh
redis-server 7001/redis.conf
redis-server 7002/redis.conf
redis-server 7003/redis.conf
```
4. Connect to one redis using
```sh
redis-cli -p 7001
```
5. Set up master-slave relationship
```sh
# By config
replicaof <masterip> <masterport>
# By command line
replicaof <masterip> <masterport>
```


### Sentinel Cluster

Assume we are going to build:

| NODE |       IP        | PORT  |
| ---- | :-------------: | :---: |
| s1   | 192.168.150.101 | 27001 |
| s2   | 192.168.150.101 | 27002 |
| s3   | 192.168.150.101 | 27003 |

1. Prepare 3 copy of sentinel.conf, and add 
```ini
# 27002/27003 in other copies
port 27001
sentinel announce-ip 192.168.150.101
# quorum 2
sentinel monitor mymaster 192.168.150.101 7001 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 60000
# your running directory
dir "./s1"
```
2. Run commands
```sh
redis-sentinel s1/sentinel.conf
redis-sentinel s2/sentinel.conf
redis-sentinel s3/sentinel.conf
```

### Sharding Clusters

Assume we are going to build:

|       IP        | PORT |  ROLE  |
| :-------------: | :--: | :----: |
| 192.168.150.101 | 7001 | master |
| 192.168.150.101 | 7002 | master |
| 192.168.150.101 | 7003 | master |
| 192.168.150.101 | 8001 | slave  |
| 192.168.150.101 | 8002 | slave  |
| 192.168.150.101 | 8003 | slave  |

1. Prepare 6 redis.conf, and add
```ini
port 6379
cluster-enabled yes
# created by redis
cluster-config-file /tmp/6379/nodes.conf
# heartbeat timeout
cluster-node-timeout 5000
# RDB
dir /tmp/6379
bind 0.0.0.0
daemonize yes
replica-announce-ip 192.168.150.101
protected-mode no
# database number
databases 1
# log
logfile /tmp/6379/run.log
```
2. Modify 6379 to corresponding ports
3. Start redis servers
```sh
cd /tmp
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t redis-server {}/redis.conf
```
4. Build cluster connection
```sh
# --cluster-replicas 1: one replica per master
# n(number of masters) = nodes / (replicas + 1)
# first n are masters
redis-cli --cluster create --cluster-replicas 1 192.168.150.101:7001 192.168.150.101:7002 192.168.150.101:7003 192.168.150.101:8001 192.168.150.101:8002 192.168.150.101:8003
```
5. Check cluster status
```sh
redis-cli -p 7001 cluster nodes
```
6. Manipulate cluster
```sh
redis-cli -c -p 7001
```
7.  Close
```sh
ps -ef | grep redis | awk '{print $2}' | xargs kill
# or
printf '%s\n' 7001 7002 7003 8001 8002 8003 | xargs -I{} -t redis-cli -p {} shutdown
```

## Persistence

### RDB(Snapshot)

**Redis Database Backup file**

Persist all data in memory to disk. When the Redis instance fails and restarts, read the snapshot file from the disk and restore the data

**Cons**
* The RDB execution interval is long, and there is a risk of data loss between two RDB writes.
* Fork child process, compress, and write out RDB files are time-consuming.

**Command**
```sh
# RDB will be stored at the location you start redis server by default
# save with main process, block all other commands
save
# save with child process
bgsave
# redis will persist when exit 
```

**Config(redis.conf)**
```sh
# Trigger mechanism
save 60 10000 # Persist if over 10000 keys were modified in 60s
save "" # Disable RDB

# It is recommended not to enable it, compression will also consume cpu, and the disk is worthless
rdbcompression yes

# RDB file name
dbfilename dump.rdb  

# Where the file is saved
dir ./ 
```

#### bgsave 

**copy-on-write**
* When the main process reads, it accesses the shared memory.
* When the main process writes, it will copy the data and perform the write operation.

![bgsave](./images/Screenshot%202023-06-19%20at%2010.55.04%20AM.png)

### AOF(Log)

**Append Only File**

Every write command processed by Redis will be recorded in the AOF file.

**Command**
```sh
# rewrite AOF to remove duplicated commands on a same key
bgrewirteaof
```

**Config**
```sh
# Whether to enable the AOF function, default is no
appendonly yes
# AOF file name
appendfilename "appendonly.aof"
# record every command
appendfsync always 
# put command into a buffer first, record every second, default config
appendfsync everysec 
# put command into a buffer first, OS decide when to persist
appendfsync no
# The AOF file will trigger rewriting by more than the percentage of the last file growth
auto-aof-rewrite-percentage 100
# The minimum size of the AOF file to trigger rewrite
auto-aof-rewrite-min-size 64mb 
```

### Tradeoff

Can combine both ways

|  | RDB | AOF |
| - | - | - |
| Persistence | Snapshot | Logging |
| Integrity | Lost data between two RDB | Depend on frequency of AOF |
| File size | Small(compress) | Large |
| Downtime recovery speed | Quick | Slow |
| System resource usage | High(CPU and Memory) | Low(Mainly Disk IO, if rewrite(CPU and Memory)) |
| Application | Data loss can be tolerated for several minutes, and faster recover speed | Higher data security requirements |

## Master-Slave Replication

![Master-Slave Replication](./images/Screenshot%202023-06-19%20at%2011.19.43%20AM.png)

### Full Sync

![Full Sync](./images/Screenshot%202023-06-19%20at%201.49.10%20PM.png)

### Incremental Sync

![Incremental Sync](./images/Screenshot%202023-06-19%20at%201.57.01%20PM.png)

**repl_baklog has limited size, if the slave has been disconnected for too long and the data that has not been backed up is overwritten, need full sync here.**

### Optimal

* Config **repl-diskless-sync** yes in master, if you have fast network.
* The memory usage on a Redis single node should not be too large to reduce excessive disk IO caused by RDB.
* Appropriately increase the size of repl_baklog, realize fault recovery as soon as possible when the slave is down, and avoid full synchronization as much as possible.
* Limit the number of slave nodes on a master. If there are too many slaves, you can use a master-slave-slave chain structure to reduce the pressure on the master.

![master-slave-slave](./images/Screenshot%202023-06-19%20at%202.09.57%20PM.png)

## Sentinel

### Responsibility

![sentinel](./images/Screenshot%202023-06-19%20at%202.26.08%20PM.png)

#### Monitor

**Sentinel constantly checks status of master and slave.**

Sentinel pings every node per second by default:
* subjective offline: if a sentinel node finds that a node does not respond within the specified time, it will consider the node to be offline subjectively
* objective offline: if more than the specified number (quorum) of sentinels think that the node is offline subjectively, the node will be objectively offline. The quorum value should preferably exceed half of the number of Sentinel nodes.

![monitor](./images/Screenshot%202023-06-19%20at%202.34.24%20PM.png)

**Leader Election**

Sentinel will select a new master from slaves based on:
* First, it will judge the length of disconnection between the slave and the master. If it exceeds the specified value (down-after-milliseconds * 10), the slave will be excluded.
* Then compare the slave-priority value, the smaller the value, the higher the priority, if it is 0, it will never participate in the election.
* If the slave-priority is the same, compare the offset. Take the larger.
* The last is to compare the size of the running id of the slave, the smaller the id, the higher the priority.


#### Failure Recover

If the master fails, Sentinel will promote a slave to master. When the faulty node recovers, it becomes a slave.

**Process**
* First, sentinel sends command "slaveof no one" to the candidate, let it becomes a master.
* Then, sentinel sends command "slaveof new_master_address new_port" to the other slaves, let them follow new master.
* Finally, sentinel target the fail master as slave.

#### Notice

When the cluster fails over, the latest information will be pushed to the Redis client.

### RedisTemplate

1. Dependency
```xml
<dependency>    
  <groupId>org.springframework.boot</groupId>    <artifactId>spring-boot-starter-data-redis</artifactId> 
</dependency>
```
2. application.yml
```yml
spring:  
  redis:    
    sentinel:      
      master: mymaster # config name  
        nodes:          
          - 192.168.150.101:27001        
          - 192.168.150.101:27002        
          - 192.168.150.101:27003
```
3. Config master-slave read-write mode
```Java
// readFrom()
// MASTER
// MASTER_PREFERRED: only read from replica when master is unreachable
// REPLICA
// REPLICA _PREFERRED: only read from master when all replicas are unreachable
@Bean
public LettuceClientConfigurationBuilderCustomizer configurationBuilderCustomizer(){    
  return configBuilder -> configBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);
}
```

## Sharding Clusters

* Sharding data
* Master ping each other as sentinel role
* Client can query every node, and then redirect

![sharding](./images/Screenshot%202023-06-19%20at%205.07.46%20PM.png)

### Slot

* 0-16383 hash slots are assigned to master.
* Data is binding to slots, not master.
* Key hash is calculated by CRC16 algorithm
  * if key includes"{String}", and String is not empty, hash = CRC16(String).
  * if key does't include "{}", hash = CRC16(key)
  * for example, {iphone}17: hash = CRC(iphone)

### Cluster Scaling

```sh
# doc
redis-cli --cluster help
# add node
add-node new_host:new_port existing_host:existing_port
# reassign slots
reshard <host:port> or <host> <port>
```

**Add Node**
1. start new node
2. add-node command 
3. reshard the slots

**Delete Node**
1. reshard the slots
2. delete the node

### Failure Recover

cluster failover command manually shut down a master in the cluster, switch to the slave node that executes this command, and realize non-aware data migration.

![Failure Recover](./images/Screenshot%202023-06-19%20at%206.03.01%20PM.png)

**Mode**
* default: as showing above.
* force: skip offset.
* takeover: direct to step 5, ignore everything.

### RedisTemplate

1. Dependency
```xml
<dependency>    
  <groupId>org.springframework.boot</groupId>    <artifactId>spring-boot-starter-data-redis</artifactId> 
</dependency>
```
2. application.yml
```yml
spring:  
  redis:    
    cluster:
      nodes: # every node in cluster
        - ip:port
        - ip:port
        - ...
```
3. Config master-slave read-write mode
```Java
// readFrom()
// MASTER
// MASTER_PREFERRED: only read from replica when master is unreachable
// REPLICA
// REPLICA _PREFERRED: only read from master when all replicas are unreachable
@Bean
public LettuceClientConfigurationBuilderCustomizer configurationBuilderCustomizer(){    
  return configBuilder -> configBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);
}
```
