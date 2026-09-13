package com.rrhh.contratos.exception;

public class DomainException extends RuntimeException {
    private final int codigo;
    private final String campo;
    public DomainException(int codigo, String mensaje) { this(codigo, mensaje, null); }
    public DomainException(int codigo, String mensaje, String campo) {
        super(mensaje);
        this.codigo = codigo;
        this.campo = campo;
    }
    public int getCodigo() { return codigo; }
    public String getCampo() { return campo; }
}
