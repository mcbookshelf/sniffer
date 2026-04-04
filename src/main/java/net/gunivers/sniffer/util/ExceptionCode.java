package net.gunivers.sniffer.util;

public enum ExceptionCode {
    REFLECT_INVOKE_EXCEPTION(0),
    NULL_POINTER_EXCEPTION(1),
    FIELD_NOT_FOUND(2),
    METHOD_NOT_FOUND(3),
    METHOD_INVOKE_EXCEPTION(4),
    CONSTRUCTOR_NOT_FOUND(5);

    private final int code;

    ExceptionCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
