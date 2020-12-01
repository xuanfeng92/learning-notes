package com.xiaohui.dubbo;

import com.xiaohui.dubbo.services.IUserService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;

public class consumer {
    public static void main(String[] args) throws IOException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
                new String[] {"classpath:dubbo-consumer.xml"});
        context.start();

        IUserService userService = (IUserService)context.getBean("userService"); // 获取远程服务代理
        String result = userService.sayHello("world");
        System.out.println("调用第一个实现："+result);

        IUserService userService2 = (IUserService)context.getBean("userService2"); // 获取远程服务代理
        String result2 = userService2.sayHello("world2222");
        System.out.println("调用第二个实现："+ result2);

        System.in.read(); // 让程序夯住（非必须）
    }
}
