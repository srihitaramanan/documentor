package com.srihitaramanan.documentor.document;

/**
 * Lifecycle states for a document. Matches the {@code status} column
 * in V1__init.sql.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}