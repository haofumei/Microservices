**Table of contents**
- [Overview](#overview)
  - [Synchronous Communication](#synchronous-communication)
  - [Asynchronous Communication](#asynchronous-communication)
  - [Message Queue Intro](#message-queue-intro)
- [RabbitMQ](#rabbitmq)
  - [BasicQueue](#basicqueue)
  - [WorkQueue](#workqueue)
  - [Publish and Subscribe](#publish-and-subscribe)
    - [Fanout Exchange](#fanout-exchange)
    - [Direct Exchange](#direct-exchange)
    - [Topic Exchange](#topic-exchange)
- [Kafka](#kafka)

# Overview

## Synchronous Communication
**Pros:**
* result returned immediately

**Cons:**
* tight coupling
* performance and throughput decrease
* resource-wasting
* cascade failure

## Asynchronous Communication

**Pros:**
* loose coupling
* throughput increase
* fault isolation
* Peak shaving

**Cons:**
* depends on Broker's performance
* complexity increase.

## Message Queue Intro
| | RabbitMQ | ActiveMQ | RocketMQ | Kafka |
| - | - | - | - | - |
| Developer | Rabbit | Apache | Alibaba | Apache |
| Language | Erlang | Java | Java | Scala&Java |
| Protocol | AMQP，XMPP，SMTP，STOMP | OpenWire,STOMP，REST,XMPP,AMQP | Customized | Customized |
| Availability | High | Normal | High | High |
| Stand-alone Throughput | Normal(dozens mb/s) | Bad | High | Very High(hundreds mb/s) |
| Latency | microseconds | milliseconds | milliseconds | microseconds to millisecond |
| Reliability | High | Normal | High | Normal |

# RabbitMQ

[RabbitMQ Tutorials](https://www.rabbitmq.com/getstarted.html)

**Architect**

![RabbitMQ Arch](./images/Screenshot%202023-06-12%20at%2010.07.21%20AM.png)

**Run on Docker**
``` sh
# install
docker pull rabbitmq:3-management

# run
docker run \
 -e RABBITMQ_DEFAULT_USER=mymq \ 
 -e RABBITMQ_DEFAULT_PASS=123321 \
 --name mq \
 --hostname mq1 \
 -p 15672:15672 \
 -p 5672:5672 \
 -d \
 rabbitmq:3-management
```

Management UI:

http://localhost:15672

## BasicQueue

![BasicQueue](./images/Screenshot%202023-06-12%20at%2010.32.06%20AM.png)

**Basic Process:**
* Publisher
  * Create connection(host, port, vhost, user, password)
  * Create channel
  * Create Queue
  * Publish message by channel
* Consumer
  * Create connection(host, port, vhost, user, password)
  * Create channel
  * Create Queue
  * Define handleDelivery()
  * Bind consumer with queue

**Integrate with SpringAMQP**
1. dependency
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>             
    <artifactId>spring-boot-starter-amqp</artifactId>
 </dependency>
```
2. config application.yml
```yml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    virtual-host: /
    username: mymq
    password: 123321
```
3. java code
```java
// Publisher
@RunWith(SpringRunner.class)
@SpringBootTest
public class SpringAMQPTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void basicQueueTest() {
        String queueName = "simple.queue";
        String message = "hello, basic queue";
        rabbitTemplate.convertAndSend(queueName, message);
    }
}

// Consumer
@Component
public class SpringRabbitListener {

    @RabbitListener(queues = "simple.queue")
    public void basicQueueListener(String msg) {
        System.out.println(msg);
    }
}
```

## WorkQueue

![workQueue](./images/Screenshot%202023-06-12%20at%2011.41.42%20AM.png)

**Mechanism**
* Round-robin dispatching
* Prefetch(default to infinite)

## Publish and Subscribe

An exchange was assigned between queue and publisher
* Receive message from publisher
* Direct message to the binding queues
* Unable to store the message

![Publish and Subscribe](./images/Screenshot%202023-06-12%20at%2011.50.56%20AM.png)

### Fanout Exchange

Fanout Exchange will direct messages to all the binding queues.

![Fanout Exchange](./images/Screenshot%202023-06-12%20at%2011.57.06%20AM.png)

**Integrate with SpringAMQP**
1. config in consumer
```java
@Configuration
public class FanoutConfig {

    // config fanout exchange
    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange("my.fanout");
    }

    // config queue1
    @Bean
    public Queue fanoutQueue1() {
        return new Queue("fanout.queue1");
    }

    // bind queue1 to fanout exchange
    @Bean
    public Binding fanoutBinding1(Queue fanoutQueue1, FanoutExchange fanoutExchange) {
        return BindingBuilder
                .bind(fanoutQueue1)
                .to(fanoutExchange);
    }

    // config queue2
    @Bean
    public Queue fanoutQueue2() {
        return new Queue("fanout.queue2");
    }

    // bind queue2 to fanout exchange
    @Bean
    public Binding fanoutBinding2(Queue fanoutQueue2, FanoutExchange fanoutExchange) {
        return BindingBuilder
                .bind(fanoutQueue2)
                .to(fanoutExchange);
    }
}
```
2. publisher
```java
@Test
public void fanoutQueueTest() {
    String exchangeName = "my.fanout";
    String message = "hello fanout";
    rabbitTemplate.convertAndSend(exchangeName, "", message);
}
```
3. consumer
```java
@RabbitListener(queues = "fanout.queue1")
public void fanoutQueue1Listener(String msg) {
    System.out.println("fanout1 receive " + msg);
}

@RabbitListener(queues = "fanout.queue2")
public void fanoutQueue2Listener(String msg) {
    System.out.println("fanout2 receive " + msg);
}
```

### Direct Exchange

* Queue was assigned "bindingKey"s with exchange
* Publish message with assigned bindingKey
* Exchange will direct the message to corresponding queue

![Direct Exchange](./images/Screenshot%202023-06-12%20at%201.23.34%20PM.png)

**Integrate with SpringAMQP**
```java
// consumer
@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "direct.queue1"),
        exchange = @Exchange(name = "my.direct", type = ExchangeTypes.DIRECT),
        key = {"black", "green"}
    ))
public void directQueue1Listener(String msg) {
    System.out.println("direct1 receive " + msg);
}

// publisher
```java
@Test
public void directQueueTest() {
    String exchangeName = "my.direct";
    String message = "hello direct";
    rabbitTemplate.convertAndSend(exchangeName, "black", message);
}
```

### Topic Exchange

The logic behind the topic exchange is similar to a direct, but for binding keys:
* (star) can substitute for exactly one word.
* (hash) can substitute for zero or more words.

For example: "lazy.tiger" will go to key "lazy.#"

![Topic Exchange](./images/Screenshot%202023-06-12%20at%201.43.50%20PM.png)

**Integrate with SpringAMQP**
```java
// consumer
@RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "topic.queue1"),
        exchange = @Exchange(name = "my.topic", type = ExchangeTypes.TOPIC),
        key = "lazy.#"
    ))
public void topicQueue1Listener(String msg) {
    System.out.println("topic1 receive " + msg);
}

// publisher
```java
@Test
public void topicQueueTest() {
    String exchangeName = "my.topic";
    String message = "hello topic";
    rabbitTemplate.convertAndSend(exchangeName, "laze.tiger", message);
}
```

# Kafka