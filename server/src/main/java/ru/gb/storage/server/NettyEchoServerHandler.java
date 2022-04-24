package ru.gb.storage.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayList;

public class NettyEchoServerHandler extends ChannelInboundHandlerAdapter {
    ArrayList<String> temp = new ArrayList<>();
    final StringBuilder sb = new StringBuilder();

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel registered");
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel unregistered");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel active");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel inactive");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("Cause exception");
            System.out.println(cause);
            ctx.close();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {  //мне не очень "красиво" от этой реализации, но увы лучше способа на момент сдачи в голову не пришло.
            final ByteBuf m = (ByteBuf) msg;
            if (!(new String(ByteBufUtil.getBytes(m)).contains(System.lineSeparator()))) {
                System.out.println("Channel read");
                temp.add(new String(ByteBufUtil.getBytes(m)));
                System.out.println(new String(ByteBufUtil.getBytes(m)));
            } else {
                for (String s : temp) {
                    sb.append(s);
                }
                temp.clear();
                System.out.println("New message: " + sb);
                msg = m.writeBytes(("ECHO: " + sb + System.lineSeparator() + System.lineSeparator()).getBytes());
                ctx.writeAndFlush(msg);
                System.out.println("Echo send");
                sb.setLength(0);
            }
        }

}
