
- [Demo Setup](#demo-setup)
- [RestTemplate](#resttemplate)
- [Eureka](#eureka)
- [Ribbon Loadbalancer](#ribbon-loadbalancer)
- [Nacos](#nacos)
- [Zookeeper](#zookeeper)

# Demo Setup
1. Load the sql file from sql directory
2. Open cloud-demo with IDEA
3. Environment: JDK1.8
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

# Eureka
*Eureka is the Netflix Service Discovery Server and Client. The server can be configured and deployed to be highly available, with each server replicating state about the registered services to the others.*
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
# Ribbon Loadbalancer
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
4. Same cluster first loadbalancer
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

# Zookeeper