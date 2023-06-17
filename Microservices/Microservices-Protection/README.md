**Table of contents**

- [Sentinel](#sentinel)
  - [Avalanche](#avalanche)
  - [Sentinel](#sentinel-1)
    - [Installation](#installation)
    - [Jmeter](#jmeter)
- [Flow Control](#flow-control)
  - [Flow control Modes](#flow-control-modes)
    - [Directed](#directed)
    - [Related](#related)
    - [Link](#link)
  - [Flow control effect](#flow-control-effect)
    - [Fail Fast](#fail-fast)
    - [Warm up](#warm-up)
    - [Queuing(traffic shaping)](#queuingtraffic-shaping)
    - [Hot params limiting](#hot-params-limiting)
- [Isolation and Degrade(Protect Caller)](#isolation-and-degradeprotect-caller)
  - [Degrade logic](#degrade-logic)
  - [Isolation](#isolation)
  - [Circuit Breaker](#circuit-breaker)
- [Authorization](#authorization)
- [Persistency](#persistency)
  - [Policy management modes](#policy-management-modes)
    - [Default](#default)
    - [Pull](#pull)
    - [Push](#push)


# Sentinel

## Avalanche

Failure of one microservice cascade to all microservices.

**Solution**:

* Timeout: return fault info immediately after a certain time.
* Bulkhead: limit the number of threads that each microservice can use, avoid exhausting server's resources.
* Circuit-breaker: breaker will intercept all the requests to a service if the abnormal proportion of its execution reaches a threshold.
* Traffic control: limit QPS so as to avoid service failure due to sudden increase in traffic.

## Sentinel

| | Sentinel | Hystrix |
| - | - | - |
| Isolation Policy | Semaphore isolation | Thread pool/semaphore isolation |
| Circuit Breaker Policy | Ratio of slow/abnormal call | Ratio of abnormal call |
| Real-time Indicator | Sliding window | Sliding window(RxJava) |
| Configuration | Support multiple data sources | Support multiple data sources |
| Scalability | Multiple extension points | Plug-in |
| Annotation | Yes | Yes |
| Limiting | QPS | Limited support |
| Traffic Shaping | Slow Start and Uniform Queuing | No | 
| System Adaptive Protection | Yes | No |
| Console | Config, Monitoring, discovery... | Limited |
| Adaptation | Servlet, Spring Cloud, Dubbo, gRPC | Servlet, Spring Cloud Netflix |

### [Installation](https://github.com/alibaba/Sentinel)

**Sentinel:**

1. Download jar.
2. Visit localhost:8080, user and password are default to sentinel.
```sh 
# change default port
java -jar sentinel-dashboard.jar -Dserver.port=8090
```
3. Integrate with microservice.
   1. Dependency
   ```xml
   <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
    </dependency>
   ```
   2. Application.yml
   ```yml
   spring:
     cloud:
       sentinel:
         transport:
           dashboard: localhost:8080
   ```
   3. Visit microservice endpoint.

### Jmeter

Load testing and performance measurement application.

```sh
# install on mac
brew install jmeter
# open UI
jmeter
```

# Flow Control

## Flow control Modes

### Directed

Count the requests of **current** resource, limit the flow of the **current resources** when the threshold is triggered.

![Directed](./images/Screenshot%202023-06-16%20at%201.08.36%20PM.png)


### Related

Count the requests of **related** resource, limit the flow of the **current resources** when the threshold is triggered.

**Application:**
* Two competing resources
* One with higher priority and one with lower priority

For example: the user needs to modify(high) the order status when paying, and at the same time, the user needs to query the order(low).Query and modification operations compete for database locks.

![Related](./images/Screenshot%202023-06-16%20at%201.32.42%20PM.png)

### Link

Count the requests to access this resource from the **specified link**, and limit the flow of the specified link when the threshold is triggered

**Application:** query order and create order business, both need to query goods, limits query link.
* order/query -> goods
* order/save -> goods

**Process:**
1. Annotation
```JSON
@SentinelResource("goods")
public void queryGoods() {
}
```
2. config
```yml
spring:  
   cloud:   
       sentinel:      
           web-context-unify: false 
```
3. UI setup
![Link](./images/Screenshot%202023-06-16%20at%201.48.29%20PM.png)

## Flow control effect

### Fail Fast

When QPS exceeds the threshold, reject new requests.

### Warm up

When the QPS exceeds the threshold, new requests are rejected; the QPS threshold is gradually increased, which can avoid service downtime caused by high concurrency during cold start

**init-threshold = threshold / coldFactor(default to 3)**

### Queuing(traffic shaping)

Queue all the requests and execute them in queuing order. Refuse the requests if their **expected** waiting time surpass the max allowing time.

### Hot params limiting

Only effect on @SentinelResource("hot") annotation.

![Hot params limiting](./images/Screenshot%202023-06-16%20at%204.11.40%20PM.png)

# Isolation and Degrade(Protect Caller)

Although current limiting can try to avoid service failures caused by high concurrency, services can also fail due to other reasons. To control these faults within a certain range and avoid avalanches, it is necessary to rely on thread isolation (bulkhead mode) and fuse downgrade methods.

## Degrade logic

**Feign integrates with Sentinel**

1. application.yml
```yml
feign:  
    sentinel:    
        enabled: true
```
1. downgrade logic
   1. FallbackClass: can't deal with RPC
   2. FallbackFactory: can deal with RPC
```Java
// 1. implement FallbackFactory
@Slf4j
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    public UserClient create(Throwable throwable) {
        return new UserClient() {
            @Override
            public User findById(Long id) {
                log.error("query user error", throwable);
                return new User();
            }
        };
    }
}
// 2. register UserClientFallbackFactory as bean in DefaultFeignConfiguration
@Bean
public UserClientFallbackFactory userClientFallbackFactory() {
    return new UserClientFallbackFactory();
}
// 3. apply UserClientFallbackFactory.class
@FeignClient(value = "userservice", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}
```

## Isolation

* Semaphore:
  * pros: light weight
  * cons: don't support positive timeout, async call
  * application: high fanout and calls
* Thread:
  * pros: positive timeout, async call
  * cons: extra cost on thread pool
  * application: low fanout

## Circuit Breaker

![Circuit Breaker](./images/Screenshot%202023-06-16%20at%205.09.50%20PM.png)

**Policy:**

* Slow call: A request whose service response time (RT) is greater than the specified time is considered a slow call request. Within the specified time, if the number of requests exceeds the set minimum number and the proportion of slow calls is greater than the set threshold, a circuit breaker will be triggered.
* Abnormal: If the number of calls exceeds the specified number of requests and the proportion of abnormalities reaches the set ratio threshold (or exceeds the specified number of abnormalities), a circuit breaker will be triggered.

![policy](./images/Screenshot%202023-06-16%20at%205.21.32%20PM.png)


# Authorization

**Setup:**

1. Get request header info.
```Java
// Microservice add
@Component
public class HeaderOriginParser implements RequestOriginParser {

    @Override
    public String parseOrigin(HttpServletRequest request) {
        String origin = request.getHeader("origin");

        if (StringUtils.isEmpty(origin)) {
            origin = "sercret";
        }
        return origin;
    }
}
```
2. Add header info by gateway
```yml
# gateway
spring:
    cloud:
        gateway:
            default-filters:
                - AddRequestHeader=origin,gateway
```
3. Sentinel setup

![sentinel authorization](./images/Screenshot%202023-06-16%20at%206.32.06%20PM.png)

4. Custom return block result
```JAVA
// implement this interface
public interface BlockExceptionHandler {    
    void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception;}
```

# Persistency

## [Policy management modes](https://github.com/alibaba/Sentinel/wiki/%E5%8A%A8%E6%80%81%E8%A7%84%E5%88%99%E6%89%A9%E5%B1%95)

| Mode | Detail | Pros | Cons |
| - | - | - | - |
| Default | Policy was stored in memory, lost if restart | simple | inconsistent and nonpersistent |
| Pull  | The client pulls the rules regularly | simple, persistent | inconsistent |
| Push | rule center push to the clients | consistent and persistent | introduce three-part dependency |


### Default

![Default](./images/Screenshot%202023-06-16%20at%206.50.56%20PM.png)

### Pull

![Pull](./images/Screenshot%202023-06-16%20at%206.51.25%20PM.png)

### Push

![Push](./images/Screenshot%202023-06-16%20at%206.51.36%20PM.png)

**Integrate with Nacos:**
1. Let microservice listens on Nacos.
2. Config Nacos data source.
3. Modify Sentinel UI.
4. Repack -dashboard.


