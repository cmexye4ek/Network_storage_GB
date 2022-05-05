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
import ru.gb.storage.commons.message.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Scanner;


public class Client {

    //    private static final GlobalChannelTrafficShapingHandler GLOBAL_CHANNEL_TRAFFIC_SHAPING_HANDLER = new GlobalChannelTrafficShapingHandler(Executors.newSingleThreadScheduledExecutor(), 1000);
    private final Scanner scanner = new Scanner(System.in);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private String command;
    private String login;
    private String password;
    private boolean isAuth = false;

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
                                            System.out.println("Connection to server established");
                                            auth(ctx);
//                                            final FileRequestMessage message = new FileRequestMessage();
//                                            message.setPath("D:\\p2p\\en_ru_windows_server_2008_r2_[4 in 1][05.2019].iso");
//                                            ctx.writeAndFlush(message);
//                                            System.out.println("File request sent");
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws IOException {
                                            if (msg instanceof TextMessage) {
                                                TextMessage message = (TextMessage) msg;
                                                if (message.getText().startsWith("/auth_error_")) {
                                                    isAuth = false;
                                                    if (message.getText().equals("/auth_error_login_exist")) {
                                                        System.out.println("User with this login already exist, chose another login");
                                                        auth(ctx);
                                                    }
                                                    if (message.getText().equals("/auth_error_wrong_pass")) {
                                                        System.out.println("Wrong password, try again");
                                                        auth(ctx);
                                                    }
                                                    if (message.getText().equals("/auth_error_login_not_exist")) {
                                                        System.out.println("User with this login is not exist, check your login or register");
                                                        auth(ctx);
                                                    }
                                                }
                                                if (message.getText().startsWith("/success_")) {
                                                    if (message.getText().equals("/success_reg")) {
                                                        System.out.println("Registration successful");
                                                    }
                                                    if (message.getText().equals("/success_auth")) {
                                                        System.out.println("Authentication successful");
                                                    }
                                                }
                                            }
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
                                                if (message.getStatus().equals("auth_after_reg")) {
                                                    message.setStatus("authentication");
                                                    ctx.writeAndFlush(message);
                                                }

                                            }
                                            if (msg instanceof FileListMessage) {
                                                FileListMessage message = (FileListMessage) msg;
                                                listFiles(message);
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


    private void auth(ChannelHandlerContext ctx) {
        final AuthMessage msg = new AuthMessage();
        while (!isAuth) {
            System.out.println("Type 'register' for register new user or 'auth' for authentication with exist user");
            command = scanner.nextLine();
            if (command.equalsIgnoreCase("register")) {
                msg.setStatus("register");
            } else if (command.equalsIgnoreCase("auth")) {
                msg.setStatus("authentication");
            } else {
                System.out.println("Wrong command, type 'register' for registration new user or 'auth' for authentication with exist user");
                continue;
            }
            System.out.println("Login:");
            login = scanner.nextLine();
            System.out.println("Password:");
            password = scanner.nextLine();
            msg.setLogin(login.strip().toLowerCase());
            msg.setPassword(password.strip().toLowerCase());
            ctx.writeAndFlush(msg);
            isAuth = true;
        }
    }

    private void listFiles(FileListMessage message) throws IOException {
        File[] fileList = message.getFileList();
        int length = 0;
        String size;
        for (File file : fileList
        ) {
            if (file.getName().length() > length) {
                length = file.getName().length();
            }
        }
        final String dirFormat = "%-" + length + "s <DIR>       Last modified: %10s %n";
        final String fileFormat = "%-" + length + "s %-7s     Last modified: %10s %n";
        System.out.println(message.getPath());
        for (File file : fileList
        ) {
            BasicFileAttributes attr = Files.readAttributes(Paths.get(file.getPath()), BasicFileAttributes.class);
            LocalDateTime modTime = attr.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            if (!file.isDirectory()) {
                if (file.length() / 1024 / 1024 > 0) {
                    size = file.length() / 1024 / 1024 + " Mb";
                } else if (file.length() / 1024 > 0) {
                    size = file.length() / 1024 + " Kb";
                } else {
                    size = file.length() + " B";
                }
                System.out.format(fileFormat, file.getName(), size, modTime.format(formatter));
            } else {
                System.out.format(dirFormat, file.getName(), modTime.format(formatter));
            }
        }
        System.out.println("Available space: " + message.getPath().getUsableSpace() / 1024 / 1024 + " Mb");
    }



}