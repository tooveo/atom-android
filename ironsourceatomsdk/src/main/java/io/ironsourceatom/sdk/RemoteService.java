package io.ironsourceatom.sdk;

import java.io.IOException;

public interface RemoteService {
    Response post(final String data, final String url) throws IOException;

    /**
     * Response-like class
     */
    class Response {
        public int code;
        public String body;
    }
}
