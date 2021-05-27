package com.github.monkeywie.proxyee.intercept.common;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;

public interface SelfRequestResponder {

    FullHttpResponse respond(HttpRequest httpRequest) throws Exception;
}
