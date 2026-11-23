package com.rrhh.contratos.service;

import com.rrhh.contratos.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Cliente de {@code rrhh-trabajadores}: confirma que un trabajador existe en el tenant del JWT actual. */
@Component
public class TrabajadoresClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String trabajadoresBaseUrl;

    public TrabajadoresClient(@Value("${rrhh.integrations.trabajadores.base-url:http://localhost:8082}") String trabajadoresBaseUrl) {
        this.trabajadoresBaseUrl = trabajadoresBaseUrl.replaceAll("/$", "");
    }

    /** 400 en {@code trabajador_id} si el trabajador no existe o es de otro tenant; 401 sin JWT. */
    public void exigirDelTenant(String trabajadorId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new DomainException(401, "No fue posible validar el trabajador autenticado");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtAuthentication.getToken().getTokenValue());
        try {
            restTemplate.exchange(
                    trabajadoresBaseUrl + "/api/v1/trabajadores/" + trabajadorId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Object.class
            );
        } catch (RestClientException ex) {
            throw new DomainException(400, "El trabajador no pertenece al tenant o no existe", "trabajador_id");
        }
    }
}
