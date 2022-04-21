package ru.gb.storage.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import ru.gb.storage.commons.message.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FirstServerHandler extends SimpleChannelInboundHandler<Message> {
    private int counter = 0;
    private RandomAccessFile raf = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("New active channel");
        TextMessage answer = new TextMessage();
        answer.setText("Successfully connection");
        ctx.writeAndFlush(answer);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws IOException {
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
            System.out.println("incoming auth message: " + message.getLogin() + " " + message.getPassword());
            ctx.writeAndFlush(msg);
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
//            ctx.channel().writeAndFlush(message).addListener(new ChannelFutureListener() {
//                @Override
//                public void operationComplete(ChannelFuture future) throws Exception {
//                    if (!eof) {
//                        sendFile(ctx);
//                        counter++;
//                    }
//                }
//            });
            ctx.channel().

                writeAndFlush(message).

                addListener((a) ->

                {
                    if (!eof) {
                        sendFile(ctx);
                        counter++;
                    }
                });
            if(eof)

                {
                    System.out.println("File sent in " + counter + " packets");
                    counter = 0;
                    raf.close();
                    raf = null;
                }
            }
        }

        @Override
        public void exceptionCaught (ChannelHandlerContext ctx, Throwable cause){
            cause.printStackTrace();
            ctx.close();
        }

        @Override
        public void channelInactive (ChannelHandlerContext ctx) throws IOException {
            System.out.println("client disconnect");
            if (raf != null) {
                raf.close();
            }
        }
    }