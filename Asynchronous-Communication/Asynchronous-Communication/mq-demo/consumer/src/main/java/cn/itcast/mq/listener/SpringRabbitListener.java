package cn.itcast.mq.listener;

import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;

@Component
public class SpringRabbitListener {

    @RabbitListener(queues = "simple.queue")
    public void basicQueueListener(String msg) {
        System.out.println(msg);
    }

    @RabbitListener(queues = "fanout.queue1")
    public void fanoutQueue1Listener(String msg) {
        System.out.println("fanout1 receive " + msg);
    }

    @RabbitListener(queues = "fanout.queue2")
    public void fanoutQueue2Listener(String msg) {
        System.out.println("fanout2 receive " + msg);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "direct.queue1"),
            exchange = @Exchange(name = "my.direct", type = ExchangeTypes.DIRECT),
            key = {"black", "green"}
    ))
    public void directQueue1Listener(String msg) {
        System.out.println("direct1 receive " + msg);
    }
}
