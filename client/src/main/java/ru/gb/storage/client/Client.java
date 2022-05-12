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
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;


public class Client {

    //    private static final GlobalChannelTrafficShapingHandler GLOBAL_CHANNEL_TRAFFIC_SHAPING_HANDLER = new GlobalChannelTrafficShapingHandler(Executors.newSingleThreadScheduledExecutor(), 1000);
    private final Scanner scanner = new Scanner(System.in);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//    private final String currentDir = Paths.get("").toAbsolutePath().toString();
    private RandomAccessFile raf = null;
    private String command;
    private String sourcePath;
    private String destPath;
    private boolean isAuth;
    private int counter = 0;


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
                                                if (message.getText().equals("/error_mkdir")) {
                                                    System.out.println("Error with directory creation");
                                                    menu(ctx);
                                                }
                                                if (message.getText().equals("/error_rm")) {
                                                    System.out.println("Deleting object error, if it is a directory it should be empty");
                                                    menu(ctx);
                                                }
                                                if (message.getText().startsWith("/success_")) {
                                                    if (message.getText().equals("/success_reg")) {
                                                        System.out.println("Registration successful");
                                                    }
                                                    if (message.getText().startsWith("/success_auth")) {
                                                        System.out.println("Authentication successful");
                                                        menu(ctx);
                                                    }
                                                }
                                            }
                                            if (msg instanceof FileContentMessage) {
                                                FileContentMessage fcm = (FileContentMessage) msg;
                                                try (final RandomAccessFile raf = new RandomAccessFile(destPath, "rw")) {
                                                    raf.seek(fcm.getStartPosition());
                                                    raf.write(fcm.getContent());
                                                    if (fcm.isLast()) {
                                                        System.out.println("Requested file received");
                                                        raf.close();
                                                        menu(ctx);
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
                                                menu(ctx);
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
            final String login = scanner.nextLine();
            System.out.println("Password:");
            final String password = scanner.nextLine();
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
        String realUserDir = message.getPath().split("\\\\", 2) [0];
        String fakeUserDir = message.getPath().replace(realUserDir, "root");
        System.out.println("Current directory: " + fakeUserDir + "\\");
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
        System.out.println("Available space: " + message.getSpace() / 1024 / 1024 + " Mb");
    }

    private void menu(ChannelHandlerContext ctx) throws IOException {
        TextMessage message = new TextMessage();
        while (true) {
            System.out.println("Type command or '-h' for command list:");
            command = scanner.nextLine();
            if (command.startsWith("-")) {
                if (command.equals("-h")) {
                    System.out.println("Commands list: \n" +
                            "-ls - shows a list of files in the current directory \n" +
                            "-cd [destination path] - changes the current directory to the one specified within the cloud storage \n" +
                            "-mkdir [name] - create a directory in current directory with specified name \n" +
                            "-cp [source path] [destination path] - copy file from source to destination \n" +
                            "-mv [old path] [new path] - move object(file or directory) from specified path to new path \n" + //not implemented yet
                            "-rm [path] - delete object(file or directory) from specified path \n" +
                            "-rename [path] [new name] - rename object(file or directory) from specified path \n" + //not implemented yet
                            "-exit - exit from application \n" //not implemented yet
                    );
                }
                if (command.equals("-ls")) {
                    message.setText(command);
                }
                if (command.startsWith("-cd")) {
                    message.setText(command);
                }
                if (command.startsWith("-mkdir")) {
                    message.setText(command);
                }
                if (command.startsWith("-cp")) {
                    sourcePath = command.split(" ", 3) [1];
                    destPath = command.split(" ", 3) [2];
                    if (sourcePath.contains("root")) {
                        sendFileRequest(ctx);
                    } else {
                        if (raf == null) {
                            final File file = new File(sourcePath);
                            raf = new RandomAccessFile(file, "r");
                            sendFile(ctx);
                            System.out.println("file sent");
                        }
                    }
                }
                if (command.startsWith("-mv")) {
                    message.setText(command);
                }
                if (command.startsWith("-rm")) {
                    message.setText(command);
                }
                if (command.startsWith("-rename")) {
                    message.setText(command);
                }
                if (command.equals("-exit")) {
                    exit();
                    return;
                }
                ctx.writeAndFlush(message);
                return;
            } else {
                System.out.println("Wrong command");
            }
        }
    }

    private void sendFile(ChannelHandlerContext ctx) throws IOException {
        if (raf != null) {
            final byte[] fileContent;
            final long available = raf.length() - raf.getFilePointer();
            if (available > 64 * 1024) {
                fileContent = new byte[64 * 1024];
            } else {
                fileContent = new byte[(int) available];
            }
            final FileContentMessage message = new FileContentMessage();
            message.setPath(destPath);
            message.setStartPosition(raf.getFilePointer());
            raf.read(fileContent);
            message.setContent(fileContent);
            final boolean eof = raf.getFilePointer() == raf.length();
            message.setLast(eof);
            counter++;
            ctx.channel().writeAndFlush(message).addListener((a) ->
            {
                if (!eof) {
                    sendFile(ctx);
                }
            });
            if (eof) {
                System.out.println("File sent in " + counter + " packet(s)");
                counter = 0;
                raf.close();
                raf = null;
                menu(ctx);
            }
        }
    }

    private void sendFileRequest(ChannelHandlerContext ctx){
        final FileRequestMessage message = new FileRequestMessage();
        message.setPath(sourcePath);
        ctx.writeAndFlush(message);
        System.out.println("File request sent");
    }

    private void exit() {

    }
}