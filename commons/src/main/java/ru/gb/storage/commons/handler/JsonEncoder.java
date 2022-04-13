package ru.gb.storage.commons.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import ru.gb.storage.commons.message.Message;
import java.util.List;

public class JsonEncoder extends MessageToMessageEncoder<Message> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        System.out.println("LOG: Sending new message " + msg);
//        byte[] value = OBJECT_MAPPER.writeValueAsBytes(msg);
//        out.add(ctx.alloc().buffer().writeBytes(value));

        String message = OBJECT_MAPPER.writeValueAsString(msg);
        out.add(message);
    }
}