package com.xiaohui.dubbo.serviceImpl;

import com.xiaohui.dubbo.services.IUserService;

public class UserService2 implements IUserService {
    @Override
    public String sayHello(String msg) {
        return "UserService2 say:"+msg;
    }
}
