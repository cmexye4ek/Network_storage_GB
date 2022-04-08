package ru.gb.storage.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.awt.desktop.SystemSleepEvent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NettyEchoServer {
    private final int port;

    public static void main(String[] args) throws InterruptedException {
        new NettyEchoServer(9000).start();
    }

    public NettyEchoServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        ArrayList<String> temp = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap server = new ServerBootstrap();
            server
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) {
                            ch.pipeline().addLast(
                                    new ChannelInboundHandlerAdapter() {
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
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = server.bind(port).sync();
            System.out.println("Server started");
            future.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
