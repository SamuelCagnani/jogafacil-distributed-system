package com.jogafacil.contracts.api;

/**
 * Media type versionado do contrato sincrono da plataforma.
 *
 * <p>Os servicos produzem e consomem {@link #V1}; a versao faz parte do contrato
 * para permitir evolucao sem quebrar clientes existentes.</p>
 */
public final class ApiMediaType {

    /** Versao 1 do contrato: {@code application/vnd.jogafacil.v1+json}. */
    public static final String V1 = "application/vnd.jogafacil.v1+json";

    /** JSON convencional, aceito como fallback. */
    public static final String JSON = "application/json";

    private ApiMediaType() {
    }
}
