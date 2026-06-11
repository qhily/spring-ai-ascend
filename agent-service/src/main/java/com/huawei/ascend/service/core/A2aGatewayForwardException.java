package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.GatewayErrorCode;

public final class A2aGatewayForwardException extends RuntimeException {

    private final GatewayErrorCode code;

    public A2aGatewayForwardException(String message, Throwable cause) {
        super(message, cause);
        this.code = GatewayErrorCode.GATEWAY_FORWARD_FAILED;
    }

    public GatewayErrorCode code() {
        return code;
    }
}
