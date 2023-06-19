**Table of contents**

- [Theory](#theory)
  - [CAP](#cap)
  - [ACID](#acid)
  - [BASE](#base)
- [Seata](#seata)
  - [Terminology](#terminology)
  - [Setup](#setup)
    - [TC-server](#tc-server)
    - [Integrate with microservices](#integrate-with-microservices)
    - [Seata Cluster](#seata-cluster)
  - [Mode](#mode)
    - [XA](#xa)
    - [AT](#at)
    - [TCC](#tcc)
    - [SAGA](#saga)


# Theory 

## CAP

* **Consistency**: all clients see the same data at the same time, no matter which node they connect to. 
* **Availability**: all working nodes in the distributed system return a valid response for any request, without exception.
* **Partition Tolerance**: the cluster must continue to work despite any number of communication breakdowns between nodes in the system.

![CAP](./images/Screenshot%202023-06-17%20at%209.38.18%20AM.png)

Since P is unavoidable in most distributed system due to network connection, C(mandatory mechanism) and A(compensate mechanism) is a tradeoff here.

## ACID

* **Atomicity**: all transactions either succeed or fail completely.
* **Consistency**: guarantees relate to how a given state of the data is observed by simultaneous operations.
* **Isolation**: how simultaneous operations potentially conflict with one another.
* **Durability**: committed changes are permanent.

## BASE

1. The ACID model provides a consistent system.
2. The BASE model provides high availability.

* **Basically Available**: a distributed system should be available to respond with some acknowledgment — even if it’s a failure message, to any incoming request.
* **Soft State**: database model does not promise the immediate consistency.
* **Eventually Consistent**: a distributed system doesn't enforce immediate consistency, but doesn't mean that it never achieves it.

# [Seata](http://seata.io/en-us/index.html)

Seata is an open source distributed transaction solution that delivers high performance and easy to use distributed transaction services under a microservices architecture. 

## Terminology

* **TC - Transaction Coordinator**: Maintain status of global and branch transactions, drive the global commit or rollback.
* **TM - Transaction Manager**: Define the scope of global transaction: begin a global transaction, commit or rollback a global transaction.
* **RM - Resource Manager**: Manage resources that branch transactions working on, talk to TC for registering branch transactions and reporting status of branch transactions, and drive the branch transaction commit or rollback.

## Setup

### TC-server
1. Download
2. Modify registry.conf in seata/conf
```properties
registry {
  # choose nacos as configuration center
  type = "nacos"

  nacos {
    # seata tc registers to nacos
    application = "seata-tc-server"
    serverAddr = "127.0.0.1:8848"
    group = "DEFAULT_GROUP" # same as microservices' location
    namespace = ""
    cluster = "CA"
    username = "nacos"
    password = "nacos"
  }
}

config {
  # configuration file location
  type = "nacos"

  # nacos configuration info
  nacos {
    serverAddr = "127.0.0.1:8848"
    namespace = ""
    group = "SEATA_GROUP" 
    username = "nacos"
    password = "nacos"
    dataId = "seataServer.properties"
  }
}
```
3. Add config to Nacos(if you use Nacos as configuration center)
```properties
# db or redis
store.mode=db
store.db.datasource=druid
store.db.dbType=mysql
store.db.driverClassName=com.mysql.cj.jdbc.Driver
# your db
store.db.url=jdbc:mysql://127.0.0.1:3306/seata?useUnicode=true&rewriteBatchedStatements=true&serverTimezone=UTC
# your db username
store.db.user=root
# your db password
store.db.password=123
store.db.minConn=5
store.db.maxConn=30
store.db.globalTable=global_table
store.db.branchTable=branch_table
store.db.queryLimit=100
store.db.lockTable=lock_table
store.db.maxWait=5000
# log configuration
server.recovery.committingRetryPeriod=1000
server.recovery.asynCommittingRetryPeriod=1000
server.recovery.rollbackingRetryPeriod=1000
server.recovery.timeoutRetryPeriod=1000
server.maxCommitRetryTimeout=-1
server.maxRollbackRetryTimeout=-1
server.rollbackRetryTimeoutUnlockEnable=false
server.undo.logSaveDays=7
server.undo.logDeletePeriod=86400000

# transport between client and server
transport.serialization=seata
transport.compressor=none
# improve performance by closing metrics
metrics.enabled=false
metrics.registryType=compact
metrics.exporterList=prometheus
metrics.exporterPrometheusPort=9898
```
4. Create database table
```sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- brand table
-- ----------------------------
DROP TABLE IF EXISTS `branch_table`;
CREATE TABLE `branch_table`  (
  `branch_id` bigint(20) NOT NULL,
  `xid` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `transaction_id` bigint(20) NULL DEFAULT NULL,
  `resource_group_id` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `resource_id` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `branch_type` varchar(8) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `status` tinyint(4) NULL DEFAULT NULL,
  `client_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `application_data` varchar(2000) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `gmt_create` datetime(6) NULL DEFAULT NULL,
  `gmt_modified` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`branch_id`) USING BTREE,
  INDEX `idx_xid`(`xid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- global table
-- ----------------------------
DROP TABLE IF EXISTS `global_table`;
CREATE TABLE `global_table`  (
  `xid` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `transaction_id` bigint(20) NULL DEFAULT NULL,
  `status` tinyint(4) NOT NULL,
  `application_id` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `transaction_service_group` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `transaction_name` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `timeout` int(11) NULL DEFAULT NULL,
  `begin_time` bigint(20) NULL DEFAULT NULL,
  `application_data` varchar(2000) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `gmt_create` datetime NULL DEFAULT NULL,
  `gmt_modified` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`xid`) USING BTREE,
  INDEX `idx_gmt_modified_status`(`gmt_modified`, `status`) USING BTREE,
  INDEX `idx_transaction_id`(`transaction_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;

SET FOREIGN_KEY_CHECKS = 1;
```
5. Run seata-server.bat/sh in seata/bin

### Integrate with microservices
1. Dependency
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
    <exclusions>
        <!--exclude 1.3 version-->
        <exclusion>
            <artifactId>seata-spring-boot-starter</artifactId>
            <groupId>io.seata</groupId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
    <version>${seata.version}</version>
</dependency>
```
2. Application.yml
```yml
seata:
  registry: # correspond to registry.conf
    type: nacos
    nacos: # tc
      server-addr: 127.0.0.1:8848
      namespace: ""
      group: DEFAULT_GROUP
      application: seata-tc-server 
      cluster: CA
  tx-service-group: seata-demo # transaction group
  service:
    vgroup-mapping: # transaction group mapping to tc cluster
      seata-demo: CA
```

### Seata Cluster
1. copy a new seata application and modify /conf/registry.conf, begin from last created node(CA)
```properties
registry {
  # choose nacos as configuration center
  type = "nacos"

  nacos {
    # seata tc registers to nacos
    application = "seata-tc-server"
    serverAddr = "127.0.0.1:8848"
    group = "DEFAULT_GROUP" # same as microservices' location
    namespace = ""
    cluster = "NY" # New York node
    username = "nacos"
    password = "nacos"
  }
}

config {
  # configuration file location
  type = "nacos"

  # nacos configuration info
  nacos {
    serverAddr = "127.0.0.1:8848"
    namespace = ""
    group = "SEATA_GROUP" 
    username = "nacos"
    password = "nacos"
    dataId = "seataServer.properties"
  }
}
```
2. run 
```powershell
sh seata-server.sh -p 8092
```
3. add new config to Nacos(client.properties)
```properties
# mapping relation
service.vgroupMapping.seata-demo=CA

service.enableDegrade=false
service.disableGlobalTransaction=false
# TC communication config
transport.type=TCP
transport.server=NIO
transport.heartbeat=true
transport.enableClientBatchSendRequest=false
transport.threadFactory.bossThreadPrefix=NettyBoss
transport.threadFactory.workerThreadPrefix=NettyServerNIOWorker
transport.threadFactory.serverExecutorThreadPrefix=NettyServerBizHandler
transport.threadFactory.shareBossWorker=false
transport.threadFactory.clientSelectorThreadPrefix=NettyClientSelector
transport.threadFactory.clientSelectorThreadSize=1
transport.threadFactory.clientWorkerThreadPrefix=NettyClientWorkerThread
transport.threadFactory.bossThreadSize=1
transport.threadFactory.workerThreadSize=default
transport.shutdown.wait=3
# RM config
client.rm.asyncCommitBufferLimit=10000
client.rm.lock.retryInterval=10
client.rm.lock.retryTimes=30
client.rm.lock.retryPolicyBranchRollbackOnConflict=true
client.rm.reportRetryCount=5
client.rm.tableMetaCheckEnable=false
client.rm.tableMetaCheckerInterval=60000
client.rm.sqlParserType=druid
client.rm.reportSuccessEnable=false
client.rm.sagaBranchRegisterEnable=false
# TM config
client.tm.commitRetryCount=5
client.tm.rollbackRetryCount=5
client.tm.defaultGlobalTransactionTimeout=60000
client.tm.degradeCheck=false
client.tm.degradeCheckAllowTimes=10
client.tm.degradeCheckPeriod=2000

# undo log config
client.undo.dataValidation=true
client.undo.logSerialization=jackson
client.undo.onlyCareUpdateColumns=true
client.undo.logTable=undo_log
client.undo.compress.enable=true
client.undo.compress.type=zip
client.undo.compress.threshold=64k
client.log.exceptionRate=100
```
4. microservices' application.yml, let Nacos decide client connect to which node(CA or NY)
```yaml
seata:
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      username: nacos
      password: nacos
      group: SEATA_GROUP
      data-id: client.properties
```

## Mode

### XA

**Process**

![XA](./images/Screenshot%202023-06-18%20at%2011.45.31%20AM.png)

* pros:
  * strong consistency(ACID)
* cons:
  * need hold the lock until finishing
  * depend on database transaction

**Setup with Spring**
1. add to application.yml (every microservice who join global transaction)
```yml
seata:
    data-source-proxy-mode: XA
```
2. Annotation on global transaction entry method
```JAVA
@Override
@GlobalTransactional
public Long create(Order order) {
    // create order
    orderMapper.insert(order);
    try {
        // deduct from account
        accountClient.deduct(order.getUserId(), order.getMoney());
        // deduct from storage
        storageClient.deduct(order.getCommodityCode(), order.getCount());
    } catch (FeignException e) {
        log.error("error:{}", e.contentUTF8(), e);            
        throw new RuntimeException(e.contentUTF8(), e);
    }
    return order.getId();
}
```

### AT

**Process**

![AT](./images/Screenshot%202023-06-18%20at%201.07.12%20PM.png)

* pros:
  * high performance
  * global lock
* cons:
  * eventually consistency
  
[Global lock mechanism](http://seata.io/en-us/docs/dev/mode/at-mode.html)


**Setup with Spring**
1. add lock table to the database related to seata tc-server
```sql
DROP TABLE IF EXISTS `lock_table`;
CREATE TABLE `lock_table`  (
  `row_key` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `xid` varchar(96) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `transaction_id` bigint(20) NULL DEFAULT NULL,
  `branch_id` bigint(20) NOT NULL,
  `resource_id` varchar(256) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `table_name` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `pk` varchar(36) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `gmt_create` datetime NULL DEFAULT NULL,
  `gmt_modified` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`row_key`) USING BTREE,
  INDEX `idx_branch_id`(`branch_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Compact;
```
2. add lock table to the database related to microservices
```sql
DROP TABLE IF EXISTS `undo_log`;
CREATE TABLE `undo_log`  (
  `branch_id` bigint(20) NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int(11) NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE INDEX `ux_undo_log`(`xid`, `branch_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = 'AT transaction mode undo table' ROW_FORMAT = Compact;
```
3. switch to AT mode
```yml
seata:
    data-source-proxy-mode: AT
```
4. annotation(check XA mode)

### TCC

**Process**

* Try: reserve resources
* Confirm: execute and commit transaction with reserved resources
* Cancel: revert Try

![TCC](./images/Screenshot%202023-06-18%20at%203.57.46%20PM.png)

* pros:
  * no global lock and snapshot, higher performance than AT
  * not rely database transaction(can apply on Redis)
* cons:
  * need to implement Try, Confirm, Cancel interfaces
  * eventual consistency
  * Confirm and Cancel should be idempotent, since they may fail and retry

**Keys**

1. implement Try, Confirm, Cancel
2. **idempotent** Confirm and Cancel
3. allow **empty rollback(node not execute Try)**
4. reject **business suspension(Try after empty rollback)**

**Example: payment service**

1. Add new table to database related to service
```sql
CREATE TABLE `account_freeze_tbl` (
  `xid` varchar(128) NOT NULL,
  `user_id` varchar(255) DEFAULT NULL COMMENT 'user_id',
  `freeze_money` int(11) unsigned DEFAULT '0' COMMENT 'freeze_money',
  `state` int(1) DEFAULT NULL COMMENT 'transaction state，0:try，1:confirm，2:cancel',
  PRIMARY KEY (`xid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPACT;
```
2. Create interface in service
```Java
@LocalTCC
public interface AccountTCCService {

    @TwoPhaseBusinessAction(name = "deduct", commitMethod = "confirm", rollbackMethod = "cancel")
    void deduct(@BusinessActionContextParameter(paramName = "userId") String userId,
                @BusinessActionContextParameter(paramName = "money") int money);

    boolean confirm(BusinessActionContext ctx);

    boolean cancel(BusinessActionContext ctx);
}
```
3. Try: 
   1. record freeze_money and state to table
   2. deduct money from account table
4. Confirm(idempotent):
   1. delete row by xid from freeze table
5. Cancel(idempotent):
   1. change freeze_money to 0, state to 2
   2. add corresponding money to account table
6. Empty rollback:
   1. in Cancel, check freeze record by xid, if it is null, need empty rollback
7. Business suspension: 
   1. in Try, check freeze record by xid, if it is not null, reject Try
```Java
@Slf4j
@Service
public class AccountTCCService implements cn.itcast.account.service.AccountTCCService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFreezeMapper accountFreezeMapper;

    @Override
    public void deduct(String userId, int money) {
        String xid = RootContext.getXID();
        // 0. reject business suspension
        AccountFreeze oldFreeze = accountFreezeMapper.selectById(xid);
        if (oldFreeze != null) {
            return;
        }
        // 1. deduct money
        accountMapper.deduct(userId, money);
        // 2. record freeze money and state
        AccountFreeze freeze = new AccountFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeMoney(money);
        freeze.setState(AccountFreeze.State.TRY);
        freeze.setXid(xid);

        accountFreezeMapper.insert(freeze);
    }

    @Override
    public boolean confirm(BusinessActionContext ctx) {
        String xid = ctx.getXid();
        // 1. delete freeze record
        int count = accountFreezeMapper.deleteById(xid);
        return count == 1;
    }

    @Override
    public boolean cancel(BusinessActionContext ctx) {
        // 1.1 check freeze record
        String xid = ctx.getXid();
        String userId = ctx.getActionContext("userId").toString();
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
        // 1.2 empty rollback
        if (accountFreeze == null) {
            AccountFreeze freeze = new AccountFreeze();
            freeze.setUserId(userId);
            freeze.setFreezeMoney(0);
            freeze.setState(AccountFreeze.State.CANCEL);
            freeze.setXid(xid);
        }
        // 1.3 idempotent
        if (accountFreeze.getState() == AccountFreeze.State.CANCEL) {
            return true;
        }
        // 2. refund
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());
        // 3. reset freeze money to 0, state to CANCEL
        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.CANCEL);
        int count = accountFreezeMapper.updateById(accountFreeze);
        return count == 1;
    }
}
```

### SAGA

**Process**

* Phase 1: Submit local transactions directly
* Phase 2: If it succeeds, do nothing; if it fails, it will roll back by writing compensation business

![SAGA](./images/Screenshot%202023-06-18%20at%205.52.56%20PM.png)

* pros:
  * transaction participants can implement asynchronous calls based on event-driven, high throughput
  * submit transactions directly in first phase, no locks, good performance
  * it is easy to implement without writing the three stages in TCC
* cons: 
  * the duration of the soft state is uncertain and the timeliness is poor
  * no locks, no transaction isolation, dirty writes

  