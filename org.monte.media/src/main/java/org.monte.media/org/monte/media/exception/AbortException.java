/*
 * @(#)AbortException.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package org.monte.media.exception;

/**
 * This exception is thrown when the production of an image
 * has been aborted.
 *
 * @author Werner Randelshofer
 */
public class AbortException extends Exception {

    public static final long serialVersionUID = 1L;

    /**
     * Creates a new exception.
     */
    public AbortException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public AbortException(String message) {
        super(message);
    }
}
