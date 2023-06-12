package cn.itcast.mq.spring;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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

    @Test
    public void fanoutQueueTest() {
        String exchangeName = "my.fanout";
        String message = "hello fanout";
        rabbitTemplate.convertAndSend(exchangeName, "", message);
    }
}
