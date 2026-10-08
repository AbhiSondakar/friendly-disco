package com.ecoloop.common;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final String clientSafeMessage;
    private final HttpStatus status;

    public DomainException(String clientSafeMessage) {
        this(clientSafeMessage, clientSafeMessage, HttpStatus.BAD_REQUEST);
    }

    public DomainException(String clientSafeMessage, HttpStatus status) {
        this(clientSafeMessage, clientSafeMessage, status);
    }

    public DomainException(String internalDetail, String clientSafeMessage) {
        this(internalDetail, clientSafeMessage, HttpStatus.BAD_REQUEST);
    }

    public DomainException(String internalDetail, String clientSafeMessage, HttpStatus status) {
        super(internalDetail);
        this.clientSafeMessage = clientSafeMessage;
        this.status = status != null ? status : HttpStatus.BAD_REQUEST;
    }

    public DomainException(String internalDetail, String clientSafeMessage, HttpStatus status, Throwable cause) {
        super(internalDetail, cause);
        this.clientSafeMessage = clientSafeMessage;
        this.status = status != null ? status : HttpStatus.BAD_REQUEST;
    }

    public String getClientSafeMessage() {
        return clientSafeMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
