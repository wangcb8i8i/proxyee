package com.github.monkeywie.proxyee.server;

import com.github.monkeywie.proxyee.crt.CertPool;
import com.github.monkeywie.proxyee.crt.CertUtil;
import com.github.monkeywie.proxyee.exception.HttpProxyExceptionHandle;
import com.github.monkeywie.proxyee.handler.HttpProxyServerHandler;
import com.github.monkeywie.proxyee.intercept.HttpTunnelIntercept;
import com.github.monkeywie.proxyee.intercept.ProxyInterceptPipelineInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class HttpProxyServer {

    private final static InternalLogger log = InternalLoggerFactory.getInstance(HttpProxyServer.class);

    @Setter
    @Accessors(fluent = true)
    private HttpProxyServerConfig serverConfig;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private void init() {
        if (serverConfig == null) {
            serverConfig = new HttpProxyServerConfig();
        }
        if (serverConfig.isSslSupported()) {
            try {
                serverConfig.setPacketAggregated(true);
                serverConfig.setClientSslCtx(
                        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build());
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

                //设置根证书以及对应私钥生成器
                serverConfig.setCaCertFactory(new HttpProxyCACertFactory() {

                    private X509Certificate caCert;
                    private PrivateKey caPriKey;

                    {
                        caCert = CertUtil.loadCert(classLoader.getResourceAsStream("ca.crt"));
                        caPriKey = CertUtil.loadPriKey(classLoader.getResourceAsStream("ca_private.der"));
                    }

                    @Override
                    public X509Certificate getCACert() throws Exception {
                        return caCert;
                    }

                    @Override
                    public PrivateKey getCAPriKey() throws Exception {
                        return caPriKey;
                    }
                });

                //读取CA证书使用者信息
                serverConfig.setIssuer(CertUtil.getSubject(serverConfig.getCaCertFactory().getCACert()));
                //读取CA证书有效时段(server证书有效期超出CA证书的，在手机上会提示证书不安全)
                serverConfig.setCaNotBefore(serverConfig.getCaCertFactory().getCACert().getNotBefore());
                serverConfig.setCaNotAfter(serverConfig.getCaCertFactory().getCACert().getNotAfter());
                //CA私钥用于给动态生成的网站SSL证书签证
                serverConfig.setCaPriKey(serverConfig.getCaCertFactory().getCAPriKey());
                //生产一对随机公私钥用于网站SSL证书动态创建
                KeyPair keyPair = CertUtil.genKeyPair();
                serverConfig.setServerPriKey(keyPair.getPrivate());
                serverConfig.setServerPubKey(keyPair.getPublic());

                serverConfig.setTunnelIntercept(new HttpTunnelIntercept() {
                    @Override
                    public void handle(RequestProto requestProto) {
                        log.debug("tunneled {}", requestProto);
                    }
                });
                serverConfig.setProxyInterceptInitializer(new ProxyInterceptPipelineInitializer());
                serverConfig.setHttpProxyExceptionHandle(new HttpProxyExceptionHandle());
            } catch (Exception e) {
                serverConfig.setSslSupported(false);
                log.warn("SSL init fail,cause:" + e.getMessage());
            }
        }
    }

    public void start(int port) {
        start(null, port);
    }

    public void start(String ip, int port) {
        try {
            ChannelFuture channelFuture = doBind(ip, port);
            channelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("proxy server is listening on {}:{}",
                            Objects.toString(ip, "localhost"), port);
                    addShutdownHook();
                } else {
                    serverConfig.getHttpProxyExceptionHandle().bootstrapFailed(future.cause());
                    close();
                }
            });
            channelFuture.awaitUninterruptibly();
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            serverConfig.getHttpProxyExceptionHandle().bootstrapFailed(e);
        } finally {
            close();
        }
    }

    public CompletableFuture<Void> startAsync(int port) {
        return startAsync(null, port);
    }

    public CompletableFuture<Void> startAsync(String ip, int port) {
        ChannelFuture channelFuture = doBind(ip, port);

        CompletableFuture<Void> future = new CompletableFuture<>();
        channelFuture.addListener(start -> {
            if (start.isSuccess()) {
                future.complete(null);
                addShutdownHook();
                log.debug("proxy server is listening on {}:{}",
                        Objects.toString(ip, "localhost"), port);
            } else {
                future.completeExceptionally(start.cause());
                close();
            }
        });
        return future;
    }

    private ChannelFuture doBind(String ip, int port) {
        init();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bossGroup = new NioEventLoopGroup(serverConfig.getBossGroupThreads());
        workerGroup = new NioEventLoopGroup(serverConfig.getWorkerGroupThreads());
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
//                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<Channel>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addFirst(new LoggingHandler());
                        ch.pipeline().addLast("httpCodec", new HttpServerCodec());
                        if (serverConfig.isPacketAggregated()) {
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(1024 * 1024 * 10));
                        }
                        ch.pipeline().addLast("serverHandle", new HttpProxyServerHandler(serverConfig));
                    }
                });

        return ip == null ? bootstrap.bind(port) : bootstrap.bind(ip, port);
    }

    /**
     * 释放资源
     */
    public void close() {
        if (!(bossGroup.isShutdown() || bossGroup.isShuttingDown())) {
            bossGroup.shutdownGracefully();
        }
        if (!(workerGroup.isShutdown() || workerGroup.isShuttingDown())) {
            workerGroup.shutdownGracefully();
        }

        CertPool.clear();
    }

    /**
     * 注册JVM关闭的钩子以释放资源
     */
    public void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "Server Shutdown Thread"));
    }

}
