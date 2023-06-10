**Microservice SOA Governance includes:**
* Service Registration and Discovery
* Remote Procedure Call, RPC
* Configuration Management
* Gateway and Router

**Table of contents**

- [RestTemplate](#resttemplate)
- [Feign](#feign)
  - [Setup](#setup)
  - [Customized configuration](#customized-configuration)
  - [Feign performance optimization](#feign-performance-optimization)
  - [Feign suggested implement](#feign-suggested-implement)
- [Eureka](#eureka)
  - [Setup](#setup-1)
  - [Ribbon Loadbalancer](#ribbon-loadbalancer)
- [Nacos](#nacos)
  - [Setup](#setup-2)
  - [Configuration managerment](#configuration-managerment)
  - [Cluster deployment](#cluster-deployment)
- [Gateway](#gateway)
  - [Gateway integrates with discovery(Nacos)](#gateway-integrates-with-discoverynacos)
  - [Route Predicate Factory](#route-predicate-factory)
  - [Gateway Filter Factory](#gateway-filter-factory)
  - [CORS](#cors)
- [Zookeeper(unfinished)](#zookeeperunfinished)

**Demo Setup and Environment**
1. Load the sql file from sql directory
2. Open cloud-demo with IDEA
3. Environment: JDK1.8 and M2 macbook pro


# RestTemplate

*RestTemplate is the central class within the Spring framework for executing synchronous HTTP requests on the client side.*
1. Register RestTemplate
```Java
@MapperScan("cn.itcast.order.mapper")
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
```
2. RPC by RestTemplate
```Java
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RestTemplate restTemplate;

    public Order queryOrderById(Long orderId) {
        // 1.search order
        Order order = orderMapper.findById(orderId);
        // 2.search user
        String url = "http://localhost:8081/user/" + order.getUserId();
        User user = restTemplate.getForObject(url, User.class);
        // 3.set user
        order.setUser(user);
        // 4.return
        return order;
    }
}
```
# Feign
Feign makes writing java http clients easier.

## Setup
1. Dependency
```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```
2. Add @EnableFeignClients to main class
3. Define Feign interface
```java
@FeignClient("userservice")
public interface UserClient {

    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}
```
## Customized configuration
| Type                | Function                | Detail |
| ------------------- | ----------------------- | ------ |
| feign.Logger.Level  | Modify log level        | Include: NONE,BASIC,HEADERS,FULL |       
| feign.codec.Decoder | Response Decoder        | For example: json string to object |
| feign.codec.Encoder | Request Encoder         | Encode parameters |
| feign. Contract     | Support annotation      | Default: SpringMVC |
| feign. Retryer      | Failure retry mechanism | Default: None, but will apply Ribbon retry mechanism |

**Two ways to config log level:**
1. By configuration file
  ```yml
  feign:
    client:
      config:
        default: # global configuration
          logger-level: FULL

  feign:
    client:
      config:
        microservice: # only config to this microservice
          logger-level: FULL
  ```
2. By java code
```java
// 1. create new class
public class FeignConfiguration {

    @Bean
    public Logger.Level logLevel() {
        return Logger.Level.FULL;
    }
}
// 2. global configuration
@EnableFeignClients(defaultConfiguration = FeignConfiguration.class)
// 2. single microservice configuration
@FeignClient(value = "microservice", configuration = FeignConfiguration.class) 
```
## Feign performance optimization

**Feign can be implemented by:**
1. URLConnection: default, not support connection pool.
2. Apache HttpClient: support connection pool.
3. OKHttp: support connection pool.

**So:**
1. Use OKHttp or HttpClient.
2. Use log level basic or none.

**Connection pool configuration**
1. dependency
```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-httpclient</artifactId>
</dependency>
```
2. application.yml
```yml
feign:
  client:
    config:
      default:
        logger-level: BASIC
  httpclient:
    enabled: true
    max-connections: 200
    max-connections-per-route: 50
```

## Feign suggested implement
1. Controller and FeignClient implement the same interface.
![tight coupling feign implement](./images/Screenshot%202023-06-09%20at%206.14.51%20PM.png)
2. Put FeignClient, POJO and configuration into a new module.
![loose coupling feign implement](./images/Screenshot%202023-06-09%20at%206.22.39%20PM.png)

Process:
1. Create feign-api module.
2. Add feign-api dependency to required microservices.
3. Import feign-api.
4. Add scan package anotation.
```java
// import all the clients
@EnableFeignClients(basePackages = "...feign.clients")
// import needed clients
@EnableFeignClients(clients = {UserClient.class})
```

# Eureka
*Eureka is the Netflix Service Discovery Server and Client. The server can be configured and deployed to be highly available, with each server replicating state about the registered services to the others.*

## Setup

1. Build EurekaServer
   1. Create eureka-server project, insert eureka-server dependency.
      ```xml
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
      </dependency>
      ```
   2. Create and add @EnableEurekaServer to application.
   3. Create application.yml and config.
      ```yml
      server:
        port: 10086
      spring:
        application:
          name: eurekaserver
      eureka:
        client:
          service-url:
            defaultZone: http://127.0.0.1:10086/eureka/
      ```
2. Register microservice
   1. Insert dependency.
      ```xml
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
      </dependency>
      ```
   2. Add config to application.yml.
      ```yml
      spring:
        application:
          name: microservice #name of microservice
      eureka:
        client:
          service-url:
            defaultZone: http://127.0.0.1:10086/eureka/
      ```
3. Pull service from eureka
   1. Use microservice name instead of ip and port.
      ```Java
      // for example
      String url = "http://userservice/user/" + order.getUserId();
      ```
   2. Add @LoadBalanced to RestTemplate.
      ```Java
      @Bean
      @LoadBalanced
      public RestTemplate restTemplate() {
          return new RestTemplate();
      }
      ```

## Ribbon Loadbalancer

**Process and Interface**
![Process](./images/Screenshot%202023-06-06%20at%207.05.45%20PM.png)
![Interface](./images/Screenshot%202023-06-06%20at%207.09.03%20PM.png)

**Two ways to modify LoadBalanced rule**
1. Define IRule in service application.
  ```Java
  @Bean
  public IRule randomRule() {
    return new RandomRule();
  }
  ```
2. Add config to application.yml.
  ```yml
  userservice:
    ribbon:
      NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RandomRule
  ```
**Eager-load**: create LoadBalanceClient at the time when the application run instead of first request. Reduce the first request time.
```yml
ribbon:
  eager-load:
    enabled: true
    clients:
      - userservice #eager-load userservice
```
# Nacos

Nacos is committed to help you discover, configure, and manage your microservices.

## Setup

1. Download and install Nacos:
[Quick Start for Nacos](https://nacos.io/en-us/docs/quick-start.html)
2. Register microservices to Nacos
   1. Add spring-cloud-alilbaba to parent dependency.
   ```xml
   <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-alibaba-dependencies</artifactId>
      <version>2.2.5.RELEASE</version>
   </dependency>
   ```
   2. Add Nacos dependency to microservices.
   ```xml
   <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-alibaba-dependencies</artifactId>
      <version>2.2.5.RELEASE</version>
   </dependency>
   ```
   3. Add Nacos address to microservices application.yml
   ```yml
   spring:
      cloud:
        nacos:
          server-addr: localhost:8848
   ```
   4. Run the microservices.
3. Cluster configuration
```yml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        cluster-name: CA
```
4. Same cluster first loadbalanced rule
```yml
userservice:
  ribbon:
    NFLoadBalancerRuleClassName: com.alibaba.cloud.nacos.ribbon.NacosRule
```
5. Change the weights of traffic in the same cluster
![weights](./images/Screenshot%202023-06-07%20at%208.50.01%20PM.png)
The node who has 0 weight can be visited.
6. namespace(use to seperate the services)
     1. Create namespace in UI
     2. Add microservices to this namespace
    ```yml
    spring:
      cloud:
        nacos:
          server-addr: localhost:8848
          discovery:
            cluster-name: CA
            namespace: 73f148f3-41e7-483d-8acb-53f0c8857bcb # namespace id
    ```
7. Non-ephemeral node(ephemeral node will be kick out when it fails)
```yml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        cluster-name: CA
        ephemeral: false
```

## Configuration managerment

1. Process
![Config fetch process](./images/Screenshot%202023-06-08%20at%204.39.34%20PM.png)
2. Add dependency to microservices
```xml
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
  <version>2.2.5.RELEASE</version>
</dependency>
```
3. Create bootstrap.yml which has higher priority than application.yml
```yml
spring:
  application:
    name: userservice
  profiles:
    active: dev # enviroment
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848 # nacos address
      config:
        file-extension: yaml # file type
```
4. Create configuration in UI
![Nacos config create](./images/Screenshot%202023-06-08%20at%204.55.46%20PM.png)
5. Fetch Nacos configuration and auto-refresh
```Java
@Value("${pattern.dateformat}")
private String dateformat;
// Two ways to refresh the config automatically
// 1. Add @RefreshScope to the "class" who owns @Value
// 2. By @ConfigurationProperties
@Component
@Data
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String dateformat;
}
```
6. Multi-environment configurations
```Java
For example we have configs:
1. microservice-dev.yaml
2. microservice-pro.yaml
3. microservice.yaml
The shared configuration can be stored in microservice.yaml, the their priority:
microservice-env.yaml > microservice.yaml > local yaml
```

## Cluster deployment

![Nacos cluster](./images/Screenshot%202023-06-08%20at%205.54.57%20PM.png)

[Cluster deployment instructions](https://nacos.io/en-us/docs/cluster-mode-quick-start.html)

1. Build MySQL cluster and create tables for Nacos.(details can be found in above link)
2. Install Nacos.
3. Modify Nacos cluster and database configuration.
```txt
1. Modify the port in cluster.conf, for example:
127.0.0.1:8845
127.0.0.1.8846
127.0.0.1.8847
2. Add database config to application.properties, for example:
spring.datasource.platform=mysql

db.num=1

db.url.0=jdbc:mysql://127.0.0.1:3306/nacos?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC
db.user.0=your database
db.password.0=your pass
```
4. Start Nacos nodes.
```txt
1. Copy enough nacos file.
2. Modify the port to the port you want in every application.properties.
3. Start nacos individually.
```
5. Set up Nginx.

Add new config to conf/nginx.conf after install. (Nacos cluster mode does not support M1 and M2 macbook, Jun 2023)
```nginx
upstream nacos-cluster {
    server 127.0.0.1:8845;
	server 127.0.0.1:8846;
	server 127.0.0.1:8847;
}

server {
    listen       80;
    server_name  localhost;

    location /nacos {
        proxy_pass http://nacos-cluster;
    }
}
```

# Gateway

1. User authentication and authorization.
2. User routing and load balancing.
3. User traffic limiting.

Gateway: Reactive programming, better performance.

Zuul: Blocking programming.

## Gateway integrates with discovery(Nacos)
1. Create and add dependency to a new module(gateway).
```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
  <groupId>com.alibaba.cloud</groupId>
  <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
  <version>2.2.5.RELEASE</version>
</dependency>
```
2. application.yml
```yml
server:
  port: 10010
spring:
  application:
    name: gateway
  cloud:
    nacos:
      server-addr: localhost:8848 # nacos address
    gateway:
      routes:
        - id: user-service # router id
          uri: lb://userservice # routing target address, lb: load balance
          predicates:
            - Path=/user/**
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
```

## Route Predicate Factory
| name   | direction | example |
| ---- | ----- | ----- |
| After      | request after this time | - After=2037-01-20T17:42:47.789-07:00[America/Denver] |
| Before     | request before this time | - Before=2031-04-13T15:14:47.433+08:00[Asia/Shanghai] |
| Between    | request between this time | - Between=2037-01-20T17:42:47.789-07:00[America/Denver], 2037-01-21T17:42:47.789-07:00[America/Denver] |
| Cookie     | request has this cookies | - Cookie=chocolate, ch.p |
| Header     | request has this header | - Header=X-Request-Id, \d+ |
| Host       | request to this host | - Host=**.somehost.org,**.anotherhost.org |
| Method     | request is this method | - Method=GET,POST |
| Path       | request path match this pattern | - Path=/red/{segment},/blue/** |
| Query      | request has this parameter | - Query=name, Jack或者- Query=name | 
| RemoteAddr | ip in this range | - RemoteAddr=192.168.1.1/24 |
| Weight     | 

## Gateway Filter Factory
| name | direction |
| ---- | ------ |
| AddRequestHeader |
| RemoveRequestHeader |
| AddResponseHeader |
| RemoveResponseHeader |
| RequestRateLimiter |
| ... |

1. Default filter
```yml
spring:
  application:
    name: gateway
  cloud:
    nacos:
      server-addr: localhost:8848 # nacos address
    gateway:
      routes:
        - id: user-service # router id
          uri: lb://userservice # routing target address, lb: load balance
          predicates:
            - Path=/user/**
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
      default-filters: # effect on all routes
        - AddRequestHeader=Truth, default filter
```
2. Route filter
```yml
spring:
  application:
    name: gateway
  cloud:
    nacos:
      server-addr: localhost:8848 # nacos address
    gateway:
      routes:
        - id: user-service # router id
          uri: lb://userservice # routing target address, lb: load balance
          predicates:
            - Path=/user/**
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
          filters: # effect on this route
            - AddRequestHeader=Truth, route filter
```
3. Global filter
```java
@Order(-1) // filter order
@Component
public class AuthorizeFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // @param exchange: query context, included request, response...
        // @chain: pass to next filter
        // @return {Mono<Void>} indicate current filter ends
        
        // 1. get request parameters
        ServerHttpRequest request = exchange.getRequest();
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        // 2. get authorization from request parameters
        String auth = queryParams.getFirst("authorization");
        // 3. check if it == "admin"
        if ("admin".equals(auth)) {
            // 4. go to next filter
            return chain.filter(exchange);
        }
        // 5. stop
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
```

**Filter order**
* Every filter has an order, and the filter who has smaller order has higher priority.
* Route and default filter's orders begin at 1, and increase by declared order.
* when the orders are same, default > route > global.

## CORS
```yml
# config in application.yml
spring:
  cloud:
    gateway:
      globalcors:
        add-to-simple-url-handler-mapping: true # options request can pass
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:8090"
            allowedMethods:
              - "GET"
              - "POST"
              - "DELETE"
              - "PUT"
              - "OPTIONS"
            allowedHeaders: "*"
            allowCredentials: true # carried cookie
            maxAge: 360000 # max available time 
```

# Zookeeper(unfinished)