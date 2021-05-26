package com.github.monkeywie.proxyee.handler;

import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipeline;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    private Channel clientChannel;
    private ProxyInterceptPipeline httpProxyProxyInterceptPipeline;
    private HttpProxyExceptionHandle httpProxyExceptionHandle;

    public HttpProxyClientHandler(Channel clientChannel,
                                  ProxyInterceptPipeline httpProxyProxyInterceptPipeline,
                                  HttpProxyExceptionHandle httpProxyExceptionHandle) {
        this.clientChannel = clientChannel;
        this.httpProxyExceptionHandle = httpProxyExceptionHandle;
        this.httpProxyProxyInterceptPipeline = httpProxyProxyInterceptPipeline;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //客户端channel已关闭则不转发了
        if (!clientChannel.isOpen()) {
            ReferenceCountUtil.release(msg);
            return;
        }
        log.trace("response from server:{}",msg);
        if (msg instanceof HttpResponse) {
            httpProxyProxyInterceptPipeline.onResponse(clientChannel, ctx.channel(), (HttpResponse) msg);
        } else if (msg instanceof HttpContent) {
            httpProxyProxyInterceptPipeline.onResponseContent(clientChannel, ctx.channel(), (HttpContent) msg);
        } else {
            clientChannel.writeAndFlush(msg);
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().close();
        clientChannel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        clientChannel.close();
        httpProxyExceptionHandle.afterCatch(clientChannel, ctx.channel(), cause);
    }
}
