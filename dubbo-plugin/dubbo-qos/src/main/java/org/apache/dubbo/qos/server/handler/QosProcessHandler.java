/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.qos.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.dubbo.common.utils.ExecutorUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class QosProcessHandler extends ByteToMessageDecoder {

    private ScheduledFuture<?> welcomeFuture;

    private String welcome;
    // true means to accept foreign IP
    private boolean acceptForeignIp;

    public static final String PROMPT = "dubbo>";

    public QosProcessHandler(String welcome, boolean acceptForeignIp) {
        this.welcome = welcome;
        this.acceptForeignIp = acceptForeignIp;
    }

    // 这是连接成功了，回写一个 dubbo 的标志，作为欢迎标记
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        welcomeFuture = ctx.executor().schedule(new Runnable() {

            @Override
            public void run() {
                if (welcome != null) {
                    ctx.write(Unpooled.wrappedBuffer(welcome.getBytes()));
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(PROMPT.getBytes()));
                }
            }

        }, 500, TimeUnit.MILLISECONDS);
    }

    // 这里是针对 ctx 的解码操作，但是这里不只是解码，这里：
    // 1. 根据入参的魔数，判断交互的协议，然后动态向 ctx 的 pipeline 管道中添加处理器
    // 2. 将本 handler 从 ctx 上下文拿掉，避免死循环
    // TODO 这里主要是对 netty 的 api 不熟悉，根据功能和代码推测如下：
    // TODO 1. ChannelHandlerContext 是 request 纬度的或者 session 纬度的
    // TODO 2. ChannelHandlerContext 里面带着 pipeline 用于标记处理管道，且是可以动态调整的
    // TODO todo ，以上项，在看 netty api 时关注、验证一下
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 1) {
            return;
        }

        // read one byte to guess protocol
        final int magic = in.getByte(in.readerIndex());

        ChannelPipeline p = ctx.pipeline();
        p.addLast(new LocalHostPermitHandler(acceptForeignIp));
        if (isHttp(magic)) {
            // no welcome output for http protocol
            if (welcomeFuture != null && welcomeFuture.isCancellable()) {
                welcomeFuture.cancel(false);
            }
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(1048576));
            p.addLast(new HttpProcessHandler());
            p.remove(this);
        } else {
            p.addLast(new LineBasedFrameDecoder(2048));
            p.addLast(new StringDecoder(CharsetUtil.UTF_8));
            p.addLast(new StringEncoder(CharsetUtil.UTF_8));
            p.addLast(new IdleStateHandler(0, 0, 5 * 60));
            p.addLast(new TelnetProcessHandler());// 拿到命令执行
            p.remove(this);// 已经完成动态调整和分发，可以把本 handler 拿掉了
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ExecutorUtil.cancelScheduledFuture(welcomeFuture);
            ctx.close();
        }
    }

    // G for GET, and P for POST
    private static boolean isHttp(int magic) {
        return magic == 'G' || magic == 'P';
    }
}
