# Dubbo学习



## Dubbo内容

### 1. 解决了什么

![image](Dubbo-learning.assets/dubbo-service-governance.jpg)

1. **当服务越来越多时，服务 URL 配置管理变得非常困难，F5 硬件负载均衡器的单点压力也越来越大。** 此时需要一个服务注册中心，动态地注册和发现服务，使服务的位置透明。并通过在消费方获取服务提供方地址列表，实现软负载均衡和 Failover，降低对 F5 硬件负载均衡器的依赖，也能减少部分成本。

2. **当进一步发展，服务间依赖关系变得错踪复杂，甚至分不清哪个应用要在哪个应用之前启动，架构师都不能完整的描述应用的架构关系。** 这时，需要自动画出应用间的依赖关系图，以帮助架构师理清关系。

3. **服务的调用量越来越大，服务的容量问题就暴露出来，这个服务需要多少机器支撑？什么时候该加机器？** 为了解决这些问题，第一步，要将服务现在每天的调用量，响应时间，都统计出来，作为容量规划的参考指标。其次，要可以动态调整权重，在线上，将某台机器的权重一直加大，并在加大的过程中记录响应时间的变化，直到响应时间到达阈值，记录此时的访问量，再以此访问量乘以机器数反推总容量。

   

### 2.  架构

![dubbo-architucture](Dubbo-learning.assets%5Cdubbo-architecture.jpg)

#### 节点角色说明

| 节点        | 角色说明                               |
| ----------- | -------------------------------------- |
| `Provider`  | 暴露服务的服务提供方                   |
| `Consumer`  | 调用远程服务的服务消费方               |
| `Registry`  | 服务注册与发现的注册中心               |
| `Monitor`   | 统计服务的调用次数和调用时间的监控中心 |
| `Container` | 服务运行容器                           |

#### 调用关系说明

0. 服务**容器**负责启动，加载，运行服务提供者。

1. 服务**提供者**在启动时，向注册中心注册自己提供的服务。
2. 服务**消费者**在启动时，向注册中心订阅自己所需的服务。
3. **注册中心**返回服务提供者地址列表给消费者，如果有变更，注册中心将基于长连接推送变更数据给消费者。
4. 服务消费者，从提供者地址列表中，基于**软负载均衡算法**，选一台提供者进行调用，如果调用失败，再选另一台调用。
5. 服务消费者和提供者，在内存中累计调用次数和调用时间，定时**每分钟发送**一次统计数据到监控中心。



### 3. 六大核心能力

1. 面向接口代理的高性能RPC调用
2. 智能容错和负载均衡
3. 服务自动注册和发现
4. 高度可扩展能力
5. 运行期流量调度
6. 可视化的服务治理与运维



### 4. xml方式使用

#### 1.项目结构

<img src="D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129140735547.png" alt="image-20201129140735547" style="zoom:67%;" />



#### 2 公共pom依赖

> (需要引用spring,dubbo,currator的相关依赖，注意其引用的**版本号**)
>
> **以下依赖，提供者和消费者都需要使用**

```xml
<dependencies>
    <!--spring 依赖-->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-core</artifactId>
        <version>${spring.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
        <version>${spring.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-aop</artifactId>
        <version>${spring.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-aspects</artifactId>
        <version>${spring.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-test</artifactId>
        <version>${spring.version}</version>
    </dependency>

    <!--1. dubbo依赖-->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo</artifactId>
        <version>2.7.8</version>
    </dependency>

    <!--2. curator使用-->
    <dependency>
        <groupId>org.apache.curator</groupId>
        <artifactId>curator-framework</artifactId>
        <version>2.12.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.curator</groupId>
        <artifactId>curator-recipes</artifactId>
        <version>2.12.0</version>
    </dependency>

    <!--3. 这里是要发布的接口服务-->
    <dependency>
        <groupId>com.xiaohui.dubbo</groupId>
        <artifactId>dubbo-interface</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

#### 3.dubbo-interface

​	只提供接口：

<img src="D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129140852854.png" alt="image-20201129140852854" style="zoom:80%;" />

```java
public interface IUserService {
    String sayHello(String msg);
}
```



#### 4.dubbo-provider

##### 4.1 提供者编写接口实现

实现1：

<img src="D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129141541476.png" alt="image-20201129141541476" style="zoom:80%;" />

```java
import com.xiaohui.dubbo.services.IUserService;

public class UserService implements IUserService {

    public String sayHello(String msg) {
        return "hello:"+ msg;
    }
}
```

实现2：

<img src="D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129142954108.png" alt="image-20201129142954108" style="zoom:80%;" />

```java
public class UserService2 implements IUserService {
    @Override
    public String sayHello(String msg) {
        return "UserService2 say:"+msg;
    }
}
```

##### 4.2 提供者dubbo.xml文件

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:dubbo="http://code.alibabatech.com/schema/dubbo"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd http://code.alibabatech.com/schema/dubbo http://code.alibabatech.com/schema/dubbo/dubbo.xsd">

    <!--1. 定义dubbo应用名称，提供方应用信息，用于计算依赖关系-->
    <dubbo:application name="dubbo-provider" />
  
    <!--2. 定义注册服务中心，使用zookeeper进行注册-->
    <dubbo:registry protocol="zookeeper" address="192.168.231.138:2181" />
  
    <!--3.用dubbo协议在20880端口暴露服务-->
    <dubbo:protocol port="20880" />
  
    <!--4.定义我们的实现类-->
    <bean id="userService" class="com.xiaohui.dubbo.serviceImpl.UserService"/>
    <bean id="userService2" class="com.xiaohui.dubbo.serviceImpl.UserService2"/>
  
    <!--5.声明暴露的服务接口,（如果接口有多个实现，可以使用group 属性，对该接口注册两个实现）-->
    <dubbo:service interface="com.xiaohui.dubbo.services.IUserService" ref="userService" group="userService" />
    <dubbo:service interface="com.xiaohui.dubbo.services.IUserService" ref="userService2" group="userService2"/>
</beans>
```

##### 4.3 启动提供者，向dubbo注册

![image-20201129143602103](D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129143602103.png)

```java
public class Provider {

    public static void main(String[] args) throws IOException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
                new String[]{"classpath:dubbo-provider.xml"});
        context.start();

        System.out.println("provider started!");

        System.in.read(); // 让程序夯住
    }
}
```

在dubbo-admin中可以查看注册信息

![image-20201129143753392](D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129143753392.png)



#### 5.dubbo-consumer

##### 5.1  消费者dubbo.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:dubbo="http://dubbo.apache.org/schema/dubbo"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd http://dubbo.apache.org/schema/dubbo http://dubbo.apache.org/schema/dubbo/dubbo.xsd">

    <!-- 1.消费方应用名，用于计算依赖关系，不是匹配条件，不要与提供方一样 -->
    <dubbo:application name="dubbo-consumer"  />

    <!-- 2. 使用zookeeper注册中心暴露发现服务地址 -->
    <dubbo:registry address="zookeeper://192.168.231.138:2181" />

    <!-- 3. 生成远程服务代理，（注意，如果一个接口提供者有多个实现，需要指定group来指定调用哪个具体的实现） -->
    <dubbo:reference id="userService" interface="com.xiaohui.dubbo.services.IUserService" group="userService" />
    <dubbo:reference id="userService2" interface="com.xiaohui.dubbo.services.IUserService" group="userService2" />
</beans>
```

##### 5.2 远程调用

![image-20201129144100543](D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129144100543.png)

```java
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

// 结果输出：
/**
调用第一个实现：hello:world
调用第二个实现：UserService2 say:world2222
**/
```

也可以查看dubbo-admin中，消费者和提供者的调用关系

<img src="D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201129144443695.png" alt="image-20201129144443695" style="zoom:80%;" />



### 5.配置

> 可以参考官网：http://dubbo.apache.org/zh/docs/v2.7/user/references/xml/

#### 5.1 配置分类

- **服务发现**：表示该配置项用于服务的注册与发现，目的是**让消费方找到提供方**。
- **服务治理**：表示该配置项用于治理**服务间的关系**，或为开发测试提供便利条件。
- **性能调优**：表示该配置项用于调优性能，不同的选项对性能会产生影响。
- 所有配置最终**都将转换为 URL**  表示，并**由服务提供方生成，经注册中心传递给消费方**，各属性对应 URL 的参数

> 1. 注意：只有 **group，interface，version** 是服务的匹配条件，三者**决定是不是同一个服务**，其它配置项均为调优和治理参数。 
> 2. URL 格式：`protocol://username:password@host:port/path?key=value&key=value`



#### dubbo:application

应用信息配置。对应的配置类：`org.apache.dubbo.config.ApplicationConfig`

| 属性     | 对应URL参数         | 类型   | 是否必填 | 缺省值    | 作用     | 描述                                                         | 兼容性         |
| -------- | ------------------- | ------ | -------- | --------- | -------- | ------------------------------------------------------------ | -------------- |
| **name** | application         | string | **必填** |           | 服务治理 | 1.当前应用名称，**用于注册中心计算应用间依赖关系**，**注意**：消费者和提供者应用名不要一样，此参数不是匹配条件，你当前项目叫什么名字就填什么，和提供者消费者角色无关，比如：kylin应用调用了morgan应用的服务，则kylin项目配成kylin，morgan项目配成morgan，可能kylin也提供其它服务给别人使用，但kylin项目永远配成kylin，这样注册中心将显示kylin依赖于morgan | 1.0.16以上版本 |
| version  | application.version | string | 可选     |           | 服务治理 | 当前应用的版本                                               | 2.2.0以上版本  |
| compiler | compiler            | string | 可选     | javassist | 性能优化 | Java字节码编译器，用于动态类的生成，可选：jdk或javassist     | 2.1.0以上版本  |
|          |                     |        |          |           |          |                                                              |                |
|          |                     |        |          |           |          |                                                              |                |
|          |                     |        |          |           |          |                                                              |                |
|          |                     |        |          |           |          |                                                              |                |
|          |                     |        |          |           |          |                                                              |                |





## Dubbo-admin

### 本地安装

####  1.下载项目

​     地址： https://gitee.com/xuanfeng92/dubbo-admin.git ，项目结构如下

<img src="Dubbo-learning.assets%5Cimage-20201128193722101.png" alt="image-20201128193722101" style="zoom:80%;" />

#### 2. 编译前端部分

如果本地安装了node，可以将下载node进行编译的步骤省略，这样可以省去下载node的时间

![image-20201128194017716](Dubbo-learning.assets%5Cimage-20201128194017716.png)

省略build部分后，在前端目录下，即package.json同级别目录下，分别执行 npm install 和 npm run build 进行页面编译。该编译后的目录如下：

<img src="Dubbo-learning.assets%5Cimage-20201128194952842.png" alt="image-20201128194952842" style="zoom:80%;" />

> 之所以会编译输出到以下目录，是通过vue.config文件进行定义的
>
> <img src="Dubbo-learning.assets%5Cimage-20201128195124453.png" alt="image-20201128195124453" style="zoom: 67%;" />

#### 3. 后端部分

3.1 首先在项目根目录下，执行mvn install 

<img src="D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201128195909650.png" alt="image-20201128195909650" style="zoom: 67%;" />

它会将前端编译后的代码复制到dubbo-admin-server项目对应的静态资源访问部分，因此随着项目启动，也就可以访问到对应的页面。

![image-20201128200316602](D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201128200316602.png)

3.2 运行项目

方式一：直接运行jar包

编译后，可以直接使用dubbo-admin-server-0.2.0-SNAPSHOT.jar包，即java -jardubbo-admin-server-0.2.0-SNAPSHOT.jar 

> 默认启动的端口是8080，也可以修改其配置

> ![image-20201128200639693](D:%5CLearningSpace%5Clearning-notes%5Cdubbos%5CDubbo-learning.assets%5Cimage-20201128200639693.png)

方式二：运行spring-boot代码

![image-20201128201952760](Dubbo-learning.assets%5Cimage-20201128201952760.png)

3.3 运行截图：

> ![image-20201128201015608](Dubbo-learning.assets%5Cimage-20201128201015608.png)



#### 4.Swagger 支持

部署完成后，可以访问 http://localhost:8080/swagger-ui.html 来查看所有的restful api

<img src="Dubbo-learning.assets%5Cimage-20201128203634964.png" alt="image-20201128203634964" style="zoom:67%;" />



