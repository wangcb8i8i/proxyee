package com.github.monkeywie.proxyee.intercept;

import com.github.monkeywie.proxyee.server.RequestProto;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import lombok.Getter;
import lombok.Setter;

public class ProxyInterceptPipeline extends AbstractPipeline<ProxyInterceptHandler> {


    public ProxyInterceptPipeline(ProxyInterceptHandler headHandler, ProxyInterceptHandler tailHandler) {
        super(headHandler, tailHandler);
    }

    public void onRequest(Channel clientChannel, HttpRequest httpRequest) throws Exception {
        invoke(h -> {
            try {
                h.onRequest(clientChannel, httpRequest, this);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * @param clientChannel channel between client and proxy
     * @param serverChannel channel between server and proxy
     * @param httpResponse
     * @throws Exception
     */
    public void onResponse(Channel clientChannel, Channel serverChannel, HttpResponse httpResponse) throws Exception {
        invoke(h -> {
            try {
                h.onResponse(clientChannel, serverChannel, httpResponse, this);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void onRequestContent(Channel clientChannel, HttpContent httpContent) throws Exception {
        invoke(h -> {
            try {
                h.onRequestContent(clientChannel, httpContent, this);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void onResponseContent(Channel clientChannel, Channel serverChannel, HttpContent httpContent) throws Exception {
        invoke(h -> {
            try {
                h.onResponseContent(clientChannel, serverChannel, httpContent, this);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Getter
    @Setter
    private RequestProto requestProto;
}
