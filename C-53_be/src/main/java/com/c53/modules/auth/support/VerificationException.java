package com.c53.modules.auth.support;

import com.c53.common.ApiException;

/** 挑战校验失败：调用方应提交失败计数后，再向 HTTP 层抛出。 */
public class VerificationException extends ApiException {
    public VerificationException(ApiException cause) {
        super(cause.getStatus(), cause.getCode(), cause.getMessage(), cause.getDetails());
    }
}
