package com.github.monkeywie.proxyee.exception;

import io.netty.channel.Channel;

public class HttpProxyExceptionHandle {

    public void bootstrapFailed(Throwable e) {
        e.printStackTrace();
    }

    public void frontendFailed(Channel clientChannel, Throwable cause) {
        new Exception(clientChannel.remoteAddress() + " <-> " + clientChannel.localAddress(), cause)
                .printStackTrace();
    }


    public void backendFailed(Channel clientChannel, Channel proxyChannel, Throwable cause) {
        String s = String.format("%s <-> %s <-> %s", clientChannel.remoteAddress(), clientChannel.localAddress()
                , proxyChannel.remoteAddress());
        new Exception(s, cause).printStackTrace();
    }
}
