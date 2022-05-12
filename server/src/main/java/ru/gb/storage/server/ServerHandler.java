package ru.gb.storage.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import ru.gb.storage.commons.message.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.Arrays;

public class ServerHandler extends SimpleChannelInboundHandler<Message> {
    private int counter = 0;
    private File userDir = null;
    private String currentDir;
    private RandomAccessFile raf = null;
    private Connection dbConnector;
    private Statement statement;
    private PreparedStatement pStatement;
    private SecureRandom random = new SecureRandom();
    private byte[] salt = new byte[16];


    public ServerHandler(Connection dbConnector, Statement statement) {
        this.dbConnector = dbConnector;
        this.statement = statement;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("New active channel");
        TextMessage answer = new TextMessage();
        answer.setText("Successfully connection");
        ctx.writeAndFlush(answer);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws IOException, SQLException, InvalidKeySpecException, NoSuchAlgorithmException {
        if (msg instanceof TextMessage) {
            TextMessage message = (TextMessage) msg;
            if (message.getText() != null) {
                if (message.getText().startsWith("-")) {
                    if (currentDir == null) {
                        currentDir = userDir.getPath();
                    }
                    if (message.getText().equals("-ls")) {
                        sendListFiles(currentDir, ctx);
                    }
                    if (message.getText().startsWith("-cd")) {
                        String newPath = message.getText().split("root", 2)[1];
                        currentDir = userDir.getPath() + newPath;
                        sendListFiles(currentDir, ctx);

                    }
                    if (message.getText().startsWith("-mkdir")) {
                        String dirName = message.getText().split(" ", 2)[1];
                        File newDir = new File(currentDir + File.separator + dirName);
                        if (newDir.mkdir()) {
                            sendListFiles(currentDir, ctx);
                        } else {
                            message.setText("/error_mkdir");
                            ctx.writeAndFlush(message);
                        }
                    }
                    if (message.getText().startsWith("-rm")) {
                        String objName = message.getText().split(" ", 2)[1];
                        File deletedObj = new File(currentDir + File.separator + objName);
                        if (deletedObj.delete()) {
                            sendListFiles(currentDir, ctx);
                        } else {
                            message.setText("/error_rm");
                            ctx.writeAndFlush(message);
                        }
                    }
                }
//            ctx.writeAndFlush(msg);
            }
        }
        if (msg instanceof DateMessage) {
            DateMessage message = (DateMessage) msg;
            System.out.println("incoming date message: " + message.getDate());
            ctx.writeAndFlush(msg);
        }
        if (msg instanceof AuthMessage) {
            AuthMessage message = (AuthMessage) msg;
            TextMessage answer = new TextMessage();
            if (message.getStatus().contains("register")) {
                ResultSet credentialsSet = statement.executeQuery("SELECT Login FROM users WHERE Login = '" + message.getLogin() + "'");

                if (credentialsSet.next()) {
                    answer.setText("/auth_error_login_exist");
                } else {
                    register(message);
                    answer.setText("/success_reg");
                    message.setStatus("auth_after_reg");
                    ctx.writeAndFlush(message);
                }
            }
            if (message.getStatus().contains("authentication")) {
                ResultSet credentialsSet = statement.executeQuery("SELECT * FROM users WHERE Login = '" + message.getLogin() + "'");
                if (credentialsSet.next()) {
                    if (Arrays.equals(credentialsSet.getBytes("Password"), passHash(message.getPassword(), credentialsSet.getBytes("Salt")))) {
                        userDir = new File("user" + credentialsSet.getString("id"));
                        answer.setText("/success_auth");
                    } else {
                        answer.setText("/auth_error_wrong_pass");
                    }
                } else {
                    answer.setText("/auth_error_login_not_exist");
                }
            }
            ctx.writeAndFlush(answer);
        }
        if (msg instanceof FileRequestMessage) {
            System.out.println("File request received");
            FileRequestMessage frm = (FileRequestMessage) msg;
            String realPath = frm.getPath().replace("root", userDir.getPath());
            if (raf == null) {
                final File file = new File(realPath);
                raf = new RandomAccessFile(file, "r");
                sendFile(ctx);
            }
        }
        if (msg instanceof FileContentMessage) {
            FileContentMessage fcm = (FileContentMessage) msg;
            String realPath = fcm.getPath().replace("root", userDir.getPath());
            try (final RandomAccessFile raf = new RandomAccessFile(realPath, "rw")) {
                raf.seek(fcm.getStartPosition());
                raf.write(fcm.getContent());
                if (fcm.isLast()) {
                    System.out.println("Requested file received");
                    raf.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
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
                System.out.println("File sent in " + counter + " packets");
                counter = 0;
                raf.close();
                raf = null;
            }
        }
    }

    private byte[] passHash(String password, byte[] salt) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return factory.generateSecret(spec).getEncoded();
    }

    private void register(AuthMessage message) throws SQLException, InvalidKeySpecException, NoSuchAlgorithmException {
        random.nextBytes(salt);
        pStatement = dbConnector.prepareStatement("INSERT INTO users (Login, Password, Salt) VALUES (?,?,?)");
        pStatement.setString(1, message.getLogin());
        pStatement.setBytes(2, passHash(message.getPassword(), salt));
        pStatement.setBytes(3, salt);
        pStatement.addBatch();
        pStatement.executeBatch();
        pStatement.close();
        ResultSet credentialsSet = statement.executeQuery("SELECT id FROM users WHERE Login = '" + message.getLogin() + "'");
        userDir = new File("user" + credentialsSet.getString("id"));
        if (userDir.mkdir()) {
            System.out.println("User directory for user " + message.getLogin() + " created successfully");
        } else {
            System.out.println("User directory for user " + message.getLogin() + " creation error");
        }
    }

    private void sendListFiles(String path, ChannelHandlerContext ctx) {
        FileListMessage flm = new FileListMessage();
        File file = new File(path);
        flm.setPath(file);
        File[] temp = file.listFiles();
        if (temp != null) {
            Arrays.sort(temp, (a, b) -> Boolean.compare(b.isDirectory(), a.isDirectory()));
            flm.setFileList(temp);
            ctx.writeAndFlush(flm);
        } else {
            System.out.println("Something wrong with file listing");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws IOException {
        System.out.println("client disconnect");
        if (raf != null) {
            raf.close();
        }
    }
}