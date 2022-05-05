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
    private File userPath = null;
    private RandomAccessFile raf = null;
    private Connection dbConnector;
    private Statement statement;
    private PreparedStatement pStatement;
    private SecureRandom random = new SecureRandom();
    private byte [] salt = new byte[16];


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
            System.out.println("incoming text message: " + message.getText());
            ctx.writeAndFlush(msg);
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
                        answer.setText("/success_auth");
                        userPath = new File("./user" + credentialsSet.getString("id"));
                        sendListFiles(ctx);
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
            if (raf == null) {
                final File file = new File(frm.getPath());
                raf = new RandomAccessFile(file, "r");
                sendFile(ctx);
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
            ctx.channel().writeAndFlush(message).addListener((a) ->
                    {
                        if (!eof) {
                            sendFile(ctx);
                            counter++;
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



    private byte [] passHash (String password, byte [] salt) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return factory.generateSecret(spec).getEncoded();
    }

    private void register (AuthMessage message) throws SQLException, InvalidKeySpecException, NoSuchAlgorithmException {
        random.nextBytes(salt);
        pStatement = dbConnector.prepareStatement("INSERT INTO users (Login, Password, Salt) VALUES (?,?,?)");
        pStatement.setString(1, message.getLogin());
        pStatement.setBytes(2, passHash(message.getPassword(), salt));
        pStatement.setBytes(3, salt);
        pStatement.addBatch();
        pStatement.executeBatch();
        pStatement.close();
        ResultSet credentialsSet = statement.executeQuery("SELECT id FROM users WHERE Login = '" + message.getLogin() + "'");
        userPath = new File("./user" + credentialsSet.getString("id"));
        userPath.mkdir(); // доделать проверку создания папки юзера
    }

    private void sendListFiles (ChannelHandlerContext ctx) {
        FileListMessage flm = new FileListMessage();
        flm.setPath(userPath);
        File [] temp = userPath.listFiles();
        Arrays.sort(temp, (a,b) -> Boolean.compare(b.isDirectory(), a.isDirectory())); //доделать проверку на null
        flm.setFileList(temp);
        ctx.writeAndFlush(flm);
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