## Netty核心组件

### **Channel（通道）**

- 描述： 

  **它代表一个到实体**（如一个硬件设备、一个文件、一个网络套接字或者一个能够执行一个或者多个不同的I/O操作的程序组件）**的开放连接**，如**读操作和写操作**。

- 特点：

  可以把Channel **看作是传入（入站）或者传出（出站）数据的载体**；它**可以被打开或者被关闭**，**连接或者断开连接**

### **CallBack（回调）**

- 描述：

  是一个方法，一个指向已经被提供给另外一个方法的方法的引用；这使得后者可以在适当的时候调用前者。

- 特点：

  1. 当一个回调被触发时，相关的事件可以被一个接口 **ChannelHandler** 的实现处理

     > ![image-20201010141616716]( assets%5Cimage-20201010141616716.png)
     >
     > 上述例子中： 当一个新的连接已经被建立时，ChannelHandler 的channelActive()回调方法将会被调用，并将打印出一条信息。

### **Future **

- 描述：

  可以看作是一个异步操作的结果的占位符；它将在未来的某个时刻完成，并提供对其结果的访问。

  > Netty提供了它自己的实现——**ChannelFuture**，用于在执行异步操作的时候使用。

- 特点：

  1. ChannelFuture **能够注册一个或者多个ChannelFutureListener实例**。监听器的回调方法**operationComplete()**，将会在对应的操作完成时被调用。然后监听器可以判断该操作是成功地完成了还是出错了。如果是后者，我们可以检索产生的Throwable。简而言之，**由ChannelFutureListener提供的通知机制消除了手动检查对应的操作是否完成的必要**。

     > `注意： 如果在ChannelFutureListener 添加到ChannelFuture 的时候，ChannelFuture 已经完成，那么该ChannelFutureListener 将会被直接地通知 ；`

  2. **每个Netty 的I/O 操作都将返回一个ChannelFuture；也就是说，它们都不会阻塞**。

     > ![image-20201010142624356]( assets%5Cimage-20201010142624356.png)
     >
     > 这里，connect()方法将会直接返回，而不会阻塞，该调用将会在后台完成。
     >
     > 因为线程不用阻塞以等待对应的操作完成，所以它可以同时做其他的工作，从而更加有效地利用资源。

  3. ChannelFutureListener **看作是回调的一个更加精细的版本**，那么你是对的。事实上，**回调和Future 是相互补充的机制**；它们相互结合，构成了Netty 本身的关键构件块之一。

- 案例：

  显示如何利用ChannelFutureListener:

  > 步骤：
  >
  > 1. 首先，要连接到远程节点上。
  > 2. 然后，要注册一个新的ChannelFutureListener 到对connect()方法的调用所返回的ChannelFuture 上.
  > 3. 当该监听器被通知连接已经建立的时候，要检查对应的状态。如果该操作是成功的，那么将数据写到该Channel。否则，要从ChannelFuture 中检索对应的Throwable。
  >
  > ![image-20201010143334663]( assets%5Cimage-20201010143334663.png)

### **Event和ChannelHandler**

- 描述：

  1. **可以认为每个ChannelHandler 的实例都类似于一种为了响应特定Event而被执行的回调。**

  2. Netty **使用不同的Event来通知我们状态的改变或者是操作的状态**。这使得我们能够基于已经发生的事件来触发适当的动作。

     > Event动作：记录日志；数据转换；流控制；应用程序逻辑。
     >
     > Event分类：
     >
     > - **入站事件**：
     >   1. 连接已被激活或者连接失活；
     >   2. 数据读取；
     >   3. 用户事件；
     >   4. 错误事件。
     > - **出站事件**：
     >   - 打开或者关闭到远程节点的连接；
     >   - 将数据写到或者冲刷到套接字。

  3. **每个Event都可以被分发给ChannelHandler 类中的某个用户实现的方法**。

  ![image-20201010145449985]( assets%5Cimage-20201010145449985.png)

  > Netty 提供了大量预定义的可以开箱即用的ChannelHandler 实现，包括用于各种协议（如HTTP 和SSL/TLS）的ChannelHandler。在内部，ChannelHandler 自己也使用了事件和Future，使得它们也成为了你的应用程序将使用的相同抽象的消费者。
  
  

### 总结一下：

1. **Future、CallBack和ChannelHandler** 

   1. Netty 的异步编程模型是建立在Future 和回调的概念之上的。而将Event派发到ChannelHandler的方法则发生在更深的层次

      > 这些元素就提供了一个处理环境，使你的应用程序逻辑可以独立于任何网络操作相关的顾虑而独立地演变。

   2. 拦截操作以及高速地转换入站数据和出站数据，都只需要你提供回调或者利用操作所返回的Future。这使得链接操作变得既简单又高效，并且促进了可重用的通用代码的编写。

2. **selector、Event和EventLoop**

   1. Netty 通过触发事件将Selector 从应用程序中抽象出来，消除了所有本来将需要手动编写的派发代码
   2. 在内部，**将会为每个Channel 分配一个EventLoop**，用以处理所有事件，包括：
      - 注册感兴趣的事件；
      - 将事件派发给ChannelHandler；
      - 安排进一步的动作。
   3. **EventLoop 本身只由一个线程驱动**，其**处理了一个Channel 的所有I/O 事件。**并且在该EventLoop 的整个生命周期内都不会改变。

   





## Bootstrap

### 1. 层次结构

![image-20201026194840710]( assets%5Cimage-20201026194840710.png)

### 2. Bootstrap

- 描述

  1. Bootstrap 类负责**为客户端和UDP的应用程序创建Channel**。

     > 创建的Channel的步骤是在： Boostrap的connect操作

     ![image-20201026202924130]( assets%5Cimage-20201026202924130.png)

- 特点

  1. 客户端使用的
  2. 只需要**一个单独的、没有父Channel 的Channel 来**用于所有的网络交互。

  ```java
   Bootstrap bootstrap = new Bootstrap();
          bootstrap.group(group)
              .channel(NioSocketChannel.class)     // !!!!只需要一个channel用于处理和客户端的网络交互
              .handler(new SimpleChannelInboundHandler<ByteBuf>() { // !!!这里添加的handler处理channel
                  @Override
                  protected void channelRead0(
                      ChannelHandlerContext channelHandlerContext,
                      ByteBuf byteBuf) throws Exception {
                      System.out.println("Received data");
                  }
                  });
  				//连接到远程主机
          ChannelFuture future = bootstrap.connect(
              new InetSocketAddress("www.manning.com", 80));
  ```

  

### 3. ServerBootstrap

- 特点

  1. 服务端使用的

  2. 使用**一个父Channel** 来接受来自客户端的连接，并**创建子Channel** 以用于它们之间的通信

     > **创建父Channel**的步骤是在：ServerBootstrap 的bind()方法被调用时创建了一个ServerChannel，并且该ServerChannel 管理了多个子Channel。
     >
     > **创建子Channel**的步骤是在：当连接被接受时，会创建一个新的子Channel

  <img src=" assets%5Cimage-20201026211004560.png" alt="image-20201026211004560" style="zoom:80%;" />

  ```java
  ServerBootstrap bootstrap = new ServerBootstrap();
  bootstrap.group(group)
      .channel(NioServerSocketChannel.class)   // ！！！这是父Channel,
      .childHandler(new SimpleChannelInboundHandler<ByteBuf>() { // !!!这里添加的handler用于处理子Channel的内容
          @Override
          protected void channelRead0(ChannelHandlerContext channelHandlerContext,
              ByteBuf byteBuf) throws Exception {
              System.out.println("Received data");
          }
      });
          //通过配置好的 ServerBootstrap 的实例绑定该 Channel
          ChannelFuture future = bootstrap.bind(new InetSocketAddress(8080));
  ```



- 方法

| group            | 设置ServerBootstrap 要用的EventLoopGroup。<br/>这个EventLoopGroup将用于ServerChannel 和被接受的子Channel 的I/O 处理 |
| ---------------- | ------------------------------------------------------------ |
| **channel**      | 设置将要被实例化的ServerChannel 类                           |
| channelFactory   | 如果不能通过默认的构造函数(无参构造)创建Channel，那么可以提供一个ChannelFactory |
| **localAddress** | 指定ServerChannel 应该绑定到的本地地址。如果没有指定，则将由操作系统使用一个随机地址。或者，可以通过bind()方法来指定该localAddress |
| **option**       | 指定要应用到新创建的ServerChannel 的**ChannelConfig** 的**ChannelOption**。                                                        这些选项将会通过bind()方法设置到Channel。在bind()方法被调用之后，设置或者改变ChannelOption 都不会有任何的效果。所支持的ChannelOption 取决于所使用的Channel 类型 |
| **childOption**  | 指定当子Channel 被接受时，应用到子Channel 的ChannelConfig 的ChannelOption |
| **attr**         | 指定ServerChannel 上的属性，属性将会通过bind()方法设置给Channel。在调用bind()方法之后改变它们将不会有任何的效果 |
| **childAttr**    | 将属性设置给已经被接受的子Channel。接下来的调用将不会有任何的效果 |



### 4. ServerBootstrap和Bootstrap的区别

1. ServerBootstrap 将绑定到一个端口，因为服务器必须要监听连接`(connect方法)`，而Bootstrap 则是由想要连接到远程节点的客户端应用程序所使用的`(bind方法)`。
2. Bootstrap只需要一个EventLoopGroup，但是一个ServerBootstrap 则需要两个（也可以是同一个实例）。

> 因为服务器需要两组不同的Channel：
>
> 1. 第一组将只包含一个ServerChannel，代表服务器自身的已绑定到某个本地端口的正在监听的套接字
> 2. 第二组将包含所有已创建的用来处理传入客户端连接（对于每个服务器已经接受的连接都有一个）的Channel。
> 3. 与ServerChannel 相关联的EventLoopGroup 将分配一个负责为传入连接请求创建Channel 的EventLoop。一旦连接被接受，第二个EventLoopGroup 就会给它的Channel分配一个EventLoop。
>
> <img src=" assets%5Cimage-20201027224902936.png" alt="image-20201027224902936" style="zoom:80%;" />



### 5. AbstractBootstrap

- 声明

``` java
public abstract class AbstractBootstrap <B extends AbstractBootstrap<B,C>,C extends Channel>

public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> 

public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap, ServerChannel>
```

AbstractBootstrap中子类型B 是其父类型的一个类型参数，因此可以返回到运行时实例的引用以支持方法的链式调用（也就是所谓的流式语法）。

> 为什么引导类是Cloneable 的?
>
> 1. 有时可能会需要创建多个具有类似配置或者完全相同配置的Channel。为了支持这种模式而又不需要为每个Channel 都创建并配置一个新的引导类实例， AbstractBootstrap 被标记为了Cloneable。
>
> 2.  在一个已经配置完成的引导类实例上调用clone()方法将返回另一个可以立即使用的引导类实例。
> 3. 这种方式只会创建引导类实例的EventLoopGroup的一个浅拷贝，所以，被浅拷贝的EventLoopGroup将在所有克隆的Channel实例之间共享。（`这是可以接受的，因为通常这些克隆的Channel的生命周期都很短暂，一个典型的场景是——创建一个Channel以进行一次HTTP请求。`）
>
> ``` java
> public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {
>     @SuppressWarnings("unchecked")
>     static final Map.Entry<ChannelOption<?>, Object>[] EMPTY_OPTION_ARRAY = new Map.Entry[0];
>     @SuppressWarnings("unchecked")
>     static final Map.Entry<AttributeKey<?>, Object>[] EMPTY_ATTRIBUTE_ARRAY = new Map.Entry[0];
> 
>     volatile EventLoopGroup group; // 类作用域中，只有group是线程可见的，所以可以在线程之间共享
> ```



### 6. Channel 和EventLoopGroup 的兼容性

**注意点1**： 用了什么类型的EventLoopGroup，则必须对应相应的Channel，比如nio类型Group，则channel必须用nio的，否则不能使用

<img src=" assets%5Cimage-20201026203807053.png" alt="image-20201026203807053" style="zoom:80%;" />

**注意点2**：在引导的过程中，在调用bind()或者connect()方法之前，必须调用以下方法来设置所需的组件：

- group()；
- channel()或者channelFactory()；
- handler()。

如果不这样做，则将会导致IllegalStateException。对handler()方法的调用尤其重要，因为它需要配置好ChannelPipeline。



### 7. 从Channel 引导客户端

假设你的服务器正在处理一个客户端的请求，这个请求需要它充当第三方系统的客户端。当一个应用程序（如一个代理服务器）必须要和组织现有的系统（如Web 服务或者数据库）集成时，就可能发生这种情况。在这种情况下，将需要从已经被接受的子Channel 中引导一个客户端Channel。

你可以按照8.2.1 节中所描述的方式创建新的Bootstrap 实例，但是这并不是最高效的解决方案，因为它将要求你为每个新创建的客户端Channel 定义另一个EventLoop。这会产生额外的线程，以及在已被接受的子Channel 和客户端Channel 之间交换数据时不可避免的上下文切换。

一个更好的解决方案是：通过将已被接受的子Channel 的EventLoop 传递给Bootstrap的group()方法来共享该EventLoop。因为分配给EventLoop 的所有Channel 都使用同一个线程，所以这避免了额外的线程创建，以及前面所提到的相关的上下文切换。这个共享的解决
方案如图8-4 所示。

<img src=" assets%5Cimage-20201026213922525.png" alt="image-20201026213922525" style="zoom:80%;" />

>```java
>ServerBootstrap bootstrap = new ServerBootstrap();
>bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup())
>    .channel(NioServerSocketChannel.class)
>    //设置用于处理已被接受的子 Channel 的 I/O 和数据的 ChannelInboundHandler
>    .childHandler(
>        new SimpleChannelInboundHandler<ByteBuf>() {
>            ChannelFuture connectFuture;
>            @Override
>            public void channelActive(ChannelHandlerContext ctx)
>                throws Exception {
>                Bootstrap bootstrap = new Bootstrap();
>                bootstrap.channel(NioSocketChannel.class).handler(
>                    new SimpleChannelInboundHandler<ByteBuf>() {
>                        @Override
>                        protected void channelRead0(
>                            ChannelHandlerContext ctx, ByteBuf in)
>                            throws Exception {
>                            System.out.println("Received data");
>                        }
>                    });
>                //使用与分配给已被接受的子Channel相同的EventLoop !!!!!!!!!!!!!! bootsrap客户端
>                bootstrap.group(ctx.channel().eventLoop());
>                connectFuture = bootstrap.connect(
>                    new InetSocketAddress("www.manning.com", 80));
>            }
>
>            @Override
>            protected void channelRead0(
>                ChannelHandlerContext channelHandlerContext,
>                    ByteBuf byteBuf) throws Exception {
>                if (connectFuture.isDone()) {
>                    //当连接完成时，执行一些数据操作（如代理）
>                    // do something with the data
>                }
>            }
>        });
>```



### 8. 使用ChannelInitializer添加多个ChannelHandler

> 场景：
>
> 在所有我们展示过的代码示例中，我们都在引导的过程中调用了handler()或者child-Handler()方法来添加单个的ChannelHandler。这对于简单的应用程序来说可能已经足够了，但是它不能满足更加复杂的需求。例如，一个必须要支持多种协议的应用程序将会有很多的ChannelHandler，而不会是一个庞大而又笨重的类。
>
> 1. 你可以根据需要，通过在ChannelPipeline 中将它们链接在一起来部署尽可能多的ChannelHandler。但是，如果在引导的过程中你只能设置一个ChannelHandler，那么你应该怎么做到这一点呢？
>
> 2. Netty 提供了一个特殊的ChannelInboundHandlerAdapter 子类：**ChannelInitializer**。它定义了下面的方法：
>
>    protect ed abstract void initChannel(C ch) throws Exception;
>
>    你只需要简单地向Bootstrap 或ServerBootstrap 的实例提供你的Channel-Initializer 实现即可，并且一旦Channel 被注册到了它的EventLoop 之后，就会调用你的initChannel()版本。在该方法返回之后，ChannelInitializer 的实例将会从ChannelPipeline 中移除它自己。

> ```java
> public void bootstrap() throws InterruptedException {
>     ServerBootstrap bootstrap = new ServerBootstrap();
>     bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup())
>         .channel(NioServerSocketChannel.class)
>         .childHandler(new ChannelInitializerImpl()); // !!! 使用ChannelInitializer加入多个Handler
>     ChannelFuture future = bootstrap.bind(new InetSocketAddress(8080));
>     future.sync();
> }
> 
> //用以设置 ChannelPipeline 的自定义 ChannelInitializerImpl 实现
> final class ChannelInitializerImpl extends ChannelInitializer<Channel> {
>     @Override
>     //将所需的 ChannelHandler 添加到 ChannelPipeline
>     protected void initChannel(Channel ch) throws Exception {
>         ChannelPipeline pipeline = ch.pipeline();
>         pipeline.addLast(new HttpClientCodec());
>         pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
> 
>     }
> }
> ```



### 9. 在Bootstrap上设置ChannelOption 和Attributes

在每个Channel 创建时都手动配置它可能会变得相当乏味。幸运的是，你不必这样做。相反，你可以使用option()方法来将ChannelOption 应用到Bootstrap上。

（在bootstrap上设置的attr，可以在channel的任何地方都可以访问的到）

> 例如，考虑一个用于跟踪用户和Channel 之间的关系的服务器应用程序。这可以通过将用户的ID 存储为Channel 的一个属性来完成。类似的技术可以被用来基于用户的ID 将消息路由给用户，或者关闭活动较少的Channel。

> ```java
> // 1. 创建一个 AttributeKey 以标识该属性!!!!!
> final AttributeKey<Integer> id = AttributeKey.newInstance("ID");
> Bootstrap bootstrap = new Bootstrap();
> bootstrap.group(new NioEventLoopGroup())
>     .channel(NioSocketChannel.class)
>     .handler(
>         new SimpleChannelInboundHandler<ByteBuf>() {
>             @Override
>             public void channelRegistered(ChannelHandlerContext ctx)
>                 throws Exception {
>                 //3. 使用 AttributeKey 检索属性以及它的值!!!!!
>                 Integer idValue = ctx.channel().attr(id).get();
>                 // do something with the idValue
>                 System.out.println("idValue is"+ idValue);
>             }
> 
>             @Override
>             protected void channelRead0(
>                 ChannelHandlerContext channelHandlerContext,
>                 ByteBuf byteBuf) throws Exception {
>                 System.out.println("Received data");
>             }
>         }
>     );
> 
> //4. 设置 ChannelOption，其将在 connect()或者bind()方法被调用时被设置到已经创建的 Channel 上
> bootstrap.option(ChannelOption.SO_KEEPALIVE, true)
>     .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
> //2. 存储该 id 属性!!!!!!
> bootstrap.attr(id, 123456);
> 
> ChannelFuture future = bootstrap.connect(
>     new InetSocketAddress("www.manning.com", 80));
> future.syncUninterruptibly();
> ```



### 10. 优雅的关闭

需要注意的是，**shutdownGracefully**()方法也是一个异步的操作，所以你需要**阻塞等待直到它完成**，或者**向所返回的Future 注册一个监听器以在关闭完成时获得通知。**

```java
public void bootstrap() {
    EventLoopGroup group = new NioEventLoopGroup();
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
         .channel(NioSocketChannel.class)
         .handler(
            new SimpleChannelInboundHandler<ByteBuf>() {
                @Override
                protected void channelRead0(
                        ChannelHandlerContext channelHandlerContext,
                        ByteBuf byteBuf) throws Exception {
                    System.out.println("Received data");
                }
                @Override
                public void channelRegistered(ChannelHandlerContext ctx) {
                    System.out.println("start registry a channel!");
                }
            }
         );
    bootstrap.connect(new InetSocketAddress("127.0.0.1", 8080));
    // 1. shutdownGracefully()方法将释放所有的资源，并且关闭所有的当前正在使用中的 Channel
    Future<?> future = group.shutdownGracefully();
    // 2. block until the group has shutdown
    future.syncUninterruptibly();
```

添加listener等待关闭

```java
ChannelFuture channelFuture = bootstrap.connect(new InetSocketAddress("127.0.0.1", 8080));
//,,,
//shutdownGracefully()方法将释放所有的资源，并且关闭所有的当前正在使用中的 Channel
channelFuture.addListener(new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture channelFuture)
            throws Exception {
        if (channelFuture.isSuccess()) {
            System.out.println("Server bound");
        } else {
            System.err.println("Bind attempt failed");
            channelFuture.cause().printStackTrace();
        }
    }
});
```



## EventLoop和线程模型

> **线程模型**指定了操作系统、编程语言、框架或者应用程序的上下文中的线程管理的关键方面。显而易见地，如何以及何时创建线程将对应用程序代码的执行产生显著的影响，因此开发人员需要理解与不同模型相关的权衡。无论是他们自己选择模型，还是通过采用某种编程语言或者框架隐式地获得它，这都是真实的。

### JDK线程池的模式（优缺点）：

- 从池的空闲线程列表中选择一个Thread，并且指派它去运行一个已提交的任务（一个Runnable 的实现）；
- 当任务完成时，将该Thread 返回给该列表，使其可被重用。

![image-20201026234723484]( assets%5Cimage-20201026234723484.png)

优点：虽然池化和重用线程相对于简单地为每个任务都创建和销毁线程

缺点：并不能消除由上下文切换所带来的开销，其将随着线程数量的增加很快变得明显，并且在高负载下愈演愈烈。



### EventLoop 接口

- 描述

  1. 用来处理在连接的生命周期内发生的事件

     > 事件/任务的执行顺序 :**先进先出（FIFO）的顺序执行的**。
     >
     > 这样可以通过保证字节内容总是按正确的顺序被处理，消除潜在的数据损坏的可能性。

  <img src=" assets%5Cimage-20201027000230343.png" alt="image-20201027000230343" style="zoom:50%;" />

  

- 特点
  1. EventLoop扩展了ScheduledExecutorService
  2. 一个EventLoop 将由一个永远都不会改变的Thread 驱动
  3. **任务（Runnable 或者Callable）可以直接提交给EventLoop 实现，以立即执行或者调度执行。**
  4. 所有的I/O操作和事件都由已经被分配给了EventLoop的那个Thread来处理.



### EventLoop 的调度任务

```java
    /**
     * 代码清单 7-3 使用 EventLoop 调度任务
     * */
    public static void scheduleViaEventLoop() {
        Channel ch = CHANNEL_FROM_SOMEWHERE; // get reference from somewhere
        ScheduledFuture<?> future = ch.eventLoop().schedule(
            //创建一个 Runnable以供调度稍后执行
            new Runnable() {
            @Override
            public void run() {
                //要执行的代码
                System.out.println("60 seconds later");
            }
            //调度任务在从现在开始的 60 秒之后执行
        }, 60, TimeUnit.SECONDS);
    }
```

既然EventLoop扩展了ScheduledExecutorService ，因此也可以使用ScheduledExecutorService 相应的方法来管理任务

```java
/**
 * 代码清单 7-4 使用 EventLoop 调度周期性的任务
 * */
public static void scheduleFixedViaEventLoop() {
    Channel ch = CHANNEL_FROM_SOMEWHERE; // get reference from somewhere
    ScheduledFuture<?> future = ch.eventLoop().scheduleAtFixedRate(
       //创建一个 Runnable，以供调度稍后执行
       new Runnable() {
       @Override
       public void run() {
            //这将一直运行，直到 ScheduledFuture 被取消
            System.out.println("Run every 60 seconds");
       }
    //调度在 60 秒之后，并且以后每间隔 60 秒运行
    }, 60, 60, TimeUnit.SECONDS);
}
```

```java
/**
 * 代码清单 7-5 使用 ScheduledFuture 取消任务
 * */
public static void cancelingTaskUsingScheduledFuture(){
    Channel ch = CHANNEL_FROM_SOMEWHERE; // get reference from somewhere
    //...
    //调度任务，并获得所返回的ScheduledFuture
    ScheduledFuture<?> future = ch.eventLoop().scheduleAtFixedRate(
            new Runnable() {
                @Override
                public void run() {
                    System.out.println("Run every 60 seconds");
                }
            }, 60, 60, TimeUnit.SECONDS);
    // Some other code that runs...
    boolean mayInterruptIfRunning = false;
    //取消该任务，防止它再次运行
    future.cancel(mayInterruptIfRunning);
}
```



### EventLoop的线程管理

![image-20201027002610986](assets%5Cimage-20201027002610986.png)

> 1. 如果（当前）调用线程正是支撑EventLoop 的线程，那么所提交的代码块将会被（直接）执行。否则，EventLoop 将调度该任务以便稍后执行，并将它放入到内部队列中。
> 2. 每个EventLoop 都有它自已的任务队列，独立于任何其他的EventLoop。

> 注意：永远不要将一个长时间运行的任务放入到执行队列中，因为它将阻塞需要在同一线程上执行的任何其他任务。”如果必须要进行阻塞调用或者执行长时间运行的任务，我们建议使用一个专门的**EventExecutor**。



### EventLoop的线程分配

- **异步分配模式**

<img src=" assets%5Cimage-20201027003921892.png" alt="image-20201027003921892" style="zoom:80%;" />

> 1. EventLoopGroup 负责为每个新创建的Channel 分配一个EventLoop
> 2. 一旦一个Channel 被分配给一个EventLoop，它将在它的整个生命周期中都使用这个EventLoop(`它可以使你从担忧你的ChannelHandler 实现中的线程安全和同步问题中解脱出来。即不会存在多线程竞争问题`）



> 注意：对ThreadLocal的有影响：因为一个EventLoop 通常会被用于支撑多个Channel，所以对于所有相关联的Channel 来说，ThreadLocal 都将是一样的。这使得它对于实现状态追踪等功能来说是个糟糕的选择。



- **阻塞分配模式**

<img src=" assets%5Cimage-20201027003945518.png" alt="image-20201027003945518" style="zoom:80%;" />

> 1. 这里每一个Channel 都将被分配给一个EventLoop（以及它的Thread）。
> 2. 保证是每个Channel 的I/O 事件都将只会被一个Thread（用于支撑该Channel 的EventLoop 的那个Thread）处理



## ChannelHandler和ChannelPipeline

### 1. Channel 的生命周期

| 状态                | 描述                                                         |
| ------------------- | ------------------------------------------------------------ |
| ChannelUnregistered | Channel 已经被创建，但还未注册到EventLoop                    |
| ChannelRegistered   | Channel 已经被注册到了EventLoop                              |
| ChannelActive       | Channel 处于活动状态（已经连接到它的远程节点）。它现在可以接收和发送数据了 |
| ChannelInactive     | Channel 没有连接到远程节点                                   |

> 1. 当这些状态发生改变时，将会生成对应的事件。
>
> 2. 这些事件将会被**转发给**ChannelPipeline 中的**ChannelHandler**可以随后对它们做出响应。

<img src=" assets%5Cimage-20201027005707977.png" alt="image-20201027005707977" style="zoom: 80%;" />

### 2. ChannelHandler 的生命周期

| 类型            | 描述                                                |
| --------------- | --------------------------------------------------- |
| handlerAdded    | 当把ChannelHandler 添加到ChannelPipeline 中时被调用 |
| handlerRemoved  | 当从ChannelPipeline 中移除ChannelHandler 时被调用   |
| exceptionCaught | 当处理过程中在ChannelPipeline 中有错误产生时被调用  |

Netty 定义了下面两个重要的ChannelHandler 子接口：

- **ChannelInboundHandler**——处理入站数据以及各种状态变化；
- **ChannelOutboundHandler**——处理出站数据并且允许拦截所有的操作。



### 3.ChannelInboundHandler 接口

- 生命周期

  | 类型                      | 说明                                                         |
  | :------------------------ | ------------------------------------------------------------ |
  | **ChannelUnregistered**   | Channel 已经被创建，但还未注册到EventLoop                    |
  | **ChannelRegistered**     | Channel 已经被注册到了EventLoop                              |
  | **ChannelActive**         | Channel 处于活动状态（已经连接到它的远程节点）。它现在可以接收和发送数据了 |
  | **ChannelInactive**       | Channel 没有连接到远程节点                                   |
  |                           |                                                              |
  | **channelReadComplete**   | 当Channel上的一个读操作完成时被调用                          |
  | **channelRead**           | 当从Channel 读取数据时被调用                                 |
  | ChannelWritabilityChanged | 当Channel 的可写状态发生改变时被调用。用户可以确保写操作不会完成得太快（以避免发生OutOfMemoryError）<br/>或者可以在Channel 变为再次可写时恢复写入。可以通过调用Channel 的isWritable()方法来检测<br/>Channel 的可写性。与可写性相关的阈值可以通过Channel.config().setWriteHighWaterMark()和Channel.config().setWriteLowWater-<br/>Mark()方法来设置 |
  | **userEventTriggered**    | 当ChannelnboundHandler.fireUserEventTriggered()方法被调用时被调用 |

  
  
  注意： 重写channelRead()方法时，它将**负责显式地释放与池化的ByteBuf 实例相关的内存**
  
  ```
  //扩展了 ChannelInboundHandlerAdapter
  public class DiscardHandler extends ChannelInboundHandlerAdapter {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
          //丢弃已接收的消息
          ReferenceCountUtil.release(msg);
      }
  }
  ```
  
  但是以这种方式管理资源可能很繁琐。一个更加简单的方式是使用**SimpleChannelInboundHandler**。
  
  ```java
  //扩展了SimpleChannelInboundHandler
  public class SimpleDiscardHandler
      extends SimpleChannelInboundHandler<Object> {
      @Override
      public void channelRead0(ChannelHandlerContext ctx,
          Object msg) {
          //不需要任何显式的资源释放
          // No need to do anything special
      }
  }
  ```
  
  > 由于SimpleChannelInboundHandler 会自动释放资源，所以你不应该存储指向任何消息的引用供将来使用，因为这些引用都将会失效。
  >
  > 这个msg会在消息被channelRead0()方法消费之后自动释放消
  
  

### 4. ChannelOutboundHandler接口

出站操作和数据将由ChannelOutboundHandler 处理。它的方法将被Channel、Channel-Pipeline 以及ChannelHandlerContext 调用。

| 类型       | 描述                                               |
| ---------- | -------------------------------------------------- |
| bind       | 当请求将Channel 绑定到本地地址时被调用             |
| connect    | 当请求将Channel 连接到远程节点时被调用             |
| disconnect | 当请求将Channel 从远程节点断开时被调用             |
| close      | 当请求关闭Channel 时被调用                         |
| deregister | 当请求将Channel 从它的EventLoop 注销时被调用       |
| read       | 当请求从Channel 读取更多的数据时被调用             |
| flush      | 当请求通过Channel 将入队数据冲刷到远程节点时被调用 |
| write      | 当请求通过Channel 将数据写到远程节点时被调用       |
| ......     | 还有一些从ChannelHandler中继承过来的方法           |

> **ChannelPromise**与ChannelFuture ChannelOutboundHandler中的大部分方法都需要一个ChannelPromise参数，以便在操作完成时得到通知。**ChannelPromise是ChannelFuture的一个子类**，其定义了一些可写的方法，如setSuccess()和setFailure()，从而使ChannelFuture不可变



### 5. ChannelHandler的适配器

![image-20201027150543169]( assets%5Cimage-20201027150543169.png)

适配器分别提供了ChannelInboundHandler和ChannelOutboundHandler 的基本实现



### 6.消息释放

1. 如果一个消息被消费或者丢弃了，并且没有传递给ChannelPipeline 中的下一个ChannelOutboundHandler，那么用户就有责任调用ReferenceCountUtil.release()。(理解：如果消息不需要继续传下一个handler时，则需要释放消息)

2. 如果消息到达了实际的传输层，那么当它被写入时或者Channel 关闭时，都将被自动释放。

> ```java
> //扩展了ChannelOutboundHandlerAdapter
> public class DiscardOutboundHandler
>     extends ChannelOutboundHandlerAdapter {
>     @Override
>     public void write(ChannelHandlerContext ctx,
>         Object msg, ChannelPromise promise) {
>         //通过使用 ReferenceCountUtil.realse(...)方法释放资源
>         ReferenceCountUtil.release(msg);
>         //通知 ChannelPromise数据已经被处理了
>         promise.setSuccess();
>     }
> }
> ```

> ```java
> //扩展了ChannelInboundandlerAdapter
> public class DiscardInboundHandler extends ChannelInboundHandlerAdapter {
>     @Override
>     public void channelRead(ChannelHandlerContext ctx, Object msg) {
>         //通过调用 ReferenceCountUtil.release()方法释放资源
>         ReferenceCountUtil.release(msg);
>     }
> }
> ```



### 7.ChannelPipeline

- **特点**：

1. 描述：是一个拦截流经Channel的入站和出站事件的ChannelHandler 实例链。

2. 每一个新创建的Channel 都将会被分配一个新的ChannelPipeline。

3. 如果一个入站事件被触发，它将被从ChannelPipeline 的头部开始一直被传播到Channel Pipeline 的尾端。

4. 如果一个出站事件被触发，出站I/O 事件将从ChannelPipeline 的最右边开始，然后向左传播。

   <img src=" assets%5Cimage-20201027152938514.png" alt="image-20201027152938514" style="zoom:80%;" />

> 规则：
>
> 1. 在ChannelPipeline 传播事件时，它会测试ChannelPipeline 中的下一个Channel-Handler 的类型是否和事件的运动方向相匹配。
> 2. 如果不匹配，ChannelPipeline 将跳过该ChannelHandler 并前进到下一个，直到它找到和该事件所期望的方向相匹配的为止
>
> > 当然，ChannelHandler 也可以同时实现ChannelInboundHandler 接口和ChannelOutbound-Handler 接口。



入站事件

<img src=" assets%5Cimage-20201028221341043.png" alt="image-20201028221341043" style="zoom:80%;" />



出站事件

<img src=" assets%5Cimage-20201028221500404.png" alt="image-20201028221500404" style="zoom:80%;" />



- **修改ChannelPipeline**

  1. ChannelHandler 可以通过添加、删除或者替换其他的ChannelHandler 来实时地修改ChannelPipeline 的布局。（它也可以将它自己从ChannelPipeline 中移除。）

  | 类型                                  | 描述                                                         |
  | ------------------------------------- | ------------------------------------------------------------ |
  | AddFirstaddBefore<br/>addAfteraddLast | 将一个ChannelHandler 添加到ChannelPipeline 中                |
  | remove                                | 将一个ChannelHandler 从ChannelPipeline 中移除                |
  | replace                               | 将ChannelPipeline 中的一个ChannelHandler 替换为另一个ChannelHandler |

  ```java
  public class ModifyChannelPipeline {
      /**
       * 代码清单 6-5 修改 ChannelPipeline
       * */
      public static void modifyPipeline() {
          ChannelPipeline pipeline = ......; // get reference to pipeline;
          //创建一个 FirstHandler 的实例
          FirstHandler firstHandler = new FirstHandler();
          //1. 将该实例作为"handler1"添加到ChannelPipeline 中
          pipeline.addLast("handler1", firstHandler);
          //2. 将一个 SecondHandler的实例作为"handler2"添加到 ChannelPipeline的第一个槽中。这意味着它将被放置在已有的"handler1"之前
          pipeline.addFirst("handler2", new SecondHandler());
          //3. 将一个 ThirdHandler 的实例作为"handler3"添加到 ChannelPipeline 的最后一个槽中
          pipeline.addLast("handler3", new ThirdHandler());
          //...
          //4. 通过名称移除"handler3"
          pipeline.remove("handler3");
          //5. 通过引用移除FirstHandler（它是唯一的，所以不需要它的名称）
          pipeline.remove(firstHandler);
          //6. 将 SecondHandler("handler2")替换为 FourthHandler:"handler4"
          pipeline.replace("handler2", "handler4", new FourthHandler());
      }
      private static final class FirstHandler
          extends ChannelHandlerAdapter {
  
      }
      private static final class SecondHandler
          extends ChannelHandlerAdapter {
      }
  
      private static final class ThirdHandler
          extends ChannelHandlerAdapter {
      }
      private static final class FourthHandler
          extends ChannelHandlerAdapter {
      }
  }
  ```



### 8.ChannelHandlerContext

- **特点：**

1. 功能：管理它所关联的ChannelHandler 和在同一个ChannelPipeline 中的其他ChannelHandler 之间的交互。

2. 每当有ChannelHandler 添加到ChannelPipeline 中时，都会创建ChannelHandlerContext

3. Context的很多方法在handler和pipeline中也都存在，但有区别：

   > 1. 如果调用Channel 或者ChannelPipeline 上的这些方法，它们将沿着整个ChannelPipeline 进行传播
   > 2. 调用位于ChannelHandlerContext上的相同方法，则将从当前所关联的ChannelHandler 开始，并且只会传播给位于该
   >    ChannelPipeline 中的下一个能够处理该事件的ChannelHandler。

   

- **使用ChannelHandlerContext**

<img src=" assets%5Cimage-20201027155237401.png" alt="image-20201027155237401" style="zoom: 67%;" />

1. ChannelHandlerContext 获取到**Channel** 的引用。调用Channel 上的write()方法将会导致写入事件**从尾端到头部地流经**ChannelPipeline

2. ChannelHandlerContext 获取到**ChannelPipeline**的引用，调用ChannelPipeline上的write()方法将**一直传播事件通过整个**ChannelPipeline

   <img src=" assets%5Cimage-20201027162729727.png" alt="image-20201027162729727" style="zoom:50%;" />

3. 直接使用ChannelHandlerContext 的write()方法，消息将从下一个ChannelHandler 开始流经ChannelPipeline，绕过了
   所有前面的ChannelHandler

   <img src=" assets%5Cimage-20201027162702483.png" alt="image-20201027162702483" style="zoom:50%;" />

4. 事件和方法：

![image-20201028221737978]( assets%5Cimage-20201028221737978.png)



### 9. 异常处理

#### 处理入站异常

1. 需要在你的ChannelInboundHandler 实现中重写exceptionCaught()方法。

   > ```java
   > public class InboundExceptionHandler extends ChannelInboundHandlerAdapter {
   >     @Override
   >     public void exceptionCaught(ChannelHandlerContext ctx,
   >         Throwable cause) {
   >         cause.printStackTrace();
   >         ctx.close();
   >     }
   > }
   > ```

> 1. ChannelHandler.exceptionCaught()的默认实现是简单地将当前异常转发给ChannelPipeline 中的下一ChannelHandler；
> 2. 如果异常到达了ChannelPipeline 的尾端，它将会被记录为未被处理；
> 3. 要想定义自定义的处理逻辑，你需要重写exceptionCaught()方法。然后你需要决定是否需要将该异常传播出去。



#### 处理出站异常

用于处理出站操作中的正常完成以及异常的选项，都基于以下的通知机制。

1. 每个出站操作都将返回一个**ChannelFuture**。注册到ChannelFuture 的ChannelFutureListener 将在操作完成时被通知该操作是成功了还是出错了。

2. 几乎所有的ChannelOutboundHandler 上的方法都会传入一个**ChannelPromise**的实例。作为ChannelFuture 的子类，ChannelPromise 也可以被分配用于异步通知的监听器。但是，ChannelPromise 还具有提供立即通知的可写方法：

   > ChannelPromise setSuccess();
   > Chan nelPromise setFailure(Throwable cause);

方式一：添加ChannelFutureListener 到ChannelFuture

```java
public static void addingChannelFutureListener(){
    Channel channel = CHANNEL_FROM_SOMEWHERE; // get reference to pipeline;
    ByteBuf someMessage = SOME_MSG_FROM_SOMEWHERE; // get reference to pipeline;
    //...
    io.netty.channel.ChannelFuture future = channel.write(someMessage);
    future.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(io.netty.channel.ChannelFuture f) {
            if (!f.isSuccess()) {
                f.cause().printStackTrace();
                f.channel().close();
            }
        }
    });
}
```

​      方式二： 添加ChannelFutureListener 到ChannelPromise

```java
@Override
public void write(ChannelHandlerContext ctx, Object msg,
    ChannelPromise promise) {
    promise.addListener(new ChannelFutureListener() { // 添加到ChannelPromise
        @Override
        public void operationComplete(ChannelFuture f) {
            if (!f.isSuccess()) {
                f.cause().printStackTrace();
                f.channel().close();
            }
        }
    });
}
```



## ByteBuf

### ByteBuf的数据结构

1. ByteBuf 维护了两个不同的索引：一个用于读取，一个用于写入
2. 从ByteBuf 读取时，它的**readerIndex** 将会被递增已经被读取的字节数。
3. 写入ByteBuf 时，它的**writerIndex** 也会被递增
4. 名称以**read** 或者**write** 开头的ByteBuf 方法，将会**推进其对应的索引**
5. 名称以**set** 或者**get** 开头的操作则不会。
6. 可以指定ByteBuf 的最大容量试图移动写索引（即writerIndex）超过这个值将会触发一个异常①。（默认的限制是Integer.MAX_VALUE。）

<img src=" assets%5Cimage-20201027164853948.png" alt="image-20201027164853948" style="zoom:80%;" />

> 异常：
>
> 1. 读取字节直到readerIndex 达到和writerIndex 同样的值时触发一个IndexOutOfBoundsException。



### 三种缓冲区

- **堆缓冲区**

  1. 存储在JVM 的堆空间中，它能在没有使用池化的情况下提供快速的分配和释放。这种模式被称为**支撑数组**（backing array）
  2. 非常适合于有遗留的数据需要处理的情况。

  ```java
  public static void heapBuffer() {
          ByteBuf heapBuf = Unpooled.buffer(1024); //get reference form somewhere
          //检查 ByteBuf 是否有一个支撑数组
          if (heapBuf.hasArray()) {
              //如果有，则获取对该数组的引用
              byte[] array = heapBuf.array(); // !!!这个就是backing array
              //计算第一个字节的偏移量
              int offset = heapBuf.arrayOffset() + heapBuf.readerIndex();
              //获得可读字节数
              int length = heapBuf.readableBytes();
              //使用数组、偏移量和长度作为参数调用你的方法
              handleArray(array, offset, length);
          }
  }
  ```

  > 注意 当hasArray()方法返回false 时，尝试访问支撑数组将触发一个UnsupportedOperationException。这个模式类似于JDK 的ByteBuffer 的用法。

  

- **直接缓冲区**

  1. 直接缓冲区的内容将驻留在常规的会被垃圾回收的堆之外,`因此直接缓冲区对于网络数据传输是理想的选择`

  2. 主要缺点

     > 1. 相对于基于堆的缓冲区，它们的分配和释放都较为昂贵。
     > 2. 因为数据不是在堆上，所以你不得不进行一次复制
     >
     > 建议： 如果事先知道容器中的数据将会被作为数组来访问，你可能更愿意使用堆内存。

  ```java
  public static void directBuffer() {
      ByteBuf directBuf = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
      //检查 ByteBuf 是否由数组支撑。如果不是，则这是一个直接缓冲区
      if (!directBuf.hasArray()) {
          //获取可读字节数
          int length = directBuf.readableBytes();
          //分配一个新的数组来保存具有该长度的字节数据
          byte[] array = new byte[length];
          //将字节复制到该数组
          directBuf.getBytes(directBuf.readerIndex(), array);
          //使用数组、偏移量和长度作为参数调用你的方法
          handleArray(array, 0, length);
      }
  }
  ```

- **复合缓冲区**
  
  1. **CompositeByteBuf**提供了一个将多个缓冲区表示为单个合并缓冲区的实现。    

```java
public static void byteBufComposite() {
    CompositeByteBuf messageBuf = Unpooled.compositeBuffer();
    ByteBuf headerBuf = Unpooled.buffer(100);
    headerBuf.writeBytes("这是head".getBytes());
    ByteBuf bodyBuf = Unpooled.buffer(100);;   
    bodyBuf.writeBytes("这是body!!!".getBytes());
    //将 ByteBuf 实例追加到 CompositeByteBuf
    messageBuf.addComponents(headerBuf, bodyBuf); // 1.将两个缓冲区合并成一个
    //...
    //循环遍历所有的 ByteBuf 实例
    for (ByteBuf buf : messageBuf) {    // 2. 可以遍历复合缓冲区
        byte[] content = new byte[buf.readableBytes()];
        buf.readBytes(content);
        System.out.println(new String(content));
    }
    //3. 删除位于索引位置为 0（第一个组件）的 ByteBuf
    messageBuf.removeComponent(0); // remove the header

}

/**
这是head
这是body!!!
*/
```

> 如果使用JDK提供的Buffer的情况
>
> ```java
> public static void main(String[] args) {
> 				ByteBuffer header = ByteBuffer.allocate(8);
>         ByteBuffer body = ByteBuffer.allocate(100);
>         header.putInt(200);
>         header.flip();
>         body.put("body".getBytes());
>         body.flip();
> 
>         ByteBuffer composite = ByteBuffer.allocate(header.remaining()+body.remaining());
> 
>         composite.put(header);
>         composite.put(body);
>         composite.flip();
> 
>         int getHead;
>         byte[] getBody = new byte[10];
>         if(composite.hasArray()){
>             getHead = composite.getInt();  // 这里会直接取4个字节，并移动4个字节的位置
>             composite.get(getBody,0,composite.remaining());// 解析剩下的字符串对应的字节
>             System.out.println("整数head:"+getHead);
>             System.out.println("字符串body:"+new String(getBody));
>         }
> }
> /**
> 整数head:200
> 字符串body:body
> */
> ```



### 缓冲字节分区

<img src=" assets%5Cimage-20201027195606623.png" alt="image-20201027195606623" style="zoom:80%;" />

1. 名称以**read** 或者**write** 开头的ByteBuf 方法，将会**推进其对应的索引**
2. 名称以**set** 或者**get** 开头的操作则不会。

```java
public static void write() {
    // Fills the writable bytes of a buffer with random integers.
    ByteBuf buffer = Unpooled.buffer(100); //get reference form somewhere
    buffer.writeInt(666);
    buffer.writeBytes("content".getBytes());
    System.out.println("初始readIndex:"+buffer.readerIndex());
    System.out.println("初始writeIndex:"+buffer.writerIndex());

    ByteBuf byteBuf = buffer.readBytes(4);
    System.out.println("移动后的readIndex:"+buffer.readerIndex());
    System.out.println("移动后的writeIndex:"+buffer.writerIndex());

    if(byteBuf.hasArray()){
        System.out.println(new String(byteBuf.array()));
    }else{
        System.out.println("整数值为："+byteBuf.readInt());  // 注意，非字符串的部分，走的是直接缓存
    }

    ByteBuf contentBytebuf = buffer.readBytes(buffer.readableBytes());
    byte[] content = new byte[contentBytebuf.readableBytes()];
    contentBytebuf.readBytes(content);
    System.out.println("字符串内容："+new String(content));
}
/**
初始readIndex:0
初始writeIndex:11
移动后的readIndex:4
移动后的writeIndex:11
整数值为：666
字符串内容：content
*/
```



### 引用计数

1. 引用计数是一种通过在某个对象所持有的资源不再被其他对象引用时释放该对象所持有的资源来优化内存使用和性能的技术.

2. 原理：只要引用计数大于0，就能保证对象不会被释放。当活动引用的数量减少到0 时，该实例就会被释放。

   ```java
   /**
    * 代码清单 5-16 释放引用计数的对象
    */
   public static void releaseReferenceCountedObject(){
       ByteBuf buffer = Unpooled.buffer(10); //get reference form somewhere
       buffer.writeInt(300);
       //减少到该对象的活动引用。当减少到 0 时，该对象被释放，并且该方法返回 true
       System.out.println("引用计数为："+buffer.refCnt());
       boolean released = buffer.release();
       System.out.println("引用计数为："+buffer.refCnt());
   }
   /**
   引用计数为：1
   引用计数为：0
   */
   ```



## 传输-Channel



### OIO阻塞版本

```java
public class PlainOioServer {
    public void serve(int port) throws IOException {
        //将服务器绑定到指定端口
        final ServerSocket socket = new ServerSocket(port);
        try {
            for(;;) {
                //接受连接
                final Socket clientSocket = socket.accept();
                System.out.println(
                        "Accepted connection from " + clientSocket);
                //创建一个新的线程来处理该连接
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        OutputStream out;
                        try {
                            //将消息写给已连接的客户端
                            out = clientSocket.getOutputStream();
                            out.write("Hi!\r\n".getBytes(
                                    Charset.forName("UTF-8")));
                            out.flush();
                            //关闭连接
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                clientSocket.close();
                            } catch (IOException ex) {
                                // ignore on close
                            }
                        }
                //启动线程
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```



### NIO异步版本

```java
public class PlainNioServer {
    public void serve(int port) throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        ServerSocket ss = serverChannel.socket();
        InetSocketAddress address = new InetSocketAddress(port);
        //将服务器绑定到选定的端口
        ss.bind(address);
        //打开Selector来处理 Channel
        Selector selector = Selector.open();
        //将ServerSocket注册到Selector以接受连接
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        final ByteBuffer msg = ByteBuffer.wrap("Hi!\r\n".getBytes());
        for (;;){
            try {
                //等待需要处理的新事件；阻塞将一直持续到下一个传入事件
                selector.select();
            } catch (IOException ex) {
                ex.printStackTrace();
                //handle exception
                break;
            }
            //获取所有接收事件的SelectionKey实例
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                try {
                    //检查事件是否是一个新的已经就绪可以被接受的连接
                    if (key.isAcceptable()) {
                        ServerSocketChannel server =
                                (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        //接受客户端，并将它注册到选择器
                        client.register(selector, SelectionKey.OP_WRITE |
                                SelectionKey.OP_READ, msg.duplicate());
                        System.out.println(
                                "Accepted connection from " + client);
                    }
                    //检查套接字是否已经准备好写数据
                    if (key.isWritable()) {
                        SocketChannel client =
                                (SocketChannel) key.channel();
                        ByteBuffer buffer =
                                (ByteBuffer) key.attachment();
                        while (buffer.hasRemaining()) {
                            //将数据写到已连接的客户端
                            if (client.write(buffer) == 0) {
                                break;
                            }
                        }
                        //关闭连接
                        client.close();
                    }
                } catch (IOException ex) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException cex) {
                        // ignore on close
                    }
                }
            }
        }
    }
}
```



### 通过Netty 使用OIO

```java
public class NettyOioServer {
    public void server(int port)
            throws Exception {
        final ByteBuf buf =
                Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("Hi!\r\n", Charset.forName("UTF-8")));
        EventLoopGroup group = new OioEventLoopGroup();
        try {
            //创建 ServerBootstrap
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    //使用 OioEventLoopGroup以允许阻塞模式（旧的I/O）
                    .channel(OioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
                    //指定 ChannelInitializer，对于每个已接受的连接都调用它
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch)
                                throws Exception {
                                ch.pipeline().addLast(
                                    //添加一个 ChannelInboundHandlerAdapter以拦截和处理事件
                                    new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelActive(
                                                ChannelHandlerContext ctx)
                                                throws Exception {
                                            ctx.writeAndFlush(buf.duplicate())
                                                    .addListener(
                                                            //将消息写到客户端，并添加 ChannelFutureListener，
                                                            //以便消息一被写完就关闭连接
                                                            ChannelFutureListener.CLOSE);
                                        }
                                    });
                        }
                    });
            //绑定服务器以接受连接
            ChannelFuture f = b.bind().sync();
            f.channel().closeFuture().sync();
        } finally {
            //释放所有的资源
            group.shutdownGracefully().sync();
        }
    }
}
```



### 通过Netty 使用NIO

```java
public class NettyNioServer {
    public void server(int port) throws Exception {
        final ByteBuf buf =
                Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("Hi!\r\n",
                        Charset.forName("UTF-8")));
        //为非阻塞模式使用NioEventLoopGroup
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            //创建ServerBootstrap
            ServerBootstrap b = new ServerBootstrap();
            b.group(group).channel(NioServerSocketChannel.class)
                .localAddress(new InetSocketAddress(port))
                //指定 ChannelInitializer，对于每个已接受的连接都调用它
                .childHandler(new ChannelInitializer<SocketChannel>() {
                      @Override
                      public void initChannel(SocketChannel ch)throws Exception {
                          ch.pipeline().addLast(
                             //添加 ChannelInboundHandlerAdapter以接收和处理事件
                              new ChannelInboundHandlerAdapter() {
                                  @Override
                                  public void channelActive( ChannelHandlerContext ctx) throws Exception {
                                      //将消息写到客户端，并添加ChannelFutureListener，
                                      //以便消息一被写完就关闭连接
                                        ctx.writeAndFlush(buf.duplicate())
                                            .addListener(ChannelFutureListener.CLOSE);
                                  }
                              });
                      }
                  }
                );
            //绑定服务器以接受连接
            ChannelFuture f = b.bind().sync();
            f.channel().closeFuture().sync();
        } finally {
            //释放所有的资源
            group.shutdownGracefully().sync();
        }
    }

    public static void main(String[] args) throws Exception {
        NettyNioServer nettyNioServer = new NettyNioServer();
        nettyNioServer.server(8888);
    }
}
```



### 传输API：Channel

传输API 的核心是interface Channel，它被用于所有的I/O 操作

![image-20201027220829116]( assets%5Cimage-20201027220829116.png)

1. 每个Channel 都将会被分配一个ChannelPipeline 和ChannelConfig。ChannelConfig 包含了该Channel 的所有配置设置，并且支持热更新。

2. Channel 是独一无二的，所以为了保证顺序将Channel 声明为java.lang.Comparable 的一个子接口。

   > 如果两个不同的Channel 实例都返回了相同的散列码，那么AbstractChannel 中的compareTo()方法的实现将会抛出一Error。

3. **ChannelPipeline** 拥有应用于入站和出站数据以及事件的所有ChannelHandler 实例。

   > ChannelPipeline 实现了一种常见的设计模式—**拦截过滤器**（InterceptingFilter）。UNIX 管道是另外一个熟悉的例子：多个命令被链接在一起，其中一个命令的输出端将连接到命令行中下一个命令的输入端。

4. Netty 的Channel 实现**是线程安全的**，因此你可以存储一个到Channel 的引用，并且每当你需要向远程节点写数据时，都可以使用它，即使当时许多线程都在使用它.

   ``` java
   /**
    * 代码清单 4-6 从多个线程使用同一个 Channel
    */
   public static void writingToChannelFromManyThreads() {
       final Channel channel = .....; // Get the channel reference from somewhere
       //创建持有要写数据的ByteBuf
       final ByteBuf buf = Unpooled.copiedBuffer("your data",
               CharsetUtil.UTF_8);
       //创建将数据写到Channel 的 Runnable
       Runnable writer = new Runnable() {
           @Override
           public void run() {
               channel.write(buf.duplicate());
           }
       };
       //获取到线程池Executor 的引用
       Executor executor = Executors.newCachedThreadPool();
   
       //递交写任务给线程池以便在某个线程中执行
       // write in one thread
       executor.execute(writer);
   
       //递交另一个写任务以便在另一个线程中执行
       // write in another thread
       executor.execute(writer);
       //...
   }
   ```

   

### Channel方法

| 方法          | 描述                                                         |
| ------------- | ------------------------------------------------------------ |
| eventLoop     | 返回分配给Channel 的EventLoop                                |
| pipeline      | 返回分配给Channel 的ChannelPipeline                          |
| isActive      | 如果Channel 是活动的，则返回true。                           |
| localAddress  | 返回本地的SokcetAddress                                      |
| remoteAddress | 返回远程的SocketAddress                                      |
| write         | 将数据写到远程节点。这个数据将被传递给ChannelPipeline，并且排队直到它被冲刷（调用flush） |
| flush         | 将之前已写的数据冲刷到底层传输，如一个Socket                 |
| writeAndFlush | 一个简便的方法，等同于调用write()并接着调用flush()           |



```java
public static void writingToChannel() {
    Channel channel = CHANNEL_FROM_SOMEWHERE; // Get the channel reference from somewhere
    //创建持有要写数据的 ByteBuf
    ByteBuf buf = Unpooled.copiedBuffer("your data", CharsetUtil.UTF_8);
    ChannelFuture cf = channel.writeAndFlush(buf);
    //添加 ChannelFutureListener 以便在写操作完成后接收通知
    cf.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            //写操作完成，并且没有错误发生
            if (future.isSuccess()) {
                System.out.println("Write successful");
            } else {
                //记录错误
                System.err.println("Write error");
                future.cause().printStackTrace();
            }
        }
    });
}
```



### Netty的传输类型

| 名称      | 包                          | 描述                                                         |
| --------- | --------------------------- | ------------------------------------------------------------ |
| **NIO**   | io.netty.channel.socket.nio | 使用java.nio.channels 包作为基础—基于**Selector**的方式      |
| **Epoll** | io.netty.channel.epoll      | 由JNI 驱动的**epoll()**和非阻塞IO.。<br />这个传输支持**只有在Linux 上**可用的多种特性，如SO_REUSEPORT，比NIO 传输更快，而且是完全非阻塞的 |
| OIO       | io.netty.channel.socket.oio | 使用java.net 包作为基础—使用阻塞流                           |
| Local     | io.netty.channel.local      | 可以在VM 内部通过管道进行通信的本地传输                      |
| Embedded  | io.netty.channel.embedded   | Embedded 传输，允许使用ChannelHandler 而又不需要一个真正的基于网络的传输。这在测试你的ChannelHandler 实现时非常有用 |



### 零拷贝

1. 零拷贝（zero-copy）是一种目前**只有在使用NIO 和Epoll 传输时**才可使用的特性。它使你可以**快速高效地将数据从文件系统移动到网络接口**，而**不需要将其从内核空间复制到用户空间**，其在像FTP 或者HTTP 这样的协议中可以显著地提升性能。
2. 但是，并不是所有的操作系统都支持这一特性。特别地，它对于实现了数据加密或者压缩的文件系统是不可用的——只能传输文件的原始内容。反过来说，传输已被加密的文件则不是问题。



## 编/解码器

### 解码器

​	分为两种类型：

1. 将**字节解码为消息**—**ByteToMessageDecoder** 和**ReplayingDecoder**；
2. 将一种**消息类型解码为另一种**——**MessageToMessageDecoder**。



#### 1. ByteToMessageDecoder

<img src=" assets%5Cimage-20201027230838236.png" alt="image-20201027230838236" style="zoom:80%;" />

1. ByteToMessageDecoder是**用于数据入站的时候，进行的解码**



| 方法                                                         | 描述                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| decode(ChannelHandlerContext **ctx**,ByteBuf **in**,List<Object> **out**) | 1.必须实现的唯一抽象方法。<br />2.decode()方法被调用时将会传入一个包含了传入数据的ByteBuf，以及一个用来添加解码消息的List<br />3.对这个方法的调用将会重复进行，直到确定没有新的元素被添加到该List，或者该ByteBuf 中没有更多可读取的字节时为止。然后，如果该List 不为空，那么它的内容将会被传递给ChannelPipeline 中的下一个ChannelInboundHandler |
| decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) | Netty提供的这个默认实现只是简单地调用了decode()方法。当Channel的状态变为非活动时，这个方法将会被调用一次。可以重写该方法以提供特殊的处理 |

<img src=" assets%5Cimage-20201027231539497.png" alt="image-20201027231539497" style="zoom:80%;" />

```java
//扩展ByteToMessageDecoder类，以将字节解码为特定的格式
public class ToIntegerDecoder extends ByteToMessageDecoder {
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in,
        List<Object> out) throws Exception {
        //检查是否至少有 4 字节可读（一个 int 的字节长度）
        if (in.readableBytes() >= 4) {
            //从入站 ByteBuf 中读取一个 int，并将其添加到解码消息的 List 中
            out.add(in.readInt());
        }
    }
}
```

> 虽然ByteToMessageDecoder 使得可以很简单地实现这种模式，但是你可能会发现，在调用readInt()方法前不得不验证所输入的ByteBuf 是否具有足够的数据有点繁琐在下一节中，我们将讨论ReplayingDecoder，它是一个特殊的解码器，以少量的开销消除了这个步骤。



#### 2. ReplayingDecoder

1. ReplayingDecoder扩展了ByteToMessageDecoder类使得我们**不必调用readableBytes()方**法。

   > public abstract class ReplayingDecoder<S> extends ByteToMessageDecoder
   >
   > 类型参数S 指定了用于状态管理的类型，其中Void 代表不需要状态管理

   ```java
   //扩展 ReplayingDecoder<Void> 以将字节解码为消息
   public class ToIntegerDecoder2 extends ReplayingDecoder<Void> {
   
       @Override
       public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
         	//传入的  ByteBuf in 是 ReplayingDecoderByteBuf
         
           //从入站 ByteBuf 中读取 一个 int，并将其添加到解码消息的 List 中
           out.add(in.readInt());
       }
   }
   ```

2. 如果没有足够的字节可用，这个readInt()方法的实现将会抛出一个Error。

   > 简单的准则：如果使用ByteToMessageDecoder 不会引入太多的复杂性，那么请使用它；
   >
   > 否则，请使用ReplayingDecoder。



#### 3. MessageToMessageDecoder

1. 在两个消息格式之间进行转换(例如，从一种POJO 类型转换为另一种)

   > public abstract class MessageToMessageDecoder<I> extends ChannelInboundHandlerAdapter
   >
   > 类型**参数I** 指定了decode()方法的输入参数msg 的类型

   | 方法                                                      | 描述                                                         |
   | --------------------------------------------------------- | ------------------------------------------------------------ |
   | decode(ChannelHandlerContext ctx,I msg, List<Object> out) | 对于每个需要被解码为另一种格式的入站消息来说，该方法都将会被调用。解码消息随后会被传递给ChannelPipeline中的下一个ChannelInboundHandler |

   <img src=" assets%5Cimage-20201027234729768.png" alt="image-20201027234729768" style="zoom:80%;" />

```java
//扩展了MessageToMessageDecoder<Integer>
public class IntegerToStringDecoder extends
    MessageToMessageDecoder<Integer> {
    @Override
    public void decode(ChannelHandlerContext ctx, Integer msg,
        List<Object> out) throws Exception {
        //将 Integer 消息转换为它的 String 表示，并将其添加到输出的 List 中
        out.add(String.valueOf(msg));
    }
}
```



#### 4. TooLongFrameException

1. 由于Netty 是一个异步框架，所以需要在字节可以解码之前在内存中缓冲它们因此，不能让解码器缓冲大量的数据以至于耗尽可用的内存。
2. Netty 提供了**TooLongFrameException** 类，其将由解码器在帧超出指定的大小限制时抛出。

> 使用场景：
>
> 1. 可以设置一个最大字节数的阈值，如果超出该阈值，则会导致抛出一个TooLongFrameException（随后会被ChannelHandler.exceptionCaught()方法捕获）。
> 2. 然后，如何处理该异常则完全取决于该解码器的用户。
> 3. 某些协议（如HTTP）可能允许你返回一个特殊的响应。而在其他的情况下，唯一的选择可能就是关闭对应的连接。

```java
//扩展 ByteToMessageDecoder 以将字节解码为消息
public class SafeByteToMessageDecoder extends ByteToMessageDecoder {
    private static final int MAX_FRAME_SIZE = 1024;
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in,
        List<Object> out) throws Exception {
            int readable = in.readableBytes();
            //检查缓冲区中是否有超过 MAX_FRAME_SIZE 个字节
            if (readable > MAX_FRAME_SIZE) {
                //跳过所有的可读字节，抛出 TooLongFrameException 并通知 ChannelHandler
                in.skipBytes(readable);
                throw new TooLongFrameException("Frame too big!");
        }
        // do something
        // ...
    }
}

```

> 如果你正在使用一个可变帧大小的协议，那么这种保护措施将是尤为重要的。



### 编码器

#### 1. MessageToByteEncoder

| 方法                                                  | 描述                                                         |
| ----------------------------------------------------- | ------------------------------------------------------------ |
| encode(ChannelHandlerContext ctx, I msg, ByteBuf out) | encode()方法是你需要实现的唯一抽象方法<br />它被调用时将会传入要被该类编码为ByteBuf 的（类型为I 的）出站消息。该ByteBuf 随后将会被转发给ChannelPipeline中的下一个ChannelOutboundHandler |

```java
//扩展了MessageToByteEncoder
public class ShortToByteEncoder extends MessageToByteEncoder<Short> {
    @Override
    public void encode(ChannelHandlerContext ctx, Short msg, ByteBuf out)
        throws Exception {
        //将 Short 写入 ByteBuf 中
        out.writeShort(msg);
    }
}
```

<img src=" assets%5Cimage-20201027235913722.png" alt="image-20201027235913722" style="zoom:80%;" />



#### 2. MessageToMessageEncoder

> 将入站数据从一种消息格式解码为另一种

| 方法                                                      | 描述                                                         |
| --------------------------------------------------------- | ------------------------------------------------------------ |
| encode(ChannelHandlerContext ctx,I msg, List<Object> out) | 这是你需要实现的唯一方法。<br />每个通过write()方法写入的消息都将会被传递给encode()方法，以编码为一个或者多个出站消息。随后，这些出站消息将会被转发给ChannelPipeline中的下一个ChannelOutboundHandler |

```java
//扩展了 MessageToMessageEncoder
public class IntegerToStringEncoder
    extends MessageToMessageEncoder<Integer> {
    @Override
    public void encode(ChannelHandlerContext ctx, Integer msg,
        List<Object> out) throws Exception {
        //将 Integer 转换为 String，并将其添加到 List 中
        out.add(String.valueOf(msg));
    }
}
```

<img src=" assets%5Cimage-20201028000434300.png" alt="image-20201028000434300" style="zoom:80%;" />



### 编解码器

> 同时实现编码和解码功能

#### 1. ByteToMessageCodec

| 方法                                                         | 描述                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| decode(ChannelHandlerContext ctx, ByteBuf in, List<Object>)  | 只要有字节可以被消费，这个方法就将会被调用。<br/>它将入站ByteBuf 转换为指定的消息格式， 并将其转发给ChannelPipeline 中的下一个ChannelInboundHandler |
| decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) | 这个方法的默认实现委托给了decode()方法。它只会在<br/>Channel 的状态变为非活动时被调用一次。它可以被重写以实现特殊的处理 |
| encode( ChannelHandlerContext ctx, I msg, ByteBuf out)       | 对于每个将被编码并写入出站ByteBuf 的（类型为I 的）消息来说，这个方法都将会被调用 |



#### 2. MessageToMessageCodec

| 方法                                                         | 描述                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| decode( ChannelHandlerContext ctx, INBOUND_IN msg, List<Object> out) | 这个方法被调用时会被传入INBOUND_IN 类型的消息。 它将把它们解码为OUTBOUND_IN 类型的消息，这些消息 将被转发给ChannelPipeline 中的下一个ChannelInboundHandler |
| encode(ChannelHandlerContext ctx, OUTBOUND_IN msg, List<Object> out) | 对于每个OUTBOUND_IN 类型的消息，这个方法都将会被调用。这些消息将会被编码为INBOUND_IN 类型的消息，然后被转发给ChannelPipeline 中的下一个ChannelOutboundHandler |

> 1. decode() 方法是将INBOUND_IN 类型的消息转换为OUTBOUND_IN 类型的消息， 而encode()方法则进行它的逆向操作。
>
> 2. 将INBOUND_IN类型的消息看作是通过网络发送的类型，而将OUTBOUND_IN类型的消息看作是应用程序所处理的类型，



```java
public class WebSocketConvertHandler extends
     MessageToMessageCodec<WebSocketFrame,WebSocketConvertHandler.MyWebSocketFrame> {
     @Override
     //将 MyWebSocketFrame 编码为指定的 WebSocketFrame 子类型
     protected void encode(ChannelHandlerContext ctx,
         WebSocketConvertHandler.MyWebSocketFrame msg,
         List<Object> out) throws Exception {
         ByteBuf payload = msg.getData().duplicate().retain();
         //实例化一个指定子类型的 WebSocketFrame
         switch (msg.getType()) {
             case BINARY:
                 out.add(new BinaryWebSocketFrame(payload));
                 break;
             case TEXT:
                 out.add(new TextWebSocketFrame(payload));
                 break;
             case CLOSE:
                 out.add(new CloseWebSocketFrame(true, 0, payload));
                 break;
             case CONTINUATION:
                 out.add(new ContinuationWebSocketFrame(payload));
                 break;
             case PONG:
                 out.add(new PongWebSocketFrame(payload));
                 break;
             case PING:
                 out.add(new PingWebSocketFrame(payload));
                 break;
             default:
                 throw new IllegalStateException(
                     "Unsupported websocket msg " + msg);}
    }

    @Override
    //将 WebSocketFrame 解码为 MyWebSocketFrame，并设置 FrameType
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg,
        List<Object> out) throws Exception {
        ByteBuf payload = msg.content().duplicate().retain();
        if (msg instanceof BinaryWebSocketFrame) {
            out.add(new MyWebSocketFrame(
                    MyWebSocketFrame.FrameType.BINARY, payload));
        } else
        if (msg instanceof CloseWebSocketFrame) {
            out.add(new MyWebSocketFrame (
                    MyWebSocketFrame.FrameType.CLOSE, payload));
        } else
        if (msg instanceof PingWebSocketFrame) {
            out.add(new MyWebSocketFrame (
                    MyWebSocketFrame.FrameType.PING, payload));
        } else
        if (msg instanceof PongWebSocketFrame) {
            out.add(new MyWebSocketFrame (
                    MyWebSocketFrame.FrameType.PONG, payload));
        } else
        if (msg instanceof TextWebSocketFrame) {
            out.add(new MyWebSocketFrame (
                    MyWebSocketFrame.FrameType.TEXT, payload));
        } else
        if (msg instanceof ContinuationWebSocketFrame) {
            out.add(new MyWebSocketFrame (
                    MyWebSocketFrame.FrameType.CONTINUATION, payload));
        } else
        {
            throw new IllegalStateException(
                    "Unsupported websocket msg " + msg);
        }
    }

    //声明 WebSocketConvertHandler 所使用的 OUTBOUND_IN 类型
    public static final class MyWebSocketFrame {
        //定义拥有被包装的有效负载的 WebSocketFrame 的类型
        public enum FrameType {
            BINARY,
            CLOSE,
            PING,
            PONG,
            TEXT,
            CONTINUATION
        }
        private final FrameType type;
        private final ByteBuf data;

        public MyWebSocketFrame(FrameType type, ByteBuf data) {
            this.type = type;
            this.data = data;
        }
        public FrameType getType() {
            return type;
        }
        public ByteBuf getData() {
            return data;
        }
    }
}
```



#### 3. CombinedChannelDuplexHandler

> 结合一个解码器和编码器可能会对可重用性造成影响,CombinedChannelDuplexHandler 可以避免这种情况

public class CombinedChannelDuplexHandler<

​							I extends ChannelInboundHandler, 

​							O e xtends ChannelOutboundHandler >

这个类充当了ChannelInboundHandler 和ChannelOutboundHandler（该类的类型参数I 和O）的容器。通过提供分别继承了解码器类和编码器类的类型，我们可以实现一个编解码器，而又不必直接扩展抽象的编解码器类。

```java
//通过该解码器和编码器实现参数化 CombinedByteCharCodec
public class CombinedByteCharCodec extends
    CombinedChannelDuplexHandler<ByteToCharDecoder, CharToByteEncoder> {
    public CombinedByteCharCodec() {
        //将委托实例传递给父类
        super(new ByteToCharDecoder(), new CharToByteEncoder());
    }
}

//扩展了ByteToMessageDecoder
public class ByteToCharDecoder extends ByteToMessageDecoder {
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in,
        List<Object> out) throws Exception {
        if (in.readableBytes() >= 2) {
            //将一个或者多个 Character 对象添加到传出的 List 中
            out.add(in.readChar());
        }
    }
}

//扩展了MessageToByteEncoder
public class CharToByteEncoder extends
    MessageToByteEncoder<Character> {
    @Override
    public void encode(ChannelHandlerContext ctx, Character msg,
        ByteBuf out) throws Exception {
        //将 Character 解码为 char，并将其写入到出站 ByteBuf 中
        out.writeChar(msg);
    }
}
```



### 编码器中的引用计数器

1. **一旦消息被编码或者解码，它就会被ReferenceCountUtil.release(message)调用自动释放。**
2. 如果你需要保留引用以便稍后使用，那么你可以调用**ReferenceCountUtil.retain**(message)方法。这将会增加该引用计数，从而防止该消息被释放。





## 内置Handler和编解码器

### Http

- **编解码器**

<img src=" assets%5Cimage-20201028003817235.png" alt="image-20201028003817235" style="zoom:80%;" />

```java
public class HttpPipelineInitializer extends ChannelInitializer<Channel> {
    private final boolean client;

    public HttpPipelineInitializer(boolean client) {
        this.client = client;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (client) {
            //如果是客户端，则添加 HttpResponseDecoder 以处理来自服务器的响应
            pipeline.addLast("decoder", new HttpResponseDecoder());
            //如果是客户端，则添加 HttpRequestEncoder 以向服务器发送请求
            pipeline.addLast("encoder", new HttpRequestEncoder());
        } else {
            //如果是服务器，则添加 HttpRequestDecoder 以接收来自客户端的请求
            pipeline.addLast("decoder", new HttpRequestDecoder());
            //如果是服务器，则添加 HttpResponseEncoder 以向客户端发送响应
            pipeline.addLast("encoder", new HttpResponseEncoder());
        }
    }
}
```



- **聚合消息**

> 由于HTTP 的请求和响应可能由许多部分组成，因此你需要聚合它们以形成完整的消息。为了消除这项繁琐的任务，Netty 提供了一个聚合器，它可以将多个消息部分合并为FullHttpRequest 或者FullHttpResponse 消息。通过这样的方式，你将总是看到完整的消息内容

```java
public class HttpAggregatorInitializer extends ChannelInitializer<Channel> {
    private final boolean isClient;

    public HttpAggregatorInitializer(boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (isClient) {
            //如果是客户端，则添加 HttpClientCodec
            pipeline.addLast("codec", new HttpClientCodec());
        } else {
            //如果是服务器，则添加 HttpServerCodec
            pipeline.addLast("codec", new HttpServerCodec());
        }
        //将最大的消息大小为 512 KB 的 HttpObjectAggregator 添加到 ChannelPipeline
        pipeline.addLast("aggregator",
                new HttpObjectAggregator(512 * 1024));
    }
}
```



- **压缩**

> Netty 为压缩和解压缩提供了ChannelHandler 实现，它们同时支持gzip 和deflate 编码。

> HTTP 请求的头部信息
> 客户端可以通过提供以下头部信息来指示服务器它所支持的压缩格式：
> GET /encrypted-area HTTP/1.1
> Host: www.example.com
> Accept -Encoding: gzip, deflate
> 然而，需要注意的是，服务器没有义务压缩它所发送的数据。

```java
public class HttpCompressionInitializer extends ChannelInitializer<Channel> {
    private final boolean isClient;

    public HttpCompressionInitializer(boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (isClient) {
            //如果是客户端，则添加 HttpClientCodec
            pipeline.addLast("codec", new HttpClientCodec());
            //如果是客户端，则添加 HttpContentDecompressor 以处理来自服务器的压缩内容
            pipeline.addLast("decompressor",
            new HttpContentDecompressor());
        } else {
            //如果是服务器，则添加 HttpServerCodec
            pipeline.addLast("codec", new HttpServerCodec());
            //如果是服务器，则添加HttpContentCompressor 来压缩数据（如果客户端支持它）
            pipeline.addLast("compressor",
            new HttpContentCompressor());
        }
    }
}
```



### Https

> 启用HTTPS 只需要将SslHandler 添加到ChannelPipeline 的
> ChannelHandler 组合中。

```java
public class HttpsCodecInitializer extends ChannelInitializer<Channel> {
    private final SslContext context;
    private final boolean isClient;

    public HttpsCodecInitializer(SslContext context, boolean isClient) {
        this.context = context;
        this.isClient = isClient;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        SSLEngine engine = context.newEngine(ch.alloc());
        //将 SslHandler 添加到ChannelPipeline 中以使用 HTTPS
        pipeline.addFirst("ssl", new SslHandler(engine));

        if (isClient) {
            //如果是客户端，则添加 HttpClientCodec
            pipeline.addLast("codec", new HttpClientCodec());
        } else {
            //如果是服务器，则添加 HttpServerCodec
            pipeline.addLast("codec", new HttpServerCodec());
        }
    }
}
```



### WebSocket

<img src=" assets%5Cimage-20201028005451030.png" alt="image-20201028005451030" style="zoom:80%;" />

- **websocket的数据类型**

![image-20201028005821554]( assets%5Cimage-20201028005821554.png)

```java
public class WebSocketServerInitializer extends ChannelInitializer<Channel> {
    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast(
            new HttpServerCodec(),
            //为握手提供聚合的 HttpRequest
            new HttpObjectAggregator(65536),
            //如果被请求的端点是"/websocket"，则处理该升级握手
            new WebSocketServerProtocolHandler("/websocket"),
            //TextFrameHandler 处理 TextWebSocketFrame
            new TextFrameHandler(),
            //BinaryFrameHandler 处理 BinaryWebSocketFrame
            new BinaryFrameHandler(),
            //ContinuationFrameHandler 处理 ContinuationWebSocketFrame
            new ContinuationFrameHandler());
    }

    public static final class TextFrameHandler extends
        SimpleChannelInboundHandler<TextWebSocketFrame> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx,
            TextWebSocketFrame msg) throws Exception {
            // Handle text frame
        }
    }

    public static final class BinaryFrameHandler extends
        SimpleChannelInboundHandler<BinaryWebSocketFrame> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx,
            BinaryWebSocketFrame msg) throws Exception {
            // Handle binary frame
        }
    }

    public static final class ContinuationFrameHandler extends
        SimpleChannelInboundHandler<ContinuationWebSocketFrame> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx,
            ContinuationWebSocketFrame msg) throws Exception {
            // Handle continuation frame
        }
    }
}
```



### 心跳超时的Handdler

<img src=" assets%5Cimage-20201028010533738.png" alt="image-20201028010533738" style="zoom:80%;" />



- 发送心跳

```java
public class IdleStateHandlerInitializer extends ChannelInitializer<Channel>
    {
    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(
                //(1) IdleStateHandler 将在被触发时发送一个IdleStateEvent 事件
                new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS));
        //将一个 HeartbeatHandler 添加到ChannelPipeline中
        pipeline.addLast(new HeartbeatHandler());
    }

    //实现 userEventTriggered() 方法以发送心跳消息
    public static final class HeartbeatHandler
        extends ChannelInboundHandlerAdapter {
        //发送到远程节点的心跳消息
        private static final ByteBuf HEARTBEAT_SEQUENCE =
                Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(
                "HEARTBEAT", CharsetUtil.ISO_8859_1));
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx,
            Object evt) throws Exception {
            //(2) 发送心跳消息，并在发送失败时关闭该连接
            if (evt instanceof IdleStateEvent) {
                ctx.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate())
                     .addListener(
                         ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                //不是 IdleStateEvent 事件，所以将它传递给下一个 ChannelInboundHandler
                super.userEventTriggered(ctx, evt);
            }
        }
    }
}
```

> 1. 如果连接超过60 秒没有接收或者发送任何的数据，那么IdleStateHandler
> 2. 将会使用一个IdleStateEvent 事件来调用fireUserEventTriggered()方法
> 3. HeartbeatHandler 实现了userEventTriggered()方法，如果这个方法检测到IdleStateEvent 事件，它将会发送心跳消息，并且添加一个将在发送操作失败时关闭该连接的ChannelFutureListener



### 分隔符解码器



<img src=" assets%5Cimage-20201028010941152.png" alt="image-20201028010941152" style="zoom:80%;" />



- 使用**LengthFieldBasedFrameDecoder**

<img src=" assets%5Cimage-20201028011026328.png" alt="image-20201028011026328" style="zoom:80%;" />

```java
public class LineBasedHandlerInitializer extends ChannelInitializer<Channel>
    {
    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //该 LineBasedFrameDecoder 将提取的帧转发给下一个 ChannelInboundHandler
        pipeline.addLast(new LineBasedFrameDecoder(64 * 1024));
        //添加 FrameHandler 以接收帧
        pipeline.addLast(new FrameHandler());
    }

    public static final class FrameHandler
        extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        //这里面的msg传入了单个帧的内容,已经分割好了
        public void channelRead0(ChannelHandlerContext ctx,
            ByteBuf msg) throws Exception {
            // Do something with the data extracted from the frame
        }
    }
}
```



- **编写自定义分隔符编解码器**

> 使用**DelimiterBasedFrameDecoder**，只需要将特定的分隔符序列指定到其构造函数即可

即将构造的规则如下：

1. 传入数据流是一系列的帧，每个帧都由换行符（\n）分隔；
2. 每个帧都由一系列的元素组成，每个元素都由单个空格字符分隔；
3. 一个帧的内容代表一个命令，定义为一个命令名称后跟着数目可变的参数。

我们用于这个协议的自定义解码器将定义以下类：

1. Cmd—将帧（命令）的内容存储在ByteBuf 中，一个ByteBuf 用于名称，另一个用于参数；
2. CmdDecoder—从被重写了的decode()方法中获取一行字符串，并从它的内容构建一个Cmd 的实例；

```java
public class CmdHandlerInitializer extends ChannelInitializer<Channel> {
    private static final byte SPACE = (byte)' ';
    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //添加 CmdDecoder 以提取 Cmd 对象，并将它转发给下一个 ChannelInboundHandler
        pipeline.addLast(new CmdDecoder(64 * 1024));
        //添加 CmdHandler 以接收和处理 Cmd 对象
        pipeline.addLast(new CmdHandler());
    }

    //Cmd POJO
    public static final class Cmd {
        private final ByteBuf name;
        private final ByteBuf args;

        public Cmd(ByteBuf name, ByteBuf args) {
            this.name = name;
            this.args = args;
        }

        public ByteBuf name() {
            return name;
        }

        public ByteBuf args() {
            return args;
        }
    }

    public static final class CmdDecoder extends LineBasedFrameDecoder {
        public CmdDecoder(int maxLength) {
            super(maxLength);
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer)
            throws Exception {
            //从 ByteBuf 中提取由行尾符序列分隔的帧
            ByteBuf frame = (ByteBuf) super.decode(ctx, buffer);
            if (frame == null) {
                //如果输入中没有帧，则返回 null

                return null;
            }
            //查找第一个空格字符的索引。前面是命令名称，接着是参数
            int index = frame.indexOf(frame.readerIndex(),
                    frame.writerIndex(), SPACE);
            //使用包含有命令名称和参数的切片创建新的 Cmd 对象
            return new Cmd(frame.slice(frame.readerIndex(), index),
                    frame.slice(index + 1, frame.writerIndex()));
        }
    }

    public static final class CmdHandler
        extends SimpleChannelInboundHandler<Cmd> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, Cmd msg)
            throws Exception {
            // Do something with the command
            //处理传经 ChannelPipeline 的 Cmd 对象
        }
    }
}
```



### 基于长度的解码器

![image-20201028011752069]( assets%5Cimage-20201028011752069.png)

FixedLengthFrameDecoder的解码过程

<img src=" assets%5Cimage-20201028011843139.png" alt="image-20201028011843139" style="zoom:80%;" />

**LengthFieldBasedFrameDecoder**解码过程：它将从头部字段确定帧长，然后从数据流中提取指定的字节数。

> 场景：当遇到被编码到消息头部的帧大小不是固定值的协议。

<img src=" assets%5Cimage-20201028012001274.png" alt="image-20201028012001274" style="zoom:80%;" />

```java
public class LengthBasedInitializer extends ChannelInitializer<Channel> {
    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(
                //使用 LengthFieldBasedFrameDecoder 解码将帧长度编码到帧起始的前 8 个字节中的消息
                new LengthFieldBasedFrameDecoder(64 * 1024, 0, 8));
        //添加 FrameHandler 以处理每个帧
        pipeline.addLast(new FrameHandler());
    }

    public static final class FrameHandler
        extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx,
             ByteBuf msg) throws Exception {
            // Do something with the frame
            //处理帧的数据
        }
    }
}
```



### 传输大型数据

1. 使用Netty提供的零拷贝特性，这种特性消除了将文件的内容从文件系统移动到网络栈的复制过程。

2. 所以应用程序所有需要做的就是使用一个FileRegion 接口的实现，其在Netty 的API 文档中的定义是：“通过支持零拷贝的文件传输的Channel 来发送的文件区域。

   ```java
   public class FileRegionWriteHandler extends ChannelInboundHandlerAdapter {
       private static final Channel CHANNEL_FROM_SOMEWHERE = new NioSocketChannel();
       private static final File FILE_FROM_SOMEWHERE = new File("");
   
       @Override
       public void channelActive(final ChannelHandlerContext ctx) throws Exception {
           File file = FILE_FROM_SOMEWHERE; //get reference from somewhere
           Channel channel = CHANNEL_FROM_SOMEWHERE; //get reference from somewhere
           //...
           //创建一个 FileInputStream
           FileInputStream in = new FileInputStream(file);
           //以该文件的完整长度创建一个新的 DefaultFileRegion
           FileRegion region = new DefaultFileRegion(
                   in.getChannel(), 0, file.length());
           //发送该 DefaultFileRegion，并注册一个 ChannelFutureListener
           channel.writeAndFlush(region).addListener(
               new ChannelFutureListener() {
               @Override
               public void operationComplete(ChannelFuture future)
                  throws Exception {
                  if (!future.isSuccess()) {
                      //处理失败
                      Throwable cause = future.cause();
                      // Do something
                  }
               }
           });
       }
   }
   ```

> 这个示例只适用于文件内容的直接传输，不包括应用程序对数据的任何处理



在需要将数据**从文件系统复制到用户内存中时**，可以使用**ChunkedWriteHandler**，它支持异步写大型数据流，而又不会导致大量的内存消耗。

> interface ChunkedInput<B>，其中类型参数B 是readChunk()方法返回的类型

![image-20201028013052082]( assets%5Cimage-20201028013052082.png)



```java
public class ChunkedWriteHandlerInitializer
    extends ChannelInitializer<Channel> {
    private final File file;
    private final SslContext sslCtx;
    public ChunkedWriteHandlerInitializer(File file, SslContext sslCtx) {
        this.file = file;
        this.sslCtx = sslCtx;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new SslHandler(sslCtx.newEngine(ch.alloc())));
        //1. 添加 ChunkedWriteHandler 以处理作为 ChunkedInput 传入的数据
        pipeline.addLast(new ChunkedWriteHandler());
        //2. 一旦连接建立，WriteStreamHandler 就开始写文件数据
        pipeline.addLast(new WriteStreamHandler());
    }

    public final class WriteStreamHandler
        extends ChannelInboundHandlerAdapter {

        @Override
        //当连接建立时，channelActive() 方法将使用 ChunkedInput 写文件数据
        public void channelActive(ChannelHandlerContext ctx)
            throws Exception {
            super.channelActive(ctx);
           // 3. 使用ChunkedStreamc传输内容
            ctx.writeAndFlush(
            new ChunkedStream(new FileInputStream(file)));
        }
    }
}
```

> 逐块输入 要使用你自己的ChunkedInput 实现，请在ChannelPipeline 中安装一个ChunkedWriteHandler。



### Protobuf 编解码器

<img src=" assets%5Cimage-20201028013718895.png" alt="image-20201028013718895" style="zoom:80%;" />



```java
public class ProtoBufInitializer extends ChannelInitializer<Channel> {
    private final MessageLite lite;

    public ProtoBufInitializer(MessageLite lite) {
        this.lite = lite;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //添加 ProtobufVarint32FrameDecoder 以分隔帧
        pipeline.addLast(new ProtobufVarint32FrameDecoder());
        //添加 ProtobufEncoder 以处理消息的编码
        pipeline.addLast(new ProtobufEncoder());
        //添加 ProtobufDecoder 以解码消息
        pipeline.addLast(new ProtobufDecoder(lite));
        //添加 ObjectHandler 以处理解码消息
        pipeline.addLast(new ObjectHandler());
    }

    public static final class ObjectHandler
        extends SimpleChannelInboundHandler<Object> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg)
            throws Exception {
            // Do something with the object
        }
    }
}
```



## 单元测试EmbeddedChannel

场景：将入站数据或者出站数据写入到EmbeddedChannel 中然后检查是否有任何东西到达了ChannelPipeline 的尾端。以这种方式，你便可以确定消息是否已经被编码或者被解码过了，以及是否触发了任何的ChannelHandler 动作。

| 名称                              | 职责                                                         |
| --------------------------------- | ------------------------------------------------------------ |
| **writeInbound**(Object... msgs)  | 将入站消息写到EmbeddedChannel 中<br />如果可以通过**readInbound()**方法从EmbeddedChannel 中读取数据，则返回true |
| **readInbound**()                 | 从EmbeddedChannel 中读取一个入站消息。<br/>任何返回的东西都穿越了整个ChannelPipeline。如果没有任何可供读取的，则返回null |
| **writeOutbound**(Object... msgs) | 将出站消息写到EmbeddedChannel中。<br/>如果现在可以通过**readOutbound()**方法从EmbeddedChannel 中读取到什么东西，则返回true |
| **readOutbound**()                | 从EmbeddedChannel 中读取一个出站消息。<br/>任何返回的东西都穿越了整个ChannelPipeline。如果没有任何可供读取的，则返回null |
| **finish**()                      | 将EmbeddedChannel 标记为完成，并且如果有可被读取的入站数据或者出站数据，则返回true。<br/>这个方法还将会调用EmbeddedChannel 上的close()方法 |

1. 入站数据由ChannelInboundHandler 处理，代表从远程节点读取的数据。
2. 出站数据由ChannelOutboundHandler 处理，代表将要写到远程节点的数据。
3. 根据你要测试的ChannelHandler，你将使用*Inbound()或者*Outbound()方法对，或者兼而有之。

<img src=" assets%5Cimage-20201028204911311.png" alt="image-20201028204911311" style="zoom:80%;" />



### 1. 测试入站消息

给定足够的数据，这个实现将产生固定大小的帧。如果没有足够的数据可供读取，它将等待下一个数据块的到来，并将再次检
查是否能够产生一个新的帧。

![image-20201028210120903]( assets%5Cimage-20201028210120903.png)

正如可以从图9-2 右侧的帧看到的那样，这个特定的解码器将产生固定为3 字节大小的帧。因此，它可能会需要多个事件来提供足够的字节数以产生一个帧。

```java
//扩展 ByteToMessageDecoder 以处理入站字节，并将它们解码为消息
public class FixedLengthFrameDecoder extends ByteToMessageDecoder {
    private final int frameLength;

    //指定要生成的帧的长度
    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException(
                "frameLength must be a positive integer: " + frameLength);
        }
        this.frameLength = frameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
        List<Object> out) throws Exception {
        //检查是否有足够的字节可以被读取，以生成下一个帧
        while (in.readableBytes() >= frameLength) {
            //从 ByteBuf 中读取一个新帧
            ByteBuf buf = in.readBytes(frameLength);
            //将该帧添加到已被解码的消息列表中
            out.add(buf);
        }
    }
}
```

最终，每个帧都会被传递给ChannelPipeline 中的下一个ChannelHandler。该解码器的实现，如代码清单9-1 所示。

```java
   public void testFramesDecoded() {
        //创建一个 ByteBuf，并存储 9 字节
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("你好啊".getBytes());
     
        ByteBuf input = buf.duplicate();
        //1. 创建一个EmbeddedChannel，并添加一个FixedLengthFrameDecoder，其将以 3 字节的帧长度被测试
        EmbeddedChannel channel = new EmbeddedChannel();

     		//2. 添加handler
        channel.pipeline().addLast(new FixedLengthFrameDecoder(3));
        channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                byte[] content = new byte[msg.readableBytes()];
                msg.readBytes(content);
                System.out.println("初次消费："+ new String(content));

                msg.resetReaderIndex();            // 重置readIndex
                ctx.fireChannelRead(msg.retain()); // 2.1 将消息发送给下一个handler
            }
        });
        channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                if(msg.hasArray()){
                    System.out.println("再次消费1："+ new String(msg.array()));
                }else{
                    if(msg.isDirect()){
                        byte[] content = new byte[msg.readableBytes()];
                        msg.readBytes(content);
                        System.out.println("再次消费2："+ new String(content));
                    }
                }
                msg.resetReaderIndex();
                ctx.fireChannelRead(msg.retain()); // 将消息发送给下一个handler
            }
        });
        //3. 将数据写入EmbeddedChannel
        assertTrue(channel.writeInbound(input.retain()));
        //3.1 标记 Channel 为已完成状态
        assertTrue(channel.finish());

        //3.2 读取所生成的消息，并且验证是否有 3 帧（切片），其中每帧（切片）都为 3 字节
        ByteBuf read = (ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3), read);
        read.release();

        read = (ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3), read);
        read.release();

        read = (ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3), read);
        read.release();

        assertNull(channel.readInbound());
        buf.release();
    }

/**
初次消费：你
再次消费2：你
初次消费：好
再次消费2：好
初次消费：啊
再次消费2：啊
*/
```



### 2.测试出站消息

该示例将会按照下列方式工作：

1. 持有AbsIntegerEncoder 的EmbeddedChannel 将会以4 字节的负整数的形式写出站数据；
2. 编码器将从传入的ByteBuf 中读取每个负整数，并将会调用Math.abs()方法来获取其绝对值；
3. 编码器将会把每个负整数的绝对值写到ChannelPipeline 中。

<img src=" assets%5Cimage-20201029134728722.png" alt="image-20201029134728722" style="zoom:80%;" />



```java
//扩展 MessageToMessageEncoder 以将一个消息编码为另外一种格式
public class AbsIntegerEncoder extends
    MessageToMessageEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext,
        ByteBuf in, List<Object> out) throws Exception {
        //1. 检查是否有足够的字节用来编码
        while (in.readableBytes() >= 4) {
            //2. 从输入的 ByteBuf中读取下一个整数，并且计算其绝对值
            int value = Math.abs(in.readInt());
            //3. 将该整数写入到编码消息的 List 中
            out.add(value);
        }
    }
}
```



```java
public class AbsIntegerEncoderTest {
    @Test
    public void testEncoded() {
        //(1) 创建一个 ByteBuf，并且写入 9 个负整数
        ByteBuf buf = Unpooled.buffer();
        for (int i = 1; i < 10; i++) {
            buf.writeInt(i * -1);
        }

        //(2) 创建一个EmbeddedChannel，并安装一个要测试的 AbsIntegerEncoder
        EmbeddedChannel channel = new EmbeddedChannel(
            new AbsIntegerEncoder());
        //(3) 写入 ByteBuf，并断言调用 readOutbound()方法将会产生数据
        assertTrue(channel.writeOutbound(buf));
        //(4) 将该 Channel 标记为已完成状态
        assertTrue(channel.finish());

        //(5) 读取所产生的消息，并断言它们包含了对应的绝对值
        for (int i = 1; i < 10; i++) {
            assertEquals(i, (int)channel.readOutbound());
        }
        assertNull(channel.readOutbound());
    }
}
```



### 3.测试异常处理

示例场景：

	1. 如果所读取的字节数超出了某个特定的限制，我们将会抛出一个TooLongFrameException。`这是一种经常用来防范资源被耗尽的方法。`
 	2. 最大的帧大小已经被设置为3 字节。如果一个帧的大小超出了该限制，那么程序将会丢弃它的字节，并抛出一个TooLongFrameException。位于ChannelPipeline 中的其他ChannelHandler 可以选择在exceptionCaught()方法中处理该异常或者忽略它。

<img src=" assets%5Cimage-20201029135437477.png" alt="image-20201029135437477" style="zoom:80%;" />



```java
//扩展 ByteToMessageDecoder以将入站字节解码为消息
public class FrameChunkDecoder extends ByteToMessageDecoder {
    private final int maxFrameSize;

    //1. 指定将要产生的帧的最大允许大小
    public FrameChunkDecoder(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
        List<Object> out)
        throws Exception {
        int readableBytes = in.readableBytes();
        if (readableBytes > maxFrameSize) {
            //2. 如果该帧太大，则丢弃它并抛出一个 TooLongFrameException……
            in.clear();
            throw new TooLongFrameException("too large length of input!");
        }
        //3. ……否则，从 ByteBuf 中读取一个新的帧
        ByteBuf buf = in.readBytes(readableBytes);
        //4. 将该帧添加到解码 读取一个新的帧消息的 List 中
        out.add(buf);
    }
}
```



```java
public class FrameChunkDecoderTest {
    @Test
    public void testFramesDecoded() {
        //1. 创建一个 ByteBuf，并向它写入 9 字节
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 9; i++) {
            buf.writeByte(i);
        }
        ByteBuf input = buf.duplicate();

        //2. 创建一个 EmbeddedChannel，并向其安装一个帧大小为 3 字节的 FixedLengthFrameDecoder
        EmbeddedChannel channel = new EmbeddedChannel(new FrameChunkDecoder(3));

        channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                ctx.fireChannelRead(ReferenceCountUtil.retain(msg)); // 2.1 将消息传递给下一个handler
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                System.out.println("[error] "+ cause.getLocalizedMessage());
                ctx.fireExceptionCaught(cause); // 2.2 触发下一个handler的exception事件
            }
        });
        //3. 向它写入 2 字节，并断言它们将会产生一个新帧
        assertTrue(channel.writeInbound(input.readBytes(2)));
        try {
            //3.1 写入一个 4 字节大小的帧，并捕获预期的TooLongFrameException
            channel.writeInbound(input.readBytes(4));
            //3.2 如果上面没有 们将会产生一个新帧抛出异常，那么就会到达这个断言，并且测试失败
            Assert.fail();
        } catch (TooLongFrameException e) {
            //3.3 如果有异常，则处理异常
            System.out.println("deal with exception："+ e.getLocalizedMessage());
        }
        //写入剩余的2字节，并断言将会产生一个有效帧
        assertTrue(channel.writeInbound(input.readBytes(3)));
        //将该 Channel 标记为已完成状态
        assertTrue(channel.finish());
        //读取产生的消息，并且验证值
        ByteBuf read = (ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(2), read);
        read.release();

        read = (ByteBuf) channel.readInbound();
        assertEquals(buf.skipBytes(4).readSlice(3), read);
        read.release();
        buf.release();
    }
}

/**
[error] too large length of input!
deal with exception：too large length of input!
*/

```



## Websocket协议（见代码）

