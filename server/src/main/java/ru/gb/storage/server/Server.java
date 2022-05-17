package ru.gb.storage.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.gb.storage.commons.handler.JsonDecoder;
import ru.gb.storage.commons.handler.JsonEncoder;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


public class Server {
    private static final Logger LOGGER = LogManager.getLogger(Server.class.getName());
    private final int port;
    private Connection dbConnector;
    private Statement statement;

    public static void main(String[] args) throws InterruptedException, SQLException, IOException {
        new Server(9000).start();
    }

    public Server(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException, SQLException, IOException {
        DBConnector.dBCreate();
        dbConnector = DBConnector.getConnection();
        statement = dbConnector.createStatement();
        createUserTable();
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
                                    new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 3, 0, 3),
                                    new LengthFieldPrepender(3),
                                    new JsonDecoder(),
                                    new JsonEncoder(),
                                    new ServerHandler(dbConnector));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = server.bind(port).sync();

            LOGGER.log(Level.INFO, "Server started");
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            dbConnector.close();
            LOGGER.log(Level.INFO, "Server stopped");
        }
    }

    private void createUserTable() throws SQLException {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS Users (\n"
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, \n"
                + "Login VARCHAR(50), \n"
                + "Password VARCHAR(50), \n"
                + "Salt VARCHAR(50) \n"
                + ")");
        statement.close();
    }
}