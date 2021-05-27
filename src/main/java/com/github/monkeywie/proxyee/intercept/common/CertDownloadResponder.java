package com.github.monkeywie.proxyee.intercept.common;

import com.github.monkeywie.proxyee.crt.CertUtil;
import com.github.monkeywie.proxyee.server.HttpProxyCACertFactory;
import io.netty.handler.codec.http.*;
import lombok.Setter;
import lombok.experimental.Accessors;

public class CertDownloadResponder implements SelfRequestResponder {

    @Setter
    @Accessors(fluent = true)
    private HttpProxyCACertFactory httpProxyCACertFactory;

    @Override
    public FullHttpResponse respond(HttpRequest httpRequest) throws Exception {
        if (httpRequest.uri().matches("^.*/ca.crt.*$")) {  //下载证书
            byte[] bts = new byte[0];
            try {
                if (httpProxyCACertFactory != null) {
                    bts = httpProxyCACertFactory.getCACert().getEncoded();
                } else {
                    bts = CertUtil.loadCert(Thread.currentThread().getContextClassLoader().getResourceAsStream("ca.crt"))
                            .getEncoded();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-x509-ca-cert");
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, bts.length);
            fullHttpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            fullHttpResponse.content().writeBytes(bts);
            return fullHttpResponse;
        } else if (httpRequest.uri().equals("/")) {
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            String html = "<html><body><div style=\"margin-top:100px;text-align:center;\"><a href=\"ca.crt\">Proxy Server root ca.crt</a></div></body></html>";
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=utf-8");
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, html.getBytes().length);
            fullHttpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            fullHttpResponse.content().writeBytes(html.getBytes());
            return fullHttpResponse;
        }
        return null;
    }
}
