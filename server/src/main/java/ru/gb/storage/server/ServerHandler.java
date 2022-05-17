package ru.gb.storage.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.storage.commons.message.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.Arrays;
import java.util.Comparator;

public class ServerHandler extends SimpleChannelInboundHandler<Message> {
    private static final Logger LOGGER = LogManager.getLogger(ServerHandler.class.getName());
    private File userDir = null;
    private String currentDir;
    private String currentUser;
    private String realPath;
    private RandomAccessFile raf = null;
    private Connection dbConnector;
    private Statement statement;
    private PreparedStatement pStatement;
    private SecureRandom random = new SecureRandom();
    private byte[] salt = new byte[16];


    public ServerHandler(Connection dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.log(Level.INFO, "New active channel");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws IOException, SQLException, InvalidKeySpecException, NoSuchAlgorithmException {
        if (msg instanceof RegistrationMessage) {
            RegistrationMessage message = (RegistrationMessage) msg;
            statement = dbConnector.createStatement();
            ResultSet credentialsSet = statement.executeQuery("SELECT Login FROM users WHERE Login = '" + message.getLogin() + "'");
            if (credentialsSet.next()) {
                message.setStatus(RegistrationMessage.Status.REGISTRATION_ERROR_LOGIN_EXIST);
                LOGGER.log(Level.ERROR, "User " + message.getLogin() + " registration failure, login exist");
            } else {
                if (register(message)) {
                    message.setStatus(RegistrationMessage.Status.SUCCESS);
                    LOGGER.log(Level.INFO, "User " + message.getLogin() + " successfully registered");
                } else {
                    message.setStatus(RegistrationMessage.Status.REGISTRATION_ERROR_USERFOLDER_CREATION);
                }
            }
            ctx.writeAndFlush(message);
            statement.close();
        }
        if (msg instanceof AuthMessage) {
            AuthMessage message = (AuthMessage) msg;
            statement = dbConnector.createStatement();
            ResultSet credentialsSet = statement.executeQuery("SELECT * FROM users WHERE Login = '" + message.getLogin() + "'");
            if (credentialsSet.next()) {
                if (Arrays.equals(credentialsSet.getBytes("Password"), passHash(message.getPassword(), credentialsSet.getBytes("Salt")))) {
                    userDir = new File("user" + credentialsSet.getString("id"));
                    currentUser = "User: " + message.getLogin();
                    message.setStatus(AuthMessage.Status.SUCCESS);
                } else {
                    message.setStatus(AuthMessage.Status.AUTH_ERROR_WRONG_PASSWORD);
                    LOGGER.log(Level.ERROR, "User " + message.getLogin() + " authentication failure, wrong password");
                }
            } else {
                message.setStatus(AuthMessage.Status.AUTH_ERROR_WRONG_LOGIN);
                LOGGER.log(Level.ERROR, "User " + message.getLogin() + " authentication failure, login not exist");
            }
            ctx.writeAndFlush(message);
            statement.close();
        }
        if (msg instanceof ChangeDirectoryMessage) {
            ChangeDirectoryMessage message = (ChangeDirectoryMessage) msg;
            currentDir = userDir.getPath() + message.getPath();
            File file = new File(currentDir);
            if (file.exists() && file.isDirectory()) {
                message.setStatus(ChangeDirectoryMessage.Status.SUCCESS);
                LOGGER.log(Level.INFO, currentUser + " Change directory to " + currentDir);
            } else {
                message.setStatus(ChangeDirectoryMessage.Status.ERROR);
                LOGGER.log(Level.ERROR, currentUser + " failed to change directory to " + currentDir);
            }
            ctx.writeAndFlush(message);
        }
        if (msg instanceof FileListRequestMessage) {
            if (currentDir == null) {
                currentDir = userDir.getPath();
            }
            sendListFiles(currentDir, ctx);
            LOGGER.log(Level.INFO, "Files list of " + currentDir + " sent to " + currentUser);
        }
        if (msg instanceof MakeDirMessage) {
            MakeDirMessage message = (MakeDirMessage) msg;
            File newDir = new File(message.getPath().replace("root", userDir.getPath()));
            if (newDir.mkdir()) {
                message.setStatus(MakeDirMessage.Status.SUCCESS);
                LOGGER.log(Level.INFO, currentUser + " create new directory " + newDir.getPath());
            } else {
                message.setStatus(MakeDirMessage.Status.ERROR);
                LOGGER.log(Level.ERROR, currentUser + " new directory " + newDir.getPath() + " not created ");
            }
            ctx.writeAndFlush(message);
        }
        if (msg instanceof RemoveFileMessage) {
            RemoveFileMessage message = (RemoveFileMessage) msg;
            File deletedObj = new File(message.getPath().replace("root", userDir.getPath()));
            if (deletedObj.isDirectory() && deletedObj.listFiles().length != 0 && message.getStatus() != RemoveFileMessage.Status.CONFIRMED) {
                message.setStatus(RemoveFileMessage.Status.CONFIRMATION_REQUEST);
                LOGGER.log(Level.INFO, currentUser + " trying to delete not empty folder " + deletedObj.getPath() + " confirmation request sent");
            } else {
                try {
                    Files.walk(deletedObj.toPath())
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (Exception e) {
                    LOGGER.log(Level.ERROR, currentUser + " try to delete not exists object " + deletedObj.getPath(), e);
                    message.setStatus(RemoveFileMessage.Status.ERROR);
                    ctx.writeAndFlush(message);
                    return;
                }
                if (!deletedObj.exists()) {
                    message.setStatus(RemoveFileMessage.Status.SUCCESS);
                    LOGGER.log(Level.INFO, currentUser + " object " + deletedObj.getPath() + " deleted");
                } else {
                    message.setStatus(RemoveFileMessage.Status.ERROR);
                    LOGGER.log(Level.ERROR, currentUser + " object " + deletedObj.getPath() + " not deleted");
                }
            }
            ctx.writeAndFlush(message);
        }
        if (msg instanceof MoveFileMessage) {
            MoveFileMessage message = (MoveFileMessage) msg;
            File source = new File(message.getSourcePath().replace("root", userDir.getPath()));
            File destination = new File(message.getDestinationPath().replace("root", userDir.getPath()));
            if (moveFile(source.toPath(), destination.toPath())) {
                message.setStatus(MoveFileMessage.Status.SUCCESS);
                LOGGER.log(Level.INFO, currentUser + " object " + source.getPath() + " moved or renamed to " + destination.getPath());
            } else {
                message.setStatus(MoveFileMessage.Status.ERROR);
            }
            ctx.writeAndFlush(message);
        }
        if (msg instanceof FileRequestMessage) {
            FileRequestMessage message = (FileRequestMessage) msg;
            realPath = message.getPath().replace("root", userDir.getPath());
            LOGGER.log(Level.INFO, currentUser + " requests to download a file " + realPath);
            if (raf == null) {
                final File file = new File(realPath);
                raf = new RandomAccessFile(file, "r");
                LOGGER.log(Level.INFO, "Started sending file " + realPath + " to " + currentUser);
                sendFile(ctx);
            }
        }
        if (msg instanceof FileContentMessage) {
            FileContentMessage message = (FileContentMessage) msg;
            realPath = message.getPath().replace("root", userDir.getPath());
            try (final RandomAccessFile randomAccessFile = new RandomAccessFile(realPath, "rw")) {
                randomAccessFile.seek(message.getStartPosition());
                randomAccessFile.write(message.getContent());
                if (message.isLast()) {
                    LOGGER.log(Level.INFO, "File " + realPath + " received from " + currentUser);
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.FATAL, "File " + realPath + " received from " + currentUser + " ,FATAL ERROR, server crashed");
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
            final FileContentMessage fileContentMessage = new FileContentMessage();
            fileContentMessage.setStartPosition(raf.getFilePointer());
            raf.read(fileContent);
            fileContentMessage.setFileLength(raf.length());
            fileContentMessage.setContent(fileContent);
            final boolean endOfFile = raf.getFilePointer() == raf.length();
            fileContentMessage.setLast(endOfFile);
            ctx.channel().writeAndFlush(fileContentMessage).addListener((a) ->
            {
                if (!endOfFile) {
                    sendFile(ctx);
                }
            });
            if (endOfFile) {
                LOGGER.log(Level.INFO, "File " + realPath + " sent to " + currentUser);
                raf.close();
                raf = null;
            }
        }
    }

    private boolean moveFile(Path source, Path destination) {
        if (source.toFile().isDirectory()) {
            for (File file : source.toFile().listFiles()) {
                moveFile(file.toPath(), destination.resolve(source.relativize(file.toPath())));
            }
        }
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, currentUser + " object " + source + " remain unchanged, error in 'moveFile' method");
            return false;
        }
    }

    private byte[] passHash(String password, byte[] salt) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return factory.generateSecret(spec).getEncoded();
    }

    private boolean register(RegistrationMessage message) throws SQLException, InvalidKeySpecException, NoSuchAlgorithmException {
        random.nextBytes(salt);
        dbConnector.setAutoCommit(false);
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
            dbConnector.commit();
            LOGGER.log(Level.INFO, "User directory for user " + message.getLogin() + " created successfully");
            return true;
        } else {
            dbConnector.rollback();
            LOGGER.log(Level.FATAL, "User directory for " + message.getLogin() + " not created, user not registered");
            return false;
        }
    }

    private void sendListFiles(String path, ChannelHandlerContext ctx) {
        FileListMessage fileListMessage = new FileListMessage();
        File file = new File(path);
        if (file.exists() && file.isDirectory()) {
            File[] temp = file.listFiles();
            fileListMessage.setPath(file);
            Arrays.sort(temp, (a, b) -> Boolean.compare(b.isDirectory(), a.isDirectory()));
            fileListMessage.setFileList(temp);
            fileListMessage.setSpace(file.getUsableSpace());
            fileListMessage.setStatus(FileListMessage.Status.SUCCESS);
        } else {
            LOGGER.log(Level.ERROR, currentUser + " use wrong path for source of list files, error sent");
            fileListMessage.setStatus(FileListMessage.Status.ERROR_WRONG_PATH);
            currentDir = userDir.getPath();
        }
        ctx.writeAndFlush(fileListMessage);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        LOGGER.log(Level.FATAL, "netty error ", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws IOException {
        LOGGER.log(Level.INFO, currentUser + " disconnected");
        if (raf != null) {
            raf.close();
        }
        ctx.close();
    }
}