package com.example.bankcards.exception;

public class RoleAlreadyAssignedException extends RuntimeException {
    public RoleAlreadyAssignedException(String message) {
        super(message);
    }
}
