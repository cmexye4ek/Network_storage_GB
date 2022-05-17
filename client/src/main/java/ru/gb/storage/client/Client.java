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
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;


public class Client {
    private final Scanner scanner = new Scanner(System.in);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private FileListRequestMessage fileListRequestMessage = new FileListRequestMessage();
    private RandomAccessFile raf = null;
    private String command;
    private String sourcePath;
    private String destPath;
    private boolean isAuth;
    long startTime = 0;


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
                                            if (msg instanceof FileContentMessage) {
                                                FileContentMessage message = (FileContentMessage) msg;
                                                try (final RandomAccessFile randomAccessFile = new RandomAccessFile(destPath, "rw")) {
                                                    randomAccessFile.seek(message.getStartPosition());
                                                    randomAccessFile.write(message.getContent());
                                                    if (startTime == 0) {
                                                        startTime = System.currentTimeMillis();
                                                    }
                                                    printProgress(startTime, message.getFileLength(), message.getStartPosition());
                                                    if (message.isLast()) {
                                                        System.out.println();
                                                        System.out.println("Requested file received");
                                                        startTime = 0;
                                                        randomAccessFile.close();
                                                        FileListRequestMessage fileListRequestMessage = new FileListRequestMessage();
                                                        ctx.writeAndFlush(fileListRequestMessage);
                                                    }
                                                } catch (IOException e) {
                                                    System.out.println("FATAL ERROR OCCURRED WHILE RECEIVING FILE, client crashed");
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            if (msg instanceof RegistrationMessage) {
                                                RegistrationMessage message = (RegistrationMessage) msg;
                                                if (message.getStatus() == RegistrationMessage.Status.SUCCESS) {
                                                    System.out.println("Registration successful");
                                                    AuthMessage authMessage = new AuthMessage();
                                                    authMessage.setLogin(message.getLogin());
                                                    authMessage.setPassword(message.getPassword());
                                                    ctx.writeAndFlush(authMessage);
                                                } else {
                                                    isAuth = false;
                                                    if (message.getStatus() == RegistrationMessage.Status.REGISTRATION_ERROR_LOGIN_EXIST) {
                                                        System.out.println("User with this login already exist, chose another login");
                                                    } else {
                                                        System.out.println("User folder creation error, try register again");
                                                    }
                                                    auth(ctx);
                                                }
                                            }
                                            if (msg instanceof AuthMessage) {
                                                AuthMessage message = (AuthMessage) msg;
                                                if (message.getStatus() == AuthMessage.Status.SUCCESS) {
                                                    System.out.println("Authentication successful");
                                                    ctx.writeAndFlush(fileListRequestMessage);
                                                } else {
                                                    isAuth = false;
                                                    if (message.getStatus() == AuthMessage.Status.AUTH_ERROR_WRONG_LOGIN) {
                                                        System.out.println("User with this login is not exist, check your login or register");
                                                        auth(ctx);
                                                    }
                                                    if (message.getStatus() == AuthMessage.Status.AUTH_ERROR_WRONG_PASSWORD) {
                                                        System.out.println("Wrong password, try again");
                                                        auth(ctx);
                                                    }
                                                }
                                            }
                                            if (msg instanceof FileListMessage) {
                                                FileListMessage message = (FileListMessage) msg;
                                                if (message.getStatus() == FileListMessage.Status.SUCCESS) {
                                                    listFiles(message);
                                                } else {
                                                    System.out.println("Error with file listing, something went horribly wrong, try again");
                                                }
                                                menu(ctx);
                                            }
                                            if (msg instanceof ChangeDirectoryMessage) {
                                                ChangeDirectoryMessage message = (ChangeDirectoryMessage) msg;
                                                if (message.getStatus() == ChangeDirectoryMessage.Status.SUCCESS) {
                                                    System.out.println("Directory changed");
                                                    ctx.writeAndFlush(fileListRequestMessage);
                                                } else {
                                                    System.out.println("Directory does not exist");
                                                    menu(ctx);
                                                }
                                            }
                                            if (msg instanceof MakeDirMessage) {
                                                MakeDirMessage message = (MakeDirMessage) msg;
                                                if (message.getStatus() == MakeDirMessage.Status.SUCCESS) {
                                                    System.out.println("Directory creating success");
                                                } else {
                                                    System.out.println("Error with directory creation");
                                                }
                                                ctx.writeAndFlush(fileListRequestMessage);
                                            }
                                            if (msg instanceof RemoveFileMessage) {
                                                RemoveFileMessage message = (RemoveFileMessage) msg;
                                                if (message.getStatus() == RemoveFileMessage.Status.CONFIRMATION_REQUEST) {
                                                    System.out.println("WARNING, FOLDER IS NOT EMPTY, ALL CONTENT OF THE FOLDER WILL BE DELETED \n" +
                                                            "Confirm operation? (y/n): ");
                                                    command = scanner.nextLine();
                                                    if (command.equals("y")) {
                                                        message.setStatus(RemoveFileMessage.Status.CONFIRMED);
                                                        ctx.writeAndFlush(message);
                                                    }
                                                } else {
                                                    if (message.getStatus() == RemoveFileMessage.Status.SUCCESS) {
                                                        System.out.println("Object removed");
                                                    } else {
                                                        System.out.println("Deleting object error");
                                                    }
                                                }
                                                ctx.writeAndFlush(fileListRequestMessage);
                                            }
                                            if (msg instanceof MoveFileMessage) {
                                                MoveFileMessage message = (MoveFileMessage) msg;
                                                if (message.getStatus() == MoveFileMessage.Status.SUCCESS) {
                                                    System.out.println("Object moved/renamed");
                                                } else {
                                                    System.out.println("Error, object not moved/renamed");
                                                }
                                                ctx.writeAndFlush(fileListRequestMessage);
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
        while (!isAuth) {
            System.out.println("Type 'register' for register new user or 'auth' for authentication with exist user");
            command = scanner.nextLine();
            if (command.equalsIgnoreCase("register") || command.equalsIgnoreCase("auth")) {
                System.out.println("Login:");
                final String login = scanner.nextLine();
                System.out.println("Password:");
                final String password = scanner.nextLine();
                if (command.equalsIgnoreCase("register")) {
                    final RegistrationMessage regMsg = new RegistrationMessage();
                    regMsg.setLogin(login.strip().toLowerCase());
                    regMsg.setPassword(password.strip().toLowerCase());
                    ctx.writeAndFlush(regMsg);
                } else {
                    final AuthMessage authMsg = new AuthMessage();
                    authMsg.setLogin(login.strip().toLowerCase());
                    authMsg.setPassword(password.strip().toLowerCase());
                    ctx.writeAndFlush(authMsg);
                }
                isAuth = true;
            } else {
                System.out.println("Wrong command");
            }

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
        String realUserDir = message.getPath().split("\\\\", 2)[0];
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

    private void printProgress(long startTime, long total, long current) {
        String units;
        if (total / 1024 / 1024 > 0) {
            total = total / 1024 / 1024;
            current = current / 1024 / 1024;
            units = "Mb";
        } else if (total / 1024 > 0) {
            total = total / 1024;
            current = current / 1024;
            units = "Kb";
        } else {
            units = "B";
        }

        long eta = current == 0 ? 0 :
                (total - current) * (System.currentTimeMillis() - startTime) / current;

        String etaHms = current == 0 ? "N/A" :
                String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(eta),
                        TimeUnit.MILLISECONDS.toMinutes(eta) % TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(eta) % TimeUnit.MINUTES.toSeconds(1));

        StringBuilder string = new StringBuilder(140);
        int percent = (int) (current * 100 / total);
        string
                .append('\r')
                .append(String.join("", Collections.nCopies(percent == 0 ? 2 : 2 - (int) (Math.log10(percent)), " ")))
                .append(String.format(" %d%% [", percent))
                .append(String.join("", Collections.nCopies(percent, "=")))
                .append('>')
                .append(String.join("", Collections.nCopies(100 - percent, " ")))
                .append(']')
                .append(String.join("", Collections.nCopies(current == 0 ? (int) (Math.log10(total)) : (int) (Math.log10(total)) - (int) (Math.log10(current)), " ")))
                .append(String.format(" %d/%d%s, ETA: %s", current, total, units, etaHms));

        System.out.print(string);
    }

    private void menu(ChannelHandlerContext ctx) throws IOException {
        while (true) {
            System.out.println("Type command or '-h' for command list:");
            command = scanner.nextLine();
            if (command.startsWith("-")) {
                if (command.equals("-h")) {
                    System.out.println("Commands list: \n" +
                            "-ls - shows a list of files in the current directory within the cloud storage\n" +
                            "-cd [destination path] - changes the current directory to the one specified within the cloud storage \n" +
                            "-mkdir [path] - create a directory with specified name within the cloud storage\n" +
                            "-cp [source path] [destination path] - copy file from source to destination (from cloud storage to local machine or vice versa) \n" +
                            "-mv [old path] [new path] - move or rename file from specified path to new path within the cloud storage \n" +
                            "-rm [path] - delete object(file or directory) from specified path within the cloud storage \n" +
                            "-exit - exit from client application \n" //not implemented yet
                    );
                }
                if (command.equals("-ls")) {
                    FileListRequestMessage fileListRequestMessage = new FileListRequestMessage();
                    ctx.writeAndFlush(fileListRequestMessage);
                }
                if (command.startsWith("-cd")) {
                    ChangeDirectoryMessage changeDirectoryMessage = new ChangeDirectoryMessage();
                    String newPath = command.split("root", 2)[1];
                    changeDirectoryMessage.setPath(newPath);
                    ctx.writeAndFlush(changeDirectoryMessage);
                }
                if (command.startsWith("-mkdir")) {
                    MakeDirMessage makeDirMessage = new MakeDirMessage();
                    String dirName = command.split(" ", 2)[1];
                    makeDirMessage.setPath(dirName);
                    ctx.writeAndFlush(makeDirMessage);
                }
                if (command.startsWith("-cp")) {
                    sourcePath = command.split(" ", 3)[1];
                    destPath = command.split(" ", 3)[2];
                    if (sourcePath.contains("root")) {
                        System.out.println("Sending file");
                        sendFileRequest(ctx);
                    } else {
                        if (raf == null) {
                            final File file = new File(sourcePath);
                            raf = new RandomAccessFile(file, "r");
                            System.out.println("Sending file");
                            sendFile(ctx);
                        }
                    }
                }
                if (command.startsWith("-mv")) {
                    sourcePath = command.split(" ", 3)[1];
                    destPath = command.split(" ", 3)[2];
                    MoveFileMessage moveFileMessage = new MoveFileMessage();
                    moveFileMessage.setSourcePath(sourcePath);
                    moveFileMessage.setDestinationPath(destPath);
                    ctx.writeAndFlush(moveFileMessage);
                }
                if (command.startsWith("-rm")) {
                    RemoveFileMessage removeFileMessage = new RemoveFileMessage();
                    String objectPath = command.split(" ", 2)[1];

                    removeFileMessage.setPath(objectPath);
                    ctx.writeAndFlush(removeFileMessage);
                }
                if (command.equals("-exit")) {
                    exit(ctx);
                }
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
            final FileContentMessage fileContentMessage = new FileContentMessage();
            fileContentMessage.setPath(destPath);
            fileContentMessage.setStartPosition(raf.getFilePointer());
            raf.read(fileContent);
            fileContentMessage.setContent(fileContent);
            final boolean endOfFile = raf.getFilePointer() == raf.length();
            fileContentMessage.setLast(endOfFile);
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            }
            printProgress(startTime, raf.length(), raf.getFilePointer());
            ctx.channel().writeAndFlush(fileContentMessage).addListener((a) ->
            {
                if (!endOfFile) {
                    sendFile(ctx);
                }
            });
            if (endOfFile) {
                System.out.println();
                System.out.println("File sent");
                startTime = 0;
                raf.close();
                raf = null;
                FileListRequestMessage fileListRequestMessage = new FileListRequestMessage();
                ctx.writeAndFlush(fileListRequestMessage);
            }
        }
    }

    private void sendFileRequest(ChannelHandlerContext ctx) {
        final FileRequestMessage fileRequestMessage = new FileRequestMessage();
        fileRequestMessage.setPath(sourcePath);
        ctx.writeAndFlush(fileRequestMessage);
        System.out.println("File request sent");
    }

    private void exit(ChannelHandlerContext ctx) throws IOException {
        ctx.close().addListener(ChannelFutureListener.CLOSE);
        if (raf != null) {
            raf.close();
        }
        System.exit(0);
    }
}