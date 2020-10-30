package nia.chapter5;

import io.netty.buffer.*;
import io.netty.channel.Channel;

import java.nio.ByteBuffer;

public class TestDB {
    public static void main(String[] args) {
        ByteBuffer header = ByteBuffer.allocate(8);
        ByteBuffer body = ByteBuffer.allocate(100);
        header.putInt(200);
        header.flip();
        body.put("body".getBytes());
        body.flip();

        ByteBuffer composite = ByteBuffer.allocate(header.remaining()+body.remaining());

        composite.put(header);
        composite.put(body);
        composite.flip();

        int getHead;
        byte[] getBody = new byte[10];
        if(composite.hasArray()){
            getHead = composite.getInt();  // 这里会直接取4个字节，并移动4个字节的位置
            composite.get(getBody,0,composite.remaining());// 解析剩下的字符串对应的字节
            System.out.println("整数head:"+getHead);
            System.out.println("字符串body:"+new String(getBody));
        }

        byteBufComposite();
        write();
        referenceCounting();
        releaseReferenceCountedObject();
    }
    public static void byteBufComposite() {
        CompositeByteBuf messageBuf = Unpooled.compositeBuffer();
        ByteBuf headerBuf = Unpooled.buffer(100); // can be backing or direct
        headerBuf.writeInt(200);
        ByteBuf bodyBuf = Unpooled.buffer(100);;   // can be backing or direct
        bodyBuf.writeBytes("body!!!".getBytes());
        //将 ByteBuf 实例追加到 CompositeByteBuf
        messageBuf.addComponents(headerBuf, bodyBuf);
        //...
        //循环遍历所有的 ByteBuf 实例
//        System.out.println("整数内容："+ messageBuf.readInt());
//
//        byte[] content = new byte[messageBuf.readableBytes()];
//        messageBuf.readBytes(content);
//        System.out.println("字符串内容："+ new String(content));
        for (ByteBuf buf : messageBuf) {
            if(buf.hasArray()){
                byte[] content = new byte[buf.readableBytes()];
                buf.readBytes(content);
                System.out.println("字符串内容："+new String(content));
            }else{
                System.out.println("整数内容："+ buf.readInt());
            }

        }
        //删除位于索引位置为 0（第一个组件）的 ByteBuf
        messageBuf.removeComponent(0); // remove the header

    }

    /**
     * 代码清单 5-8 写数据
     */
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
            System.out.println("整数值为："+byteBuf.readInt());
        }

        ByteBuf contentBytebuf = buffer.readBytes(buffer.readableBytes());
        byte[] content = new byte[contentBytebuf.readableBytes()];
        contentBytebuf.readBytes(content);
        System.out.println("字符串内容："+new String(content));
    }

    /**
     * 代码清单 5-15 引用计数
     * */
    public static void referenceCounting(){
        //分配一个 ByteBuf
        ByteBuf buffer = Unpooled.buffer(10);
        //检查引用计数是否为预期的 1
        assert buffer.refCnt() == 1;
        //...
    }

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

    static class BufferReferenceTest{
        public ByteBuf buffer;
    }
}
