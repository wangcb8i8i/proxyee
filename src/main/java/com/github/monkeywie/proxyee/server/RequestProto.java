package com.github.monkeywie.proxyee.server;

import java.io.Serializable;
import java.util.Objects;

public class RequestProto implements Serializable {

    private static final long serialVersionUID = -6471051659605127698L;
    private String host;
    private int port;
    private boolean ssl;

    public RequestProto() {
    }

    public RequestProto(String host, int port, boolean ssl) {
        this.host = host;
        this.port = port;
        this.ssl = ssl;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean getSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public boolean isDefaultPort() {
        return ssl ? port == 443 : port == 80;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof RequestProto) {
            RequestProto that = (RequestProto) o;
            return host.equals(that.host) && port == that.port && ssl == that.ssl;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, ssl);
    }

    @Override
    public String toString() {
        return String.format("%s://%s:%s", ssl ? "https" : "http", host, port);
    }
}
