package com.proconsi.electrobazar.model;

/**
 * Reason for AEAT rejection or failure in VeriFactu.
 */
public enum AeatRejectionReason {
    NETWORK_ERROR,         // Connection failed, timeout, SSL error
    VALIDATION_ERROR,      // AEAT rejected due to bad data (2xxx or 4xxx)
    WAIT_TIME_EXCEEDED     // Could not send within window
}
