## Java内存区域与内存溢出异常

### 1.  Java虚拟机运行时数据区



**线程共享区域**：

 1. 方法区

    >- 定义： 存储已被虚拟机加载的 **类信息、 常量、 静态变量、即时编译器编译后的代码**等数据
    >
    >  > 别名 Non-Heap(非堆) ，与Java堆区分。 
    >  >
    >  > 很多人愿意把方法区称为“永久代”，本质上并不等价
    >
    >- 特点：
    >
    >  	1.  和Java堆一样，不需要连续的内存和可以选择固定大小或者可扩展。 此外还可以选择不实现垃圾收集
    >   	2.  主要**针对常量池的回收和对类型的卸载**
    >
    >- 异常：
    >
    >  OutOfMemoryError:
    >
    >  > 场景： 当方法区无法满足内存分配需求时，将抛出异常
    >
    >

 2. Java堆

    >- 定义： **存放对象实例**
    >
    >- 特点：
    >
    >   1. **垃圾收集器管理的主要区域**，可以称为“GC堆”
    >
    >   2. 划分角度
    >
    >      >1. 从分代收集算法角度：
    >      >
    >      >   Java堆还可以细分为： **新生代**和**老年代** ； 再细致一点的有 **Eden空间，From Survivor空间、To Survivor空间**等
    >      >
    >      >2. 从内存分配的角度：
    >      >
    >      >   线程共享的Java堆可能划分出多个**私有地分配缓冲区**（Thread Local Allocation Buffer,TLAB）
    >      >
    >      >无论怎么划分，都与存放内容无关，存储的仍然是对象实例，划分的目的是为了更好的回收内存或更快的分配内存
    >
    >   3. Java堆可以处于物理上不连续的内存空间中，只要逻辑连续即可。在实现时既可以 固定大小， 也可以是可扩展的。 当前主流的虚拟机都是按照可扩展来实现的（通过 **-Xmx 和-Xms**控制）
    >
    >- 异常：
    >
    >  OutOfMemoryError:
    >
    >  > 场景： 如果堆中没有内存完成实例分配，并且堆无法再扩展时，会抛出异常

    

	3.  运行时常量池：

     > - 定义： **是方法区的一部分**，用于存放编译器生成的**各种字面量和符号引用**
     >
     > - 特点：
     >
     >   	1. 这部分内容将在**类加载后进入方法区时**常量池中存放
     >    	2. **不要求**常量一定只有编译期才能产生，运行期间也可能将新的常量放入池中，比如String类的intern()方法
     >
     > - 异常：
     >
     >   ​	OutOfMemoryError:
     >
     >   >  场景：当常量池无法再申请到内存时，会抛出异常

     

	4.  直接内存

     > - 定义： 使用Native函数库直接分配堆外内存
     >
     > - 特点：
     >
     >   	1. **注意**：直接内存不是虚拟机运行时的数据区部分，不受Java虚拟机规范定义的内存区域
     >    	2. 分配不会受到Java堆大小的限制，但是会受到本机总内存（包括RAM以及SWAP区或者分页文件）大小，以及处理器寻址空间的限制
     >
     > - 异常：
     >
     >   OutOfMemoryError：
     >
     >   > 场景：当设置的各个内存区域总和大雨物理内存限制时，会出现异常

     

**线程隔离区域**：

 1. 虚拟机栈

    >- 定义：Java方法执行的内存模型： **每个方法**在执行同时，会**创建一个栈帧**用于**存储局部变量、操作数栈、动态链接、方法出口**等信息
    >
    >- 特点：
    >
    >  	1. 每个方法从调用到执行完成的过程中，对应这一个栈帧在虚拟机栈中 **入栈** 到**出栈**的过程。
    >   	2. 这里的栈的概念： 表示虚拟机栈中**局部变量表**的部分
    >
    >- **局部变量表**包含：
    >
    >   1. 存储内容：
    >
    >      >  1. 基本数据类型（boolean、 byte、char、 short、 int、 float、 long、 double）
    >      >  2. 对象引用（**reference类型**，可能代表 对象起始的引用指针 或者是代表 当前对象）
    >      >  3. returnAddress类型（指向了一条字节码指令的地址）
    >
    >  	2. 特点：
    >
    >      >1. long和double类型数据占用2个局部变量空间，其余只占用1个
    >      >2. 局部变量的内存空间在编译期间完成分配，换句话说：**当进入一个方法时，这个方法需在帧中分配多大的局部变量空间是完全确定的，方法运行期间不会改变局部变量的大小**
    >
    >- 异常：
    >
    >   1. StackOverflowError:
    >
    >      > 场景： 线程请求的栈深度大于虚拟机所运行的深度
    >
    >  	2. OutOfMemoryError:
    >
    >      > 场景：如果虚拟机可以动态扩展，扩展时无法申请到足够内存，就会抛出该异常

    

 2. 本地方法栈：

    >- 定义：作用和虚拟机栈发挥的相似，**区别**：只不过虚拟机栈为 Java方法（字节码）服务，而本地栈则为虚拟机用到的Native方法服务
    >- 异常： StackOverflowError和OutOfMemoryError

    

 3. 程序计数器

    >- 定义：当前线程所执行的字节码的**行号指示器**
    >
    >- 特点：
    >
    >  1. 每条线程都需要有一个独立的程序计数器，**各条线程之间互不影响，独立存储**
    >
    >     > 原因是： 为了线程切换后能恢复到正确的执行位置，每条线程都需呀一个独立的程序计数器
    >
    >  2. 如果执行的是Java方法，计数器记录的是正在执行的虚拟机字节码指令地址
    >
    >  3. 如果执行的是Native方法，计数器值则为空
    >
    >  4. 是唯一个没有规定OutOfMemoryError情况的区域



### 2. Java对象创建过程

​	当虚拟机遇到一条new指令时

 1. 检查这个指令的参数是否能在常量池中定位到一个符号引用，并检查这个符号引用代表的类是否加载、解析和初始化过。如果没有，必须进行相应的类加载过程

 2. 加载成功后，准备为新生对象分配内存，所需内存大小在类加载完成后便可以完全确定。

    > - 如果Java对内存时绝对规整的，采用“指针碰撞”的方式分配
    >
    >   > 所有用过的内存都在一边，空闲的内存放在另一边，中间放着一个指针作为分界点的指示器，那所分配的内存就仅仅是把指针向空空闲空间那边挪动一段与对象大小想等的距离，该模式为“**指针碰撞**”
    >
    > - 如果Java堆的内存不是规整的，采用“空闲列表”的方式分配
    >
    >   > 已使用的内存和空闲的内存相互交错，在分配的时候找到足够大小的空间划分给对象实例，并更新列表上的记录,这种分配为“**空闲列表**”
    >
    > - 另外还要考虑的问题：线程安全
    >
    >   >场景： 可能出现正在给对象A分配内存，指针还没来得及修改，对象B又同时使用原来的指针来分配内存情况。
    >   >
    >   >解决方案：
    >   >
    >   >- 一种是**对分配内存空间的动作进行同步处理**：虚拟机采用CAS + 失败重试的 方式保证更新操作的原子性
    >   >
    >   >- 另一种是**把内存分配的动作按照线程划分在不同的空间中进行**，即每个线程在Java堆中预先分配一小块内存称为**本地线程分配池**（Thread Local Allocation Buffer, TLAB），哪个线程要分配内存就在那个内存的TLAB上分配，只有TLAB用完并分配新的TLAB时，才需要同步锁定。
    >   >
    >   >  虚拟机是否使用TLAB，可以通过 **-XX:+/-UseTLAB** 参数来使用

	3. 内存分配完成后，将分配到的内存空间都初始化为零值（不包括对象头）如果使用TLAB，这一工作过程也可以提前至TLAB分配时进行  。

    > **这个操作保证： 对象的实例字段在Java代码中可以不赋值就可以直接使用**，程序能访问到这些字段的数据类型所对应的零值

	4. 接下来，虚拟机要对对象进行必要的设置。

    > 例如：这个对象是哪个类的实例、如何才能找到类的元数据信息、对象的哈希码、对象的GC分代年龄等信息。这些信息存放在对象的对象头中（Object Header）。

	5. 执行<init> 方法

    > 从虚拟机视角看，到第4步时，对象已经产生。但从Java程序的视角看，对象创建才刚刚开始——<init>方法还没有执行，执行new指令后会接着执行<init> 方法，把对象按照程序员的意愿进行初始化，这样真正可用的对象才算完全产生出来



### 3. 对象的内存布局

对象在内存中存储的布局，可以分为3块区域：对象头（Header）、实例数据（Instance Data）和对齐填充(Padding)

- 对象头

  >- 两部分信息
  >
  >  >1.  一部分**用于存储对象自身运行时的数据**，如哈希码，GC分代年龄，锁状态标志、线程持有锁、偏向锁ID等。 数据长度在32位和64位的虚拟机中分别为32bit和64bit, 称为**Mark Word**
  >  >
  >  >   >Mark Word 被设计成一个非固定的数据结构，以便在极小的空间存储尽量多的信息，它会根据对象的状态复用自己的存储空间。
  >  >   >
  >  >   >比如一个32bit空间中： 25bit用于存储对象哈希码，4bit存储分代年龄，2bit存储锁标志，1bit固定为0，而在其它状态下对象的存储如下
  >  >   >
  >  >   >![image-20201002160049481](jvm.assets%5Cimage-20201002160049481.png)
  >  >
  >  >2. 另一部分是**类型指针**，即对象指向它的类元数据的指针，虚拟机**通过这个指针来确定这个对象是哪个类的实例**。
  >  >
  >  >   > 注意： 并不是所有的虚拟机实现都必须在对象数据上保留类型，换句话说查找对象的元数据信息并不一定要经过对象本身
  >  >
  >  >3. 另外对象**如果是一个Java数组**，那在对象头中还必须有一块用于记录数组长度的数据。因为虚拟机可以通过普通Java对象的元数据信息确定Java对象的代销，但是从数据的元数据中却无法确定数组的大小

- 实例数据

  >作用： **是对象真正存储的有效信息，也是在程序代码中所定义的各种类型的字段内容**。 无论是从父类继承下来的，还是在子类中定义的，都需要记录起来。
  >
  >>**特点**：
  >>
  >>1. 存储顺序受到虚拟机分配策略参数（FieldsAllocationStyle）和字段在Java源码中定义顺序的影响。
  >>
  >>   > Hotspot虚拟机默认的分配策略为 long/doubles、 int 、short/chars、 bytes/booleans 、oop(Ordinary Object Points)。 从分配策略中可以看出，相同宽度的字段总是被分配到一起
  >>
  >>2. 在满足1的前提下，父类中定义的变量会出现在子类之前，如果CompactFields参数值为true（默认为true），那么子类中较窄的变量可能会插入到父类变量空隙之中。

- 对齐填充

  > 说明： 不是必然存在的，仅仅起着占位符的作用，没有特别含义
  >
  > > 原因： Hotspot VM的自动内存管理系统要求对象起始地址必须是8字节的整数倍，因此当对象实例数据部分没有对齐时，就需要通过对齐填充来补全。



### 3. 对象的访问定位

Java程序需要通过栈（这里应该指的是虚拟机栈）上的reference数据来操作堆上的具体对象。

> 由于reference类在Java虚拟机规范中只规定了一个指向对象的引用，并没有定义这个引用应该通过何种方式去定位、访问堆中对象的具体位置，所以对象的访问方式取决于虚拟机实现而定

目前主流的访问方式： 使用句柄和直接指针两种

- **使用句柄方式**：

  > 描述： Java堆会划分出一块内存来作为句柄池，reference存储的就是对象的句柄地址，而**句柄中包含了对象实例数据与类型数据各自的具体地址信息**
  >
  > ![image-20201002163015330]( jvm.assets%5Cimage-20201002163015330.png)

- 使用**直接指针访问**

  > 描述： reference中存储的直接就是对象的地址
  >
  > ![image-20201002163507799]( jvm.assets%5Cimage-20201002163507799.png)

各自的优势：

​	1. 句柄访问的好处就是：reference中存储的是稳定的句柄地址，在对象移动时只会改变句柄中的实例数据指针，而reference本身不需要改变。

 	2. 直接指针的好处就是：速度快，节省了一次指针定位的时间开销



### 4. 内存泄露和溢出的概念

- 内存溢出：（Out Of Memory---OOM）

   系统已经不能再分配出你所需要的空间，比如你需要100M的空间，系统只剩90M了，这就叫内存溢出

- 内存泄漏： (Memory Leak)

  强引用所指向的对象不会被回收，可能导致内存泄漏，虚拟机宁愿抛出OOM也不会去回收他指向的对象

  换句话说：你用资源的时候为他开辟了一段空间，当你用完时忘记释放资源了，这时内存还被占用着，一次没关系，但是内存泄漏次数多了就会导致内存溢出



## 垃圾收集器与内存分配策略

### 学习方向？

1. 在介绍Java内存区域的各个部分时，其中**程序计数器、虚拟机栈、本地方法栈**3个区域**随线程而生，随线程而死**；栈中的栈帧随着方法的进入和推出而有条不紊的执行着出栈和入栈操作，每一个栈帧中分配多少内存基本上是在类结构确定下来时就已知的（虽然运行期间会有JIT编译器进行优化，但大体上认为是编译器可知的）；因此**这几个区域的内存分配和回收都具备确定性。不需要过多考虑回收问题**，因为方法结束或者线程结束时，内存自然就跟着回收了

 	2.  **Java堆和方法区**内存不一样： 一个接口中的多个实现类需要的内存可能不一样，一个方法中的多个分支需要的内存也可能不一样，我们只有在程序处于运行期间时，才能知道会创建哪些对象。这**部分的内存分配和回收时动态的，垃圾收集器所关注的就是这部分内存**。



### 判断对象是否引用的算法

- 1. **引用计数算法**

  - 过程： 

    	1. 给对象中添加一个引用计数器，每当有一个地方引用它时，计数器值就加1；
     	2. 当引用时效时，计数器就减1；
     	3. 任何时刻计数器为0的对象就是不可能再被使用的

  - 缺陷：**很难解决对象之间相互引用的问题**

    以下场景中： 对象objectA和objectB都有字段instance，赋值令objectA.instance = objectB以及objectB.instance = objectA,实际上这两个对象已经不可再被访问，但是它们相互引用着对方，导致引用计数器都不为0.于是引用计数算法无法通知GC收集器回收它们。

    >使用vm参数：  **-XX:+PrintGCDetails** 输出GC的详细日志
    >
    >代码：
    >
    >```java
    >/**
    > *
    > * -XX:+PrintGC 输出GC日志
    > * -XX:+PrintGCDetails 输出GC的详细日志
    > * -XX:+PrintGCTimeStamps 输出GC的时间戳（以基准时间的形式）
    > * -XX:+PrintGCDateStamps 输出GC的时间戳（以日期的形式，如 2013-05-04T21:53:59.234+0800）
    > * -XX:+PrintHeapAtGC 在进行GC的前后打印出堆的信息
    > * -Xloggc:../logs/gc.log 日志文件的输出路径
    > */
    >public class ReferenceCountingGC {
    >    public Object instance = null;
    >    private static final int _1MB = 1024*1024;
    >
    >    // 这个成员属性唯一的意义就是占点内存，以便能在GC日志中看清楚是否被回收过
    >    private byte[] bigSize = new byte[2*_1MB];
    >
    >    public static void testCG() {
    >        ReferenceCountingGC objectA = new ReferenceCountingGC();
    >        ReferenceCountingGC objectB = new ReferenceCountingGC();
    >        objectA.instance = objectB;
    >        objectB.instance = objectA;
    >
    >        objectA = null;
    >        objectB = null;
    >
    >        // 假设在这行发生GC，objA和objcB能否被回收？
    >        System.gc();
    >    }
    >
    >    public static void main(String[] args) {
    >        ReferenceCountingGC.testCG();
    >    }
    >}
    >
    >```
    >
    >输出：
    >
    >```java
    >[GC (System.gc()) [PSYoungGen: 9994K->840K(114688K)] 9994K->848K(376832K), 0.0013416 secs] [Times: user=0.02 sys=0.00, real=0.00 secs] 
    >[Full GC (System.gc()) [PSYoungGen: 840K->0K(114688K)] [ParOldGen: 8K->623K(262144K)] 848K->623K(376832K), [Metaspace: 3140K->3140K(1056768K)], 0.0041246 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
    >Heap
    > PSYoungGen      total 114688K, used 2949K [0x0000000740a00000, 0x0000000748a00000, 0x00000007c0000000)
    >  eden space 98304K, 3% used [0x0000000740a00000,0x0000000740ce1608,0x0000000746a00000)
    >....
    >```
    >
    >运行结果中： 9994K->840K 意味着虚拟机并没有因为这两个对象相互引用就不回收它们，这从侧面说明虚拟机不是通过引用计数算法来判断的对象是否存活的。

- 2.**可达性分析**

  - 过程：

    1. 通过一系列称为“**GC Roots**”的对象作为起点，从这些节点开始向下搜索，搜索所走过的路径称为**引用链**（Reference Chain）。

       > GC Roots 的对象包括以下几种：
       >
       > - **虚拟机栈（帧栈中的本地变量表）中引用的对象**
       > - **方法区中类静态属性引用的对象**
       > - **方法区中常量引用的对象**
       > - **本地方法栈中JNI（既一般说的Native方法）引用的对象**	

    2. 当一个对象到GC Roots没有任何引用链相连接时，证明这个对象是不可用的。

       > 如下图： 对象object5、object6、 object7虽然有关联，但它们到GC Roots是不可达的，所以它们将会被判定是可回收的对象
       >
       > ![image-20201002204501196]( jvm.assets%5Cimage-20201002204501196.png)



### 对象引用的划分

无论是通过引用计数算法判断对象的引用数量，还是通过可达性分析算法判断对象的引用链是否可达，判定对象是否存活都与“引用” 有关。(引用类型 是在虚拟机栈中分配局部变量表中进行存储的)

目前将对象引用分为： 强引用、软引用、弱引用、虚引用。强度依次减弱

- 强引用：类似“Object obj = new Object()”, 这类引用，**只要强引用还在，垃圾收集器永远不会回收掉被引用的对象**
- 软引用： 用来描述一些有用但非必须的对象，**在系统将要发生内存溢出异常之前，将会把这些对象列进回收范围之中进行第二次回收**。如果这次回收还没有足够内存，才会抛出内存溢出。 提供了**SoftReference**类来实现软引用。
- 弱引用： 用来描述非必须的对象。被弱引用关联的对象**只能生存到下一次垃圾收集发生之前。当垃圾收集器工作时，无论内存是否够用，都会回收掉只被弱引用关联的对象**。提供了**WeakReference**类来实现弱引用。
- 虚引用： 一个对象是否有虚引用完全不会对其生存时间构成影响，也无法通过虚引用取得一个对象实例。**唯一的目的就是能在这个对象被收集器回收时收到一个系统通知**。 提供**PhantomReference**类实现



### GC的两次标记

真正宣告一个对象死亡，至少要经历两次标记过程：

 1. 如果对象进行可达性分析后发现没有与GC Roots相连的引用链，则进行一次标记

 2. 第一次标记成功后，进行下一次的筛选，筛选条件是次对象是否有必要执行finalize()方法，当对象没有覆盖finalize方法或者finalize方法已经被虚拟机调用过（`注意：这里是说调用过，因此只有一次调用机会，多次调用不会生效了`），此时虚拟机将这两种情况视为“没有必要执行”，进而GC

    >注意：
    >
    >1. 如果这个对象被判定为有必要执行finalize()方法，那么这个对象将会放置在一个叫做F-Queue队列中并在一个由虚拟机自动建立的、低优先级的Finalizer线程去执行它，但并不承诺等它运行结束.
    >2. 这样做的原因是：防止finalize方法执行缓慢，或者发生了死循环，将可能导致F-Queue队列中其他对象永久处于等待，甚至导致整个内存回收系统崩溃。

**finalize()是对象逃脱GC的最后一次机会**，如果对象要在finalize中拯救自己——只要重新与引用链上的任何对象建立关联即可，譬如把自己（this关键字）赋值给某个类变量或者对象成员变量，那么第二次标记时它会被踢出GC列表。

代码：

```java
package com.xiaohui.jvm;

/**
 * 此代码演示两点：
 * 1. 对象可以被GC时自我拯救
 * 2. 这种自救机会只有一次，因为一个对象的finalize()方法最多只能被系统自动调用一次
 */
public class FinalizeEscapeGC {
    public static FinalizeEscapeGC SAVE_HOOK =null;

    public void isAlive(){
        System.out.println("yes, i am still alive :)");
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        System.out.println("finalize method executed!");
        FinalizeEscapeGC.SAVE_HOOK = this;
    }

    public static void main(String[] args) throws InterruptedException {
        SAVE_HOOK = new FinalizeEscapeGC();

        // 1. 对象第一次成功拯救自己
        SAVE_HOOK = null;
        System.gc();

        // 2. 由于finalize方法优先级别很低，所以暂停0.5秒等待它
        Thread.sleep(500);
        if (SAVE_HOOK != null){
            SAVE_HOOK.isAlive();
        }else{
            System.out.println("no, i am dead :(");
        }

        // 3. 下面这段代码与上面完全相同，但由于已经调用过自身的finalize方法了，再次就不会调用了
        SAVE_HOOK = null;
        System.gc();  // 第二次GC则不会逃脱了

        Thread.sleep(500);
        if (SAVE_HOOK != null){
            SAVE_HOOK.isAlive();
        }else{
            System.out.println("no, i am dead :(");
        }
    }
}

```

输出：

```java
finalize method executed!
yes, i am still alive :)
no, i am dead :(
```

>值得注意的是：代码中有两段完全一样的代码片段，执行结果却是一次逃脱成功，一次失败，这是因为任何一个对象的finalize()方法都只会被系统自动调用一次，如果面临下次回收，它的finalize不会再次被执行。因此第二段代码自救行动失败。

**强烈建议**： 不要使用这种方式来拯救对象，应该避免使用它。它的运行代价高昂，不确定性大，无法保证每个对象的调用顺序。



### 回收方法区

> 很多人认为方法区（或者虚拟机中的永久代）是没有垃圾收集的，在堆中，尤其是在新生代中，常规应用进行一次GC一般可以回收70%~95%的空间，而永久代的GC效率远低于此。

永久代的GC主要回收两部分：废弃的常量和无用的类。

- **回收废弃的常量**

  - 场景

    > 以常量池中字面量的回收为例：加入一个字符串“abc" 已经进入了常量池中，但是当前系统没有任何String对象引用常量池的“adb"常量，也没有在其他地方引用这个字面量，如果这时发生内存回收，而且必要的，这个”abc"常量会被系统清理出常量池。 常量池中的其他类（接口） 方法、字段地符号引用也与此类似

- **回收无用的类**

  - 判定常量是否为废弃常量的标准

    > 1. 该类所有的实例都已经被回收。
    > 2. 加载该类的ClassLoader已经被回收
    > 3. 该类对应的java.lang.Class对象没有在任何地方被引用，无法在任何地方通过反射访问该类的方法	

  - 虚拟机参数控制

    >1. 提供**-Xnoclassgc**参数进行控制
    >2. 可以使用**-verbose:class** 以及 **-XX:+TraceClassLoading**、**-XX:+TraceClassUnLoading**查看类加载和卸载信息

  - 大量使用反射、动态代理、CGLib等ByteCode框架、动态生成JSP以及OSGI这类频繁自定义ClassLoaderd的场景都需要虚拟机具备卸载功能，以保证永久代不会溢出

​	

### 垃圾回收算法（只做理论介绍）

- **标记-清除算法**

  - 过程：

    	1. 首先标记出所有需要回收的对象
     	2. 在标记完成后统一回收所有被标记的对象。

  - 不足：

    1. 效率问题：标记和清除两个过程的效率都不高
    2. 空间问题：标记清除后会产生大量不连续的内存碎片，碎片太多可能导致以后再程序运行过程中需要分配较大对象时，无法找到足够的连续内存而不得不提前触发一次垃圾收集动作

    > ![image-20201002231405401]( jvm.assets%5Cimage-20201002231405401.png)

- **复制算法**

  - 过程：

    1. 将可用内存按容量划分大小想等的两块，每次只使用其中的一块。
    2. 当这一块内存用完了，就将还存活的对象复制到另外一块上面
    3. 然后把已经使用过的内存空间一次性清理掉。

    > 这样使得每次都是对半区进行内存回收，内存分配时也就不用考虑内存碎片等复杂情况，只要推动堆顶指针，按顺序分配内存即可，实现简单，运行高效

  - 代价：

    将内存缩小为原来的一般，未免太高了。

    > ![image-20201002232332582]( jvm.assets%5Cimage-20201002232332582.png)

  - **当前主流的虚拟机算法分配内存的规则是**： 

    1. 将内存分为一块较大的Eden空间和两块较小的Survivor空间
    2. 每次使用Eden和其中一小块Survivor
    3. 当回收时，将Eden和Survivor中还存活着的对象一次性赋值到另外一块Survivor空间上
    4. 最后清理掉Eden和刚才使用过的Survivor空间。

    > **HotSpot虚拟机默认Eden和Survivor的大小比例是8：1**，也就是新生代中可用内存为整个新生代容量的90%，只有10%的内存会被消费。
    >
    > 
    >
    > 要是每次回收的存活对象都大于10%怎么办？
    >
    > 答： **当Survivor空间不够时，需要依赖其他内存（这里指老年代）进行分配担保**（Handle Promotion）

  

- **标记-整理算法**

  - 与标记-清除算法的区别：

    1. 当标记完可回收对象后，不是直接对可回收对象进行清理，而是让所有存活的对象都向一端移动，然后直接清理掉端边界以外的内存

    > ![image-20201002234614112]( jvm.assets%5Cimage-20201002234614112.png)

  

- **分代收集算法**

  - 过程：

    1. 根据对象存活周期的不同，将内存划分为几块，一般是把Java堆分为新生代和老年代

    2. 在新生代中： 每次来及收集时都发现有大批对象死去，只有少量存活，那就选用复制算法。只需要付出少量存活对象的复制成本就可以完成收集
    3. 在老年代中：因为对象存活率高，没有额外空间对它进行分配担保，就必须使用 “标记-清理” 或者 “标记-整理”



### 垃圾收集器

虚拟机包含的垃圾收集器分布（jdk1.7），`重点在CMS和G1收集器`，其他可以做了解

<img src=" jvm.assets%5Cimage-20201003001442455.png" alt="image-20201003001442455" style="zoom:67%;" />

> 1. 其中两个收集器之间的连线表示它们可以搭配使用。
> 2. 虚拟机所处的区域，则表示它属于新生代收集器还是老年代收集器

- **Serial收集器(新生代)**

  - 特点：

    1. 是一个**单线程收集器**，但不仅仅作用一个CPU或一条收集线程完成。
    2. 使用**复制算法**。
    3. 可使用的**控制参数**
       - -XX:SurvivorRadio：Eden与Survivor区的比例
       - -XX:PretenureSizeThreshold： 晋升老年代对象年龄
       - -XX:HandlePromotionFailure：是否允许担保失败
    4. 进行垃圾收集时，必须暂停其他所有的工作线程，直到收集结束（缺点）。
    5. 简单而高效（与其他收集器的单线程比）（优点）

    > ![image-20201003002458451]( jvm.assets%5Cimage-20201003002458451.png)

  - 适用场景：

    ​	在用户桌面应用场景中，分配给虚拟机管理的内存一般来说不会很大，停顿时间完全可以控制在几十毫秒最多一百多毫秒以内，只要不是频繁发生。所以对于运行CLient模式下的虚拟机来说是一个很好的选择

- **Parnew收集器(新生代)**

  - 特点：

    1. 其实是Serial收集器的多线程版本
    2. 其行为和Serial收集器差不多，参考就行
    3. 使用**-XX:+UserConMarkSweepGC**设置为默认新生代收集器，也可以使用 **-XX:+UseParNewGC**强制指定它
    4. **默认开启的收集线程数与CPU的数量相同**，在CPU非常多的环境下，可以使用**-XX:ParanllelGCThreads**参数来限制垃圾手机线程数

    > ![image-20201003003509986]( jvm.assets%5Cimage-20201003003509986.png)

  - 适用场景：

    1. 是运行在**Server模式下**的虚拟机中**首选的新生代收集器**，重要原因是：**目前只有它能够与CMS收集器配合工作**（它第一次实现了让垃圾手机线程与用户线程基本上同时工作，是真正意义上的并发收集器）

- **Parallel Scavenge收集器(新生代)**

  - 特点：

    1. 它的关注点和其他收集器不同： 目标则是**达到一个可控制的吞吐量**。**吞吐量=运行用户代码时间/(运行代码时间+垃圾收集时间**)

    2. **控制吞吐量的参数**： 

       - **-XX:MaxGCPauseMillis**:控制停顿时间（收集器尽可能达到设定值）

         > 说明：
         >
         > 1. GC停顿时间缩短是以牺牲吞吐量和新生代空间换来的
         > 2. 系统把新生代调小些，手机300MB新生代肯定比手机500MB快把，这也导致垃圾手机发生的更频繁。

       - **-XX:GCTimeRadio**:设置吞吐量大小

    3. **自适应策略**

       - 开关： **-XX:+UseAdaptiveSizePolice**

       - 特点：

         1. 当这个参数打开后，就**不需要**手工指定**新生代的大小(-Xmn)**、**Eden与Survivor区的比例(-XX:SurvivorRatio)**、**晋升老年代对象年龄（-XX:PretenureSizeThreshold）**等细节参数；虚拟机会根据当前系统的运行情况手机性能监控信息，动态调整这些参数以提供最合适的停顿时间或者最大的吞吐量

         2. 手工优化困难的时候，**使用Paranllel Scavenge收集器配合自适应策略，把内存管理的调优任务交给虚拟机完成是一个不错的选择**，只需要把基本的内存数据设置好（如 -Xmx设置最大堆），然后使用 MaxGCPauseMillis 参数或者GCTimeRadio参数给虚拟机设立一个优化目标
         3. 自适应策略也是Parallel Scavenge收集器与parnew收集器的一个主要区别

  - 适用场景

    ​	高吞吐量则可以高效率地利用CPU时间，尽快完成程序的运行任务，主要适用于后台运算而不需要太多交互的任务
  
- **Serial Old收集器(老年代)**

  - 用途：

    - 作为**CMS收集器的后备方案**，在**并发收集发生Concurrent Mode Failure时使用**
    - 给Client模式下的虚拟机使用

    >![image-20201003123742081]( jvm.assets%5Cimage-20201003123742081.png)

- **Parallel Old收集器（老年代）**

  - 特点

    1. 使用多线程和“**标记-整理**”算法
    2. **在注重吞吐量以及CPU资源敏感的场合，可以优先考虑Parallel Scavenge 加 Parallel Old 收集器**

    > ![image-20201003124219438]( jvm.assets%5Cimage-20201003124219438.png)

- **CMS 收集器（老年代）**

  - 特点

    1.  CMS（Concurrent Mark Sweep） 是一种**以获取最短回收停顿时间为目标**的收集器；
    2. 基于“**标记-清除**” 算法

  - **运作过程**

    1. 初始标记
       - 标记一下GC Roots能直接关联到的对象，速度很快
       - 会有一段时间的**停顿**
    2. 并发标记
       - 进行GC Roots Tracing的过程。
       - 可以与**用户线程一起工作**
    3. 重新标记
       - 修正并发标记期间因用户程序继续运作而导致标记产生变动的那一部分对象标记记录；
       - 这个阶段的**停顿**会比初始标记阶段稍长一些，但远比并发标记的时间短
    4. 并发清除
       - 可以**与用户线程一起工作**

    > 由于整个过程中耗时最长的并发标记和并发清除过程收集器线程都可以与用户线程一起工作
    >
    > 总体上来说： CMS收集器的内存回收过程是与用户线程一起并发执行的
    >
    > ![image-20201003125651839]( jvm.assets%5Cimage-20201003125651839.png)

  - 适用场景

    1. 目前大部分Java应用集中在互联网或者B/S系统的**服务端**上，这类应用尤其**重视服务响应速度，希望系统停顿时间最短，以给用户带来较好的体验**。

  - **缺点**：

    - **对CPU资源非常敏感**，因为要占用一部分线程（或者说CPU资源）而导致程序变慢，总吞吐量降低。

      > CMS 默认启动的**回收线程数时（CPU数量+3）/4** ，也就是4个CPU以上时，并发回收时垃圾收集线程不少于25%的CPU资源，并且随着CPU数量的增加而下降。
      >
      > 当CPU数量不足4个时（比如2个），CMS将会分出一半的CPU资源去执行收集器线程，就可能导致用户程序的执行速度忽然降低50%，这是不能接受的。

    - 无法处理**浮动垃圾**，可能出现“Concurrent Mode Failure”失败而导致另一次Full GC 的产生；

      > 浮动垃圾： 由于CMS并发清理阶段用户线程还在运行，此时会有新的垃圾不断产生，这部分垃圾出现在标记过程之后，CMS无法在当前的收集中处理掉它们，只好等待下一次GC时清理掉。

    - “标记-清除”算法的缺陷： 产生**大量空间碎片**

      > -XX:+**UseCMSCompactAtFullCollection**（默认开启）：用于CMS收集器顶不住要继续FullGC 时开启内存碎片整理过程。该过程无法并发，因此会造成停顿时间变长
      >
      > -XX:**CMSFullGCsBeforeCompation**:设置执行多少次不压缩的Full GC 后，跟着来一次带压缩的（默认为0，表示每次进入Full GC 时都进行碎片整理）

- **G1收集器(新生代和老年代)**

  - **特点**：

    1. 并行与并发： 
       - 充分利用多CPU、多核环境下的硬件优势，使用多个CPU来缩短 stop-the-world停顿的时间。
    2. 分代收集
    3. 空间整合：
       - 给予”标记-整理“算法实现收集器。收集后能提供规整的可用内存，这种特性有利于程序长时间运行，分配大对象是不会因为无法找到连续内存空间而提前触发下一次GC。
    4. 可预测的停顿：
       - 能让使用者明确指定在一个长度为M毫秒的时间片段内，消耗在垃圾收集上的时间不得超过N毫秒。-XX:MaxGCPauseMillis=50 :设置停顿时间

  - **差别**（和其他收集器比）：

    - 它**将整个Java堆划分为多个大小相等的独立区域（Region）****，虽然还保留着新生代和老年代的概念，但**新生代和老年代不再是物理隔离**，它们都是异步Region(不需要连续)的集合。

  - **过程**：

    1. 初始标记

       标记一下GC Roots能直接关联到的对象，并修改TAMS(Next Top at Mark Start)的值，让下一阶段用户程序并发并行时，能在正确可用的Region中创建对象，这个阶段要停顿，但耗时很短    

    2. 并发标记

       从GC Root开始对堆中对象进行可达性分析，找出存活对象，这阶段耗时较长，但可与用户程序并发执行

    3. 最终标记

       修改在并发标记期间因用户程序继续运作而导致标记产生变动的那部分标记记录，虚拟机将这段时间的变化记录在线程Remembered Set Logs里面，最终将Logs数据合并到Remembered Set中。这阶段需要停顿线程，但可并行执行。     

    4. 筛选回收

       首先对各个Region的回收价值和成本进行排序，根据用户所期望的GC停顿时间来指定回收计划

       > 这阶段其实可以和用户程序并发执行，但是因为只回收一部分Region,时间是用户可控制的，而且停顿用户线程将大幅提高收集效率

  > ![image-20201003141150761]( jvm.assets%5Cimage-20201003141150761.png)



### 理解GC 日志

```javascript
Heap(堆)
 PSYoungGen(Parallel Scavenge收集器新生代)      total 9216K, used 6234K [0x00000000ff600000, 0x0000000100000000, 0x0000000100000000)
  eden space(堆中的Eden区默认占比是8) 8192K, 76% used [0x00000000ff600000,0x00000000ffc16b08,0x00000000ffe00000)
  from space(堆中的Survivor，这里是From Survivor区默认占比是1) 1024K, 0% used [0x00000000fff00000,0x00000000fff00000,0x0000000100000000)
  to   space(堆中的Survivor，这里是to Survivor区默认占比是1，这个需要先了解一下堆的分配策略) 1024K, 0% used [0x00000000ffe00000,0x00000000ffe00000,0x00000000fff00000)
 ParOldGen(老年代总大小和使用大小)       total 10240K, used 7001K [0x00000000fec00000, 0x00000000ff600000, 0x00000000ff600000)
  object space(显示个使用百分比) 10240K, 68% used [0x00000000fec00000,0x00000000ff2d6630,0x00000000ff600000)
 PSPermGen(永久代总大小和使用大小)        total 21504K, used 4949K [0x00000000f9a00000, 0x00000000faf00000, 0x00000000fec00000)
  object space(显示个使用百分比，自己能算出来) 21504K, 23% used [0x00000000f9a00000,0x00000000f9ed55e0,0x00000000faf00000)
```



### 垃圾收集器参数总结



| 参数                           | 描述                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| UseSerialGC                    | 虚拟机运行在Client模式下的默认值，打开此开关后。使用Serial+Serial Old的手机器组合进行内存回收 |
| UseParNewGC                    | 打开此开关后，使用**ParNew+Serial Old** 的收集器组合进行内存回收 |
| UseConcMarkSweepGC             | 打开此开关后，使用**ParNew+CMS+Serial Old**的收集器组合进行内存回收。Serial Old 收集器将作为CMS收集器出现Concurrent Mode Failure失败后的后备后机器 |
| UseParallelGC                  | 虚拟机运行在Server模式下的默认值，打开后，使用**Parallel Scavenge + Serial Old** 的收集器组合进行回收 |
| UseParallelOldGC               | 打开后使用 **Parallel Scavenge+ Parallel Old**的收集器组合进行内存回收 |
| **老年代相关参数**             | **描述**                                                     |
| PreTenureSizeThreshold         | 大于这个参数的对象将直接在老年代分配,`只对Serial 和ParNew收集器有效` |
| MaxTenuringThreshold           | 晋升到老年代的对象年龄。每个对象在坚持过一次Minor GC 之后，年龄就增加1，当超过这个参数值时就进入老年代 |
| UseAdaptiveSizePolicy          | 动态调整Java堆中各个区域的大小以及进入老年代的年龄           |
| HandlePromotionFailure         | 是否允许分配担保失败，即老年代的剩余空间不足以应付新生代整个Edean和Survivor区的所有对象都存活的极端情况 |
| **Parallel Scavage收集器参数** | **描述**                                                     |
| ParallelGCThreads              | 设置并行GC时进行内存回收的线程数                             |
| GCTimeRatio                    | GC时间占总时间的比率，默认值99，即允许1%的GC时间，仅在使用Parallel Scavage收集器时生效 |
| MaxGCPauseMillis               | 设置GC的最大停顿时间，仅在使用Parallel Scavage收集器时生效   |
| **CMS收集器参数**              | **描述**                                                     |
| CMSInitiatingOcucpancyFraction | 设置CMS收集器在老年代空间被使用多少后触发来及收集器。默认值为68%，仅在CMS收集器时生效 |
| UseCMSCompactAtFullConllection | 设置CMS收集器在完成垃圾收集后是否要进行一次内存碎片整理，仅在CMS收集器时生效 |
| CMSFullGCsBeforeCompaction     | 设置CMS收集器在进行若干次垃圾收集后再启动一次内存碎片整理，仅在CMS收集器时生效 |
|                                |                                                              |



### 内存分配与回收策略

> 对象的内存分配，往大方向讲：就是在堆上分配`（也可以经过JIT编译后被拆散为标量类型并间接地栈上分配）` 
>
> 大致分配规则：
>
> 1. 对象主要分配在新生代的Edea区上。
> 2. 如果启动了本地线程分配缓冲，将按线程有限在TLAB上分配。
> 3. 少数情况会直接分配在老年代中



- **在Serial/serial Old收集器组合（ParNew/Serial Old组合）下内存分配的规则**:

  >1. **对象优先在Eden分配**
  >2. **大对象直接进入老年代**
  >3. **长期存活的对象直接进入老年代**

  `（注意测试的环境是Client模式!!!!）`

  1. **对象优先在Eden分配**

     - 过程：

       1. 大多数情况下，对象在新生代Eden区分配
       2. 当Eden区没有足够空间进行分配时，虚拟机将发起一次Minor GC.

     - **场景测试**：

       vm 参数： -verbose:gc -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8 -XX:+UseParallelGC

       > **参数说明**： 
       >
       > - -**Xms**20M：堆最小值	-**Xmx**20M：堆最大值 ； 其中两者相等表示不可扩展
       >
       > - -**Xmn**10M: 设置新生代的堆大小10M，剩下的就是老年代的空间大小（20-10=10M）
       >
       >   > 可以查看代码运行结果：
       >   >
       >   > `par new generation   total 9216K, used 6972KK`
       >   >
       >   > `tenured generation   total 10240K`
       >   >
       >   > 基本是按照当前的设置结果
       >
       > - -**XX:SurvivorRatio**=8：设置新生代中Eden:Survivor的空间比例8:1 
       >
       >   > 可以查看代码运行结果：
       >   >
       >   > `eden space 8192K,  77% used `
       >   >
       >   > `from space 1024K,  64% used `
       >   >
       >   > ` to   space 1024K,   0% used` 
       >   >
       >   > 基本是按照当前的设置结果
       >
       > - -**XX:+PrintGCDetails**： 打印GC详细信息
       >
       > - -XX:+**UseParNewGC**： 开启**ParNew+Serial Old** 的收集器组合进行内存回收

       代码：

       ```java
       /**
        * vm 参数： -verbose:gc -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8 -XX:+UseParNewGC
        */
       public class MinorGCTest {
           private static final int _1MB = 1024*1024;
           public static void main(String[] args) {
               byte[] allocation1 , allocation2,allocation3,allocation4;
               allocation1 = new byte[2 *_1MB];
               allocation2 = new byte[2 *_1MB];
               allocation3 = new byte[2 *_1MB];
               allocation4 = new byte[4 *_1MB]; // 这里会出现一次Minor GC
           }
       }
       ```

       测试结果：

       > 1. 在分配 allocation4 对象时，会发生一次Minor GC 
       > 2. 发生的原因时给allocation4分配内存的时候，发现Eden以及被占用了6MB，剩余空间已经不足以分配其所需的4MB内存。因此会发生Minor GC 
       > 3. GC 期间虚拟机又发现已有的4MB无法全部放入Survivor空间（Survivor空间只有1MB），所以只好通过分配担保机制提前转移到老年代。

       返回：

     ```javascript
     [GC (Allocation Failure) [ParNew: 8132K->657K(9216K), 0.0037911 secs] 8132K->6801K(19456K), 0.0038204 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
     Heap
      par new generation   total 9216K, used 4919K [0x00000000fec00000, 0x00000000ff600000, 0x00000000ff600000)
       eden space 8192K,  52% used [0x00000000fec00000, 0x00000000ff029780, 0x00000000ff400000)
       from space 1024K,  64% used [0x00000000ff500000, 0x00000000ff5a4760, 0x00000000ff600000)
       to   space 1024K,   0% used [0x00000000ff400000, 0x00000000ff400000, 0x00000000ff500000)
      tenured generation   total 10240K, used 6144K [0x00000000ff600000, 0x0000000100000000, 0x0000000100000000)
        the space 10240K,  60% used [0x00000000ff600000, 0x00000000ffc00030, 0x00000000ffc00200, 0x0000000100000000)
      Metaspace       used 3184K, capacity 4496K, committed 4864K, reserved 1056768K
       class space    used 344K, capacity 388K, committed 512K, reserved 1048576K
     ```

  2. **大对象直接进入老年代**

     `使用UseConcMarkSweepGC 设置ParNew+CMS+Serial Old 组合的回收策略`

     **vm 参数**： -verbose:gc -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8 -XX:**PretenureSizeThreshold**=3M -XX:+**UseConcMarkSweepGC**

     参数说明：

     > -XX:**PretenureSizeThreshold** : 设置进入老年代的起始大小，`该参数只能针对ParNew 和Serial有效`
     >
     > -XX:+**UseConcMarkSweepGC**： 使用ParNew+CMS+Serial Old 组合的回收策略。

     代码：

     ```java
     /**
      * vm 参数： -verbose:gc -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8 -XX:PretenureSizeThreshold=3M  -XX:+UseConcMarkSweepGC
      */
     public class PretenureSizeThresholdTest {
         private static final int _1MB = 1024*1024;
         public static void main(String[] args) {
             byte[] allocation;
             allocation = new byte[6*_1MB]; // 设置
         }
     }
     ```

     结果分析：

     1. 使用PretenureSizeThreshold设置之后，eden区域几乎没有使用
  
    	2.  老年代被使用了很多 `concurrent mark-sweep generation total 10240K, used 6144K`
  
   返回：
  
     ```javascript
     Heap
      par new generation   total 9216K, used 2151K [0x00000000fec00000, 0x00000000ff600000, 0x00000000ff600000)
       eden space 8192K,  26% used [0x00000000fec00000, 0x00000000fee19c28, 0x00000000ff400000)
       from space 1024K,   0% used [0x00000000ff400000, 0x00000000ff400000, 0x00000000ff500000)
       to   space 1024K,   0% used [0x00000000ff500000, 0x00000000ff500000, 0x00000000ff600000)
      concurrent mark-sweep generation total 10240K, used 6144K [0x00000000ff600000, 0x0000000100000000, 0x0000000100000000)
      Metaspace       used 3177K, capacity 4496K, committed 4864K, reserved 1056768K
       class space    used 344K, capacity 388K, committed 512K, reserved 1048576K
     ```
  
3. **长期存活的对象直接进入老年代**
  
   - 过程：
   
     1. 虚拟机给每个对象定义了一个对象年龄（Age）计数器。
   
     2. 如果对象Eden出生并经过第一次MinorGC后，仍然存活，并且能被Survivor容纳的话，将被迁到Survivor空间中，并且对象年龄为1，对象在Survivor区中每熬过一次MinorGC ，年龄就增加1岁
   
     3. 如果年龄增加到一定程度（默认为15岁）将会被晋升到老年代中
   
        > 对象晋升老年代的年龄阀值可以通过参数 -**XX:MaxTenuringThreshold**设置
   
   - 场景测试：
   
     vm参数： -verbose:gc -Xms20M -Xmx20M -Xmn10M -XX:SurvivorRatio=8 -XX:+UseConcMarkSweepGC -XX:+PrintGCDetails -**XX:MaxTenuringThreshold**=1 -XX:+**PrintTenuringDistribution**
   
     参数说明：
   
       > **-XX:MaxTenuringThreshold**:设置进入老年代的年龄
       >
     > **XX:+PrintTenuringDistribution**：打印老年代分布
   
     代码：
   
       ```java
       /**
        * vm 参数：
        * -verbose:gc -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8
        * -XX:+UseConcMarkSweepGC -XX:MaxTenuringThreshold=1 -XX:PrintTenuringDistribution
        */
       public class TenuringThresholdTest {
           private static final int _1MB = 1024*1024;
           public static void main(String[] args) {
               byte[] allocation1 , allocation2,allocation3;
               allocation1 = new byte[_1MB / 4];
               allocation2 = new byte[4 *_1MB];
               allocation3 = new byte[4 *_1MB];
               allocation3 = null;
               allocation3 = new byte[4 *_1MB];
           }
       }
       ```
   
     返回：
   
       ```javascript
       [GC (Allocation Failure) [ParNew
       Desired survivor size 524288 bytes, new threshold 1 (max 1)
       - age   1:     905904 bytes,     905904 total
       : 6338K->929K(9216K), 0.0027747 secs] 6338K->5027K(19456K), 0.0028133 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
       [GC (Allocation Failure) [ParNew
       Desired survivor size 524288 bytes, new threshold 1 (max 1)
       - age   1:        608 bytes,        608 total
       : 5109K->96K(9216K), 0.0025955 secs] 9207K->5072K(19456K), 0.0026160 secs] [Times: user=0.08 sys=0.00, real=0.02 secs] 
       Heap
        par new generation   total 9216K, used 4330K [0x00000000fec00000, 0x00000000ff600000, 0x00000000ff600000)
         eden space 8192K,  51% used [0x00000000fec00000, 0x00000000ff022760, 0x00000000ff400000)
         from space 1024K,   9% used [0x00000000ff400000, 0x00000000ff4182b0, 0x00000000ff500000)
         to   space 1024K,   0% used [0x00000000ff500000, 0x00000000ff500000, 0x00000000ff600000)
        concurrent mark-sweep generation total 10240K, used 4976K [0x00000000ff600000, 0x0000000100000000, 0x0000000100000000)
        Metaspace       used 3198K, capacity 4496K, committed 4864K, reserved 1056768K
         class space    used 345K, capacity 388K, committed 512K, reserved 1048576K
       ```
  
4. **空间分配担保**
  
     - 过程：
       1. 在发生MinorGC 之前，虚拟机会**先检查老年代最大可用的连续空间是否大于新生代所有对象总空间**
       2. 如果大于，则表示MinorGC 可以确保是安全的
       3. 如果不成立，则虚拟机会查看HandlePromotionFailure设置值是否允许担保失败。
       4. 如果允许担保失败，那么会继续**检查老年代最大可用的连续空间是否大于历次晋升到老年代对象的平均大小**
       5. 如果平均大于，将尝试着进行一次MinorGC ,尽管这次MinorGC 是有风险的
     6. 如果平均小于，或者HandlePromotionFailure设置不允许冒险，那么这时也要改为进行一次Full GC（老年代垃圾回收）
  
     > ![image-20201003171153611]( jvm.assets%5Cimage-20201003171153611.png)



### MinorGC 和FullGC 的区别

- MinorGC: 表示新生代GC，是发生在新生代的垃圾收集动作。因为Java对象大多具备朝生夕死的特性，所以MinorGC 非常频繁，一般回收速度也比较快
- FullGC: 指老年代GC，又为MajoirGC ，是发生在老年代的GC，出现MajorGC ,经常会伴随至少一次的MinorGC(但非绝对，在Parallel Scavenge收集器的策略就直接进行MajorGC），速度比一般的MinorGC 慢10倍以上



## 虚拟机性能监控与故障处理工具

### 监控和故障工具汇总

| 名称   | 主要作用                                                     |
| ------ | ------------------------------------------------------------ |
| jps    | 显示指定系统内所有的HotSpot虚拟机进程                        |
| jstat  | 用于手机HotSpot虚拟机各方面运行数据                          |
| jinfo  | 显示虚拟机配置信息                                           |
| jmap   | 生成虚拟机的内存转储快照（headdump文件）                     |
| jhat   | 用于分析headdump文件，会建立一个http/html服务器，让用户可以在浏览器上查看分析结果 |
| jstack | 显示虚拟机的线程快照                                         |



### jps:查看虚拟机进程

​	使用方式和ps命令类似：可以列出正在运行的虚拟机进程，并显示虚拟机**执行主类**、**名称**以及这些进程的本地虚拟机唯一ID（就是**进程ID**）

![image-20201003173240271]( jvm.assets%5Cimage-20201003173240271.png)



### jstat :虚拟机统计信息监视工具

- 作用

  1. 用于监视虚拟机各种运行状态信息的命令行工具
  2. 显示本地或者远程虚拟机进程中的类装载。内存、垃圾手机、JIT编译等运行数据

- 具体

  ![image-20201003173911973]( jvm.assets%5Cimage-20201003173911973.png)

  ​	![image-20201003174055126]( jvm.assets%5Cimage-20201003174055126.png)

- 示例：

  ![image-20201003174530223]( jvm.assets%5Cimage-20201003174530223.png)



### jinfo: Java配置信息

- 说明

  ![image-20201003174919026]( jvm.assets%5Cimage-20201003174919026.png)

- 示例

  <img src=" jvm.assets%5Cimage-20201003175014620.png" alt="image-20201003175014620" style="zoom:80%;" />



### jmap:Java内存映射工具

- ​	描述：

  ![image-20201003175432598]( jvm.assets%5Cimage-20201003175432598.png)

  ![image-20201003175457554]( jvm.assets%5Cimage-20201003175457554.png)

- 示例：

  ![image-20201003175601050]( jvm.assets%5Cimage-20201003175601050.png)



### jhat：虚拟机堆转储快照分析工具

了解即可，后续用可视化程序了。。。



### jstack : Java堆栈跟踪器

- 描述：

  ![image-20201003180148445]( jvm.assets%5Cimage-20201003180148445.png)

  ![image-20201003180223664]( jvm.assets%5Cimage-20201003180223664.png)

- 示例：

  ![image-20201003180315823]( jvm.assets%5Cimage-20201003180315823.png)



### hidis: JIT生成代码反编译

这个看书了解即可。。。。



### jconcole.exe: Java监视与管理控制台

这个依然看书，掌握。。



### visualVM:多合一故障处理工具

这个依然看书，掌握。。

idea 安装相应插件： https://blog.csdn.net/qq_37960603/article/details/85224547



## 虚拟机类加载机制

###  类的七个生命周期

**加载-验证-准备-解析-初始化-使用-卸载**

![image-20200914100545154]( jvm.assets%5Cimage-20200914100545154.png)

其中解析阶段可以在初始化之后再开始，这是为了支持java语言的运行时绑定（动态绑定）



加载的时机由虚拟机决定。



### 初始化（主动使用）的7种情况：

1. 遇到**new**，**getstatic**，**putstatis**或者**invokestatis**这四个字节码指令

   常见场景：

   1. 使用new关键字实例化对象时
   2. 读取或设置一个类地静态字段（被final修饰，已经在编译器把结果放入常量池对象的字段除外）；
   3. 调用一个类的静态方法时

2. 使用java.lang.**reflect**包的方法对类进行反射调用的时候，如果类没有初始化。则需要先出发其初始化。

3. 当初始化一个类的时候，如果发现其**父类**还没有进行过初始化，则需要**先出发其父类初始化**。

4. 虚拟机启动时，用户需要指定一个要执行的**主类**（包含main()方法的类），虚拟机会先初始化这个主类

5. 当时用动态语言支持时，如果java.lang.invoke.MethodHandle实例最后的解析结果是REF_getStatic,REF_putStatic,REF_invokeStatic的方法句柄，并且这个方法句柄对应的类没有进行过初始化，则需要先出发其初始化



### Java程序对类的使用分为：主动引用和被动引用

由以上5中情况的行为称为对一个类进行**主动引用**，除此之外引用类的方式都不会触发初始化，被称为**被动引用**

演示一：**通过子类引用父类的静态字段，不会导致子类初始化**

```java
package com.xiaohui.jvm.classloading;

/**
 * 被动使用类字段掩饰一：
 * 通过子类引用父类的静态字段，不会导致子类初始化
 */
class SuperClass {
    static {
        System.out.println("SuperClass init!");
    }
    public static int value = 123;
}
class SubClass extends SuperClass{
    static {
        System.out.println("SubClass init!");
    }
}

/**
 * 非主动类初始化演示
 */
public class NotInitialization{
    public static void main(String[] args){
        System.out.println(SubClass.value);
    }
}

/**
SuperClass init!
123
*/
```

上述代码中，子类引用的字段只是父类的，所以只会出发父类的初始化而不会出发子类的初始化。

但如果子类引用了自己的字段，则父类和子类都会初始化。

```java
class SubClass extends SuperClass{
    static {
        System.out.println("SubClass init!");
    }
    static int value = 234; // 覆盖父类的静态字段
}
/**
SuperClass init!
SubClass init!
234
*/
```

演示二： **数组定义来引用类，不会触发此类的初始化**

```java
public class NotInitialization{
    public static void main(String[] args){
        // 1.通过子类引用父类的静态字段，不会导致子类初始化
//        System.out.println(SubClass.value);
        // 2. 数组定义来引用类，不会触发此类的初始化
        SuperClass[] superClasses = new SuperClass[10];
    }
}
/**

*/
```

上述代码没有任何输出。但这段代码却触发了SuperClass的类的初始化阶段(使用-XX:+TraceClassLoader 参数开启加载过程)，它是由虚拟机自动生成的，直接继承与java.lang.Object的子类，创建动作有字节码指令newarray触发。

![image-20200914110947736]( jvm.assets%5Cimage-20200914110947736.png)



演示三：**常量在编译阶段会存入调用类的常量池中，本质上并没有直接引用到定义常量的类，因此不会出发定义常量类的初始化**

```java
class ConstClass{
    static {
        System.out.println("ConstClass init!");
    }
    public static final String HELLOWORLD = "hello world";
}

/**
 * 非主动类初始化演示
 */
public class NotInitialization{
    public static void main(String[] args){
        // 1.通过子类引用父类的静态字段，不会导致子类初始化
//        System.out.println(SubClass.value);
        // 2. 数组定义来引用类，不会触发此类的初始化
//        SuperClass[] superClasses = new SuperClass[10];
       // 3. 常量在编译阶段会存入调用类的常量池中，本质上并没有直接引用到定义常量的类，因此不会出发定义常量类的初始化
        System.out.println(ConstClass.HELLOWORLD);
    }
}

/**
hello world
*/
```

在编译阶段，通过常量传播优化，已经将此常量值得“hello world“存储到NotInitialization类的常量池中，以后NotInitialization对常量ConstClass.HELLOWORLD 的引用实际都转化为NotInitializaton类对自身常量池的引用。



> 对接口的特殊说明：
>
> 1. **接口中不能使用”static{}"语句块，但编译器仍然会为接口生成“<clinit>()”类构造器，用于初始化接口中所定义的成员变量**
>
> 2. **一个接口在初始化时，并不要求其父接口全部完成了初始化，只有在真正使用到父接口的时候（如引用接口中 定义的常量）才会初始化**





### 类加载

在加载阶段，虚拟机需要完成以下3 件事情

1. 通过一个类的**全限定名**来在取定义此类的**二进制字节流**。

   >注意： 该条没有指明二进制字节流要从一个Class文件中获取，准确地说没有指明要从哪里获取，怎样获取，因此可以灵活运用
   >
   >以下场景都和该条有关系：
   >
   >1. 从zip包中读取，最终成为日后jar,war格式的基础
   >
   >2. 动态代理技术，在java.lang.refleact.Proxy中。就是用了ProxyGenerator.generateProxyClass来为特定接口生成形式为“$Proxy”的代理类的二进制字节流
   >
   >3. 从其他文件生成，典型场景时JSP应用。即由文件生成对应的Class类
   >
   >  .....
   >
   >

2. 将这个**字节流**所代理的**静态存储结构**转化为**方法区的运行时数据结构**。

3. 在内存中**生成**一个代表**这个类的java.lang.Class对象**，**作为方法区**这个类的各种**数据的访问入口**



​    相对于类加载过程的其他阶段，**一个非数组类**的加载阶段，是开发人员可控性最强的，因为加载阶段**既可以**使用系统提供的引导类加载器完成，**也可以**由用户自定义的类加载器去完成。

​    开发人员可以通过**定义自己的类加载器**去控制字节流的获取方式（即**重写一个类加载器的loadClass()方法**）

>​	但**数组不同**： **数组本身不通过类加载器创建，是由虚拟机直接创建的**，但数组与类加载器仍然有密切关系，因为一个**数组类的元素类型**（ElementType，指的是数组去掉所有维度的类型）最终是要靠类加载器去创建.
>
>> 数组类的创建过程遵循以下规则：
>>
>> 1. 如果数组的**组件类型**是**引用类型**，那就递归采用定义的加载过程去加载这个组件类型。数组类将在加载该组件类型的类加载器的类名空间上被**标识**（因为一个类必须与类加载器一起确定唯一性）
>> 2. 如果数组的组件类型**不是引用类型**（如int[] 数组），虚拟机会把数组类**标记**为与引导类加载器关联
>> 3. 数组类的可见性与它的组件类型的可见性一致，如果组件类型不是引用类型，那么数组类型的可见性将默认为public.



### 验证

​	目的是为了确保Class文件的字节流中包含的信息符合当前虚拟即得要求，并且不会危害虚拟机自身的安全

​	验证阶段的4个验证动作：

#### 1. 文件格式验证

![image-20200914115915002]( jvm.assets%5Cimage-20200914115915002.png)

#### 2. 元数据验证

![image-20200914115834577]( jvm.assets%5Cimage-20200914115834577.png)

#### 3.字节码验证

![image-20200914120140297]( jvm.assets%5Cimage-20200914120140297.png)

![image-20200914120235047]( jvm.assets%5Cimage-20200914120235047.png)

​	

#### 4.符号引用验证

![image-20200914120524405]( jvm.assets%5Cimage-20200914120524405.png)

![image-20200914120700076]( jvm.assets%5Cimage-20200914120700076.png)



### 准备

#### 目的： 

​	正式为**类变量分配内存**并**设置变量初始值**阶段，这些变量所使用的内存都将**在方法区**进行分配

>注意： 
>
>1. 这时候的内存分配**仅包括类变量**（被static修饰的变量），而不包括实例变量，实例变量将会在对象实例化时伴随对象一起分配在java堆中。
>
>2. 初始值 通常表示 数据类型的零值,假设类变量定义为：
>
>  public static int value = 123 
>
>  那么变量value在准备阶段过后的初始值为0，而不是123，因为还没有执行任何java方法，而把value赋值为123的putstatic指令时程序被编译后，存放在类构造器<clinit>方法中，所以把value赋值为123的动作将在初始化阶段才会执行
>
>![image-20200914122013289]( jvm.assets%5Cimage-20200914122013289.png)
>
>特殊情况：
>
>![image-20200914122213719]( jvm.assets%5Cimage-20200914122213719.png)





### 解析

目的： 将常量池内的符号引用替换为直接引用的过程

##### 直接引用和符号引用的关联：

![image-20200914122534255]( jvm.assets%5Cimage-20200914122534255.png)



##### 解析的时间：

​		要求在执行 anewamy，chcckcast 、gcrfield 、getstatic 、instanceof、invokedynamic 、invokeinterface 、invokespccial 、invokestatìc 、invokcvirtual 、Idc 、Idc_w、rnultianewarray 、new、pulfìeld和putstatic这16个用于操作符号引用的字节码指令之前，先对它们使用的符号引用进行解析。所以虚拟机实现可以根据需要来判断到底是在类被加载器加载时就对常量池中的符号引用进行解析还是等到一个符号应用将要被使用前才去解析它。



##### invokedynamic指令的特殊说明：

![image-20200914123817814]( jvm.assets%5Cimage-20200914123817814.png)



##### 符号引用与常量池的对应：

![image-20200914124008562]( jvm.assets%5Cimage-20200914124008562.png)



###### 1 类或接口的解析

假设当前代码所处的类为D，如果要把一个从未解析过的符号引用N解析程为一个类或接口C的直接引用。那么虚拟机解析过程需要以下3个步骤：

![image-20200914124619161]( jvm.assets%5Cimage-20200914124619161.png)

###### 2.字段解析

![image-20200914124749218]( jvm.assets%5Cimage-20200914124749218.png)

![image-20200914124834731]( jvm.assets%5Cimage-20200914124834731.png)





###### 3.类方法解析

先解析类方法表的class_index项中索引方法所属的类或接口的符号引用，解析成功则：

![image-20200914125329140]( jvm.assets%5Cimage-20200914125329140.png)



###### 5.接口方法解析

![image-20200914130113588]( jvm.assets%5Cimage-20200914130113588.png)

![image-20200914130143947]( jvm.assets%5Cimage-20200914130143947.png)



### 初始化

![image-20200914133754905]( jvm.assets%5Cimage-20200914133754905.png)

![image-20200914133912844]( jvm.assets%5Cimage-20200914133912844.png)



![image-20200914134352824]( jvm.assets%5Cimage-20200914134352824.png)



```java
class DeadLoopClass{
    static {
        /** 如果不加上这个if语句，编译器执行提示“Initializer does not complte normally 拒绝编译”*/
        if(true){
            System.out.println(Thread.currentThread()+"init DeadLoopClass");
            while (true){

            }
        }
    }
}

public class DeadLoopClassTest {
    public static void main(String[] args) {
        Runnable runnable = new Runnable() {
            public void run() {
                System.out.println(Thread.currentThread() + "start");
                DeadLoopClass deadLoopClass = new DeadLoopClass();
                System.out.println(Thread.currentThread() + " run over");
            }
        };
        Thread thread1= new Thread(runnable);
        Thread thread2= new Thread(runnable);
        thread1.start();
        thread2.start();
    }
}

/**
Thread[Thread-1,5,main]start
Thread[Thread-0,5,main]start
Thread[Thread-1,5,main]init DeadLoopClass
*/
```

![image-20200914134730197]( jvm.assets%5Cimage-20200914134730197.png)



### 类加载器

​	对于任意一个类，都需要由加载它的类加载器和这个类本身一同确立其在java虚拟机中的唯一性，每一个类加载器，都拥有一个独立的类名空间。

​	**比较两个类是否“相等”，只有在这两个类是由同一个类加载器加载的前提下，才有意义**，否则即使这两个类来源于同一个class文件，被同一个虚拟机加载，只要加载的类加载器不同，那么这两个类就必定不同。

​	这里的“相等” 包括代表类的class对象的equals()方法、isAssignableFrom()方法、isInstance()方法返回结果，也包括使用instanceof挂件子做对象所属关系判定等情况。

``` java
public class ClassLoaderTest {
    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ClassLoader myLoader = new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                try {
                    String fileName = name.substring(name.lastIndexOf(".") + 1) + ".class";
                    InputStream is = getClass().getResourceAsStream(fileName);
                    if(is == null){
                        return super.loadClass(name);
                    }
                    byte[] bytes = new byte[is.available()];
                    is.read(bytes);
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new ClassNotFoundException(name);
                }
            }
        };

        Class<?> obj = myLoader.loadClass("com.xiaohui.jvm.classloading.ClassLoaderTest");
        System.out.println(obj.getClassLoader());
        System.out.println(ClassLoaderTest.class.getClassLoader());

        Object o = obj.newInstance(); // 使用的是 自定义类加载器 加载的
        Object o2 = new ClassLoaderTest(); // 使用的是 系统应用程序类加载器 加载的
        
        System.out.println(o instanceof com.xiaohui.jvm.classloading.ClassLoaderTest);
        System.out.println(o2 instanceof com.xiaohui.jvm.classloading.ClassLoaderTest);
    }
}

/**
com.xiaohui.jvm.classloading.ClassLoaderTest$1@74a14482
sun.misc.Launcher$AppClassLoader@18b4aac2
false
true
*/
```

![image-20200914162835267]( jvm.assets%5Cimage-20200914162835267.png)



### 双亲委派



从java虚拟机的角度，只存在两种不同的类加载器：

1. 一种是启动类加载器（Bootstrap ClassLoader），这个类加载器使用C++实现，是虚拟机自身的一部分。
2. 两一种是所有其他的类加载器，这些加载器都由java语言实现，独立于虚拟机外部，并且全都继承抽象类java.lang.ClassLoader.

从开发人员角度看，类加载器进一步细分：

1. 启动类加载器（Bootstrap ClassLoader）:放在<JAVA_HOME>\lib 目录中。或被-Xbootclasspath参数所指定的路径中的，并且是虚拟机识别的类库加载到虚拟机内存中（仅按照文件名识别，如rt.jar，名字不符合的类库即使放在lib目录中也不会被加载）

   这些启动类无法被java程序直接引用，用户在编写自定义类加载器时，如果需要把加载请求委派给引导类加载器，那直接使用null代替即可。

2. 扩展类加载器（Extension ClassLoader） :这个加载器由sun.misc.Launcher$ExtClassLoader实现，负责<JAVA_HOME>\lib\ext目录中，或者被java.ext.dir系统变量所指定的路径中的所有类库，开发者可以直接使用扩展类加载器。

3. 应用程序类加载器（Application ClassLoader）:这个类加载器由sun.misc.Launcer$AppClassLoader实现（*由于这个类加载器是ClassLoader中的getSystemClassLoader()方法的返回值，所以也称为系统类加载器。*）。它负责加载用户类路径（ClassPath）上所指定的类库。



双亲委派模型：

![image-20200914164605575]( jvm.assets%5Cimage-20200914164605575.png)

工作过程：

![image-20200914164813924]( jvm.assets%5Cimage-20200914164813924.png)

好处：

![image-20200914164914593]( jvm.assets%5Cimage-20200914164914593.png)

代码过程：

![image-20200914165924120]( jvm.assets%5Cimage-20200914165924120.png)

```java
 protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {
                        c = parent.loadClass(name, false);
                    } else {
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                    // ClassNotFoundException thrown if class not found
                    // from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order
                    // to find the class.
                    long t1 = System.nanoTime();
                    c = findClass(name);

                    // this is the defining class loader; record the stats
                    sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    sun.misc.PerfCounter.getFindClasses().increment();
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
```



### 破坏双亲委托

![image-20200914170605710]( jvm.assets%5Cimage-20200914170605710.png)













## 问题排查：

### 1.  OutOfmemoryErrory异常

> 注意:需要使用映像分析插件（**JProfilerl**），参照https://www.cnblogs.com/jpfss/p/11057440.html 进行配置和使用

#### 1.1 Java堆溢出

VM 参数： -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError

代码：

```java
package com.xiaohui.jvm;

import java.util.ArrayList;
import java.util.List;

public class OutOfMemory {
    public static void main(String[] args) {
        List<TestObject> list=new ArrayList<TestObject>();
        while(true){
            list.add(new TestObject());
        }
    }

    static class TestObject{}
}

```

返回内容：

```java
java.lang.OutOfMemoryError: Java heap space
Dumping heap to java_pid13804.hprof ...
Heap dump file created [28277306 bytes in 0.061 secs]
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
	at java.util.Arrays.copyOf(Arrays.java:3210)
	at java.util.Arrays.copyOf(Arrays.java:3181)
	at java.util.ArrayList.grow(ArrayList.java:265)
	at java.util.ArrayList.ensureExplicitCapacity(ArrayList.java:239)
	at java.util.ArrayList.ensureCapacityInternal(ArrayList.java:231)
	at java.util.ArrayList.add(ArrayList.java:462)
	at com.xiaohui.jvm.OutOfMemory.main(OutOfMemory.java:10)
```

排查思路：

 1. 使用内存映像工具确认内存中的对象是否是必要的，也就是先分清楚到底是出现了内存泄露(Memory Leak)还是内存溢出(Memory Overflow)

    > ![image-20201002172843983]( jvm.assets%5Cimage-20201002172843983.png)
    >
    > ​											使用JProfilerl打开的堆转储快照文件

2. 如果是**内存泄露**，可以进一步通过工具**查看泄露对象到GC Roots的引用链**，于是就能找到泄露对象是通过怎样的路径与GC Roots相关联并导致垃圾手机器无法自动回收它们的。掌握了泄露对象的类型信息及GC Roots引用链信息，就可以比较准确的定位出泄露代码的位置

3. 如果是**内存溢出**，换句话说就是内存中的对象确实都还或者，那就应该**检查虚拟机堆参数（-Xmx与-Xms）,与机器物理内存对比是否还可以调大**。并且**从代码上检查是否存在某些对象生命周期过长，持有状态时间过长的情况，尝试减少程序运行期的内存消耗**



#### 1.2 虚拟机栈和本地方法栈溢出

> -Xoss参数： 设置本地方法栈大小（由于虚拟机不区分虚拟机栈和本地方法栈，因此实际上该参数设置无效）
>
> -Xss参数：栈容量

栈异常分为： 

- 如果线程请求的栈深度大于虚拟机所允许的最大深度，则抛出StackOverflowError异常
- 如果虚拟机在扩展栈时无法申请到足够的内存空间，则抛出OutOfMemoryError异常

总结： 当栈空间无法继续分配时，到底是内存太小，还是使用的栈空间太大



测试场景：

- 使用-Xss参数减少栈内容量，结果：抛出StackOverflowError异常，异常出现时，输出的堆栈深度相应缩小

  >vm参数： -Xss128k
  >
  >代码：
  >
  >```java
  >package com.xiaohui.jvm;
  >
  >/**
  > * VM Args: -Xss128k
  > */
  >public class StackOverflowTest {
  >    private int stackLength = 1;
  >    public void stackLeak(){
  >        stackLength ++;
  >        stackLeak(); // 这里不停的调用自身的栈
  >    }
  >
  >    public static void main(String[] args) throws Exception {
  >        StackOverflowTest stackOverflowTest = new StackOverflowTest();
  >        try {
  >            stackOverflowTest.stackLeak();
  >        } catch (Exception e) {
  >            System.out.println("stack length:"+ stackOverflowTest.stackLength);
  >            throw e;
  >        }
  >    }
  >}
  >
  >```
  >
  >返回：
  >
  >```java
  >Exception in thread "main" java.lang.StackOverflowError
  >	at com.xiaohui.jvm.StackOverflowTest.stackLeak(StackOverflowTest.java:9)
  >	at com.xiaohui.jvm.StackOverflowTest.stackLeak(StackOverflowTest.java:10)
  >	at com.xiaohui.jvm.StackOverflowTest.stackLeak(StackOverflowTest.java:10)
  >	at com.xiaohui.jvm.StackOverflowTest.stackLeak(StackOverflowTest.java:10)
  >	....
  >```

  结果表明：

  1. 在单个线程下，无论是由于栈帧太大还是虚拟机栈容量太小，当内存无法分配的时候，虚拟机抛出的都是StackOverflowError异常
  2. 多线程下，可以产生内存溢出异常，在这种情况下，为每个线程的栈分配的内训越大，反而与容易产生内存溢出

  解决方案： 如果使用虚拟机默认参数，栈深度在大多数情况下达到1000~2000完全没问题，但是如果是建立过多线程导致内存移除，在不减少线程数或者更换64位虚拟机情况下，只能通过减少最大堆和减少栈容量来换取更多的线程



#### 1.3 方法区和运行时常量池溢出

> -XX:PermSize 和 -XX:MaxPermSize ：设置方法区大小，从而限制其中常量池的容量
>
> JDK1.6 及以前版本中，由于常量池分配在永久代中，可以通过上述参数设置限制方法区的大小，

vm参数：-XX:PermSize=10M -XX:MaxPermSize=10M

代码：

>以下代码在1.6及以前都会出现内存溢出的现象，但1.7以后逐步取消永久代 的方式，因此不会报错

```java
package com.xiaohui.jvm;

import java.util.ArrayList;
import java.util.List;

public class RuntimeConstantPoolOOM {
    public static void main(String[] args) {
        // 使用List保持常量池引用，笔描Full GC回收常量池行为
        List<String> list = new ArrayList();
        int i =0;
        while (true){
            list.add(String.valueOf(i++).intern());
        }
    }
}
```

 结果：

​	1.6及以前版本会内存溢出，1.7以上的不会



场景2：借助CGLib使方法区出现内存溢出异常

>设计思路： 
>
>​	方法区用于存放Class的相关信息，如类名、访问修饰符、常量池、字段描述、方法描述等。对于这些区域的测试，基本思路是运行时产生大量的类去填充方法区，直到溢出。
>
>​	两种方式：要么使用反射动态生成类，要么使用CGLib技术直接操作字节码，让字节码运行时产生大量的类
>
>代码：
>
>```java
>package com.xiaohui.jvm;
>
>import net.sf.cglib.proxy.Enhancer;
>import net.sf.cglib.proxy.MethodInterceptor;
>import net.sf.cglib.proxy.MethodProxy;
>
>import java.lang.reflect.Method;
>
>/**
> * vm Args: -XX:PermSize=10M -XX:MaxPermSize=10M
> */
>public class JavaMethodAreaOOM {
>    public static void main(String[] args) {
>        while (true){
>            Enhancer enhancer = new Enhancer();
>            enhancer.setSuperclass(OOMObject.class);
>            enhancer.setUseCache(false);
>            enhancer.setCallback(new MethodInterceptor() {
>                public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
>                    return proxy.invokeSuper(obj, args);
>                }
>            });
>            enhancer.create();
>        }
>    }
>    static class OOMObject {
>
>    }
>}
>
>```
>
>



#### 1.4  本机直接内存溢出

> -XX:MaxDirecMemorySize ： 设置DirectMemory容量，默认与Java堆最大值（-Xmx指定）一样





## jvm内存模型jdk1.7和jdk1.8的区别

本文主要比较下jvm虚拟机的内存模型在jdk1.7到jdk1.8之后的变化

**首先我用下图让大家了解它的构造区别：**

![在这里插入图片描述](D:%5CLearningSpace%5Clearning-notes%5Cjvm%5Cjvm.assets%5C20190706155611749.PNG)

**从图中我们可以看到，jdk1.7相较jdk1.8多了一个方法区（一般这么说，其实是永久代），而jdk1.8则多了一个元空间。**

首先，我们应该了解方法区只是JVM规范定义（即jdk1.7和jdk1.8都有方法区这个概念），而永久代和元空间则是方法区的实现

**永久代和元空间的区别**
永久代：在运行时数据区域开辟空间实现方法区
元空间：在本地内存区域开辟空间实现方法区

**为什么要移除永久代呢？**
永久代中的元数据信息在每次FullGc时可能被收集，为永久代分配多少空间很难确定，超出指定空间容易造成内存泄漏

**元空间的特点**

1. 类及相关的元数据的生命周期与类加载器的一致
2. 每个加载器有专门的存储空间
3. 只进行线性分配
4. 不会单独回收某个类
5. 省掉了GC扫描及压缩的时间
6. 元空间里的对象的位置是固定的
7. 如果GC发现某个类加载器不再存活了，会把相关的空间整个回收掉