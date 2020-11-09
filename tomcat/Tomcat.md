## Tomcat调优

### **一、内存优化**

 **修改内存等 JVM相关配置**

[Linux](http://lib.csdn.net/base/linux)下修改TOMCAT_HOME/bin/catalina.sh，在其中加入，可以放在CLASSPATH=下面：

> JAVA_OPTS="-server -XX:**PermSize**=512M -**XX:MaxPermSize**=1024m -**Xms**2048m -**Xmx**2048m"

> ​    -server：启用 JDK的 server 版本；
>
> ​    -Xms：[Java](http://lib.csdn.net/base/17)虚拟机初始化时堆的最小内存，一般与 Xmx配置为相同值，这样的好处是GC不必再为扩展内存空间而消耗性能；
>
> ​    -Xmx：[Java](http://lib.csdn.net/base/java)虚拟机可使用堆的最大内存；
>
> ​    -XX:PermSize：Java虚拟机永久代大小；
>
> ​    -XX:MaxPermSize：Java虚拟机永久代大小最大值；



###  **二、配置优化**

####  **1.Connector 优化**

​	Connector是连接器，负责接收客户的请求，以及向客户端回送响应的消息。所以 Connector的优化是重要部分。默认情况下 Tomcat只支持200线程访问，超过这个数量的连接将被等待甚至超时放弃，所以我们需要提高这方面的处理能力。

修改这部分配置需要修改TOMCAT_HOME/conf/server.xml，打开server.xml找到Connector 标签项，默认配置如下：

> <Connector port="8080" protocol="HTTP/1.1"  
>            connectionTimeout="20000"  
>            redirectPort="8443" />

其中Connector 支持参数属性可以参考Tomcat官方网站（https://tomcat.apache.org/tomcat-8.0-doc/config/http.html），非常多，所以本文就只介绍些常用的。

优化示例：

``` xml
<Connector port="8080"   
          protocol="HTTP/1.1"   
          maxThreads="1000"   
          minSpareThreads="100"   
          acceptCount="1000"  
          maxConnections="1000"  
          connectionTimeout="20000"   
          maxHttpHeaderSize="8192"  
          tcpNoDelay="true"  
          compression="on"  
          compressionMinSize="2048"  
          disableUploadTimeout="true"  
          redirectPort="8443"  
          enableLookups="false"  
          URIEncoding="UTF-8" /> 
```

**port：**代表Tomcat监听端口，也就是网站的访问端口，默认为8080，可以根据需要改成其他。

**protocol：**协议类型，可选类型有四种，分别为BIO（阻塞型IO），NIO，NIO2和APR。

> 1）BIO：BIO(Blocking I/O)，顾名思义，即阻塞式I/O操作，表示Tomcat使用的是传统的[Java ](http://lib.csdn.net/base/java)I/O操作(即java.io包及其子包)。Tomcat在默认情况下，是以bio模式运行的。遗憾的是，就一般而言，bio模式是三种运行模式中性能最低的一种。BIO配置采用默认即可。
>
> 2）NIO：NIO(New I/O)，是[Java SE](http://lib.csdn.net/base/12) 1.4及后续版本提供的一种新的I/O操作方式(即java.nio包及其子包)。Java nio是一个基于缓冲区、并能提供非阻塞I/O操作的[java ](http://lib.csdn.net/base/java)API，因此nio也被看成是non-blocking I/O的缩写。它拥有比传统I/O操作(bio)更好的并发运行性能。要让Tomcat以nio模式来运行也比较简单，我们只需要protocol类型修改为：
>
> ```properties
> //NIO  
> protocol="org.apache.coyote.http11.Http11NioProtocol"  
> //NIO2  
> protocol="org.apache.coyote.http11.Http11Nio2Protocol" 
> ```
>
> 3）APR：APR(Apache Portable Runtime/Apache可移植运行时)，是Apache HTTP服务器的支持库。你可以简单地理解为:Tomcat将以JNI的形式调用 Apache HTTP服务器的核心动态链接库来处理文件读取或网络传输操作，从而大大地提高 Tomcat对静态文件的处理性能。 
>
>  与配置 NIO运行模式一样，也需要将对应的 Connector节点的 protocol属性值改为：
>
> ```properties
> protocol="org.apache.coyote.http11.Http11AprProtocol"  
> ```

**maxThreads：**由该连接器创建的处理请求线程的最大数目，也就是可以处理的同时请求的最大数目。如果未配置默认值为200。`如果一个执行器与此连接器关联，则忽略此属性，因为该属性将被忽略，所以该连接器将使用执行器而不是一个内部线程池来执行任务。`

> 这个最大值是受[操作系统](http://lib.csdn.net/base/operatingsystem)及相关硬件所制约的，并且最大值并不一定是最优值，所以我们追寻的应该是最优值而不是最大值。
>
> **QPS**（Query Per Second）：每秒查询率QPS是对一个特定的查询服务器在规定时间内所处理流量多少的衡量标准。我们常常使用 QPS值来衡量一个服务器的性能。
>
> > QPS = 并发数 / 平均响应时间
>
> 一个系统吞吐量通常由QPS、并发数两个因素决定，每套系统的这两个值都有一个相对极限值，在应用场景访问压力下，只要某一项达到系统最高值，系统的吞吐量就上不去了，如果压力继续增大，系统的吞吐量反而会下降，原因是系统超负荷工作，上下文切换、内存等等其它消耗导致系统性能下降。所谓**吞吐量这里可以理解为每秒能处理请求的次数**。
>
> > 我们可以通过以下几种方式来**获取 maxThreads的最佳值**：
> >
> > ​    （1）通过线上系统不断使用和用户的不断增长来进行性能[测试](http://lib.csdn.net/base/softwaretest)，观察QPS，响应时间，这种方式会在爆发式增长时系统崩溃，如双12等。
> >
> > ​    （2）根据公式计算，服务器端最佳线程数量=((线程等待时间+线程cpu时间)/线程cpu时间) * cpu数量，这种方式有时会被误导，因为某些系统处理环节可能会耗时比较长，从而影响公式的结果。
> >
> > ​	 (5）定期修改为不同的 maxThreads值，看服务器响应结果及用户反应。

**minSpareThreads：**线程的最小运行数目，这些始终保持运行。如果未指定，默认值为10。

**acceptCount：**当所有可能的请求处理线程都在使用时，传入连接请求的最大队列长度。如果未指定，默认值为100。一般是设置的跟 maxThreads一样或一半

> 此值设置的过大会导致排队的请求超时而未被处理。所以这个值应该是主要根据应用的访问峰值与平均值来权衡配置。

**maxConnections：**在任何给定的时间内，服务器将接受和处理的最大连接数。当这个数字已经达到时，服务器将接受但不处理，等待进一步连接。NIO与NIO2的默认值为10000，APR默认值为8192。

**connectionTimeout：**当请求已经被接受，但未被处理，也就是等待中的超时时间。单位为毫秒，默认值为60000。通常情况下设置为30000。

**maxHttpHeaderSize：**请求和响应的HTTP头的最大大小，以字节为单位指定。如果没有指定，这个属性被设置为8192（8 KB）。

**tcpNoDelay：**如果为true，服务器socket会设置TCP_NO_DELAY选项，在大多数情况下可以提高性能。缺省情况下设为true。

**compression：**是否启用gzip压缩，默认为关闭状态。这个参数的可接受值为“off”（不使用压缩），“on”（压缩文本数据），“force”（在所有的情况下强制压缩）。

**compressionMinSize：**如果compression="on"，则启用此项。被压缩前数据的最小值，也就是超过这个值后才被压缩。如果没有指定，这个属性默认为“2048”（2K），单位为byte。

**disableUploadTimeout：**这个标志允许servlet [Container](http://lib.csdn.net/base/4)在一个servlet执行的时候，使用一个不同的，更长的连接超时。最终的结果是给servlet更长的时间以便完成其执行，或者在数据上载的时候更长的超时时间。如果没有指定，设为false。



#### **2.线程池优化**

Executor代表了一个线程池，可以在Tomcat组件之间共享。使用线程池的好处在于减少了创建销毁线程的相关消耗，而且可以提高线程的使用效率。

要想使用线程池，首先需要在 Service标签中配置 Executor，如下：

```xml
<Service name="Catalina">  
  
  <Executor name="tomcatThreadPool"   
         namePrefix="catalina-exec-"   
         maxThreads="1000"   
         minSpareThreads="100"  
         maxIdleTime="60000"  
         maxQueueSize="Integer.MAX_VALUE"  
         prestartminSpareThreads="false"  
         threadPriority="5"  
         className="org.apache.catalina.core.StandardThreadExecutor"/>
```

> ​    name：线程池名称，用于 Connector中指定。
>
> ​    namePrefix：所创建的每个线程的名称前缀，一个单独的线程名称为 namePrefix+threadNumber。
>
> ​    **maxThreads**：池中最大线程数。
>
> ​    **minSpareThreads**：活跃线程数，也就是核心池线程数，这些线程不会被销毁，会一直存在。
>
> ​    **maxIdleTime**：线程空闲时间，超过该时间后，空闲线程会被销毁，默认值为6000（1分钟），单位毫秒。
>
> ​    **maxQueueSize**：在被执行前最大线程排队数目，默认为Int的最大值，也就是广义的无限。除非特殊情况，这个值不需要更改，否则会有请求不会被处理的情况发生。
>
> ​    prestartminSpareThreads：启动线程池时是否启动 minSpareThreads部分线程。默认值为false，即不启动。
>
> ​    threadPriority：线程池中线程优先级，默认值为5，值从1到10。
>
> ​    className：线程池实现类，未指定情况下，默认实现类为org.apache.catalina.core.StandardThreadExecutor。如果想使用自定义线程池首先需要实现 org.apache.catalina.Executor接口。

线程池配置完成后需要在 Connector中指定：

```xml
<Connector executor="tomcatThreadPool"  
```



#### **3.Listener优化(无需配置)**

 另一个影响Tomcat 性能的因素是内存泄露。Server标签中可以配置多个Listener，其中 JreMemoryLeakPreventionListener是用来预防JRE内存泄漏。此Listener只需在Server标签中配置即可，默认情况下无需配置，已经添加在 Server中。

```xml
<Listener className="org.apache.catalina.core.JreMemoryLeakPreventionListener" />
```

