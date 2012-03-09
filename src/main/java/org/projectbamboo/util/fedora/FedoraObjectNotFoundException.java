package org.projectbamboo.util.fedora;

public class FedoraObjectNotFoundException extends FedoraException {

    public FedoraObjectNotFoundException(String message) {
        super(message);
    }
    
    public FedoraObjectNotFoundException(Throwable rootCause) {
        super(rootCause);
    }
    
    public FedoraObjectNotFoundException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

}
