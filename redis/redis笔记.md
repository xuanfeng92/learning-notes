# Redis哨兵主要知识点

## 1 哨兵的作用

哨兵是redis集群架构中非常重要的一个组件，主要功能如下： 

1. 集群监控：负责监控redis master和slave进程是否正常工作 
2. 消息通知：如果某个redis实例有故障，那么哨兵负责发送消息作为报警通知给管理员 
3. 故障转移：如果master node挂掉了，会自动转移到slave node上 
4. 配置中心：如果故障转移发生了，通知client客户端新的master地址

## 2 哨兵需要注意的几点

1. 故障转移时，判断一个master node是宕机了，需要大部分的哨兵都同意才行，涉及到了分布式选举的问题
2. 哨兵至少需要3个实例，来保证自己的健壮性
3. 哨兵 + redis主从的部署架构，是不会保证数据零丢失的，只能保证redis集群的高可用性

## 3 sdown和odown

1. sdown和odown两种失败状态
2. sdown是主观宕机，就一个哨兵如果自己觉得一个master宕机了，那么就是主观宕机
3. odown是客观宕机，如果quorum数量的哨兵都觉得一个master宕机了，那么就是客观宕机
4. sdown达成的条件：如果一个哨兵ping一个master，超过了is-master-down-after-milliseconds指定的毫秒数之后，就主观认为master宕机
5. odown达成的条件：如果一个哨兵在指定时间内，收到了quorum指定数量的其他哨兵也认为那个master是sdown了，那么就认为是odown了，客观认为master宕机

## 4 quorum和majority

1. quorum：确认odown的最少的哨兵数量
2. majority：授权进行主从切换的最少的哨兵数量
3. 每次一个哨兵要做主备切换，首先需要quorum数量的哨兵认为odown，然后选举出一个哨兵来做切换，这个哨兵还得得到majority哨兵的授权，才能正式执行切换
4. 如果quorum < majority，比如5个哨兵，majority就是3，quorum设置为2，那么就3个哨兵授权就可以执行切换，但是如果quorum >= majority，那么必须quorum数量的哨兵都授权，比如5个哨兵，quorum是5，那么必须5个哨兵都同意授权，才能执行切换

## 5 为什么哨兵至少3个节点

哨兵集群必须部署2个以上节点。如果哨兵集群仅仅部署了个2个哨兵实例，那么它的majority就是2（2的majority=2，3的majority=2，5的majority=3，4的majority=2），如果其中一个哨兵宕机了，就无法满足majority>=2这个条件，那么在master发生故障的时候也就无法进行主从切换。

## 6 脑裂以及redis数据丢失

主备切换的过程，可能会导致数据丢失 
（1）异步复制导致的数据丢失 
因为master -> slave的复制是异步的，所以可能有部分数据还没复制到slave，master就宕机了，此时这些部分数据就丢失了 
（2）脑裂导致的数据丢失 
脑裂，也就是说，某个master所在机器突然脱离了正常的网络，跟其他slave机器不能连接，但是实际上master还运行着 
此时哨兵可能就会认为master宕机了，然后开启选举，将其他slave切换成了master，这个时候，集群里就会有两个master，也就是所谓的脑裂。 
此时虽然某个slave被切换成了master，但是可能client还没来得及切换到新的master，还继续写向旧master的数据可能也丢失了，因此旧master再次恢复的时候，会被作为一个slave挂到新的master上去，自己的数据会清空，重新从新的master复制数据

## 7 如何尽可能减少数据丢失

下面两个配置可以减少异步复制和脑裂导致的数据丢失：

```
min-slaves-to-write 1 ## 要求至少有1个slave
min-slaves-max-lag 10 ## 数据复制和同步的延迟不能超过10秒
```

解释：要求至少有1个slave，数据复制和同步的延迟不能超过10秒，如果说一旦所有的slave，数据复制和同步的延迟都超过了10秒钟，那么这个时候，master就不会再接收任何请求了 
（1）减少异步复制的数据丢失 
有了**min-slaves-max-lag**这个配置，就可以确保说，一旦slave复制数据和ack延时太长，就认为可能master宕机后损失的数据太多了，那么就拒绝写请求，这样可以把master宕机时由于部分数据未同步到slave导致的数据丢失降低的可控范围内 
（2）减少脑裂的数据丢失 
如果一个master出现了脑裂，跟其他slave丢了连接，那么上面两个配置可以确保说，如果不能继续给指定数量的slave发送数据，而且slave超过10秒没有给自己ack消息，那么就直接拒绝客户端的写请求，这样脑裂后的旧master就不会接受client的新数据，也就避免了数据丢失 
上面的配置就确保了，如果跟任何一个slave丢了连接，在10秒后发现没有slave给自己ack，那么就拒绝新的写请求。
因此在脑裂场景下，最多就丢失10秒的数据

## 8 哨兵集群的自动发现机制

哨兵互相之间的发现，**是通过redis的pub/sub系统实现的**，每个哨兵都会往sentinel:hello这个channel里发送一个消息，这时候所有其他哨兵都可以消费到这个消息，并感知到其他的哨兵的存在 
每隔两秒钟，每个哨兵都会往自己监控的某个master+slaves对应的sentinel:hello channel里发送一个消息，内容是自己的host、ip和runid还有对这个master的监控配置 
每个哨兵也会去监听自己监控的每个master+slaves对应的sentinel:hello channel，然后去感知到同样在监听这个master+slaves的其他哨兵的存在 
每个哨兵还会跟其他哨兵交换对master的监控配置，互相进行监控配置的同步

## 9 slave配置的自动纠正

哨兵会负责自动纠正slave的一些配置，比如slave如果要成为潜在的master候选人，哨兵会确保slave在复制现有master的数据; 如果slave连接到了一个错误的master上，比如故障转移之后，那么哨兵会确保它们连接到正确的master上

## 10 master选举算法

如果一个master被认为odown了，而且majority哨兵都允许了主备切换，那么某个哨兵就会执行主备切换操作，此时首先要选举一个slave来。 
选举的时候会考虑slave的一些信息： 

1. 跟master断开连接的时长 
2. slave优先级 
3. 复制offset 
4. run id 

如果一个slave跟master断开连接已经超过了down-after-milliseconds的10倍，外加master宕机的时长，那么slave就被认为不适合选举为master，计算公式如下：

```
(down-after-milliseconds * ``10``) + milliseconds_since_master_is_in_SDOWN_state
```

　　

接下来会对slave进行排序 
（1）按照slave优先级进行排序，slave priority越低，优先级就越高 
（2）如果slave priority相同，那么看replica offset，哪个slave复制了越多的数据，offset越靠后，优先级就越高 
（3）如果上面两个条件都相同，那么选择一个run id比较小的那个slave

## 11 configuration epoch

哨兵会对一套redis master+slave进行监控，有相应的监控的配置 
执行切换的那个哨兵，会从要切换到的新master（salve->master）那里得到一个configuration epoch，这就是一个version号，每次切换的version号都必须是唯一的 
如果第一个选举出的哨兵切换失败了，那么其他哨兵，会等待failover-timeout时间，然后接替继续执行切换，此时会重新获取一个新的configuration epoch，作为新的version号

## 12 configuraiton传播

哨兵完成切换之后，会在自己本地更新生成最新的master配置，然后同步给其他的哨兵，就是通过之前说的pub/sub消息机制 
这里之前的version号就很重要了，因为各种消息都是通过一个channel去发布和监听的，所以一个哨兵完成一次新的切换之后，新的master配置是跟着新的version号的 
其他的哨兵都是根据版本号的大小来更新自己的master配置的



# Redis 内存模型

参考如下 https://www.cnblogs.com/kaleidoscope/p/9722927.html

## **一、Redis内存统计**

工欲善其事必先利其器，在说明Redis内存之前首先说明如何统计Redis使用内存的情况。

在客户端通过redis-cli连接服务器后（后面如无特殊说明，客户端一律使用redis-cli），通过info命令可以查看内存使用情况：

> info memory

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pGaxGFE65nR1tm02zrDFiakukF5HrZia0a5bNBMo4x6lNcFicLibo20bQGA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



其中，info命令可以显示redis服务器的许多信息，包括服务器基本信息、CPU、内存、持久化、客户端连接信息等等；memory是参数，表示只显示内存相关的信息。

返回结果中比较重要的几个说明如下：

（1）**used_memory**：**Redis分配器分配的内存总量**（单位是字节），**包括使用的虚拟内存**（即swap）；Redis分配器后面会介绍。used_memory_human只是显示更友好。

（2）**used_memory_rss**：Redis进程占据操作系统的内存（单位是字节），与top及ps命令看到的值是一致的；除了分配器分配的内存之外，used_memory_rss还包括进程运行**本身需要的内存**、**内存碎片**等，但是**不包括虚拟内存**。

因此，used_memory和used_memory_rss，前者是从Redis角度得到的量，后者是从操作系统角度得到的量。二者之所以有所不同，一方面是因为内存碎片和Redis进程运行需要占用内存，使得前者可能比后者小，另一方面虚拟内存的存在，使得前者可能比后者大。

由于在实际应用中，Redis的数据量会比较大，此时进程运行占用的内存与Redis数据量和内存碎片相比，都会小得多；因此used_memory_rss和used_memory的比例，便成了衡量Redis内存碎片率的参数；这个参数就是mem_fragmentation_ratio。

（3）**mem_fragmentation_ratio**：内存碎片比率，该值是**used_memory_rss / used_memory的比值**。

> 1. mem_fragmentation_ratio**一般大于1，且该值越大，内存碎片比例越大**。
> 2. mem_fragmentation_ratio**<1，说明Redis使用了虚拟内存**，由于虚拟内存的媒介是磁盘，比内存速度要慢很多，当这种情况出现时，应该及时排查，`如果内存不足应该及时处理，如增加Redis节点、增加Redis服务器的内存、优化应用等`。
> 3. 一般来说，mem_fragmentation_ratio在**1.03左右**是比较健康的状态（对于jemalloc来说）；上面截图中的mem_fragmentation_ratio值很大，是因为还没有向Redis中存入数据，Redis进程本身运行的内存使得used_memory_rss 比used_memory大得多。

（4）**mem_allocator**：Redis使用的内存分配器，在编译时指定；可以是 libc 、jemalloc或者tcmalloc，默认是jemalloc；截图中使用的便是默认的jemalloc。



## **二、Redis内存划分**



Redis作为内存数据库，在内存中存储的内容主要是数据（键值对）；通过前面的叙述可以知道，除了数据以外，Redis的其他部分也会占用内存。

Redis的内存占用主要可以划分为以下几个部分：

### **1、数据**

作为数据库，数据是最主要的部分；这部分占用的内存会统计在**used_memory**中。

Redis使用键值对存储数据，其中的值（对象）包括5种类型，即字符串、哈希、列表、集合、有序集合。这5种类型是Redis对外提供的，实际上，在Redis内部，每种类型可能有2种或更多的内部编码实现；此外，Redis在存储对象时，并不是直接将数据扔进内存，而是会对对象进行各种包装：如redisObject、SDS等；这篇文章后面将重点介绍Redis中数据存储的细节。



### **2、进程本身运行需要的内存**

Redis主进程本身运行肯定需要占用内存，如代码、常量池等等；这部分内存大约几兆，在大多数生产环境中与Redis数据占用的内存相比可以忽略。这部分内存不是由jemalloc分配，因此不会统计在used_memory中。

补充说明：除了主进程外，Redis创建的子进程运行也会占用内存，如Redis执行AOF、RDB重写时创建的子进程。当然，这部分内存不属于Redis进程，也不会统计在used_memory和used_memory_rss中。



### **3、缓冲内存**

缓冲内存包括客户端缓冲区、复制积压缓冲区、AOF缓冲区等；其中，**客户端缓冲**存储客户端连接的输入输出缓冲；**复制积压缓**冲用于**部分复制**功能；**AOF缓冲区**用于在进行AOF重写时，保存最近的写入命令。在了解相应功能之前，不需要知道这些缓冲的细节；这部分内存由jemalloc分配，因此会**统计在used_memory**中。



### **4、内存碎片**

内存碎片是Redis在**分配、回收物理内存过程中产生的**。

> 1. 例如 ： **如果对数据的更改频繁，而且数据之间的大小相差很大，可能导致redis释放的空间在物理内存中并没有释放，但redis又无法有效利用，这就形成了内存碎片。**内存碎片不会统计在used_memory中。
> 2. 内存碎片的产生**与对数据进行的操作、数据的特点等都有关**；
> 3. 此外还与使用的**内存分配器也有关系**：如果内存分配器设计合理，可以尽可能的减少内存碎片的产生。后面将要说到的**jemalloc**便在控制内存碎片方面做的很好。

如果Redis服务器中的内存碎片已经很大，可以**通过安全重启的方式减小内存碎片**：`因为重启之后，Redis重新从备份文件中读取数据，在内存中进行重排，为每个数据重新选择合适的内存单元，减小内存碎片`。



## **三、Redis数据存储的细节**



### **1、概述**



关于Redis数据存储的细节，涉及到内存分配器（如jemalloc）、简单动态字符串（SDS）、5种对象类型及内部编码、redisObject。在讲述具体内容之前，先说明一下这几个概念之间的关系。



下图是执行set hello world时，所涉及到的数据模型。



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pFYUviaXiaTkh6RdsjngIeLyRe4VSdmUk9jLVZBNquYwgeaO4iakv8a9yA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

图片来源：https://searchdatabase.techtarget.com.cn/7-20218/



（1）**dictEntry**：Redis是Key-Value数据库，因此对每个键值对都会有一个dictEntry，里面存储了指向Key和Value的指针；next指向下一个dictEntry，与本Key-Value无关。



（2）**Key**：图中右上角可见，Key（”hello”）并不是直接以字符串存储，而是**存储在SDS结构**中。



（3）**redisObject**：**Value**(“world”)既不是直接以字符串存储，也不是像Key一样直接存储在SDS中，而是存储在redisObject中。实际上，**不论Value是5种类型的哪一种，都是通过redisObject来存储的**；`而redisObject中的type字段指明了Value对象的类型，ptr字段则指向对象所在的地址`。不过可以看出，**字符串对象虽然经过了redisObject的包装，但仍然需要通过SDS存储**。

> 实际上，redisObject除了type和ptr字段以外，还有其他字段图中没有给出，如用于指定对象内部编码的字段；后面会详细介绍。



（4）**jemalloc**：无论是DictEntry对象，还是redisObject、SDS对象，都需要内存分配器（如jemalloc）分配内存进行存储。以DictEntry对象为例，有3个指针组成，在64位机器下占24个字节，jemalloc会为它分配32字节大小的内存单元。



下面来分别介绍jemalloc、redisObject、SDS、对象类型及内部编码。



### **2、jemalloc**



Redis在编译时便会指定内存分配器；内存分配器可以是 libc 、jemalloc或者tcmalloc，默认是jemalloc。



jemalloc作为Redis的默认内存分配器，**在减小内存碎片方面做的相对比较好**。jemalloc在64位系统中，**将内存空间划分为小、大、巨大三个范围；每个范围内又划分了许多小的内存块单位；**当Redis存储数据时，会选择大小最合适的内存块进行存储。



jemalloc划分的内存单元如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pVHXXpgU6vspU0xaQoKxuNib7IPaKjwAQ8HtkJZeqFZh7zVDNVbfvq5A/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

图片来源：http://blog.csdn.net/zhengpeitao/article/details/76573053



例如，如果需要存储大小为130字节的对象，jemalloc会将其放入160字节的内存单元中。



### **3、redisObject**



前面说到，Redis对象有5种类型；**无论是哪种类型，Redis都不会直接存储，而是通过redisObject对象进行存储**。



redisObject对象非常重要，Redis对象的类型、内部编码、内存回收、共享对象等功能，都需要redisObject支持，下面将通过redisObject的结构来说明它是如何起作用的。



redisObject的定义如下（不同版本的Redis可能稍稍有所不同）：



> **typedef** **struct** redisObject {
>
> 　　**unsigned** type:4;
>
> 　　**unsigned** encoding:4;
>
> 　　**unsigned** lru:REDIS_LRU_BITS; */\* lru time (relative to server.lruclock) \*/*
>
> 　　**int** refcount;
>
> 　　**void** *ptr;
>
> } robj;



redisObject的每个字段的含义和作用如下：



#### （1）type



type字段表示对象的类型，占4个比特；目前包括REDIS_STRING(字符串)、REDIS_LIST (列表)、REDIS_HASH(哈希)、REDIS_SET(集合)、REDIS_ZSET(有序集合)。



当我们执行type命令时，便是通过读取RedisObject的type字段获得对象的类型；如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16p0lFJIibfPljHE9pTtnhKTrxSUkjp9P7Cp2rLe9dH4ItgzgbFsIpESdw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



#### （2）encoding

encoding表示对象的内部编码，占4个比特。



对于Redis支持的每种类型，都有至少两种内部编码，例如对于字符串，有int、embstr、raw三种编码。通过encoding属性，**Redis可以根据不同的使用场景来为对象设置不同的编码**，大大提高了Redis的灵活性和效率。

> 以**列表**对象为例，**有压缩列表**和**双端链表**两种编码方式；
>
> 1. 如果列表中的元素较少，Redis倾向于使用压缩列表进行存储，因为压缩列表占用内存更少，而且比双端链表可以更快载入；
> 2. 当列表对象元素较多时，压缩列表就会转化为更适合存储大量元素的双端链表。



通过object encoding命令，可以查看对象采用的编码方式，如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16paf4tOYkSTGOXJHqQkvBDadYoxXMiaOMhcxAXdBSjS83EFQhGXJGfcKQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



5种对象类型对应的编码方式以及使用条件，将在后面介绍。



#### （3）lru



lru记录的是**对象最后一次被命令程序访问的时间**，占据的比特数不同的版本有所不同（如4.0版本占24比特，2.6版本占22比特）。



**通过对比lru时间与当前时间，可以计算某个对象的空转时间**；**object idletime**命令可以显示该空转时间（单位是秒）。object idletime命令的一个特殊之处在于它不改变对象的lru值。



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16paXrsqsT7bM4oNNfAVUzsU49t38OfGfoYibEASzmTzTn0G3PngVrz7Sg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



> lru值除了通过object idletime命令打印之外，还与Redis的内存回收有关系：
>
> 1. 如果Redis打开了maxmemory选项，且内存回收算法选择的是volatile-lru或allkeys—lru，那么当Redis内存占用超过maxmemory指定的值时，**Redis会优先选择空转时间最长的对象进行释放**。



#### （4）refcount 与共享对象



- refcount与共享对象：

refcount记录的是**该对象被引用的次数**，类型为整型。refcount的**作用，主要在于对象的引用计数和内存回收**。

> 当创建新对象时，refcount初始化为1；当有新程序使用该对象时，refcount加1；当对象不再被一个新程序使用时，refcount减1；当refcount变为0时，对象占用的内存会被释放。

Redis中被多次使用的对象(refcount>1)，称为**共享对象**。Redis为了节省内存，当有一些对象重复出现时，新的程序不会创建新的对象，而是仍然使用原来的对象。这个被重复使用的对象，就是共享对象。目前共享对象仅支持整数值的字符串对象。



- 共享对象的具体实现：

Redis的共享对象**目前只支持整数值的字符串对象**。之所以如此，实际上是对内存和CPU（时间）的平衡：共享对象虽然会降低内存消耗，但是判断两个对象是否相等却需要消耗额外的时间。对于整数值，判断操作复杂度为O(1)；对于普通字符串，判断复杂度为O(n)；而对于哈希、列表、集合和有序集合，判断的复杂度为O(n^2)。



虽然共享对象只能是整数值的字符串对象，但是5种类型都可能使用共享对象（如哈希、列表等的元素可以使用）。

> 就目前的实现来说，Redis服务器在初始化时，会创建10000个字符串对象，值分别是0~9999的整数值；当Redis需要使用值为0~9999的字符串对象时，可以直接使用这些共享对象。10000这个数字可以通过调整参数REDIS_SHARED_INTEGERS（4.0中是OBJ_SHARED_INTEGERS）的值进行改变。

共享对象的引用次数可以通过**object refcount**命令查看，如下图所示。命令执行的结果页佐证了只有0~9999之间的整数会作为共享对象。



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16puXkVuZe3HbzKD0xa4JxSCU0R89500iaMCO1iccDdZEYlhFqsVLBS0srQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



#### （5）ptr

ptr指针指向具体的数据，如前面的例子中，set hello world，ptr指向包含字符串world的**SDS**。



#### （6）总结

综上所述，redisObject的结构与对象类型、编码、内存回收、共享对象都有关系；

一个redisObject对象的大小为16字节：4bit+4bit+24bit+4Byte+8Byte=16Byte。



### **4、SDS**

Redis没有直接使用C字符串(即以空字符’\0’结尾的字符数组)作为默认的字符串表示，而是使用了SDS。SDS是简单动态字符串(**Simple Dynamic String**)的缩写。

#### （1）SDS结构

sds的结构如下：

> **struct** sdshdr {
>
>   **int** len;
>
>   **int** free;
>
>   **char** buf[];
>
> };

其中，**buf表示字节数组，用来存储字符串**；**len表示buf已使用的长度**，**free表示buf未使用的长度**。下面是两个例子。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pAXtS6f90icDw2hmDAJKO2yYKalm4p53yLzHFabMiaiakEPhm1AvtI68UA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pSyicaCvW88aSnAuBWaoUibguTEqpKoJygnH1K2qvXJehFAicyw3pMZjxA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

图片来源：《Redis设计与实现》



通过SDS的结构可以看出，buf数组的长度=free+len+1（其中1表示字符串结尾的空字符）；所以，一个SDS结构占据的空间为：free所占长度+len所占长度+ buf数组的长度=4+4+free+len+1=free+len+9。



#### （2）SDS与C字符串的比较



SDS在C字符串的基础上加入了free和len字段，带来了很多好处：

- **获取字符串长度**：SDS是O(1)，C字符串是O(n)
- **缓冲区溢出**：使用C字符串的API时，如果字符串长度增加（如strcat操作）而忘记重新分配内存，很容易造成缓冲区的溢出；而SDS由于记录了长度，相应的API在可能造成缓冲区溢出时会自动重新分配内存，杜绝了缓冲区溢出。
- **修改字符串时内存的重分配**：对于C字符串，如果要修改字符串，必须要重新分配内存（先释放再申请），因为如果没有重新分配，字符串长度增大时会造成内存缓冲区溢出，字符串长度减小时会造成内存泄露。而对于SDS，由于可以记录len和free，因此解除了字符串长度和空间数组长度之间的关联，可以在此基础上进行优化：空间预分配策略（即分配内存时比实际需要的多）使得字符串长度增大时重新分配内存的概率大大减小；惰性空间释放策略使得字符串长度减小时重新分配内存的概率大大减小。
- **存取二进制数据**：SDS可以，C字符串不可以。因为C字符串以空字符作为字符串结束的标识，而对于一些二进制文件（如图片等），内容可能包括空字符串，因此C字符串无法正确存取；而SDS以字符串长度len来作为字符串结束标识，因此没有这个问题。



此外，由于SDS中的buf仍然使用了C字符串（即以’\0’结尾），因此SDS可以使用C字符串库中的部分函数；但是需要注意的是，只有当SDS用来存储文本数据时才可以这样使用，在存储二进制数据时则不行（’\0’不一定是结尾）。



#### （3）SDS与C字符串的应用



Redis在存储对象时，一律使用SDS代替C字符串。例如set hello world命令，hello和world都是以SDS的形式存储的。而sadd myset member1 member2 member3命令，不论是键（”myset”），还是集合中的元素（”member1”、 ”member2”和”member3”），都是以SDS的形式存储。除了存储对象，SDS还用于存储各种缓冲区。

> 只有在字符串不会改变的情况下，如打印日志时，才会使用C字符串。



## **四、Redis的对象类型与内部编码**

前面已经说过，Redis支持5种对象类型，而**每种结构都有至少两种编码**；这样做的**好处在于**：**一方面接口与实现分离，当需要增加或改变内部编码时，用户使用不受影响，另一方面可以根据不同的应用场景切换内部编码，提高效率**。



Redis各种对象类型支持的内部编码如下图所示(图中版本是Redis3.0，Redis后面版本中又增加了内部编码，略过不提；本章所介绍的内部编码都是基于3.0的)：



<img src="https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16psToqkhLlldRPLh7DnS3FdLbs96B99Ql949ibpCgJia3MmAdrwSMRBwZA/640?wx_fmt=png&amp;tp=webp&amp;wxfrom=5&amp;wx_lazy=1&amp;wx_co=1" alt="img" style="zoom:150%;" />

图片来源：《Redis设计与实现》



关于Redis内部编码的转换，都符合以下规律：编码转换在Redis写入数据时完成，且转换过程不可逆，只能从小内存编码向大内存编码转换。



### **1、字符串**



#### （1）概况



字符串是最基础的类型，因为所有的键都是字符串类型，且字符串之外的其他几种复杂类型的元素也是字符串。

字符串**长度不能超过512MB**。



#### （2）内部编码

字符串类型的内部编码有3种，它们的应用场景如下：

- **int**：8个字节的长整型。字符串值是整型时，这个值使用long整型表示。

- **embstr**：<=39字节的字符串。

  > 注意：embstr与raw都使用redisObject和sds保存数据，**区别在于**：
  >
  > 1. embstr的使用**只分配一次内存空间**（因此redisObject和sds是连续的），而raw需要分配两次内存空间（分别为redisObject和sds分配空间）。
  > 2. 因此与raw相比，embstr的好处在于创建时少分配一次空间，删除时少释放一次空间，以及对象的所有数据连在一起，寻找方便。
  > 3. 而embstr的坏处也很明显，如果字符串的长度增加需要重新分配内存时，整个redisObject和sds都需要重新分配空间，因此redis中的**embstr实现为只读**。

- **raw**：大于39个字节的字符串



示例如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pJxILEU2AxGht0K3oylYV6xQzw2Zve6WZnvzKcmHL67p5ek6oYlBk1w/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

> embstr和raw进行区分的长度，是39；是因为redisObject的长度是16字节，sds的长度是9+字符串长度；因此当字符串长度是39时，embstr的长度正好是16+9+39=64，jemalloc正好可以分配64字节的内存单元。



#### （3）编码转换

1. 当int数据不再是整数，或大小超过了long的范围时，自动转化为raw。

2. 而对于embstr，由于其实现是只读的，因此**在对embstr对象进行修改时，都会先转化为raw再进行修改**，因此，只要是修改embstr对象，修改后的对象一定是raw的，无论是否达到了39个字节。示例如下图所示：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pVP5KJGuoY6FMMHvfmlicemTibdvFMEArCF5AHmSicib4U0jzGNOc37FvZA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



### **2、列表**



#### （1）概况

列表（list）用来存储多个有序的字符串，每个字符串称为元素；**一个列表可以存储2^32-1个元素**。Redis中的列表支持两端插入和弹出，并可以获得指定位置（或范围）的元素，可以充当数组、队列、栈等。



#### （2）内部编码

列表的内部编码可以是**压缩列表**（ziplist）或**双端链表**（linkedlist）。

- **双端链表**：

  由一个list结构和多个listNode结构组成；典型结构如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16p0ibuibEicLo7glW5t3G8nfKCwT3hpRpE1nnIGRKgh49F8ebZicg1GWFdYA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

​	图片来源：《Redis设计与实现》

通过图中可以看出，**双端链表同时保存了表头指针和表尾指针**，并且**每个节点都有指向前和指向后的指针**；链表中保存了**列表的长度**；dup、free和match为节点值设置类型特定函数，所以链表可以用于保存各种不同类型的值。而**链表中每个节点指向的是type为字符串的redisObject**。



- **压缩列表**：

  压缩列表是Redis**为了节约内存而开发的**，是**由一系列特殊编码的连续内存块(而不是像双端链表一样每个节点是指针)组成的顺序型数据结构**；具体结构相对比较复杂，略。

  > 1. 与双端链表相比，压缩列表**可以节省内存空间，但是进行修改或增删操作时，复杂度较高**；
  >
  > 2. 因此**当节点数量较少时，可以使用压缩列表；但是节点数量多时，还是使用双端链表划算**。
  > 3. 压缩列表不仅用于实现列表，也用于实现哈希、有序列表；使用非常广泛。



#### **（3）编码转换**

只有同时满足下面**两个条件时**，才会使用压缩列表：

1. 列表中元素数量小于512个；
2. 列表中所有字符串对象都不足64字节。

如果有一个条件不满足，则使用双端列表；且编码只可能由压缩列表转化为双端链表，反方向则不可能。

> 下图展示了列表编码转换的特点：
>
> ![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16poSvSslDkSib4p5C6my09bPkMnwKFzWnsdzDODFJMRDD6zQiaaRicDiadoA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)
>
> 其中，单个字符串不能超过64字节，是为了便于统一分配每个节点的长度；**这里的64字节是指字符串的长度**，**不包括SDS结构**，因为压缩列表使用连续、定长内存块存储字符串，不需要SDS结构指明长度。后面提到压缩列表，也会强调长度不超过64字节，原理与这里类似。



### **3、哈希**



#### （1）概况



哈希（作为一种数据结构），不仅是redis对外提供的5种对象类型的一种（与字符串、列表、集合、有序结合并列），也是Redis作为Key-Value数据库所使用的数据结构。为了说明的方便，在本文后面当使用“内层的哈希”时，代表的是redis对外提供的5种对象类型的一种；使用“外层的哈希”代指Redis作为Key-Value数据库所使用的数据结构。



#### （2）内部编码(大概看看，具体的可以查看源码)

**内层的哈希使用的内部编码**可以是**压缩列表**（ziplist）和**哈希表**（hashtable）两种；Redis的**外层的哈希则只使用了hashtable**。



**压缩列表**前面已介绍。与哈希表相比，压缩列表用于元素个数少、元素长度小的场景；其优势在于集中存储，节省空间；同时，虽然对于元素的操作复杂度也由O(n)变为了O(1)，但由于哈希中元素数量较少，因此操作的时间并没有明显劣势。



**hashtable**：一个hashtable由**1个dict结构**、**2个dictht结构**、**1个dictEntry指针数组**（称为`bucket`）和**多个dictEntry结构**组成。



正常情况下（即hashtable没有进行rehash时）各部分关系如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pY7pZeFKqicsxUic7icxcEia6Hn2fic71fD01bOkDYiapnicNxJic5JbRz3azHQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

图片改编自：《Redis设计与实现》

下面从底层向上依次介绍各个部分：

- **dictEntry**:

dictEntry结构用于保存键值对，结构定义如下：

> **typedef** **struct** dictEntry{
>
>   **void** *key;
>
>   union{
>
> ​    **void** *val;
>
> ​    uint64_tu64;
>
> ​    int64_ts64;
>
>   }v;
>
>   **struct** dictEntry *next;
>
> }dictEntry;

其中，各个属性的功能如下：

- key：键值对中的键；
- val：键值对中的值，使用union(即共用体)实现，存储的内容既可能是一个指向值的指针，也可能是64位整型，或无符号64位整型；
- next：指向下一个dictEntry，用于解决哈希冲突问题

在64位系统中，一个dictEntry对象占24字节（key/val/next各占8字节）。



- **bucket**

bucket是一个数组，数组的每个元素都是指向dictEntry结构的指针。redis中bucket数组的大小计算规则如下：大于dictEntry的、最小的2^n；例如，如果有1000个dictEntry，那么bucket大小为1024；如果有1500个dictEntry，则bucket大小为2048。



- **dictht**

dictht结构如下：

> **typedef** **struct** dictht{
>
>   dictEntry **table;
>
>   **unsigned** **long** size;
>
>   **unsigned** **long** sizemask;
>
>   **unsigned** **long** used;
>
> }dictht;



其中，各个属性的功能说明如下：

> - table属性是一个指针，指向bucket；
> - size属性记录了哈希表的大小，即bucket的大小；
> - used记录了已使用的dictEntry的数量；
> - sizemask属性的值总是为size-1，这个属性和哈希值一起决定一个键在table中存储的位置。



- **dict**

一般来说，通过使用dictht和dictEntry结构，便可以实现普通哈希表的功能；但是Redis的实现中，在dictht结构的上层，还有一个dict结构。下面说明dict结构的定义及作用。

dict结构如下：

> **typedef** **struct** dict{
>
>   dictType *type;
>
>   **void** *privdata;
>
>   dictht ht[2];
>
>   **int** trehashidx;
>
> } dict;

其中，type属性和privdata属性是为了适应不同类型的键值对，用于创建多态字典。



ht属性和trehashidx属性则用于rehash，即当哈希表需要扩展或收缩时使用。ht是一个包含两个项的数组，每项都指向一个dictht结构，这也是Redis的哈希会有1个dict、2个dictht结构的原因。通常情况下，所有的数据都是存在放dict的ht[0]中，ht[1]只在rehash的时候使用。dict进行rehash操作的时候，将ht[0]中的所有数据rehash到ht[1]中。然后将ht[1]赋值给ht[0]，并清空ht[1]。



因此，Redis中的哈希之所以在dictht和dictEntry结构之外还有一个dict结构，一方面是为了适应不同类型的键值对，另一方面是为了rehash。



#### （3）编码转换



如前所述，Redis中内层的哈希既可能使用哈希表，也可能使用压缩列表。



只有同时满足下面**两个条件时**，才会使用压缩列表：

1. 哈希中元素数量小于512个；
2. 哈希中所有键值对的键和值字符串长度都小于64字节。

如果有一个条件不满足，则使用哈希表；且编码只可能由压缩列表转化为哈希表，反方向则不可能。



下图展示了Redis内层的哈希编码转换的特点：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pAC4aMuYESA87ibHotthxiclOpReYlNnEGicKEqn8MnASkGiapNdz1tfq9w/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



### **4、集合**

#### （1）概况



集合（set）与列表类似，都是用来保存多个字符串，但集合与列表有两点不同：

1. 集合中的元素是无序的，因此不能通过索引来操作元素；
2. 集合中的元素不能有重复。

一个集合中最多可以**存储2^32-1个元**素；除了支持常规的增删改查，Redis还支持多个集合取交集、并集、差集。



#### （2）内部编码



集合的内部编码可以是**整数集合**（intset）或**哈希表**（hashtable）。



哈希表前面已经讲过，这里略过不提；需要注意的是，**集合在使用哈希表时，值全部被置为null。**



整数集合的结构定义如下：

> **typedef** **struct** intset{
>
>   uint32_t encoding;
>
>   uint32_t length;
>
>   int8_t contents[];
>
> } intset;

其中，**encoding**代表contents中存储内容的类型，虽然**contents**（存储集合中的元素）是int8_t类型，但实际上其存储的值是int16_t、int32_t或int64_t，具体的类型便是由encoding决定的；**length**表示元素个数。



**整数集合适用于集合所有元素都是整数且集合元素数量较小的时候**，与哈希表相比，整数集合的优势在于集中存储，节省空间；同时，虽然对于元素的操作复杂度也由O(n)变为了O(1)，但由于集合数量较少，因此操作的时间并没有明显劣势。



### （3）编码转换



只有同时满足下面两个条件时，集合才会使用整数集合：

1. 集合中元素数量小于512个；
2. 集合中所有元素都是整数值。

如果有一个条件不满足，则使用哈希表；且编码只可能由整数集合转化为哈希表，反方向则不可能。



下图展示了集合编码转换的特点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16pXEdBptKZTaAQF1t9ozFPpbs0K6NEZ4sn49cDlEVxf0CoKUTPG3LiacQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



### 5、有序集合



#### （1）概况

有序集合与集合一样，元素都不能重复；但与集合不同的是，有序集合中的元素是有顺序的。与列表使用索引下标作为排序依据不同，有序集合为每个元素设置一个分数（score）作为排序依据。



#### （2）内部编码

有序集合的内部编码可以是**压缩列表**（ziplist）或**跳跃表**（skiplist）。ziplist在列表和哈希中都有使用，前面已经讲过，这里略过不提。



**跳跃表**是一种有序数据结构，**通过在每个节点中维持多个指向其他节点的指针，从而达到快速访问节点的目的**。

> 除了跳跃表，实现有序数据结构的另一种典型实现是平衡树；大多数情况下，跳跃表的效率可以和平衡树媲美，且跳跃表实现比平衡树简单很多，因此redis中选用跳跃表代替平衡树。跳跃表支持平均O(logN)、最坏O(N)的复杂点进行节点查找，并支持顺序操作。Redis的跳跃表实现由zskiplist和zskiplistNode两个结构组成：前者用于保存跳跃表信息（如头结点、尾节点、长度等），后者用于表示跳跃表节点。具体结构相对比较复杂，略。(简单看一下，后续有机会可以深入)



#### （3）编码转换

只有同时满足下面两个条件时，才会使用压缩列表：

1. 有序集合中元素数量小于128个；
2. 有序集合中所有成员长度都不足64字节。

如果有一个条件不满足，则使用跳跃表；且编码只可能由压缩列表转化为跳跃表，反方向则不可能。



下图展示了有序集合编码转换的特点：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B2ibpIb1Cad6IsYzmTyJl16ptaicvYib4gSTGWHYPNkdE8icPHd5fw4I6XkRvXbW12iaFiaPNzV01GG0kQg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



## 五、应用举例(优化相关)

了解Redis的内存模型之后，下面通过几个例子说明其应用。

### **1、估算Redis内存使用量**

要估算redis中的数据占据的内存大小，需要对redis的内存模型有比较全面的了解，包括前面介绍的hashtable、sds、redisobject、各种对象类型的编码方式等。

下面以最简单的字符串类型来进行说明。

假设有90000个键值对，每个key的长度是7个字节，每个value的长度也是7个字节（且key和value都不是整数）；下面来估算这90000个键值对所占用的空间。在估算占据空间之前，首先可以判定字符串类型使用的编码方式：embstr。

90000个键值对占据的内存空间主要可以分为两部分：一部分是90000个dictEntry占据的空间；一部分是键值对所需要的bucket空间。

每个dictEntry占据的空间包括：

1)    一个dictEntry，24字节，jemalloc会分配32字节的内存块

2)    一个key，7字节，所以SDS(key)需要7+9=16个字节，jemalloc会分配16字节的内存块

3)    一个redisObject，16字节，jemalloc会分配16字节的内存块

4)    一个value，7字节，所以SDS(value)需要7+9=16个字节，jemalloc会分配16字节的内存块

5)    综上，一个dictEntry需要32+16+16+16=80个字节。

bucket空间：bucket数组的大小为大于90000的最小的2^n，是131072；每个bucket元素为8字节（因为64位系统中指针大小为8字节）。

因此，可以估算出这90000个键值对占据的内存大小为：90000*80 + 131072*8 = 8248576。

下面写个程序在redis中验证一下：

> public **class** RedisTest {
>
> 　　public static Jedis jedis = **new** Jedis("localhost", 6379);
>
> 　	public static **void** main(**String**[] args) throws Exception{
>
> 　　　　**Long** m1 = **Long**.valueOf(getMemory());
>
> 　　　　insertData();
>
> 　　　　**Long** m2 = **Long**.valueOf(getMemory());
>
> 　　　　System.out.println(m2 - m1);
>
> 　　}
>
> 　　public static **void** insertData(){
>
> 　　　　**for**(**int** i = 10000; i < 100000; i++){
>
> 　　　　　　jedis.set("aa" + i, "aa" + i); *//key和value长度都是7字节，且不是整数*
>
> 　　　　}
>
> 　　}
>
> 　　public static **String** getMemory(){
>
> 　　　　**String** memoryAllLine = jedis.info("memory");
>
> 　　　　**String** usedMemoryLine = memoryAllLine.split("\r\n")[1];
>
> 　　　　**String** memory = usedMemoryLine.substring(usedMemoryLine.indexOf(':') + 1);
>
> 　　　　**return** memory;
>
> 　　}
>
> }

运行结果：8247552

理论值与结果值误差在万分之1.2，对于计算需要多少内存来说，这个精度已经足够了。之所以会存在误差，是因为在我们插入90000条数据之前redis已分配了一定的bucket空间，而这些bucket空间尚未使用。

作为对比将key和value的长度由7字节增加到8字节，则对应的SDS变为17个字节，jemalloc会分配32个字节，因此每个dictEntry占用的字节数也由80字节变为112字节。此时估算这90000个键值对占据内存大小为：90000*112 + 131072*8 = 11128576。

在redis中验证代码如下（只修改插入数据的代码）：

> public static **void** insertData(){
>
> 　　**for**(**int** i = 10000; i < 100000; i++){
>
> 　　　　jedis.set("aaa" + i, "aaa" + i); *//key和value长度都是8字节，且不是整数*
>
> 　　}
>
> }

运行结果：11128576；估算准确。

对于字符串类型之外的其他类型，对内存占用的估算方法是类似的，需要结合具体类型的编码方式来确定。



### **2、优化内存占用**

了解redis的内存模型，对优化redis内存占用有很大帮助。下面介绍几种优化场景。

（1）**利用jemalloc特性进行优化**

上一小节所讲述的90000个键值便是一个例子。由于jemalloc分配内存时数值是不连续的，因此key/value字符串变化一个字节，可能会引起占用内存很大的变动；在设计时可以利用这一点。

例如，如果key的长度如果是8个字节，则SDS为17字节，jemalloc分配32字节；此时将key长度缩减为7个字节，则SDS为16字节，jemalloc分配16字节；则每个key所占用的空间都可以缩小一半。

（2）**使用整型/长整型**

如果是整型/长整型，Redis会使用int类型（8字节）存储来代替字符串，可以节省更多空间。因此在可以使用长整型/整型代替字符串的场景下，尽量使用长整型/整型。

（3）**共享对象**

利用共享对象，可以减少对象的创建（同时减少了redisObject的创建），节省内存空间。目前redis中的共享对象只包括10000个整数（0-9999）；可以通过调整REDIS_SHARED_INTEGERS参数提高共享对象的个数；例如将REDIS_SHARED_INTEGERS调整到20000，则0-19999之间的对象都可以共享。

考虑这样一种场景：论坛网站在redis中存储了每个帖子的浏览数，而这些浏览数绝大多数分布在0-20000之间，这时候通过适当增大REDIS_SHARED_INTEGERS参数，便可以利用共享对象节省内存空间。

（4）避免过度设计

然而需要注意的是，不论是哪种优化场景，都要考虑内存空间与设计复杂度的权衡；而设计复杂度会影响到代码的复杂度、可维护性。

如果数据量较小，那么为了节省内存而使得代码的开发、维护变得更加困难并不划算；还是以前面讲到的90000个键值对为例，实际上节省的内存空间只有几MB。但是如果数据量有几千万甚至上亿，考虑内存的优化就比较必要了。



### **3、关注内存碎片率**

内存碎片率是一个重要的参数，对redis 内存的优化有重要意义。

1. 如果内存碎片率过高（jemalloc在1.03左右比较正常），说明内存碎片多，内存浪费严重；这时便可以考虑重启redis服务，在内存中对数据进行重排，减少内存碎片。

2. 如果内存碎片率小于1，说明redis内存不足，部分数据使用了虚拟内存（即swap）；由于虚拟内存的存取速度比物理内存差很多（2-3个数量级），此时redis的访问速度可能会变得很慢。因此必须设法增大物理内存（可以增加服务器节点数量，或提高单机内存），或减少redis中的数据。
3. 要减少redis中的数据，除了选用合适的数据类型、利用共享对象等，还有一点是要设置合理的数据回收策略（maxmemory-policy），当内存达到一定量后，根据不同的优先级对内存进行回收。



# Redis 持久化



## **一、Redis高可用概述**

在Redis中，实现高可用的**技术主要包括持久化、复制、哨兵和集群**，下面分别说明它们的作用，以及解决了什么样的问题。

1. **持久化**：持久化是最简单的高可用方法(有时甚至不被归为高可用的手段)，主要作用是数据备份，即将数据存储在硬盘，保证数据不会因进程退出而丢失。
2. **复制**：复制是高可用Redis的基础，哨兵和集群都是在复制基础上实现高可用的。复制主要实现了数据的多机备份，以及对于读操作的负载均衡和简单的故障恢复。缺陷：故障恢复无法自动化；写操作无法负载均衡；存储能力受到单机的限制。
3. **哨兵**：在复制的基础上，哨兵实现了自动化的故障恢复。缺陷：写操作无法负载均衡；存储能力受到单机的限制。
4. **集群**：通过集群，Redis解决了写操作无法负载均衡，以及存储能力受到单机限制的问题，实现了较为完善的高可用方案。

 

## **二、Redis 持久化概述**

持久化的功能：Redis是内存数据库，数据都是存储在内存中，为了避免进程退出导致数据的永久丢失，需要定期将Redis中的数据以某种形式(数据或命令)从内存保存到硬盘；当下次Redis重启时，利用持久化文件实现数据恢复。除此之外，为了进行灾难备份，可以将持久化文件拷贝到一个远程位置。



Redis持久化分为**RDB持久化**和**AOF持久化**：**前者将当前数据保存到硬盘，后者则是将每次执行的写命令保存到硬盘（类似于MySQL的binlog）**；由于AOF持久化的实时性更好，即当进程意外退出时丢失的数据更少，因此AOF是目前主流的持久化方式，不过RDB持久化仍然有其用武之地。



下面依次介绍RDB持久化和AOF持久化；由于Redis各个版本之间存在差异，如无特殊说明，以Redis3.0为准。



## **三、RDB持久化**



RDB持久化是**将当前进程中的数据生成快照保存到硬盘(因此也称作快照持久化)，保存的文件后缀是rdb；当Redis重新启动时，可以读取快照文件恢复数据。**



### **1. 触发条件**

RDB持久化的触发分为**手动触发**和**自动触发**两种。



#### 1) 手动触发

**save**命令和**bgsave命**令都可以生成RDB文件。

save命令会阻塞Redis服务器进程，直到RDB文件创建完毕为止，在Redis服务器阻塞期间，服务器不能处理任何命令请求。

<img src="https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5Z88cpG3BDOS7HsIzWA2xCZZzVmAiarfrreL4OL0Zz62effRDMLMRj6QQ/640?wx_fmt=png&amp;tp=webp&amp;wxfrom=5&amp;wx_lazy=1&amp;wx_co=1" alt="img" style="zoom:150%;" />

而**bgsave**命令会创建一个子进程，由子进程来负责创建RDB文件，父进程(即Redis主进程)则继续处理请求。

<img src="https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5Z4hjD7U6wQw1Dmib6InibPmX43BCoiccr6ND7iaTLhFO0rbcqheg2VXmXcw/640?wx_fmt=png&amp;tp=webp&amp;wxfrom=5&amp;wx_lazy=1&amp;wx_co=1" alt="img" style="zoom:150%;" />

此时服务器执行日志如下：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZzlHaObS9mRv8mbic46nkfZicr0zVmUeoSzvx99OZjjgtUN67BfojZ1wg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



注意： **bgsave命令执行过程中，只有fork子进程时会阻塞服务器，而对于save命令，整个过程都会阻塞服务器**，因此save已基本被废弃，线上环境要杜绝save的使用；后文中也将只介绍bgsave命令。此外，在自动触发RDB持久化时，Redis也会选择bgsave而不是save来进行持久化；下面介绍自动触发RDB持久化的条件。



#### 2) 自动触发

**save m n**



自动触发最常见的情况是在**配置文件**中通过**save m n，指定当m秒内发生n次变化时，会触发bgsave。**



例如，查看redis的默认配置文件(Linux下为redis根目录下的redis.conf)，可以看到如下配置信息：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZViaiasx2lfF4Jia1y3TST2DLePicxo4edVkqU97JBibKfjlw0A9ehvGCbtg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

其中save 900 1的含义是：当时间到900秒时，如果redis数据发生了至少1次变化，则执行bgsave；save 300 10和save 60 10000同理。当三个save条件满足任意一个时，都会引起bgsave的调用。

> **save m n的实现原理（了解下）**: 
>
> Redis的save m n，是通过serverCron函数、dirty计数器、和lastsave时间戳来实现的。
>
> serverCron是Redis服务器的周期性操作函数，默认每隔100ms执行一次；该函数对服务器的状态进行维护，其中一项工作就是检查 save m n 配置的条件是否满足，如果满足就执行bgsave。
>
> dirty计数器是Redis服务器维持的一个状态，记录了上一次执行bgsave/save命令后，服务器状态进行了多少次修改(包括增删改)；而当save/bgsave执行完成后，会将dirty重新置为0。
>
> 例如，如果Redis执行了set mykey helloworld，则dirty值会+1；如果执行了sadd myset v1 v2 v3，则dirty值会+3；注意dirty记录的是服务器进行了多少次修改，而不是客户端执行了多少修改数据的命令。
>
> lastsave时间戳也是Redis服务器维持的一个状态，记录的是上一次成功执行save/bgsave的时间。
>
> save m n的原理如下：每隔100ms，执行serverCron函数；在serverCron函数中，遍历save m n配置的保存条件，只要有一个条件满足，就进行bgsave。对于每一个save m n条件，只有下面两条同时满足时才算满足：
>
> （1）当前时间-lastsave > m
>
> （2）dirty >= n



**save m n 执行日志**

下图是save m n触发bgsave执行时，服务器打印日志的情况：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5Zwl7MCE16laCyibyZTQHgqfB7FxXibafMUt62GBtpwzfPGoic8BsGULWGw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



**其他自动触发机制**

除了save m n 以外，还有一些其他情况会触发bgsave：

- 在主从复制场景下，如果从节点执行全量复制操作，则主节点会执行bgsave命令，并将rdb文件发送给从节点
- 执行shutdown命令时，自动执行rdb持久化，如下图所示：

![img](assets%5C640)



### **2. 执行流程**

前面介绍了触发bgsave的条件，下面将说明bgsave命令的执行流程，如下图所示(图片来源：https://blog.csdn.net/a1007720052/article/details/79126253)：



![img](assets%5C640)



图片中的5个步骤所进行的操作如下：

1) Redis父进程首先判断：当前是否在执行save，或bgsave/bgrewriteaof（后面会详细介绍该命令）的子进程，如果在执行则bgsave命令直接返回。bgsave/bgrewriteaof 的子进程不能同时执行，主要是基于性能方面的考虑：两个并发的子进程同时执行大量的磁盘写操作，可能引起严重的性能问题。

2) 父进程执行fork操作创建子进程，这个过程中父进程是阻塞的，Redis不能执行来自客户端的任何命令

3) 父进程fork后，bgsave命令返回”Background saving started”信息并不再阻塞父进程，并可以响应其他命令

4) 子进程创建RDB文件，根据父进程内存快照生成临时快照文件，完成后对原有文件进行原子替换

5) 子进程发送信号给父进程表示完成，父进程更新统计信息



### **3. RDB文件**

RDB文件是经过压缩的二进制文件，下面介绍关于RDB文件的一些细节。



**存储路径**

RDB文件的存储路径既可以在启动前配置，也可以通过命令动态设定。

配置：dir配置指定目录，dbfilename指定文件名。默认是Redis根目录下的dump.rdb文件。

动态设定：Redis启动后也可以动态修改RDB存储路径，在磁盘损害或空间不足时非常有用；执行命令为config set dir {newdir}和config set dbfilename {newFileName}。如下所示(Windows环境)：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZudxpicrVhibvptsAkPCz9ticsFsodnobODfNIdkiczcuQlh6tvhjwZjghQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



**RDB文件格式**

RDB文件格式如下图所示（图片来源：《Redis设计与实现》）：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZxNhXhCjjLcpcdkmUJeooy5Us7ZH60QOQgJYZAxFT1y8Y0Q93RldulA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

其中各个字段的含义说明如下：

1) REDIS：常量，保存着”REDIS”5个字符。

2) db_version：RDB文件的版本号，注意不是Redis的版本号。

3) SELECTDB 0 pairs：表示一个完整的数据库(0号数据库)，同理SELECTDB 3 pairs表示完整的3号数据库；只有当数据库中有键值对时，RDB文件中才会有该数据库的信息(上图所示的Redis中只有0号和3号数据库有键值对)；如果Redis中所有的数据库都没有键值对，则这一部分直接省略。其中：SELECTDB是一个常量，代表后面跟着的是数据库号码；0和3是数据库号码；pairs则存储了具体的键值对信息，包括key、value值，及其数据类型、内部编码、过期时间、压缩信息等等。

4) EOF：常量，标志RDB文件正文内容结束。

5) check_sum：前面所有内容的校验和；Redis在载入RBD文件时，会计算前面的校验和并与check_sum值比较，判断文件是否损坏。



**压缩**

Redis默认采用LZF算法对RDB文件进行压缩。虽然压缩耗时，但是可以大大减小RDB文件的体积，因此压缩默认开启；可以通过命令关闭：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZCWppugNAJ5jibsgGcoxEe0Oq98OkY1vegJldFAojP2kAMbNrwGmaCVQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

需要注意的是，RDB文件的压缩并不是针对整个文件进行的，而是对数据库中的字符串进行的，且只有在字符串达到一定长度(20字节)时才会进行。



### **4. 启动时加载**

RDB文件的载入工作是在服务器启动时自动执行的，并没有专门的命令。但是由于AOF的优先级更高，因此当AOF开启时，Redis会优先载入AOF文件来恢复数据；只有当AOF关闭时，才会在Redis服务器启动时检测RDB文件，并自动载入。服务器载入RDB文件期间处于阻塞状态，直到载入完成为止。



Redis启动日志中可以看到自动载入的执行：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZvLHiaRmUMEQWqAwGBsLiceaFkvotF5zxtgjYA2UL5Mct0XQKOT6FUnIw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

Redis载入RDB文件时，会对RDB文件进行校验，如果文件损坏，则日志中会打印错误，Redis启动失败。



### **5. RDB常用配置总结**

下面是RDB常用的配置项，以及默认值；前面介绍过的这里不再详细介绍。

- **save m n**：bgsave自动触发的条件；如果没有save m n配置，相当于自动的RDB持久化关闭，不过此时仍可以通过其他方式触发
- **stop-writes-on-bgsave-error yes**：当bgsave出现错误时，Redis是否停止执行写命令；设置为yes，则当硬盘出现问题时，可以及时发现，避免数据的大量丢失；设置为no，则Redis无视bgsave的错误继续执行写命令，当对Redis服务器的系统(尤其是硬盘)使用了监控时，该选项考虑设置为no
- **rdbcompression yes**：是否开启RDB文件压缩
- **rdbchecksum yes**：是否开启RDB文件的校验，在写入文件和读取文件时都起作用；关闭checksum在写入文件和启动文件时大约能带来10%的性能提升，但是数据损坏时无法发现
- **dbfilename dump.rdb**：RDB文件名
- **dir ./**：RDB文件和AOF文件所在目录

 

## **四、AOF持久化**



RDB持久化是将进程数据写入文件，而AOF持久化(即Append Only File持久化)，则是**将Redis执行的每次写命令记录到单独的日志文件中**（有点像MySQL的binlog）；当Redis**重启时再次执行AOF文件中的命令来恢复数据**。

与RDB相比，**AOF的实时性更好**，因此已成为主流的持久化方案。



### **1. 开启AOF**

Redis服务器默认开启RDB，关闭AOF；要开启AOF，需要在配置文件中配置：

​			**appendonly yes**



### **2. 执行流程**

由于需要记录Redis的每条写命令，因此AOF不需要触发，下面介绍AOF的执行流程。

AOF的执行流程包括：

- 命令追加(append)：将Redis的写命令追加到缓冲区aof_buf；

- 文件写入(write)和文件同步(sync)：根据不同的同步策略将aof_buf中的内容同步到硬盘；

- 文件重写(rewrite)：定期重写AOF文件，达到压缩的目的。

  

#### 1) 命令追加(append) 

Redis**先将写命令追加到缓冲区**，而不是直接写入文件，主要是为了避免每次有写命令都直接写入硬盘，导致硬盘IO成为Redis负载的瓶颈。

命令追加的格式是Redis命令请求的协议格式，它是一种纯文本格式，具有兼容性好、可读性强、容易处理、操作简单避免二次开销等优点；具体格式略。在AOF文件中，除了用于指定数据库的select命令（如select 0 为选中0号数据库）是由Redis添加的，其他都是客户端发送来的写命令。



#### 2) 文件写入(write)和文件同步(sync)

Redis提供了多种AOF缓存区的同步文件策略，策略涉及到操作系统的write函数和fsync函数，说明如下：

> 1. 为了提高文件写入效率，在现代操作系统中，当用户调用write函数将数据写入文件时，操作系统通常会将数据暂存到一个内存缓冲区里，当缓冲区被填满或超过了指定时限后，才真正将缓冲区的数据写入到硬盘里。
> 2. 这样的操作虽然提高了效率，但也带来了安全问题：如果计算机停机，内存缓冲区中的数据会丢失；
> 3. 因此系统同时提供了**fsync**、**fdatasync**等同步函数，可以强制操作系统立刻将缓冲区中的数据写入到硬盘里，从而确保数据的安全性。

AOF缓存区的同步文件策略由参数**appendfsync**控制，各个值的含义如下：

- **always**：命令写入aof_buf后立即调用系统fsync操作同步到AOF文件，fsync完成后线程返回。这种情况下，每次有写命令都要同步到AOF文件，硬盘IO成为性能瓶颈，Redis只能支持大约几百TPS写入，严重降低了Redis的性能；即便是使用固态硬盘（SSD），每秒大约也只能处理几万个命令，而且会大大降低SSD的寿命。
- **no**：命令写入aof_buf后调用系统write操作，不对AOF文件做fsync同步；同步由操作系统负责，通常同步周期为30秒。这种情况下，文件同步的时间不可控，且缓冲区中堆积的数据会很多，数据安全性无法保证。
- **everysec**：命令写入**aof_buf**后调用系统write操作，write完成后线程返回；fsync同步文件操作由专门的线程每秒调用一次。everysec是前述两种策略的折中，是性能和数据安全性的平衡，因此是Redis的**默认配置**，也是我们推荐的配置。



#### 3) 文件重写(rewrite)

随着时间流逝，Redis服务器执行的写命令越来越多，AOF文件也会越来越大；过大的AOF文件不仅会影响服务器的正常运行，也会导致数据恢复需要的时间过长。



文件重写是**指定期重写AOF文件，减小AOF文件的体积**。需要注意的是，**AOF重写是把Redis进程内的数据转化为写命令，同步到新的AOF文件；不会对旧的AOF文件进行任何读取、写入操作**!



关于文件重写**需要注意**的另一点是：对于AOF持久化来说，**文件重写虽然是强烈推荐的，但并不是必须的**；即使没有文件重写，数据也可以被持久化并在Redis启动的时候导入；因此在一些实现中，会关闭自动的文件重写，然后通过定时任务在每天的某一时刻定时执行。



> **文件重写能够压缩AOF文件，原因在于**：
>
> - 过期的数据不再写入文件
>
> - 无效的命令不再写入文件：如有些数据被重复设值(set mykey v1, set mykey v2)、有些数据被删除了(sadd myset v1, del myset)等等
>
> - 多条命令可以合并为一个：如sadd myset v1, sadd myset v2, sadd myset v3可以合并为sadd myset v1 v2 v3。
>
>   > 不过为了防止单条命令过大造成客户端缓冲区溢出，对于list、set、hash、zset类型的key，并不一定只使用一条命令；而是以某个常量为界将命令拆分为多条。这个常量在redis.h/REDIS_AOF_REWRITE_ITEMS_PER_CMD中定义，不可更改，3.0版本中值是64。
>   >
>   > ![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZLIeSTvnvEcskNwWyVAqFhhgewg7Vsty1IKOuyRI9NIjELyjxnY208A/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



通过上述内容可以看出，由于重写后AOF执行的命令减少了，文件重写既可以减少文件占用的空间，也可以加快恢复速度。



#### 文件重写的触发

文件重写的触发，分为**手动触发**和**自动触发**：



**手动触发**：直接调用**bgrewriteaof**命令，该命令的执行与bgsave有些类似：都是fork子进程进行具体的工作，且都只有在fork时阻塞。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZibfDvVm9W74XMeXzAYeA2AqXJCmxT0Xks0tVINt9ibbIdicR9wZBMn0VQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

此时服务器执行日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZN4TbuFGOWFpnZuQQbhvGIaQqPTuJLDuQ7aIUHCvicccmeic451z1MD8w/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



**自动触发**：根据**auto-aof-rewrite-min-size和auto-aof-rewrite-percentage参**数，以及**aof_current_size**和**aof_base_size**状态确定触发时机。

- auto-aof-rewrite-min-size：执行AOF重写时，文件的最小体积，默认值为64MB。
- auto-aof-rewrite-percentage：执行AOF重写时，当前AOF大小(即aof_current_size)和上一次重写时AOF大小(aof_base_size)的比值。

其中，参数可以通过config get命令查看：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZqKxoaHwDC3GA1bEdlkjs2hwoSEQdh3iceQ3wjrvQodUdWYGJeGuufEg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

状态可以通过info persistence查看：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5Z8Y3YCYrRViaRNGdAHAI6XYNE5AK5EJRmLzWmxk1JUHbnmI3KxgVM8dw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

注意： 只有当auto-aof-rewrite-min-size和auto-aof-rewrite-percentage两个参数同时满足时，才会自动触发AOF重写，即bgrewriteaof操作。

自动触发bgrewriteaof时，可以看到服务器日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZPpRqTXOGUkpWeBXYxcVLXwPysYnQp37OeVzm4QHDibMf5uHuzB9bJibA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



#### **文件重写的流程**

文件重写流程如下图所示(图片来源：http://www.cnblogs.com/yangmingxianshen/p/8373205.html)：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZuQ4vLjwkiaUfHej1PBoUB79jasbpE6d6PQfCP2OT7kRHfmpaPG8oGBw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

关于文件重写的流程，有两点需要特别注意：(1)重写由父进程fork子进程进行；(2)重写期间Redis执行的写命令，需要追加到新的AOF文件中，为此Redis引入了**aof_rewrite_buf**缓存。

对照上图，文件重写的流程如下：

1) Redis父进程首先判断当前是否存在正在执行 bgsave/bgrewriteaof的子进程，如果存在则bgrewriteaof命令直接返回，如果存在bgsave命令则等bgsave执行完成后再执行。前面曾介绍过，这个主要是基于性能方面的考虑。

2) 父进程执行fork操作创建子进程，这个过程中父进程是阻塞的。

3.1) 父进程fork后，bgrewriteaof命令返回”Background append only file rewrite started”信息并不再阻塞父进程，并可以响应其他命令。Redis的所有写命令依然写入AOF缓冲区，并根据appendfsync策略同步到硬盘，保证原有AOF机制的正确。

3.2) 由于fork操作使用写时复制技术，子进程只能共享fork操作时的内存数据。由于父进程依然在响应命令，因此Redis使用AOF重写缓冲区(图中的aof_rewrite_buf)保存这部分数据，防止新AOF文件生成期间丢失这部分数据。也就是说，bgrewriteaof执行期间，Redis的写命令同时追加到aof_buf和aof_rewirte_buf两个缓冲区。

4) 子进程根据内存快照，按照命令合并规则写入到新的AOF文件。

5.1) 子进程写完新的AOF文件后，向父进程发信号，父进程更新统计信息，具体可以通过info persistence查看。

5.2) 父进程把AOF重写缓冲区的数据写入到新的AOF文件，这样就保证了新AOF文件所保存的数据库状态和服务器当前状态一致。

5.3) 使用新的AOF文件替换老文件，完成AOF重写。



### **3. 启动时加载**



前面提到过，当AOF开启时，Redis启动时会优先载入AOF文件来恢复数据；只有当AOF关闭时，才会载入RDB文件恢复数据。

当AOF开启，且AOF文件存在时，Redis启动日志：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZKQY7Neswu5wOiaZ25XRPkWa1r7EU0NMCJhXgRrwTFpHzulSpyl8BB1Q/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

当AOF开启，但AOF文件不存在时，即使RDB文件存在也不会加载(更早的一些版本可能会加载，但3.0不会)，Redis启动日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZFEFuO9K8ho6yO0eyBt2khhqudet7lHRYH2Pp6P7pmqOWxTN8edFhoQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



**文件校验**

与载入RDB文件类似，Redis载入AOF文件时，会对AOF文件进行校验，如果文件损坏，则日志中会打印错误，Redis启动失败。但如果是AOF文件结尾不完整(机器突然宕机等容易导致文件尾部不完整)，且aof-load-truncated参数开启，则日志中会输出警告，Redis忽略掉AOF文件的尾部，启动成功。aof-load-truncated参数默认是开启的：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5Z29aBoJWyD4ExK6TW7U8I685HL60G2nl2RlZjxJh3csLhBVTkHp847Q/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

**伪客户端**

因为Redis的命令只能在客户端上下文中执行，而载入AOF文件时命令是直接从文件中读取的，并不是由客户端发送；因此Redis服务器在载入AOF文件之前，会创建一个没有网络连接的客户端，之后用它来执行AOF文件中的命令，命令执行的效果与带网络连接的客户端完全一样。



### **4. AOF常用配置总结**

下面是AOF常用的配置项，以及默认值；前面介绍过的这里不再详细介绍。

- **appendonly no**：是否开启AOF
- **appendfilename “appendonly.aof”**：AOF文件名
- **dir ./**：RDB文件和AOF文件所在目录
- **appendfsync everysec**：fsync持久化策略
- no-appendfsync-on-rewrite no：AOF重写期间是否禁止fsync；如果开启该选项，可以减轻文件重写时CPU和硬盘的负载（尤其是硬盘），但是可能会丢失AOF重写期间的数据；需要在负载和安全性之间进行平衡
- **auto-aof-rewrite-percentage** 100：文件重写触发条件之一
- **auto-aof-rewrite-min-size 64mb**：文件重写触发提交之一
- aof-load-truncated yes：如果AOF文件结尾损坏，Redis启动时是否仍载入AOF文件

 

## **五、方案选择与常见问题**



前面介绍了RDB和AOF两种持久化方案的细节，下面介绍RDB和AOF的特点、如何选择持久化方案，以及在持久化过程中常遇到的问题等。



### **1. RDB和AOF的优缺点**

RDB和AOF各有优缺点：

**RDB持久化**：

1. 优点：**RDB文件紧凑，体积小，网络传输快，适合全量复制**；**恢复速度比AOF快很多**。当然，与AOF相比，RDB最重要的优点之一是**对性能的影响相对较小**。

2. 缺点：RDB文件的致命缺点在于其数据快照的持久化方式决定了必然**做不到实时持久化**，而在数据越来越重要的今天，数据的大量丢失很多时候是无法接受的，因此AOF持久化成为主流。此外，RDB文件需要满足特定格式，兼容性差（如老版本的Redis不兼容新版本的RDB文件）。

**AOF持久化**：

1. 优点：与RDB持久化相对应，AOF的优点在于支持秒级持久化、兼容性好，
2. 缺点：是文件大、恢复速度慢、对性能影响大。



### **2. 持久化策略选择**

> 在介绍持久化策略之前，首先要明白无论是RDB还是AOF:
>
> 1. 持久化的开启都是要付出性能方面代价的：
>    - 对于RDB持久化，一方面是bgsave在进行fork操作时Redis主进程会阻塞，另一方面，子进程向硬盘写数据也会带来IO压力；
>    - 对于AOF持久化，向硬盘写数据的频率大大提高(everysec策略下为秒级)，IO压力更大，甚至可能造成AOF追加阻塞问题（后面会详细介绍这种阻塞），
>    - 此外，AOF文件的重写与RDB的bgsave类似，会有fork时的阻塞和子进程的IO压力问题。相对来说，由于AOF向硬盘中写数据的频率更高，因此对Redis主进程性能的影响会更大。
>
> 在实际生产环境中，根据数据量、应用对数据的安全要求、预算限制等不同情况，会有各种各样的持久化策略；如完全不使用任何持久化、使用RDB或AOF的一种，或同时开启RDB和AOF持久化等。此外，持久化的选择必须与Redis的主从策略一起考虑，因为主从复制与持久化同样具有数据备份的功能，而且主机master和从机slave可以独立的选择持久化方案。



下面分场景来讨论持久化策略的选择，下面的讨论也只是作为参考，实际方案可能更复杂更具多样性。

**（1）**如果Redis中的数据完全丢弃也没有关系（如Redis完全用作DB层数据的cache），那么无论是单机，还是主从架构，都可以不进行任何持久化。

**（2）**在单机环境下（对于个人开发者，这种情况可能比较常见），如果可以接受十几分钟或更多的数据丢失，选择RDB对Redis的性能更加有利；如果只能接受秒级别的数据丢失，应该选择AOF。

**（3）**但在多数情况下，我们都会**配置主从环境**，slave的存在既可以实现数据的热备，也可以进行读写分离分担Redis读请求，以及在master宕掉后继续提供服务。

> 在这种情况下，一种可行的做法是：
>
> **master**：完全关闭持久化（包括RDB和AOF），这样可以让master的性能达到最好
>
> **slave**：1. 关闭RDB，开启AOF（如果对数据安全要求不高，开启RDB关闭AOF也可以），并定时对持久化文件进行备份（如备份到其他文件夹，并标记好备份的时间）；2. 然后关闭AOF的自动重写，然后添加定时任务，在每天Redis闲时（如凌晨12点）调用bgrewriteaof。
>
> 这里需要解释一下，为什么开启了主从复制，可以实现数据的热备份，还需要设置持久化呢？因为在一些特殊情况下，主从复制仍然不足以保证数据的安全，例如：
>
> - master和slave进程同时停止：考虑这样一种场景，如果master和slave在同一栋大楼或同一个机房，则一次停电事故就可能导致master和slave机器同时关机，Redis进程停止；如果没有持久化，则面临的是数据的完全丢失。
> - master误重启：考虑这样一种场景，master服务因为故障宕掉了，如果系统中有自动拉起机制（即检测到服务停止后重启该服务）将master自动重启，由于没有持久化文件，那么master重启后数据是空的，slave同步数据也变成了空的；如果master和slave都没有持久化，同样会面临数据的完全丢失。需要注意的是，即便是使用了哨兵(关于哨兵后面会有文章介绍)进行自动的主从切换，也有可能在哨兵轮询到master之前，便被自动拉起机制重启了。因此，应尽量避免“自动拉起机制”和“不做持久化”同时出现。

**（4）**异地灾备：上述讨论的几种持久化策略，针对的都是一般的系统故障，如进程异常退出、宕机、断电等，这些故障不会损坏硬盘。但是对于一些可能导致硬盘损坏的灾难情况，如火灾地震，就需要进行异地灾备。例如对于单机的情形，可以定时将RDB文件或重写后的AOF文件，通过scp拷贝到远程机器，如阿里云、AWS等；对于主从的情形，可以定时在master上执行bgsave，然后将RDB文件拷贝到远程机器，或者在slave上执行bgrewriteaof重写AOF文件后，将AOF文件拷贝到远程机器上。一般来说，由于RDB文件文件小、恢复快，因此灾难恢复常用RDB文件；异地备份的频率根据数据安全性的需要及其他条件来确定，但最好不要低于一天一次。



### **3. fork阻塞：CPU的阻塞**



在Redis的实践中，众多因素限制了**Redis单机的内存不能过大**，例如：

- 当面对请求的暴增，需要从库扩容时，Redis内存过大会导致扩容时间太长；
- 当主机宕机时，切换主机后需要挂载从库，Redis内存过大导致挂载速度过慢；
- 以及持久化过程中的fork操作，下面详细说明。

首先说明一下fork操作：

父进程通过fork操作可以创建子进程；子进程创建后，父子进程共享代码段，不共享进程的数据空间，但是子进程会获得父进程的数据空间的副本。在操作系统fork的实际实现中，基本都采用了写时复制技术，即在父/子进程试图修改数据空间之前，父子进程实际上共享数据空间；但是当父/子进程的任何一个试图修改数据空间时，操作系统会为修改的那一部分(内存的一页)制作一个副本。



虽然fork时，子进程不会复制父进程的数据空间，但是会复制内存页表（页表相当于内存的索引、目录）；父进程的数据空间越大，内存页表越大，fork时复制耗时也会越多。



在Redis中，无论是RDB持久化的bgsave，还是AOF重写的bgrewriteaof，都需要fork出子进程来进行操作。如果Redis内存过大，会导致fork操作时复制内存页表耗时过多；而Redis主进程在进行fork时，是完全阻塞的，也就意味着无法响应客户端的请求，会造成请求延迟过大。



对于不同的硬件、不同的操作系统，fork操作的耗时会有所差别，一般来说，如果Redis单机内存达到了10GB，fork时耗时可能会达到百毫秒级别（如果使用Xen虚拟机，这个耗时可能达到秒级别）。因此，**一般来说Redis单机内存一般要限制在10GB以内**；不过这个数据并不是绝对的，可以通过观察线上环境fork的耗时来进行调整。观察的方法如下：执行命令**info stats**，查看**latest_fork_usec**的值，单位为微秒。



为了减轻fork操作带来的阻塞问题，除了控制Redis单机内存的大小以外，还可以适度放宽AOF重写的触发条件、选用物理机或高效支持fork操作的虚拟化技术等，例如使用Vmware或KVM虚拟机，不要使用Xen虚拟机。



### **4. AOF追加阻塞：硬盘的阻塞**



前面提到过，在AOF中，如果AOF缓冲区的文件同步策略为everysec，则：在主线程中，命令写入aof_buf后调用系统write操作，write完成后主线程返回；fsync同步文件操作由专门的文件同步线程每秒调用一次。



这种做法的问题在于，如果硬盘负载过高，那么fsync操作可能会超过1s；如果Redis主线程持续高速向aof_buf写入命令，硬盘的负载可能会越来越大，IO资源消耗更快；如果此时Redis进程异常退出，丢失的数据也会越来越多，可能远超过1s。



为此，Redis的处理策略是这样的：主线程每次进行AOF会对比上次fsync成功的时间；如果距上次不到2s，主线程直接返回；如果超过2s，则主线程阻塞直到fsync同步完成。因此，如果系统硬盘负载过大导致fsync速度太慢，会导致Redis主线程的阻塞；此外，使用everysec配置，AOF最多可能丢失2s的数据，而不是1s。



AOF追加阻塞问题定位的方法：

（1）监控info Persistence中的aof_delayed_fsync：当AOF追加阻塞发生时（即主线程等待fsync而阻塞），该指标累加。

（2）AOF阻塞时的Redis日志：

Asynchronous AOF fsync is taking too long (disk is busy?). Writing the AOF buffer without waiting for fsync to complete, this may slow down Redis.

（3）如果AOF追加阻塞频繁发生，说明系统的硬盘负载太大；可以考虑更换IO速度更快的硬盘，或者通过IO监控分析工具对系统的IO负载进行分析，如iostat（系统级io）、iotop（io版的top）、pidstat等。



### **5. info命令与持久化**

前面提到了一些通过info命令查看持久化相关状态的方法，下面来总结一下。

（1）info Persistence

执行结果如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0D7K8ncficWpoibhqsaMNM5ZVz1Jje4s8pVspuGoZWFhFcJYqf7K4KdU9knBDjxxOqqT2BU4CicTnlQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

其中比较重要的包括：

- rdb_last_bgsave_status:上次bgsave 执行结果，可以用于发现bgsave错误
- rdb_last_bgsave_time_sec:上次bgsave执行时间（单位是s），可以用于发现bgsave是否耗时过长
- aof_enabled:AOF是否开启
- aof_last_rewrite_time_sec: 上次文件重写执行时间（单位是s），可以用于发现文件重写是否耗时过长
- aof_last_bgrewrite_status: 上次bgrewrite执行结果，可以用于发现bgrewrite错误
- aof_buffer_length和aof_rewrite_buffer_length:aof缓存区大小和aof重写缓冲区大小
- aof_delayed_fsync:AOF追加阻塞情况的统计

（2）info stats

其中与持久化关系较大的是：latest_fork_usec，代表上次fork耗时，可以参见前面的讨论。



## **六、总结**



本文主要内容可以总结如下：

1、持久化在Redis高可用中的作用：数据备份，与主从复制相比强调的是由内存到硬盘的备份。

2、RDB持久化：将数据快照备份到硬盘；介绍了其触发条件（包括手动出发和自动触发）、执行流程、RDB文件等，特别需要注意的是文件保存操作由fork出的子进程来进行。

3、AOF持久化：将执行的写命令备份到硬盘（类似于MySQL的binlog），介绍了其开启方法、执行流程等，特别需要注意的是文件同步策略的选择（everysec）、文件重写的流程。

4、一些现实的问题：包括如何选择持久化策略，以及需要注意的fork阻塞、AOF追加阻塞等。



# Redis 主从复制

## **一、主从复制概述**

主从复制，是指将一台Redis服务器的数据，复制到其他的Redis服务器。前者称为主节点(master)，后者称为从节点(slave)；数据的复制是单向的，只能由主节点到从节点。

> 默认情况下，每台Redis服务器都是主节点；且**一个主节点可以有多个从节点(或没有从节点)，但一个从节点只能有一个主节点**



**主从复制的作用**

主从复制的作用主要包括：

1. 数据冗余：主从复制实现了数据的热备份，是持久化之外的一种数据冗余方式。

2. 故障恢复：当主节点出现问题时，可以由从节点提供服务，实现快速的故障恢复；实际上是一种服务的冗余。

3. 负载均衡：在主从复制的基础上，配合读写分离，可以由主节点提供写服务，由从节点提供读服务（即写Redis数据时应用连接主节点，读Redis数据时应用连接从节点），分担服务器负载；尤其是在写少读多的场景下，通过多个从节点分担读负载，可以大大提高Redis服务器的并发量。

4. 高可用基石：除了上述作用以外，主从复制还是哨兵和集群能够实施的基础，因此说**主从复制是Redis高可用的基础**。

   > 缺陷：故障恢复无法自动化；写操作无法负载均衡；存储能力受到单机的限制。



## 二、如何使用主从复制

为了更直观的理解主从复制，在介绍其内部原理之前，先说明我们需要如何操作才能开启主从复制。



### **1. 建立复制**

需要注意，主从复制的开启，完全是在**从节点发起**的；不需要我们在主节点做任何事情。

从节点开启主从复制，有3种方式：

（1）配置文件

在从服务器的配置文件中加入：**slaveof <masterip> <masterport>**

（2）启动命令

redis-server启动命令后加入 **–slaveof <masterip> <masterport>**

（3）客户端命令

Redis服务器启动后，直接通过客户端执行命令：**slaveof <masterip> <masterport>**，则该Redis实例成为从节点。

上述3种方式是等效的，下面以客户端命令的方式为例，看一下当执行了slaveof后，Redis主节点和从节点的变化。



### **2. 实例**

#### 准备工作：启动两个节点

方便起见，实验所使用的主从节点是在一台机器上的不同Redis实例，其中主节点监听6379端口，从节点监听6380端口；从节点监听的端口号可以在配置文件中修改：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIxFEF3P6Xz2Z8bicI7ULgX8OhS8sd9WMqh7exiaCXducicJrOhHiabicrrsg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

启动后可以看到：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafI9QwWt976YEbQZGvIZAL1flMoyX0uqGxboR0t7aqGIiaPN4OHpt40daw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

两个Redis节点启动后（分别称为6379节点和6380节点），默认都是主节点。



#### 建立复制

此时在6380节点执行slaveof命令，使之变为从节点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIhDCibROiaPcDBrP4NkEs9FEIf5tic1INUnVTygYZPv0EuSjQkibAjxoWSg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

#### 观察效果

下面验证一下，在主从复制建立后，主节点的数据会复制到从节点中。

（1）首先在从节点查询一个不存在的key：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafI7z5q68K6pFEYmqaGAIoVI9dgmaSDD50Vey0shcaNn79CHXibzGoUaOg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（2）然后在主节点中增加这个key：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIyP3icibVff4T4MB1TiaNIHJTXNQ2DhfHNGvgsF91NyHywE01Dy8UibrjOw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（3）此时在从节点中再次查询这个key，会发现主节点的操作已经同步至从节点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIQlFEVSAlHAwgXNnhoIWZr8CO13MjbAc5cA5RjVyOUW0UQzYdQ7yicUQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（4）然后在主节点删除这个key：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIfrfHxQlkNkAFfaINJuRIqF8Z0mK59Kuqicgxtch3zhEMGuJibG0Dr3iag/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（5）此时在从节点中再次查询这个key，会发现主节点的操作已经同步至从节点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIFam6gDDibYhMOnvsLuZxCI8FSuoURDfpkMEvdpjX0icsqc2RX3ERTazQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



### **3. 断开复制**

通过slaveof <masterip> <masterport>命令建立主从复制关系以后，可以通过**slaveof no one断开**。需要注意的是，**从节点断开复制后，不会删除已有的数据，只是不再接受主节点新的数据变化**。

从节点执行slaveof no one后，打印日志如下所示；可以看出断开复制后，从节点又变回为主节点。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIG86JUegQg1PgBXsyklc4B6zfQrvLfOSEcpFibicSMKicYt2Os0j3llJJA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

主节点打印日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIj3B95biblQweTia2pX53q9tBzM5SDMweVCs8ucDDQ7DRKEORAD9RGHxg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)





## **三、主从复制的三个阶段**

上面一节中，介绍了如何操作可以建立主从关系；本小节将介绍主从复制的实现原理。

主从复制过程大体可以分为3个阶段：**连接建立阶段**（即准备阶段）、**数据同步阶段**、**命令传播阶段**；下面分别进行介绍。

### **1. 连接建立阶段（从节点发起）**

该阶段的主要作用是在主从节点之间建立连接，为数据同步做好准备。

#### 步骤1：保存主节点信息

**从节点**服务器内部维护了**两个字段**，即**masterhost**和**masterport**字段，用于存储主节点的ip和port信息。

需要注意的是，slaveof是异步命令，从节点完成主节点ip和port的保存后，向发送slaveof命令的客户端直接返回OK，实际的复制操作在这之后才开始进行。

这个过程中，可以看到从节点打印日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIuULax0wg91cHToHO4CjmeFl0dNGjF9sZXJ3dImMeiaoF8L2BY7Qw3Pg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

![img](data:image/gif;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQImWNgYGBgAAAABQABh6FO1AAAAABJRU5ErkJggg==)

#### 步骤2：建立socket连接

从节点**每秒1次**调用**复制定时函数replicationCron()**，如果发现了有主节点可以连接，便会根据主节点的ip和port，创建socket连接。如果连接成功，则：

1. 从节点：为该socket建立一个专门处理复制工作的**文件事件处理器**，负责后续的复制工作，如接收RDB文件、接收命令传播等。.

2. 主节点：接收到从节点的socket连接后（即accept之后），为该socket创建相应的客户端状态，并**将从节点看做是连接到主节点的一个客户端**，后面的步骤会以从节点向主节点发送命令请求的形式来进行。

这个过程中，从节点打印日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIicnfvXk7F2wianW54ut2uflEzCGj5TAcbMHNTz4yePFecjibtmENa98Nw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



#### 步骤3：发送ping命令

从节点成为主节点的客户端之后，发送ping命令进行首次请求，**目的是：检查socket连接是否可用，以及主节点当前是否能够处理请求。**

从节点发送ping命令后，可能出现3种情况：

（1）返回pong：说明socket连接正常，且主节点当前可以处理请求，复制过程继续。

（2）超时：一定时间后从节点仍未收到主节点的回复，说明socket连接不可用，则从节点断开socket连接，并重连。

（3）返回pong以外的结果：如果主节点返回其他结果，如正在处理超时运行的脚本，说明主节点当前无法处理命令，则从节点断开socket连接，并重连。

在主节点返回pong情况下，从节点打印日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIrkTMUuWymRabbKdhTd7GJN1jCuibibEC12eicTKt88K89aUn1cWrjkkAg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

![img](data:image/gif;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQImWNgYGBgAAAABQABh6FO1AAAAABJRU5ErkJggg==)

#### 步骤4：身份验证

如果**从节点**中设置了**masterauth**选项，则从节点需要向主节点进行身份验证；没有设置该选项，则不需要验证。

从节点进行身份验证是通过向主节点发送auth命令进行的，auth命令的参数即为配置文件中的masterauth的值。

1. 如果主节点设置密码的状态，与从节点masterauth的状态一致（一致是指都存在，且密码相同，或者都不存在），则身份验证通过，复制过程继续；
2. 如果不一致，则从节点断开socket连接，并重连。



#### 步骤5：发送从节点端口信息

身份验证之后，**从节点会向主节点发送其监听的端口号**（前述例子中为6380），主节点将该信息保存到该从节点对应的客户端的**slave_listening_port**字段中；`该端口信息除了在主节点中执行info Replication时显示以外，没有其他作用。`



### **2. 数据同步阶段（主节点发起）**



主从节点之间的连接建立以后，便可以开始进行数据同步，该阶段可以**理解为从节点数据的初始化**。**具体执行的方式是：从节点向主节点发送psync命令（Redis2.8以前是sync命令），开始同步。**

数据同步阶段是主从复制最核心的阶段，根据主从节点当前状态的不同，可以分为**全量复制**和**部分复制**

> 需要注意的是，在数据同步阶段之前，从节点是主节点的客户端，主节点不是从节点的客户端；而到了这一阶段及以后，**主从节点互为客户端**。原因在于：在此之前，主节点只需要响应从节点的请求即可，不需要主动发请求，而在数据同步阶段和后面的命令传播阶段，主节点需要主动向从节点发送请求（如推送缓冲区中的写命令），才能完成复制。



### **3. 命令传播阶段（主节点发起）**

数据同步阶段完成后，**主从节**点进入命令传播阶段；

1. 在这个阶段**主节点将自己执行的写命令发送给从节点，从节点接收命令并执行，从而保证主从节点数据的一致性。**

2. 主从节点还维持着**心跳机制**：**PING**和**REPLCONF ACK**。心跳机制的原理**涉及部分复制**



**命令传播是异步：**

需要注意的是，**命令传播是异步的过程**，`即主节点发送写命令后并不会等待从节点的回复`；因此实际上主从节点之间很难保持实时的一致性，延迟在所难免。

数据不一致的程度，与**主从节点之间的网络状况**、**主节点写命令的执行频率**、以及**主节点中的repl-disable-tcp-nodelay配置**等有关。



**repl-disable-tcp-nodelay no**：该配置作用于命令传播阶段，控制主节点是否禁止与从节点的TCP_NODELAY；

> 1. 默认no，即不禁止TCP_NODELAY；TCP会**立马将主节点的数据发送给从节点，带宽增加但延迟变小**。
> 2. 当设置为yes时，TCP会**对包进行合并从而减少带宽**，但是**发送的频率会降低**，**从节点数据延迟增加，一致性变差**；具体发送频率与Linux内核的配置有关，默认配置为40ms。

一般来说，只有当应用对Redis数据不一致的容忍度较高，且主从节点之间网络状况不好时，才会设置为yes；多数情况使用默认值no。



## **四、【数据同步阶段】全量复制和部分复制**

在Redis2.8以前，从节点向主节点发送sync命令请求同步数据，此时的同步方式是全量复制；在Redis2.8及以后，从节点可以发送psync命令请求同步数据，此时根据主从节点当前状态的不同，同步方式可能是全量复制或部分复制。后文介绍以Redis2.8及以后版本为例。

1. **全量复制**：用于**初次复制**或其**他无法进行部分复制**的情况，将主节点中的所有数据都发送给从节点，是一个非常重型的操作。

2. **部分复制**：用于**网络中断等情况后的复制，只将中断期间主节点执行的写命令发送给从节点**，与全量复制相比更加高效。

   > 需要注意的是，如果网络中断时间过长，导致主节点没有能够完整地保存中断期间执行的写命令，则无法进行部分复制，仍使用全量复制。



### 1. 全量复制

Redis通过**psync**命令进行全量复制的过程如下：

（1）从节点判断无法进行部分复制，向主节点发送全量复制的请求；或从节点发送部分复制的请求，但主节点判断无法进行全量复制；具体判断过程需要在讲述了部分复制原理后再介绍。

（2）主节点收到全量复制的命令后，执行**bgsave**，在后台生成RDB文件，并使用一个缓冲区（称为复制缓冲区）记录从现在开始执行的所有写命令

（3）主节点的bgsave执行完成后，将RDB文件发送给从节点；**从**节点**首先清除自己的旧数据**，然后载入接收的RDB文件，将数据库状态更新至主节点执行bgsave时的数据库状态

（4）主节点将前述**复制缓冲区中**（`注意这里不是复制积压缓冲区，属于客户端缓冲区`）的所有写命令发送给从节点，从节点执行这些写命令，将数据库状态更新至主节点的最新状态

（5）如果从节点开启了AOF，则会**触发bgrewriteaof**的执行，从而保证AOF文件更新至主节点的最新状态

下面是执行全量复制时，主从节点打印的日志；可以看出日志内容与上述步骤是完全对应的。

主节点的打印日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIAmV2A36dI9huibTGU6HTIquheuCXsoYS4GWRfbyS4XDRb9AOE3dXcEg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

从节点打印日志如下图所示：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIptTKQPlsJV8BZQicGJ64serrACJJwmzpsKbBonohB0YDvJ5eryHDyuQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

> 其中，有几点需要注意：
>
> 1. 从节点接收了来自主节点的89260个字节的数据；
> 2. 从节点在载入主节点的数据之前要先将老数据清除；
> 3. 从节点在同步完数据后，调用了bgrewriteaof。



**全量复制的问题**：

（1）主节点通过bgsave命令fork子进程进行RDB持久化，该过程是非常消耗CPU、内存(页表复制)、硬盘IO的；关于bgsave的性能问题，可以参考 [深入学习Redis（2）：持久化](http://mp.weixin.qq.com/s?__biz=MzA5ODM5MDU3MA==&mid=2650863933&idx=1&sn=90dd7a349ef0ffe47347a97879789552&chksm=8b661e78bc11976ec8b18cc91c55aae252e5c42c457ce0df42fcd0b70b7f2df23d7b23c4490e&scene=21#wechat_redirect)

（2）主节点通过网络将RDB文件发送给从节点，对主从节点的带宽都会带来很大的消耗

（3）从节点清空老数据、载入新RDB文件的过程是阻塞的，无法响应客户端的命令；如果从节点执行bgrewriteaof，也会带来额外的消耗



### **2. 部分复制**

由于全量复制在主节点数据量较大时效率太低，因此Redis2.8开始提供部分复制，用于处理网络中断时的数据同步。

部分复制的实现，依赖于三个重要的概念：

#### （1）复制偏移量

主节点和从节点分别维护一个复制偏移量（offset），代表的是主节点向从节点传递的字节数；主节点每次向从节点传播N个字节数据时，主节点的offset增加N；从节点每次收到主节点传来的N个字节数据时，从节点的offset增加N。

offset用于**判断主从节点的数据库状态是否一致**：如果二者offset相同，则一致；如果offset不同，则不一致，此时**可以根据两个offset找出从节点缺少的那部分数据**。例如，如果主节点的offset是1000，而从节点的offset是500，那么部分复制就需要将offset为501-1000的数据传递给从节点。而offset为501-1000的数据存储的位置。



#### （2）复制积压缓冲区

复制积压缓冲区是：由**主节点维护**的、**固定长度**的、**先进先出(FIFO)队列**，**默认大小1MB**；

缓冲创建的时间： 当主节点开始有从节点时创建

缓冲的作用： 是备份主节点最近发送给从节点的数据。

> 注意，无论主节点有一个还是多个从节点，都只需要一个复制积压缓冲区。



在命令传播阶段，主节点除了将写命令发送给从节点，还会发送一份给复制积压缓冲区，作为写命令的备份；除了存储写命令，复制积压缓冲区中还存储了其中的每个字节对应的复制偏移量（offset）。由于复制积压缓冲区定长且是先进先出，所以它保存的是主节点最近执行的写命令；时间较早的写命令会被挤出缓冲区。



**复制积压缓冲区的有效场景**：

1. 由于该缓冲区长度固定且有限，因此可以备份的写命令也有限，**当主从节点offset的差距过大超过缓冲区长度时，将无法执行部分复制，只能执行全量复制**。

2. 为了**提高部分复制执行的概率**，可以根据需要**增大复制积压缓冲区的大小**(通过配置**repl-backlog-size**)；

   > 例如如果网络中断的平均时间是60s，而主节点平均每秒产生的写命令(特定协议格式)所占的字节数为100KB，则复制积压缓冲区的平均需求为6MB，保险起见，可以设置为12MB，来保证绝大多数断线情况都可以使用部分复制。



**从节点将offset发送给主节点后，主节点根据offset和缓冲区大小决定能否执行部分复制**：

- 如果offset偏移量之后的数据，仍然都在复制积压缓冲区里，则执行部分复制；
- 如果offset偏移量之后的数据已不在复制积压缓冲区中（数据已被挤出），则执行全量复制。



#### （3）服务器运行ID(runid)

每个Redis节点(无论主从)，在启动时都会自动生成一个随机ID(每次启动都不一样)，由40个随机的十六进制字符组成；runid用来唯一识别一个Redis节点。通过info Server命令，可以查看节点的runid：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIotvWBPicxfUEvBJFJjKof99fcsh0LPBniaaNmEH8jcT1ffsu30ybUnAA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

1. 主从节点初次复制时，主节点将自己的runid发送给从节点，从节点将这个runid保存起来；
2. 当断线重连时，从节点会将这个runid发送给主节点；



**主节点根据runid判断能否进行部分复制**：

- 如果从节点保存的runid与主节点现在的runid相同，说明主从节点之前同步过，主节点会继续尝试使用部分复制(到底能不能部分复制还要看offset和复制积压缓冲区的情况)；

- 如果从节点保存的runid与主节点现在的runid不同，说明从节点在断线前同步的Redis节点并不是当前的主节点，只能进行全量复制。

  

###  **3. psync命令的执行流程**



在了解了复制偏移量、复制积压缓冲区、节点运行id之后，本节将介绍psync命令的参数和返回值，从而说明psync命令执行过程中，主从节点是如何确定使用全量复制还是部分复制的。

psync命令的执行过程可以参见下图（图片来源：《Redis设计与实现》）：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIUzgOQRPTicRxHia7uQM4rzicuBR3HI32Fc9kJj00TOEibAcgwm77SdAtZA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



（1）首先，从节点根据当前状态，决定如何调用psync命令：

- 如果从节点之前未执行过slaveof或最近执行了slaveof no one，则从节点发送命令为psync ? -1，向主节点请求全量复制；
- 如果从节点之前执行了slaveof，则发送命令为psync <runid> <offset>，其中runid为上次复制的主节点的runid，offset为上次复制截止时从节点保存的复制偏移量。

（2）主节点根据收到的psync命令，及当前服务器状态，决定执行全量复制还是部分复制：

- 如果主节点版本低于Redis2.8，则返回-ERR回复，此时从节点重新发送sync命令执行全量复制；
- 如果主节点版本够新，且runid与从节点发送的runid相同，且从节点发送的offset之后的数据在复制积压缓冲区中都存在，则回复+CONTINUE，表示将进行部分复制，从节点等待主节点发送其缺少的数据即可；
- 如果主节点版本够新，但是runid与从节点发送的runid不同，或从节点发送的offset之后的数据已不在复制积压缓冲区中(在队列中被挤出了)，则回复+FULLRESYNC <runid> <offset>，表示要进行全量复制，其中runid表示主节点当前的runid，offset表示主节点当前的offset，从节点保存这两个值，以备使用。



### **4. 部分复制演示**

在下面的演示中，网络中断几分钟后恢复，断开连接的主从节点进行了部分复制；为了便于模拟网络中断，本例中的主从节点在局域网中的两台机器上。



**网络中断**

网络中断一段时间后，主节点和从节点都会发现失去了与对方的连接（关于主从节点对超时的判断机制，后面会有说明）；此后，从节点便开始执行对主节点的重连，由于此时网络还没有恢复，重连失败，从节点会一直尝试重连。

主节点日志如下：

![img](D:%5CLearningSpace%5C%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0%5Credis%5Credis%E7%AC%94%E8%AE%B0.assets%5C640)

从节点日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIaVtsOJZdezicwicGW9Y5P2qX2vb3ofxeSsicA87v1ibcDutzMBeQtpg5gg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



**网络恢复**

网络恢复后，从节点连接主节点成功，并请求进行部分复制，主节点接收请求后，二者进行部分复制以同步数据。

主节点日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafICa9icg97H5ZOgFiadJPTy6Mia9xduh3xchBvPLuBUzRjroVeC2MKKEmibA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

从节点日志如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIzBlxGZ61ZJiaUMADedv2v2DwiabU5oe506acsNCxddPB0OZJ9RwlCxug/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



## **五、【命令传播阶段】心跳机制**



在**命令传播阶段**，除了发送写命令，主从节点还维持着心跳机制：PING和REPLCONF ACK。心跳机制**对于主从复制的超时判断、数据安全等有作用**。



### **1.主->从：PING**

每隔指定的时间，**主节点会向从节点发送PING命令**，这个PING命令的作用，主要是**为了让从节点进行超时判断**。

PING**发送的频率**由**repl-ping-slave-period参数**控制，单位是秒，默认值是10s。

> 关于该PING命令究竟是由主节点发给从节点，还是相反，有一些争议；因为在Redis的官方文档中，对该参数的注释中说明是从节点向主节点发送PING命令，如下图所示：
>
> ![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafI4icvOicHVo0LJcDLefaBuqs96tC0JKgLSzBB7CqY7Wygz9ZdUHKWdnGg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

但是根据该参数的名称(含有ping-slave)，以及代码实现，我认为该PING命令是主节点发给从节点的。相关代码如下：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIRibsUdajCoXj02sNbzXzNW5bpQusgLFPo3dzc4fdXJIOO24t2mL09DQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



### **2. 从->主：REPLCONF ACK**

在命令传播阶段，从节点会向主节点发送REPLCONF ACK命令，频率是每秒1次；命令格式为：**REPLCONF ACK {offset}**，其中offset指从节点保存的复制偏移量。REPLCONF ACK命令的作用包括：

（1）**实时监测主从节点网络状态**：该命令会**被主节点用于复制超时的判断**。此外，在主节点中使用info Replication，可以看到其从节点的状态中的lag值，代表的是主节点上次收到该REPLCONF ACK命令的时间间隔，在正常情况下，该值应该是0或1，如下图所示：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIr7DRiaQtHDTggfxdcje4kEQRiaWlKY82MANX6GQuL1b8Zca73L5icicj7A/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（2）**检测命令丢失**：**从节点发送了自身的offset，主节点会与自己的offset对比，如果从节点数据缺失（如网络丢包），主节点会推送缺失的数据（这里也会利用复制积压缓冲区）**。

> 注意，**offset和复制积压缓冲区，不仅可以用于部分复制，也可以用于处理命令丢失等情形**；**区别在于**前者是在断线重连后进行的，而后者是在主从节点没有断线的情况下进行的。

（3）**辅助保证从节点的数量和延迟**：Redis主节点中使用**min-slaves-to-write和min-slaves-max-lag参数，来保证主节点在不安全的情况下不会执行写命令**；

> 所谓不安全，是指：从节点数量太少，或延迟过高。例如min-slaves-to-write和min-slaves-max-lag分别是3和10，含义是如果从节点数量小于3个，或所有从节点的延迟值都大于10s，则主节点拒绝执行写命令。而这里从节点延迟值的获取，就是通过主节点接收到REPLCONF ACK命令的时间来判断的，即前面所说的info Replication中的lag值。



## **六、应用中的问题**



### **1. 读写分离的问题**

在主从复制基础上实现的读写分离，可以实现Redis的读负载均衡：由主节点提供写服务，由一个或多个从节点提供读服务（多个从节点既可以提高数据冗余程度，也可以最大化读负载能力）；在读负载较大的应用场景下，可以大大提高Redis服务器的并发量。下面介绍在使用Redis读写分离时，需要注意的问题。

#### （1）延迟与不一致问题

前面已经讲到，由于主从复制的命令传播是异步的，延迟与数据的不一致不可避免。如果应用对数据不一致的接受程度程度较低.

可能的优化措施包括：

1. 优化主从节点之间的网络环境（如在同机房部署）；
2. `监控主从节点延迟（通过offset）判断，如果从节点延迟过大，通知应用不再通过该从节点读取数据；`
3. 使用集群同时扩展写负载和读负载等。



在命令传播阶段以外的其他情况下，从节点的数据不一致可能更加严重，例如连接在数据同步阶段，或从节点失去与主节点的连接时等。从节点的**slave-serve-stale-data**参数便与此有关：它控制这种情况下从节点的表现；

1. 如果为yes（默认值），则从节点仍能够响应客户端的命令，
2. 如果为no，则从节点只能响应info、slaveof等少数命令。该参数的设置与应用对数据一致性的要求有关；如果对数据一致性要求很高，则应设置为no。



#### （2）数据过期问题

在单机版Redis中，存在两种删除策略：

- 惰性删除：服务器不会主动删除数据，只有当客户端查询某个数据时，服务器判断该数据是否过期，如果过期则删除。
- 定期删除：服务器执行定时任务删除过期数据，但是考虑到内存和CPU的折中（删除会释放内存，但是频繁的删除操作对CPU不友好），该删除的频率和执行时间都受到了限制。

在主从复制场景下，为了主从节点的数据一致性，**从节点不会主动删除数据**，**而是由主节点控制从节点中过期数据的删除**。由于主节点的惰性删除和定期删除策略，都不能保证主节点及时对过期数据执行删除操作，因此，当客户端通过Redis从节点读取数据时，很容易读取到已经过期的数据。

> Redis 3.2中，从节点在读取数据时，增加了对数据是否过期的判断：如果该数据已过期，则不返回给客户端；将Redis升级到3.2可以解决数据过期问题。



#### （3）故障切换问题

在没有使用哨兵的读写分离场景下，应用针对读和写分别连接不同的Redis节点；当主节点或从节点出现问题而发生更改时，需要及时修改应用程序读写Redis数据的连接；连接的切换可以手动进行，或者自己写监控程序进行切换，但前者响应慢、容易出错，后者实现复杂，成本都不算低。



#### （4）总结

在使用读写分离之前，可以考虑其他方法增加Redis的读负载能力：

1. 如尽量优化主节点（减少慢查询、减少持久化等其他情况带来的阻塞等）提高负载能力；
2. 使用Redis集群同时提高读负载能力和写负载能力等。
3. 如果使用读写分离，可以使用哨兵，使主从节点的故障切换尽可能自动化，并减少对应用程序的侵入。



### **2. 复制超时问题**

主从节点复制超时是导致复制中断的最重要的原因之一，本小节单独说明超时问题，下一小节说明其他会导致复制中断的问题。



#### **超时判断意义：**

在复制连接建立过程中及之后，主从节点都有机制判断连接是否超时，其意义在于：

（1）如果主节点判断连接超时，其会释放相应从节点的连接，从而释放各种资源，否则无效的从节点仍会占用主节点的各种资源（输出缓冲区、带宽、连接等）；此外连接超时的判断可以让主节点更准确的知道当前有效从节点的个数，有助于保证数据安全（配合前面讲到的min-slaves-to-write等参数）。

（2）如果从节点判断连接超时，则可以及时重新建立连接，避免与主节点数据长期的不一致。



#### **判断机制：**

主从复制超时判断的核心，在于**repl-timeout参数**，**该参数规定了超时时间的阈值（默认60s）**，对于主节点和从节点同时有效；主从节点触发超时的条件分别如下：

（1）主节点：每秒1次调用复制定时函数replicationCron()，在其中判断当前时间距离上次收到各个从节点REPLCONF ACK的时间，是否超过了repl-timeout值，如果超过了则释放相应从节点的连接。

（2）从节点：从节点对超时的判断同样是在复制定时函数中判断，基本逻辑是：

- 如果当前处于连接建立阶段，且距离上次收到主节点的信息的时间已超过repl-timeout，则释放与主节点的连接；
- 如果当前处于数据同步阶段，且收到主节点的RDB文件的时间超时，则停止数据同步，释放连接；
- 如果当前处于命令传播阶段，且距离上次收到主节点的PING命令或数据的时间已超过repl-timeout值，则释放与主节点的连接。



主从节点判断连接超时的相关源代码如下：

> */\* Replication cron function, called 1 time per second. \*/*
>
> **void** replicationCron(**void**) {
>
>   static **long** **long** replication_cron_loops = 0;
>
>  
>
>   */\* Non blocking connection timeout? \*/*
>
>   **if** (server.masterhost &&
>
> ​    (server.repl_state == REDIS_REPL_CONNECTING ||
>
> ​     slaveIsInHandshakeState()) &&
>
> ​     (time(**NULL**)-server.repl_transfer_lastio) > server.repl_timeout)
>
>   {
>
> ​    redisLog(REDIS_WARNING,"Timeout connecting to the MASTER...");
>
> ​    undoConnectWithMaster();
>
>   }
>
>  
>
>   */\* Bulk transfer I/O timeout? \*/*
>
>   **if** (server.masterhost && server.repl_state == REDIS_REPL_TRANSFER &&
>
> ​    (time(**NULL**)-server.repl_transfer_lastio) > server.repl_timeout)
>
>   {
>
> ​    redisLog(REDIS_WARNING,"Timeout receiving bulk data from MASTER... If the problem persists try to set the 'repl-timeout' parameter in redis.conf to a larger value.");
>
> ​    replicationAbortSyncTransfer();
>
>   }
>
>  
>
>   */\* Timed out master when we are an already connected slave? \*/*
>
>   **if** (server.masterhost && server.repl_state == REDIS_REPL_CONNECTED &&
>
> ​    (time(**NULL**)-server.master->lastinteraction) > server.repl_timeout)
>
>   {
>
> ​    redisLog(REDIS_WARNING,"MASTER timeout: no data nor PING received...");
>
> ​    freeClient(server.master);
>
>   }
>
>  
>
>   *//此处省略无关代码……*
>
>  
>
>   */\* Disconnect timedout slaves. \*/*
>
>   **if** (listLength(server.slaves)) {
>
> ​    listIter li;
>
> ​    listNode *ln;
>
> ​    listRewind(server.slaves,&li);
>
> ​    **while**((ln = listNext(&li))) {
>
> ​      redisClient *slave = ln->value;
>
> ​      **if** (slave->replstate != REDIS_REPL_ONLINE) **continue**;
>
> ​      **if** (slave->flags & REDIS_PRE_PSYNC) **continue**;
>
> ​      **if** ((server.unixtime - slave->repl_ack_time) > server.repl_timeout)
>
> ​      {
>
> ​        redisLog(REDIS_WARNING, "Disconnecting timedout slave: %s",
>
> ​          replicationGetSlaveName(slave));
>
> ​        freeClient(slave);
>
> ​      }
>
> ​    }
>
>   }
>
>  
>
>   *//此处省略无关代码……*
>
>  
>
> }



#### **需要注意的坑:**

下面介绍与复制阶段连接超时有关的一些实际问题：

（1）数据同步阶段：在主从节点进行全量复制bgsave时，主节点需要首先fork子进程将当前数据保存到RDB文件中，然后再将RDB文件通过网络传输到从节点。如果RDB文件过大，主节点在fork子进程+保存RDB文件时耗时过多，可能会导致从节点长时间收不到数据而触发超时；此时从节点会重连主节点，然后再次全量复制，再次超时，再次重连……这是个悲伤的循环。为了避免这种情况的发生，除了注意**Redis单机数据量不要过大**，另一方面就是**适当增大repl-timeout值**，具体的大小可以根据bgsave耗时来调整。

（2）命令传播阶段：如前所述，在该阶段主节点会向从节点发送PING命令，频率由**repl-ping-slave-period控制；该参数应明显小于repl-timeout值(后者至少是前者的几倍)**。否则，如果两个参数相等或接近，网络抖动导致个别PING命令丢失，此时恰巧主节点也没有向从节点发送数据，则从节点很容易判断超时。

（3）慢查询导致的阻塞：如果主节点或从节点执行了一些慢查询（如keys *或者对大数据的hgetall等），导致服务器阻塞；阻塞期间无法响应复制连接中对方节点的请求，可能导致复制超时。



### **3. 复制中断问题**

主从节点超时是复制中断的原因之一，除此之外，还有其他情况可能导致复制中断，其中最主要的是复制缓冲区溢出问题。



#### **复制缓冲区溢出**

前面曾提到过，在全量复制阶段，主节点会将执行的写命令放到**复制缓冲区**（`注意不是复制积压缓冲区`）中该缓冲区存放的数据包括了以下几个时间段内主节点执行的写命令：

1. bgsave生成RDB文件、
2. RDB文件由主节点发往从节点、
3. 从节点清空老数据并载入RDB文件中的数据。

当主节点数据量较大，或者主从节点之间网络延迟较大时，可能导致该缓冲区的大小超过了限制，此时主节点会断开与从节点之间的连接；这种情况可能引起全量复制->复制缓冲区溢出导致连接中断->重连->全量复制->复制缓冲区溢出导致连接中断……的循环。



**复制缓冲区的大小**由**client-output-buffer-limit slave {hard limit} {soft limit} {soft seconds}**配置，**默认值为client-output-buffer-limit slave 256MB 64MB 60**，其含义是：如果buffer大于256MB，或者连续60s大于64MB，则主节点会断开与该从节点的连接。该参数是可以通过config set命令动态配置的（即不重启Redis也可以生效）。



当复制缓冲区溢出时，主节点打印日志如下所示：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIdJyibjbqzv6OziaYoSacsIFBPBt6HNYyrRDL30XoBwSusybbOdttXmhA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

**需要注意的是**，**复制缓冲区**是客户端输出缓冲区的一种，主节点会为**每一个从节点分别分配复制缓冲区**；而**复制积压缓冲区则是一个主节点只有一个，无论它有多少个从节点**。



### **4. 各场景下复制的选择及优化技巧**

在介绍了Redis复制的种种细节之后，现在我们可以来总结一下，在下面常见的场景中，何时使用部分复制，以及需要注意哪些问题。



#### （1）第一次建立复制

此时全量复制不可避免，但仍有几点需要注意：

1. 如果主节点的数据量较大，应该尽量**避开流量的高峰期，避免造成阻塞**；
2. 如果有多个从节点需要建立对主节点的复制，可以考虑将几个从节点错开，避免主节点带宽占用过大。
3. 此外，如果从节点过多，也可以调整主从复制的拓扑结构，**由一主多从结构变为树状结构**`（中间的节点既是其主节点的从节点，也是其从节点的主节点）`；但使用树状结构应该谨慎：虽然主节点的直接从节点减少，降低了主节点的负担，但是多层从节点的延迟增大，数据一致性变差；且结构复杂，维护相当困难。



#### （2）主节点重启

主节点重启可以分为两种情况来讨论，一种是故障导致宕机，另一种则是有计划的重启。



**主节点宕机**

主节点宕机重启后，runid会发生变化，因此不能进行部分复制，**只能全量复制**。

实际上在主节点宕机的情况下，应进行故障转移处理，将其中的一个从节点升级为主节点，其他从节点从新的主节点进行复制；且故障转移应尽量的自动化，后面文章将要介绍的哨兵便可以进行自动的故障转移。



**安全重启：debug reload**

在一些场景下，可能希望对主节点进行重启，例如主节点内存碎片率过高，或者希望调整一些只能在启动时调整的参数。

**产生问题的原因**：如果使用普通的手段重启主节点，会使得runid发生变化，可能导致不必要的全量复制。

**解决办法**：Redis提供了debug reload的重启方式，**重启后，主节点的runid和offset都不受影响，避免了全量复制**。

如下图所示，debug reload重启后runid和offset都未受影响：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIibdjDTibRoZQw3463nfST7Z6QRcnwXgPicdzux8mu0LVQEBb2pCPMg1icQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

> 但debug reload是一柄双刃剑：**它会清空当前内存中的数据，重新从RDB文件中加载，这个过程会导致主节点的阻塞，因此也需要谨慎。**



#### （3）从节点重启

从节点宕机重启后，其保存的主节点的runid会丢失，因此即使再次执行slaveof，也**无法进行部分复制**。



#### （4）网络中断

如果主从节点之间出现网络问题，造成短时间内网络中断，可以分为多种情况讨论。

**第一种情况**：网络问题时间极为短暂，只造成了短暂的丢包，主从节点都没有判定超时（未触发repl-timeout）；此时只需要通过REPLCONF ACK来补充丢失的数据即可。

**第二种情况**：网络问题时间很长，主从节点判断超时（触发了repl-timeout），且丢失的数据过多，超过了复制积压缓冲区所能存储的范围；此时主从节点无法进行部分复制，只能进行全量复制。为了尽可能避免这种情况的发生，应该根据实际情况适当调整复制积压缓冲区的大小；此外及时发现并修复网络中断，也可以减少全量复制。

**第三种情况**：介于前述两种情况之间，主从节点判断超时，且丢失的数据仍然都在复制积压缓冲区中；此时主从节点可以进行部分复制。



### **5. 复制相关的参数配置**

这一节总结一下与复制有关的配置，说明这些配置的作用、起作用的阶段，以及配置方法等；通过了解这些配置，一方面加深对Redis复制的了解，另一方面掌握这些配置的方法，可以优化Redis的使用，少走坑。

配置大致可以分为**主节点相关配置**、**从节点相关配置**以及与**主从节点都有关的配**置，下面分别说明。



#### （1）与主从节点都有关的配置

首先介绍最特殊的配置，它决定了该节点是主节点还是从节点：

1)  **slaveof <masterip> <masterport>**：Redis启动时起作用；作用是建立复制关系，开启了该配置的Redis服务器在启动后成为从节点。该注释默认注释掉，即Redis服务器默认都是主节点。

2)  **repl-timeout 60**：与各个阶段主从节点连接超时判断有关，见前面的介绍。



#### （2）主节点相关配置

1)  **repl-diskless-sync no**：作用于全量复制阶段，控制主节点是否使用diskless复制（无盘复制）。所谓diskless复制，是指在全量复制时，主节点不再先把数据写入RDB文件，而是直接写入slave的socket中，整个过程中不涉及硬盘；diskless复制在磁盘IO很慢而网速很快时更有优势。需要注意的是，截至Redis3.0，diskless复制处于实验阶段，默认是关闭的。

2)  **repl-diskless-sync-delay 5**：该配置作用于全量复制阶段，当主节点使用diskless复制时，该配置决定主节点向从节点发送之前停顿的时间，单位是秒；只有当diskless复制打开时有效，默认5s。

> 之所以设置停顿时间，是基于以下两个考虑：
>
> (1)向slave的socket的传输一旦开始，新连接的slave只能等待当前数据传输结束，才能开始新的数据传输
>
> (2)多个从节点有较大的概率在短时间内建立主从复制。

3)  **client-output-buffer-limit slave 256MB 64MB 60**：与全量复制阶段主节点的缓冲区大小有关，见前面的介绍。

4)  **repl-disable-tcp-nodelay no**：与命令传播阶段的延迟有关，见前面的介绍。

5)  **masterauth <master-password>**：与连接建立阶段的身份验证有关，见前面的介绍。

6)  **repl-ping-slave-period** 10：与命令传播阶段主从节点的超时判断有关，见前面的介绍。

7)  **repl-backlog-size** 1mb：复制积压缓冲区的大小，见前面的介绍。

8)  **repl-backlog-ttl 3600**：当主节点没有从节点时，复制积压缓冲区保留的时间，这样当断开的从节点重新连进来时，可以进行全量复制；默认3600s。如果设置为0，则永远不会释放复制积压缓冲区。

9)  **min-slaves-to-write** 3与**min-slaves-max-lag** 10：规定了主节点的最小从节点数目，及对应的最大延迟，见前面的介绍。



#### （3）从节点相关配置

**1)  slave-serve-stale-data yes**：与从节点数据陈旧时是否响应客户端命令有关，见前面的介绍。

2)  **slave-read-only yes**：从节点是否只读；默认是只读的。由于从节点开启写操作容易导致主从节点的数据不一致，因此该配置尽量不要修改。



### **6. 单机内存大小限制**

在 [深入学习Redis（2）：持久化](http://mp.weixin.qq.com/s?__biz=MzA5ODM5MDU3MA==&mid=2650863933&idx=1&sn=90dd7a349ef0ffe47347a97879789552&chksm=8b661e78bc11976ec8b18cc91c55aae252e5c42c457ce0df42fcd0b70b7f2df23d7b23c4490e&scene=21#wechat_redirect) 一文中，讲到了fork操作对Redis单机内存大小的限制。实际上在Redis的使用中，限制单机内存大小的因素非常之多，下面总结一下在主从复制中，单机内存过大可能造成的影响：



（1）切主：当主节点宕机时，一种常见的容灾策略是将其中一个从节点提升为主节点，并将其他从节点挂载到新的主节点上，此时这些从节点只能进行全量复制；如果Redis单机内存达到10GB，一个从节点的同步时间在几分钟的级别；如果从节点较多，恢复的速度会更慢。如果系统的读负载很高，而这段时间从节点无法提供服务，会对系统造成很大的压力。



（2）从库扩容：如果访问量突然增大，此时希望增加从节点分担读负载，如果数据量过大，从节点同步太慢，难以及时应对访问量的暴增。



（3）缓冲区溢出：（1）和（2）都是从节点可以正常同步的情形（虽然慢），但是如果数据量过大，导致全量复制阶段主节点的复制缓冲区溢出，从而导致复制中断，则主从节点的数据同步会全量复制->复制缓冲区溢出导致复制中断->重连->全量复制->复制缓冲区溢出导致复制中断……的循环。



（4）超时：如果数据量过大，全量复制阶段主节点fork+保存RDB文件耗时过大，从节点长时间接收不到数据触发超时，主从节点的数据同步同样可能陷入全量复制->超时导致复制中断->重连->全量复制->超时导致复制中断……的循环。



此外，主节点单机内存除了绝对量不能太大，其占用主机内存的比例也不应过大：最好只使用50%-65%的内存，留下30%-45%的内存用于执行bgsave命令和创建复制缓冲区等。



### **7. info Replication**

在Redis客户端通过info Replication可以查看与复制相关的状态，对于了解主从节点的当前状态，以及解决出现的问题都会有帮助。

主节点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafIjlmxp77fSpf9FzNY1OyibcMR4QTQH8R7SAZ2cNxCfpS4fXlLXiaicDKeg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

从节点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B0AcrUtIh4Zk3WkTWQicSafI9KTSRwr2ZsgKIibpzWtXpicib3QjHTWsIK43xLricOqDIrPD7WNUKGLpiaA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



对于从节点，上半部分展示的是其作为从节点的状态，从connectd_slaves开始，展示的是其作为潜在的主节点的状态。

info Replication中展示的大部分内容在文章中都已经讲述，这里不再详述。



## **七、总结**

下面回顾一下本文的主要内容：

1、主从复制的作用：宏观的了解主从复制是为了解决什么样的问题，即数据冗余、故障恢复、读负载均衡等。

2、主从复制的操作：即slaveof命令。

3、主从复制的原理：主从复制包括了连接建立阶段、数据同步阶段、命令传播阶段；其中数据同步阶段，有全量复制和部分复制两种数据同步方式；命令传播阶段，主从节点之间有PING和REPLCONF ACK命令互相进行心跳检测。

4、应用中的问题：包括读写分离的问题（数据不一致问题、数据过期问题、故障切换问题等）、复制超时问题、复制中断问题等，然后总结了主从复制相关的配置，其中repl-timeout、client-output-buffer-limit slave等对解决Redis主从复制中出现的问题可能会有帮助。

主从复制虽然解决或缓解了数据冗余、故障恢复、读负载均衡等问题，但其缺陷仍很明显：故障恢复无法自动化；写操作无法负载均衡；存储能力受到单机的限制；这些问题的解决，需要哨兵和集群的帮助。



# Redist 哨兵

## **一、作用和架构**

### **1.  作用**

在介绍哨兵之前，首先从宏观角度回顾一下Redis实现高可用相关的技术。它们包括：持久化、复制、哨兵和集群，其主要作用和解决的问题是：

- **持久化**：持久化是最简单的高可用方法(有时甚至不被归为高可用的手段)，主要作用是数据备份，即将数据存储在硬盘，保证数据不会因进程退出而丢失。
- **复制**：复制是高可用Redis的基础，哨兵和集群都是在复制基础上实现高可用的。复制主要实现了数据的多机备份，以及对于读操作的负载均衡和简单的故障恢复。`缺陷：故障恢复无法自动化；写操作无法负载均衡；存储能力受到单机的限制`。
- **哨兵**：在复制的基础上，哨兵实现了自动化的故障恢复。`缺陷：写操作无法负载均衡；存储能力受到单机的限制`。
- **集群**：通过集群，Redis解决了写操作无法负载均衡，以及存储能力受到单机限制的问题，实现了较为完善的高可用方案。



下面说回哨兵。

Redis Sentinel，即Redis哨兵，在Redis 2.8版本开始引入。哨兵的核心功能是**主节点的自动故障转移**。下面是Redis官方文档对于哨兵功能的描述：

1. 集群监控（Monitoring）：负责监控redis master和slave进程是否正常工作 
2. 消息通知（Notification）：如果某个redis实例有故障，那么哨兵负责发送消息作为报警通知给管理员 
3. 故障转移（Automatic failover）：如果master node挂掉了，会自动转移到slave node上 
4. 配置中心（Configuration provider）：如果故障转移发生了，通知client客户端新的master地址



其中，监控和自动故障转移功能，使得哨兵可以及时发现主节点故障并完成转移；而配置提供者和通知功能，则需要在与`客户端`的交互中才能体现。

> 这里对“**客户端**”一词在文章中的用法做一个说明：在前面的文章中，只要通过API访问redis服务器，都会称作客户端，包括redis-cli、Java客户端Jedis等；为了便于区分说明，本文中的客户端并不包括redis-cli，而是比redis-cli更加复杂：redis-cli使用的是redis提供的底层接口，而客户端则对这些接口、功能进行了封装，以便充分利用哨兵的配置提供者和通知功能。



### **2.  架构**

典型的哨兵架构图如下所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6ex0zS8cKUJEHzQbUu3TNicibedlXNCBsicXP9b1vlspJQVLIPq2y8XlicQDg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



它由两部分组成，哨兵节点和数据节点：

- 哨兵节点：哨兵系统由一个或多个哨兵节点组成，哨兵节点是特殊的redis节点，不存储数据。
- 数据节点：主节点和从节点都是数据节点。



## **二、部署**



这一部分将部署一个简单的哨兵系统，包含1个主节点、2个从节点和3个哨兵节点。方便起见：所有这些节点都部署在一台机器上（局域网IP：192.168.92.128），使用端口号区分；节点的配置尽可能简化。

### **1.  部署主从节点**



哨兵系统中的主从节点，与普通的主从节点配置是一样的，并不需要做任何额外配置。下面分别是主节点（port=6379）和2个从节点（port=6380/6381）的配置文件，配置都比较简单，不再详述。



> \#redis-6379.conf
>
> port 6379
>
> daemonize yes
>
> logfile "6379.log"
>
> dbfilename "dump-6379.rdb"
>
>  
>
> \#redis-6380.conf
>
> port 6380
>
> daemonize yes
>
> logfile "6380.log"
>
> dbfilename "dump-6380.rdb"
>
> slaveof 192.168.92.128 6379
>
>  
>
> \#redis-6381.conf
>
> port 6381
>
> daemonize yes
>
> logfile "6381.log"
>
> dbfilename "dump-6381.rdb"
>
> slaveof 192.168.92.128 6379



配置完成后，依次启动主节点和从节点：



> redis-server redis-6379.conf
>
> redis-server redis-6380.conf
>
> redis-server redis-6381.conf



节点启动后，连接主节点查看主从状态是否正常，如下图所示：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6ex7pI2fTuftJ28Aw6iczSZsnARAaI8EA2JTAOiak1Cjaqkv3R7GotdkChA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



###  **2.  部署哨兵节点**

哨兵节点本质上是特殊的Redis节点。

3个哨兵节点的配置几乎是完全一样的，主要区别在于端口号的不同（26379/26380/26381），下面以26379节点为例介绍节点的配置和启动方式；配置部分尽量简化，更多配置会在后面介绍。

> \#sentinel-26379.conf
>
> port 26379
>
> daemonize yes
>
> logfile "26379.log"
>
> sentinel monitor mymaster 192.168.92.128 6379 2

其中，sentinel monitor mymaster 192.168.92.128 6379 2 配置的含义是：该哨兵节点监控192.168.92.128:6379这个主节点，该主节点的名称是mymaster，最后的2的含义与主节点的故障判定有关：至少需要2个哨兵节点同意，才能判定主节点故障并进行故障转移。

哨兵节点的启动有两种方式，二者作用是完全相同的：

> redis-sentinel sentinel-26379.conf
>
> redis-server sentinel-26379.conf --sentinel



按照上述方式配置和启动之后，整个哨兵系统就启动完毕了。可以通过**redis-cli连接哨兵节点进行验证** ` 需要登陆哨兵客户顿 redis-cli -p 26379`，如下图所示：可以看出26379哨兵节点已经在监控mymaster主节点(即192.168.92.128:6379)，并发现了其2个从节点和另外2个哨兵节点。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exP1xeuHAoNAgjePBT8FMIXogELRswCdhs7UAILSCbQaPcb3eJGU3hvA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

此时如果查看哨兵节点的配置文件，会发现一些变化，以26379为例：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exj86dyhOrQo9zibZqZosjKWSm5l5uSrfLdWY59SuHj0MibmVeOY4QDDPw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

其中，dir只是显式声明了数据和日志所在的目录（在哨兵语境下只有日志）；known-slave和known-sentinel显示哨兵已经发现了从节点和其他哨兵；带有**epoch的参数**与配置纪元有关（**配置纪元是一个从0开始的计数器，每进行一次领导者哨兵选举，都会+1**；领导者哨兵选举是故障转移阶段的一个操作，在后文原理部分会介绍）。



### **3.  演示故障转移**

哨兵的4个作用中，配置提供者和通知需要客户端的配合，本文将在下一章介绍客户端访问哨兵系统的方法时详细介绍。这一小节将演示当主节点发生故障时，哨兵的监控和自动故障转移功能。

（1）首先，使用kill命令杀掉主节点：

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exPACBqvzQNQk2yA0Ljp8EYwIOEBoNPcr3XJWndiaMNDo7b0F9u2J0wRw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（2）如果此时立即在哨兵节点中使用**info Sentinel**命令查看，会发现主节点还没有切换过来，因为哨兵发现主节点故障并转移，需要一段时间。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exkPkQNrlRsic0WoPxKwdlZNu22yg9BBuFJrTTibn6ibBVKWpwibmBN5YfoA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

（3）一段时间以后，再次在哨兵节点中执行info Sentinel查看，发现主节点已经切换成6380节点。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exDzu99faP5LTZm6xaHH44xs5fPnNmYqpwaFZRMiaKY1sFkCubXYoXZpg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



但是同时可以发现:

1. 哨兵节点认为新的主节点仍然有2个从节点，这是因为哨兵在将6380切换成主节点的同时，将6379节点置为其从节点；
2. 虽然6379从节点已经挂掉，但是由于哨兵并不会对从节点进行客观下线（其含义将在原理部分介绍），因此认为该从节点一直存在。当6379节点重新启动后，会自动变成6380节点的从节点。下面验证一下。



（4）重启6379节点：可以看到6379节点成为了6380节点的从节点。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6ex53v8CRXd7tBKk2ptKzz08Zoib9KAMXICibqCFzniaovS1QQqGFHvRuKDA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



（5）在故障转移阶段，哨兵和主从节点的配置文件都会被改写。

1. 对于主从节点，主要是slaveof配置的变化：新的主节点没有了slaveof配置，其从节点则slaveof新的主节点。

2. 对于哨兵节点，除了主从节点信息的变化，纪元(epoch)也会变化，下图中可以看到纪元相关的参数都+1了。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exWAjkDBmMDIyXVQ9Z5SAXZ1YdhFPiaTiblJwz35SqzzwWPG4QLGWSoNaA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



###  **4.  哨兵的几个特点**

哨兵系统的搭建过程，有几点需要注意：

（1）哨兵系统中的主从节点，与普通的主从节点并没有什么区别，故障发现和转移是由哨兵来控制和完成的。

（2）哨兵节点本质上是redis节点。

（3）每个哨兵节点，**只需要配置监控主节点，****便可以自动发现其他的哨兵节点和从节点**。

（4）在哨兵节点启动和故障转移阶段，**各个节点的配置文件会被重写(config rewrite)**。

（5）本章的例子中，一个哨兵只监控了一个主节点；实际上，一个哨兵可以监控多个主节点，通过配置多条sentinel monitor即可实现。



## **三、客户端访问哨兵系统**

上一小节演示了哨兵的两大作用：监控和自动故障转移，本小节则结合客户端演示哨兵的另外两个作用：配置提供者和通知。



### **1.  代码示例**

在介绍客户端的原理之前，先以Java客户端Jedis为例，演示一下使用方法：下面代码可以连接我们刚刚搭建的哨兵系统，并进行各种读写操作（代码中只演示如何连接哨兵，异常处理、资源关闭等未考虑）。

![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exicl7FqPLvrGIxWic9d1YgRc9jhMSOMAps5hG3Fw8ypzJYjpwjFagFFWg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



### **2.  客户端原理**



Jedis客户端对哨兵提供了很好的支持。如上述代码所示，我们只需要向Jedis提供哨兵节点集合和masterName，构造JedisSentinelPool对象；然后便可以像使用普通redis连接池一样来使用了：通过**pool.getResource()**获取连接，执行具体的命令。



在整个过程中，我们的代码不需要显式的指定主节点的地址，就可以连接到主节点；代码中对故障转移没有任何体现，就可以在哨兵完成故障转移后自动的切换主节点。之所以可以做到这一点，是因为在JedisSentinelPool的构造器中，进行了相关的工作；主要包括以下两点：

（1）**遍历哨兵节点，获取主节点信息**：遍历哨兵节点，通过其中一个哨兵节点+masterName获得主节点的信息；该功能是通过调用哨兵节点的**sentinel get-master-addr-by-name**命令实现，该命令示例如下：



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exQibFjDibwtjoT5JVpsliaiaE1ibumTdDfcpTBpyjoV0qkFm4dbvicchbA6uQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



一旦获得主节点信息，停止遍历（因此一般来说遍历到第一个哨兵节点，循环就停止了）。



（2）**增加对哨兵的监听**：这样当发生故障转移时，客户端便可以收到哨兵的通知，从而完成主节点的切换。具体做法是：利用redis提供的发布订阅功能，为每一个哨兵节点开启一个单独的线程，订阅哨兵节点的+switch-master频道，当收到消息时，重新初始化连接池。



### **3.  总结**



通过客户端原理的介绍，可以加深对哨兵功能的理解：

（1）**配置提供者**：客户端可以通过哨兵节点+masterName获取主节点信息，在这里哨兵起到的作用就是配置提供者。

> 需要注意的是，哨兵只是配置提供者，而不是代理。二者的区别在于：如果是配置提供者，客户端在通过哨兵获得主节点信息后，会直接建立到主节点的连接，后续的请求(如set/get)会直接发向主节点；如果是代理，客户端的每一次请求都会发向哨兵，哨兵再通过主节点处理请求。



举一个例子可以很好的理解哨兵的作用是配置提供者，而不是代理。在前面部署的哨兵系统中，将哨兵节点的配置文件进行如下修改：

> sentinel monitor mymaster 192.168.92.128 6379 2
>
> 改为
>
> sentinel monitor mymaster 127.0.0.1 6379 2



然后，将前述客户端代码在局域网的另外一台机器上运行，会发现客户端无法连接主节点；这是因为哨兵作为配置提供者，客户端通过它查询到主节点的地址为127.0.0.1:6379，客户端会向127.0.0.1:6379建立redis连接，自然无法连接。如果哨兵是代理，这个问题就不会出现了。



（2）**通知：**哨兵节点在故障转移完成后，会将新的主节点信息发送给客户端，以便客户端及时切换主节点。



## **四、哨兵的查询命令**



前面介绍了哨兵部署、使用的基本方法，本部分介绍哨兵实现的基本原理。



### **1.  哨兵节点支持的命令**

哨兵节点作为运行在特殊模式下的redis节点，其支持的命令与普通的redis节点不同。在运维中，我们可以通过这些命令查询或修改哨兵系统；不过更重要的是，哨兵系统要实现故障发现、故障转移等各种功能，离不开哨兵节点之间的通信，而通信的很大一部分是通过哨兵节点支持的命令来实现的。下面介绍哨兵节点支持的主要命令。

（1）基础查询：通过这些命令，可以查询哨兵系统的拓扑结构、节点信息、配置信息等。

- info sentinel：获取监控的所有主节点的基本信息
- sentinel masters：获取监控的所有主节点的详细信息
- sentinel master mymaster：获取监控的主节点mymaster的详细信息
- sentinel slaves mymaster：获取监控的主节点mymaster的从节点的详细信息
- sentinel sentinels mymaster：获取监控的主节点mymaster的哨兵节点的详细信息
- sentinel get-master-addr-by-name mymaster：获取监控的主节点mymaster的地址信息，前文已有介绍
- sentinel is-master-down-by-addr：哨兵节点之间可以通过该命令询问主节点是否下线，从而对是否客观下线做出判断



（2）增加/移除对主节点的监控

sentinel monitor mymaster2 192.168.92.128 16379 2：与部署哨兵节点时配置文件中的sentinel monitor功能完全一样，不再详述

sentinel remove mymaster2：取消当前哨兵节点对主节点mymaster2的监控



（3）强制故障转移

sentinel failover mymaster：该命令可以强制对mymaster执行故障转移，即便当前的主节点运行完好；例如，如果当前主节点所在机器即将报废，便可以提前通过failover命令进行故障转移。



### **2.  基本原理**



关于哨兵的原理，关键是了解以下几个概念。



（1）定时任务：每个哨兵节点维护了3个定时任务。定时任务的功能分别如下：通过向主从节点发送info命令获取最新的主从结构；通过发布订阅功能获取其他哨兵节点的信息；通过向其他节点发送ping命令进行心跳检测，判断是否下线。



（2）主观下线：在心跳检测的定时任务中，如果其他节点超过一定时间没有回复，哨兵节点就会将其进行主观下线。顾名思义，主观下线的意思是一个哨兵节点“主观地”判断下线；与主观下线相对应的是客观下线。



（3）客观下线：哨兵节点在对主节点进行主观下线后，会通过sentinel is-master-down-by-addr命令询问其他哨兵节点该主节点的状态；如果判断主节点下线的哨兵数量达到一定数值，则对该主节点进行客观下线。



需要特别注意的是，客观下线是主节点才有的概念；如果从节点和哨兵节点发生故障，被哨兵主观下线后，不会再有后续的客观下线和故障转移操作。



（4）选举领导者哨兵节点：当主节点被判断客观下线以后，各个哨兵节点会进行协商，选举出一个领导者哨兵节点，并由该领导者节点对其进行故障转移操作。



监视该主节点的所有哨兵都有可能被选为领导者，选举使用的算法是Raft算法；Raft算法的基本思路是先到先得：即在一轮选举中，哨兵A向B发送成为领导者的申请，如果B没有同意过其他哨兵，则会同意A成为领导者。选举的具体过程这里不做详细描述，一般来说，哨兵选择的过程很快，谁先完成客观下线，一般就能成为领导者。



（5）故障转移：选举出的领导者哨兵，开始进行故障转移操作，该操作大体可以分为3个步骤：



- 在从节点中选择新的主节点：选择的原则是，首先过滤掉不健康的从节点；然后选择优先级最高的从节点(由slave-priority指定)；如果优先级无法区分，则选择复制偏移量最大的从节点；如果仍无法区分，则选择runid最小的从节点。
- 更新主从状态：通过slaveof no one命令，让选出来的从节点成为主节点；并通过slaveof命令让其他节点成为其从节点。
- 将已经下线的主节点(即6379)设置为新的主节点的从节点，当6379重新上线后，它会成为新的主节点的从节点。



通过上述几个关键概念，可以基本了解哨兵的工作原理。为了更形象的说明，下图展示了领导者哨兵节点的日志，包括从节点启动到完成故障转移。



![img](https://mmbiz.qpic.cn/mmbiz_png/DmibiaFiaAI4B3CbdsQ759icniceK4UJHr6exNFicbiaKAJ2YnHU1GajlWRzzpwZfQvXkQxsr4knSibXQYr437DXpMj62Q/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



## **五、配置与实践建议**



### **1.  配置**

下面介绍与哨兵相关的几个配置。

（1） sentinel monitor {masterName} {masterIp} {masterPort} {quorum}

sentinel monitor是哨兵最核心的配置，在前文讲述部署哨兵节点时已说明，其中：masterName指定了主节点名称，masterIp和masterPort指定了主节点地址，quorum是判断主节点客观下线的哨兵数量阈值：当判定主节点下线的哨兵数量达到quorum时，对主节点进行客观下线。建议取值为哨兵数量的一半加1。



（2） **sentinel down-after-milliseconds** {masterName} {time}

sentinel down-after-milliseconds与主观下线的判断有关：**哨兵使用ping命令对其他节点进行心跳检测，如果其他节点超过down-after-milliseconds配置的时间没有回复，哨兵就会将其进行主观下线**。该配置对主节点、从节点和哨兵节点的主观下线判定都有效。



down-after-milliseconds的默认值是30000，即30s；可以根据不同的网络环境和应用要求来调整：值越大，对主观下线的判定会越宽松，好处是误判的可能性小，坏处是故障发现和故障转移的时间变长，客户端等待的时间也会变长。例如，如果应用对可用性要求较高，则可以将值适当调小，当故障发生时尽快完成转移；如果网络环境相对较差，可以适当提高该阈值，避免频繁误判。



（3） **sentinel parallel-syncs** {masterName} {number}

sentinel parallel-syncs与故障转移之后从节点的复制有关：它规定了每次向新的主节点发起复制操作的从节点个数。例如，假设主节点切换完成之后，有3个从节点要向新的主节点发起复制；如果parallel-syncs=1，则从节点会一个一个开始复制；如果parallel-syncs=3，则3个从节点会一起开始复制。



parallel-syncs取值越大，从节点完成复制的时间越快，但是对主节点的网络负载、硬盘负载造成的压力也越大；应根据实际情况设置。例如，如果主节点的负载较低，而从节点对服务可用的要求较高，可以适量增加parallel-syncs取值。parallel-syncs的默认值是1。



（4） **sentinel failover-timeout** {masterName} {time}

sentinel failover-timeout与故障转移超时的判断有关，但是该参数不是用来判断整个故障转移阶段的超时，而是其几个子阶段的超时，例如如果主节点晋升从节点时间超过timeout，或从节点向新的主节点发起复制操作的时间(不包括复制数据的时间)超过timeout，都会导致故障转移超时失败。

failover-timeout的默认值是180000，即180s；如果超时，则下一次该值会变为原来的2倍。



### **2.  实践建议**



（1）哨兵节点的数量应不止一个，一方面增加哨兵节点的冗余，避免哨兵本身成为高可用的瓶颈；另一方面减少对下线的误判。此外，这些不同的哨兵节点应部署在不同的物理机上。



（2）哨兵节点的数量应该是奇数，便于哨兵通过投票做出“决策”：领导者选举的决策、客观下线的决策等。



（3）各个哨兵节点的配置应一致，包括硬件、参数等；此外，所有节点都应该使用ntp或类似服务，保证时间准确、一致。



（4）哨兵的配置提供者和通知客户端功能，需要客户端的支持才能实现，如前文所说的Jedis；如果开发者使用的库未提供相应支持，则可能需要开发者自己实现。



（5）当哨兵系统中的节点在docker（或其他可能进行端口映射的软件）中部署时，应特别注意端口映射可能会导致哨兵系统无法正常工作，因为哨兵的工作基于与其他节点的通信，而docker的端口映射可能导致哨兵无法连接到其他节点。例如，哨兵之间互相发现，依赖于它们对外宣称的IP和port，如果某个哨兵A部署在做了端口映射的docker中，那么其他哨兵使用A宣称的port无法连接到A。



## **六、总结**



本文首先介绍了哨兵的作用：监控、故障转移、配置提供者和通知；然后讲述了哨兵系统的部署方法，以及通过客户端访问哨兵系统的方法；再然后简要说明了哨兵实现的基本原理；最后给出了关于哨兵实践的一些建议。



在主从复制的基础上，哨兵引入了主节点的自动故障转移，进一步提高了Redis的高可用性；但是哨兵的缺陷同样很明显：哨兵无法对从节点进行自动故障转移，在读写分离场景下，从节点故障会导致读服务不可用，需要我们对从节点做额外的监控、切换操作。



此外，哨兵仍然没有解决写操作无法负载均衡、及存储能力受到单机限制的问题；这些问题的解决需要使用集群，我将在后面的文章中介绍，欢迎关注。



# Redis-Cluster集群

> redis最开始使用主从模式做集群，若master宕机需要手动配置slave转为master；
>
> 后来为了高可用提出来**哨兵**模式，该模式下有一个哨兵监视master和slave，若master宕机可自动将slave转为master，但它也有一个问题，就是不能动态扩充；所以在3.x提出cluster集群模式。

## **一、redis-cluster设计**

Redis-Cluster采用无中心结构，每个节点保存数据和整个集群状态,每个节点都和其他所有节点连接。

![img](https://upload-images.jianshu.io/upload_images/12185313-0f55e1cc574cae70.png?imageMogr2/auto-orient/strip|imageView2/2/w/275/format/webp)



其结构特点：
 1、所有的redis节点彼此互联(PING-PONG机制),内部使用二进制协议优化传输速度和带宽。
 2、节点的fail是通过集群中超过半数的节点检测失效时才生效。
 3、客户端与redis节点直连,不需要中间proxy层.客户端不需要连接集群所有节点,连接集群中任何一个可用节点即可。
 4、redis-cluster把所有的物理节点映射到[0-16383]slot上（不一定是平均分配）,cluster 负责维护node<->slot<->value。
 5、Redis集群预分好16384个桶，当需要在 Redis 集群中放置一个 key-value 时，根据 CRC16(key) mod 16384的值，决定将一个key放到哪个桶中。



> Redis 3.0之后，节点之间通过去中心化的方式，提供了完整的 sharding、replication（复制机制仍使用原有机制，并且具备感知主备的能力）、failover 解决方案，称为 Redis Cluster。即：将 proxy/sentinel 的工作融合到了普通Redis节点里。后面将介绍 Redis Cluster 这种模式下，水平拆分、故障转移等需求的实现方式。



## 拓扑结构

1. 一个 Redis Cluster 由多个Redis节点组成。不同的节点组服务的数据无交集，**每个节点对应数据 sharding 的一个分片**。
2. **节点组内部分为主备 2 类**，对应前面叙述的 master 和 slave。**两者数据准实时一致**，通过异步化的主备复制机制保证。
3. **一个节点组有且仅有一个master，同时有0到多个slave**。**只有master对外提供写服务，读服务可由 master/slave 提供**。如下所示：

![img](assets%5C697e3aa0-e08a-4662-bf61-76392ececf34.jpg)

上图中，key-value 全集被分成了 5 份，5个 slot（实际上Redis Cluster有 16384 [0-16383] 个slot，每个节点服务一段区间的slot，这里面仅仅举例）。A和B为master节点，对外提供写服务。分别负责 1/2/3 和 4/5 的slot。A/A1 和B/B1/B2 之间通过主备复制的方式同步数据。

上述的5个节点，两两通过 **Redis Cluster Bus** 交互，相互交换如下的信息：

1、数据分片（slot）和节点的对应关系；

2、集群中每个节点可用状态；

3、集群结构发生变更时，通过一定的协议对配置信息达成一致。数据分片的迁移、主备切换、单点 master 的发现和其发生主备关系变更等，都会导致集群结构变化。

4、publish/subscribe（发布订阅）功能，在Cluster版内部实现所需要交互的信息。



Redis Cluster Bus 通过单独的端口进行连接，由于Bus是节点间的内部通信机制，交互的是字节序列化信息。相对Client的字符序列化来说，效率较高。

Redis Cluster是一个去中心化的分布式实现方案，客户端和集群中任一节点连接，然后通过后面的交互流程，逐渐的得到全局的数据分片映射关系。



## 配置的一致性

对于去中心化的实现，集群的拓扑结构并不保存在单独的配置节点上，后者的引入同样会带来新的一致性问题。那么孤立的节点间，如何对集群的拓扑达成一致，是Redis Cluster配置机制要解决的问题。Redis Cluster通过引入2个自增的**Epoch**变量，来**使得集群配置在各个节点间最终达成一致**。

### 1、配置信息数据结构

Redis Cluster 中的每个节点都保存了集群的配置信息，并且存储在 clusterState 中，结构如下：

![img](http://moguhu.com/images/58a6c17e-3f7f-478e-be3b-0bc526031fcd.png)

上图的各个变量语义如下:

clusterState 记录了从集群中某个节点视角，来看集群配置状态；

currentEpoch 表示整个集群中最大的版本号，集群信息每变更一次，改版本号都会自增。

nodes 是一个列表，包含了本节点所感知的，集群所有节点的信息（clusterNode），也包含自身的信息。

clusterNode 记录了每个节点的信息，其中包含了节点本身的版本 Epoch；自身的信息描述：节点对应的数据分片范围（slot）、为master时的slave列表、为slave时的master等。

每个节点包含一个全局唯一的 NodeId。

当集群的数据分片信息发生变更（数据在节点间迁移时），Redis Cluster 仍然保持对外服务。

当集群中某个master出现宕机时，Redis Cluster 会自动发现，并触发故障转移的操作。会将master的某个slave晋升为新的 master。

由此可见，每个节点都保存着Node视角的集群结构。它描述了数据的分片方式，节点主备关系，并通过Epoch 作为版本号实现集群结构信息的一致性，同时也控制着数据迁移和故障转移的过程。

### 2、信息交互

去中心化的架构不存在统一的配置中心。在Redis Cluster中，这个配置信息交互通过 Redis Cluster Bus 来完成（独立端口）。Redis Cluster Bus 上交互的信息结构如下：

![img](http://moguhu.com/images/824cc413-5830-42b9-9390-166040af5573.png)

clusterMsg 中的type指明了消息的类型，配置信息的一致性主要依靠 PING/PONG。每个节点向其他节点频繁的周期性的发送PING/PONG消息。对于消息体中的 Gossip 部分，包含了sender/receiver 所感知的其他节点信息，接受者根据这些Gossip 跟新对集群的认识。

对于大规模的集群，如果每次PING/PONG 都携带着所有节点的信息，则网络开销会很大。此时Redis Cluster 在每次PING/PONG，只包含了随机的一部分节点信息。由于交互比较频繁，短时间的几次交互之后，集群的状态也会达成一致。

### 3、一致性的达成

当Cluster 结构不发生变化时，各个节点通过gossip 协议在几轮交互之后，便可以得知Cluster的结构信息，达到一致性的状态。但是当集群结构发生变化时（故障转移/分片迁移等），优先得知变更的节点通过Epoch变量，将自己的最新信息扩散到Cluster，并最终达到一致。

clusterNode 的Epoch描述的单个节点的信息版本；

clusterState 的currentEpoch 描述的是集群信息的版本，它可以辅助Epoch 的自增生成。因为currentEpoch 是维护在每个节点上的，在集群结构发生变更时，Cluster 在一定的时间窗口控制更新规则，来保证每个节点的 currentEpoch 都是最新的。

更新规则如下：

1、当某个节点率先知道了变更时，将自身的 currentEpoch 自增，并使之成为集群中的最大值。再用自增后的 currentEpoch 作为新的 Epoch 版本；

2、当某个节点收到了比自己大的 currentEpoch 时，更新自己的 currentEpoch；

3、当收到的 Redis Cluster Bus 消息中的 某个节点的 Epoch > 自身的 时，将更新自身的内容；

4、当Redis Cluster Bus 消息中，包含了自己没有的节点时，将其加入到自身的配置中。

上述的规则保证了信息的更新都是单向的，最终朝着 Epoch 更大的信息收敛。同时 Epoch 也随着 currentEpoch 的增加而增加，最终将各节点信息趋于稳定。



## sharding

不同节点分组服务于相互无交集的分片（sharding），Redis Cluster 不存在单独的 proxy 或配置服务器，所以需要将客户端路由到目标的分片。

### 1、数据分片（slot）

Redis Cluster 将所有的数据划分为16384 [0-16383] 个分片，每个分片负责其中一部分。每一条数据（key-value）根据key值通过数据分布算法（一致性哈希）映射到16384 个slot中的一个。数据分布算法为：

```
slotId = crc16(key) % 16384
```

![img](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

客户端根据 slotId 决定将请求路由到哪个Redis 节点。Cluster 不支持跨节点的单命令，如：sinterstore，如果涉及的 2 个key对应的slot 在不同的 Node，则执行失败。通常Redis的key都是带有业务意义的，如：Product:Trade:20180890310921230001、Product:Detail:20180890310921230001。当在集群中存储时，上述同一商品的交易和详情可能会存储在不同的节点上，进而对于这2个key 不能以原子的方式操作。为此，Redis 引入了 HashTag的概念，使得数据分布算法可以根据key 的某一部分进行计算，让相关的2 条记录落到同一个数据分片。如：

```
商品交易记录key：Product:Trade:{20180890310921230001}
商品详情记录key：Product:Detail:{20180890310921230001}
```



Redis 会根据 {} 之间的字符串作为数据分布式算法的输入。

### 2、客户端的路由

Redis Cluster 的客户端相比单机Redis 需要具备路由语义的识别能力，且具备一定的路由缓存能力。当Client 访问的key 不在当前Redis 节点的 slots 中，Redis 会返回给Client 一个 moved命令。并告知其正确的路由信息，如下所示：

![img](http://moguhu.com/images/667468a5-b7ea-4420-b0d0-96d45382732c.jpg)

当Client 接收到moved 后，再次请求新的Redis时，此时Cluster 的结构又可能发生了变化（slot 迁移）。此时有可能再次返回moved 。Client 会根据 moved响应，更新其内部的路由缓存信息，以便后续的操作直接找到正确的节点，减少交互次数。

当Cluster 在 slot迁移过程中时，可以通过 ask命令控制客户端的路由，如下所示：

![img](http://moguhu.com/images/92062e2c-523f-44f2-a363-a6cb2c0cabb5.jpg)

上图中，Source节点的slot 需要迁移到 Target节点上，此时如果客户端已经完成迁移的 key，节点将相应ask 告知客户端想目标节点重试。

**ask命令和 moved命令的不同在于：**

> 1、moved 会更新 Client数据路由，ask 只是重定向新节点，但是后续的相同 slot 仍会路由到旧节点
>
> 2、slot 在迁移过程中，如果slot已经确定迁移，返回moved；如果正在迁移中，返回ask。
>
> 迁移的过程可能会持续一段时间，这段时间某个slot 的数据，同时可能存在于新旧 2 个节点。由于move 操作会使Client 的路由缓存变更，如果新旧节点对于迁移中的slot 所有key 都回应moved，客户端的路由缓存会频繁变更。因此引入ask 类型消息，将重定向和路由缓存分离。



### 3、分片的迁移

在一个稳定的 Redis Cluster 中，每个 slot 对应的节点都是确定的。在某些情况下，节点和分片需要变更：

1、新的节点作为master加入；

2、某个节点分组需要下线；

3、负载不均衡需要调整 slot 分布。

此时需要进行分片的迁移，迁移的触发和过程控制由外部系统完成。Redis Cluster 只提供迁移过程中需要的原语，包含下面 2 种：

```
节点迁移状态设置：迁移前标记源/目标节点。
key迁移的原子化命令：迁移的具体步骤。
```

![img](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

下面的Demo会介绍slot 1 从节点A 迁移到B的过程。

![img](http://moguhu.com/images/99d4cd88-bd5b-4598-bbe8-1df970debf45.jpg)

1、向节点B发送状态变更命令，将B的对应slot 状态置为importing。

2、向节点A发送状态变更命令，将A对应的slot 状态置为migrating。

3、针对A上的 slot 的所有 key，分别向 A 发送 migrate 命令，告知 A 将对应的key 迁移到 B。

当A节点的状态置为 migrating 后，表示对应的slot 正在从 A 迁出，为保证该 slot 数据的一致性。A 此时提供的写服务和通常状态下有所区别，对于某个迁移中的 slot：

```
如果Client 访问的key 尚未迁出，则正常的处理该key；

如果key已经迁出或者key不存在，则回复Client ASK 信息让其跳转到B处理；
```

![img](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

当节点B 状态变成 importing 后，表示对应的 slot 正在向 B 迁入。即使 B 能对外提供该slot 的读写服务，但是和通常情况下有所区别：

```
当Client的访问不是从ask 跳转的，说明Client 还不知道迁移。有可能操作了尚未迁移完成的，处在A上面的key，如果这个key 在A上被修改了，则后续会产生冲突。

所以对于该slot 上所有非ask 跳转的操作，B不会进行操作，而是通过moved 让Client 跳转至A执行。
```

![img](data:image/gif;base64,R0lGODlhAQABAPABAP///wAAACH5BAEKAAAALAAAAAABAAEAAAICRAEAOw==)

这样的状态控制，保证了同一个key 在迁移之前总是在源节点执行。迁移后总是在目标节点执行，从而杜绝了双写的冲突。迁移过程中，新增加的key 会在目标节点执行，源节点不会新增key。使得迁移有界限，可以在某个确定的时刻结束。

单个key 的迁移过程可以通过原子化的migrate 命令完成。对于 A/B 的slave 节点，是通过主备复制，从而达到增删数据。

当所有key 迁移完成后，Client 通过 cluster setslot 命令设置 B 的分片信息，从而包含了迁入的 slot。设置过程中会让 Epoch自增，并且是Cluster 中的最新值。然后通过相互感知，传播到Cluster 中的其他节点。

failover

同Sentinel 一样，Redis Cluster 也具备一套完整的故障发现、故障状态一致性保证、主备切换机制。

1、failover的状态变迁

1）故障发现：当某个master 宕机时，宕机时间如何被集群其他节点感知。

2）故障确认：多个节点就某个master 是否宕机如何达成一致。

3）slave选举：集群确认了某个master 宕机后，如何将它的slave 升级成新的master；如果有多个slave，如何选择升级。

4）集群结构变更：成功选举成为master后，如何让整个集群知道，以更新Cluster 结构信息。

2、故障发现

Redis Cluster 节点间通过 Redis Cluster Bus 两两周期性的 PING/PONG 交互。当某个节点宕机时，其他Node 发出的PING消息没有收到响应，并且超过一定时间（NODE_TIMEOUT）未收到，则认为该节点故障，将其置为 PFAIL状态（Possible Fail）。后续通过Gossip 发出的 PING/PONG 消息中，这个节点的 PFAIL 状态会传播到集群的其他节点。

Redis Cluster 的节点两两通过 TCP 保持 Redis Cluster Bus 连接，当对PING 无反馈时，可能是节点故障，也可能是TCP 链接断开。如果是TCP 断开导致的误报，虽然误报消息会因为其他节点的正常连接被忽略，但是也可以通过一定的方式减少误报。Redis Cluster 通过 预重试机制 排除此类误报：当 NODE_TIMEOUT / 2 过去了，但是还未收到响应，则重新连接重发 PING 消息，如果对端正常，则在很短的时间内就会有响应。

3、故障确认

对于网络分隔的情况，某个节点（B）并没有故障，但是和A 无法连接，但是和 C/D 等其他节点可以正常联通。此时只会有A 将 B 标记为 PFAIL状态，其他节点认为B 正常。此时A 和C/D 等其他节点信息不一致，Redis Cluster 通过故障 确认协议 达成一致。

集群中每个节点都是Gossip 的接收者，A 也会接收到来自其他节点的Gossip 消息，被告知B 是否处于PFAIL 状态。当A收到来气其他master 节点对于 B 的PFAIL 达到一定数量后，会将 B 的 PFAIL状态升级为 FAIL状态。表示B 已经确认为故障态，后面会发起 slave选举流程。

A节点内部的集群信息中，对于B的状态从 PFAIL 到 FAIL 的变迁，如下图所示：

![img](http://moguhu.com/images/d250ee43-841c-4cf8-92f4-1018def9d04c.png)

4、slave选举

上图中，B是A的 master，并且B 已经被集群公认是 FAIL状态了，那么 A 发起竞选，期望成为新的 master。

如果B 有多个slave （A/E/F）都认知到B 处于FAIL 状态了，A/E/F 可能会同时发起竞选。当 B的slave个数 >= 3 时，很有可能产生多轮竞选失败。为了减少冲突的出现，优先级高的slave 更有可能发起竞选，从而提升成功的可能性。这里的优先级是slave的数据最新的程度，数据越新的（最完整的）优先级越高。

slave 通过向其他master 发送 FAILVOER_AUTH_REQUEST 消息发起竞选，master 收到后回复 FAILOVER_AUTH_ACK 消息告知是否同意。slave 发送 FAILOVER_AUTH_REQUEST 前会将 currentEpoch 自增，并将最新的Epoch 带入到 FAILOVER_AUTH_REQUEST 消息中，如果自己未投过票，则回复同意，否则回复拒绝。

5、结构变更通知

当slave 收到 过半的master 同意时，会替代B 成为新的 master。此时会以最新的Epoch 通过PONG 消息广播自己成为master，让Cluster 的其他节点尽快的更新拓扑结构。

当B 恢复可用之后，它仍然认为自己是master，但逐渐的通过 Gossip协议 得知 A 已经替代了自己，然后降级为 A 的 slave。

可用性和性能

Redis Cluster 还提供了一些方法可以提升性能和可用性。

1、Redis Cluster的读写分离

对于读写分离的场景，应用对于某些读请求允许舍弃一定的数据一致性，以换取更高的吞吐量。此时希望将读请求交给slave处理，以分担master的压力。

通过分片映射关系，某个slot 一定对应着一个master节点。Client 通过moved 命令，也只会路由到各个master中。即使Client 将请求直接发送到slave上，也会回复moved 到master去处理。

为此，Redis Cluster 引入了 readonly命令。Client 向 slave发送该命令后，不再moved 到 master处理，而是自己处理，这成为slave的 readonly模式。通过 readwrite命令，可以将 slave的 readonly模式重置。

2、master单点保护

假如 Cluster的初始状态如下所示：

![img](http://moguhu.com/images/48d4649c-e0bc-437e-aad9-6022872870ab.jpg)

上图中A、B两个master 分别有自己的 slave，假设A1 发生宕机，结构变为如下所示：

![img](http://moguhu.com/images/e7dc9902-1db0-4a40-a523-e2cf004948f5.jpg)

此时A 成为了单点，一旦A 再次宕机，将造成不可用。此时Redis Cluster 会把B 的某个slave （如 B1 ）进行副本迁移，变成A的slave。如下所示：

![img](http://moguhu.com/images/72fc0247-f0ca-4a09-a5e2-ea2a80a83d9a.jpg)

这样集群中每个master 至少有一个slave，使得Cluster 具有高可用。集群中只需要保持 2*master+1 个节点，就可以保持任一节点宕机时，故障转移后继续高可用。



# **Reids的6种淘汰策略**

- **noeviction**: 不删除策略, 达到最大内存限制时, 如果需要更多内存, 直接返回错误信息。大多数写命令都会导致占用更多的内存(有极少数会例外。
- **allkeys-lru:**所有key通用; 优先删除最近最少使用(less recently used ,LRU) 的 key。
- **volatile-lru:**只限于设置了 expire 的部分; 优先删除最近最少使用(less recently used ,LRU) 的 key。
- **allkeys-random:**所有key通用; 随机删除一部分 key。
- **volatile-random**: 只限于设置了 **expire** 的部分; 随机删除一部分 key。
- **volatile-ttl**: 只限于设置了 **expire** 的部分; 优先删除剩余时间(time to live,TTL) 短的key。

# Redis的3种过期策略

我们都知道，Redis是key-value数据库，我们可以设置Redis中缓存的key的过期时间。Redis的过期策略就是指当Redis中缓存的key过期了，Redis如何处理。

过期策略通常有以下三种：

- **定时过期**：每个设置过期时间的key都需要创建一个定时器，到过期时间就会立即清除。该策略可以立即清除过期的数据，对内存很友好；但是会占用大量的CPU资源去处理过期的数据，从而影响缓存的响应时间和吞吐量。
- **惰性过期**：只有当访问一个key时，才会判断该key是否已过期，过期则清除。该策略可以最大化地节省CPU资源，却对内存非常不友好。极端情况可能出现大量的过期key没有再次被访问，从而不会被清除，占用大量内存。
- **定期过期**：每隔一定的时间，会扫描一定数量的数据库的expires字典中一定数量的key，并清除其中已过期的key。该策略是前两者的一个折中方案。通过调整定时扫描的时间间隔和每次扫描的限定耗时，可以在不同情况下使得CPU和内存资源达到最优的平衡效果。
   (expires字典会保存所有设置了过期时间的key的过期时间数据，其中，key是指向键空间中的某个键的指针，value是该键的毫秒精度的UNIX时间戳表示的过期时间。键空间是指该Redis集群中保存的所有键。)

Redis中同时使用了**惰性过期**和**定期过期**两种过期策略。



## **RDB对过期key的处理**

过期key对RDB没有任何影响

- 从内存数据库持久化数据到RDB文件
  - 持久化key之前，会检查是否过期，过期的key不进入RDB文件
- 从RDB文件恢复数据到内存数据库
  - 数据载入数据库之前，会对key先进行过期检查，如果过期，不导入数据库（主库情况）

## **AOF对过期key的处理**

过期key对AOF没有任何影响

- 从内存数据库持久化数据到AOF文件：
  - 当key过期后，还没有被删除，此时进行执行持久化操作（该key是不会进入aof文件的，因为没有发生修改命令）
  - 当key过期后，在发生删除操作时，程序会向aof文件追加一条del命令（在将来的以aof文件恢复数据的时候该过期的键就会被删掉）
- AOF重写
  - 重写时，会先判断key是否过期，已过期的key不会重写到aof文件 