package com.github.monkeywie.proxyee.server;

import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Data
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
