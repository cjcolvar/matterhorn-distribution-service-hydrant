package org.projectbamboo.util.fedora;

/**
 * A base Exception class for errors that occur while
 * accessing Fedora or Fedora components. 
 */
public class FedoraException extends Exception {

    public FedoraException(String message) {
        super(message);
    }
    
    public FedoraException(Throwable rootCause) {
        super(rootCause);
    }
    
    public FedoraException(String message, Throwable rootCause) {
        super(message, rootCause);
    }
}
