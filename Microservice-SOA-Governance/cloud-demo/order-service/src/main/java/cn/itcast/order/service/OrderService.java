package cn.itcast.order.service;

import cn.itcast.order.clients.UserClient;
import cn.itcast.order.mapper.OrderMapper;
import cn.itcast.order.pojo.Order;
import cn.itcast.order.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserClient userClient;

    public Order queryOrderById(Long orderId) {
        // 1.search order
        Order order = orderMapper.findById(orderId);
        // 2.search user
        User user = userClient.findById(order.getUserId());
        // 3.set user
        order.setUser(user);
        // 4.return
        return order;
    }

    /*@Autowired
    private RestTemplate restTemplate;

    public Order queryOrderById(Long orderId) {
        // 1.search order
        Order order = orderMapper.findById(orderId);
        // 2.search user
        String url = "http://userservice/user/" + order.getUserId();
        User user = restTemplate.getForObject(url, User.class);
        // 3.set user
        order.setUser(user);
        // 4.return
        return order;
    }*/
}
