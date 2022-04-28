package ru.gb.storage.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import ru.gb.storage.commons.handler.JsonDecoder;
import ru.gb.storage.commons.handler.JsonEncoder;
import ru.gb.storage.commons.message.AuthMessage;
import ru.gb.storage.commons.message.FileContentMessage;
import ru.gb.storage.commons.message.FileRequestMessage;
import ru.gb.storage.commons.message.Message;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Scanner;


public class Client {

    //    private static final GlobalChannelTrafficShapingHandler GLOBAL_CHANNEL_TRAFFIC_SHAPING_HANDLER = new GlobalChannelTrafficShapingHandler(Executors.newSingleThreadScheduledExecutor(), 1000);
    private Scanner scanner = new Scanner(System.in);
    private String command;
    private String login;
    private String password;

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        final NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
//                                    GLOBAL_CHANNEL_TRAFFIC_SHAPING_HANDLER,
                                    new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 3, 0, 3),
                                    new LengthFieldPrepender(3),
                                    new JsonDecoder(),
                                    new JsonEncoder(),
                                    new SimpleChannelInboundHandler<Message>() {
                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) {
                                            System.out.println("Connection to server established, please login");
                                            ctx.writeAndFlush(auth());
//                                            final FileRequestMessage message = new FileRequestMessage();
//                                            message.setPath("D:\\p2p\\en_ru_windows_server_2008_r2_[4 in 1][05.2019].iso");
//                                            ctx.writeAndFlush(message);
//                                            System.out.println("File request sent");
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
                                            if (msg instanceof FileContentMessage) {
                                                FileContentMessage fcm = (FileContentMessage) msg;
                                                String path = "D:\\test.iso";
                                                try (final RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
                                                    raf.seek(fcm.getStartPosition());
                                                    raf.write(fcm.getContent());
                                                    if (fcm.isLast()) {
                                                        System.out.println("Requested file received");
                                                        ctx.close();
                                                    }
                                                } catch (IOException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            if (msg instanceof AuthMessage) {
                                                AuthMessage message = (AuthMessage) msg;
                                                System.out.println(message.getStatus());
                                            }
                                        }
                                    }
                            );
                        }
                    });

            System.out.println("Client started");
            Channel channel = bootstrap.connect("localhost", 9000).sync().channel();
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }



    private AuthMessage auth() {
        final AuthMessage msg = new AuthMessage();
        System.out.println("would you like to register or login?");
        command = scanner.nextLine();
        if (command.contains("register")) {
            msg.setStatus("register");
        } else if (command.contains("auth")) {
            msg.setStatus("authentication");
        } else {
            System.out.println("some shit");
        }
        System.out.println("Login: ");
        login = scanner.nextLine();
        System.out.println("Password: ");
        password = scanner.nextLine();
        msg.setLogin(login);
        msg.setPassword(password);
        return msg;
    }


}