package com.xiaohui.dubbo.serviceImpl;


import com.xiaohui.dubbo.services.IUserService;

public class UserService implements IUserService {

    public String sayHello(String msg) {
        return "hello:"+ msg;
    }
}
