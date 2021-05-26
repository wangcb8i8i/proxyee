package com.github.monkeywie.proxyee.server;

import com.github.monkeywie.proxyee.server.accept.HttpProxyAcceptHandler;
import com.github.monkeywie.proxyee.server.auth.HttpProxyAuthenticationProvider;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.Data;
import lombok.experimental.Accessors;

import java.net.SocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;


@Data
@Accessors(chain = true)
public class HttpProxyServerConfig {
    private SslContext clientSslCtx;
    private String issuer;
    private Date caNotBefore;
    private Date caNotAfter;
    private PrivateKey caPriKey;        //为代理站点自动签发证书的私钥
    private PrivateKey serverPriKey;    //为代理站点动态生成证书的私钥
    private PublicKey serverPubKey;     //为代理站点动态生成证书的公钥
    private EventLoopGroup proxyLoopGroup;
    private int bossGroupThreads;
    private int workerGroupThreads;
    private int proxyGroupThreads;
    private boolean handleSsl;
    private HttpProxyAcceptHandler httpProxyAcceptHandler;
    private HttpProxyAuthenticationProvider authenticationProvider;
    private final AddressResolverGroup<? extends SocketAddress> nameResolver =DefaultAddressResolverGroup.INSTANCE;
}
