package com.docencia.aed.exception;

public class ConflictException extends RuntimeException{

    public ConflictException(String mensaje){
        super(mensaje);
    }
}
