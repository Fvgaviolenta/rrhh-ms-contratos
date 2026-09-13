package com.rrhh.contratos.controller;

import com.rrhh.contratos.dto.ApiResponse;
import com.rrhh.contratos.dto.request.ActualizarContratoRequest;
import com.rrhh.contratos.dto.request.CalcularLiquidacionRequest;
import com.rrhh.contratos.dto.request.CrearContratoRequest;
import com.rrhh.contratos.dto.response.ContratoResponse;
import com.rrhh.contratos.dto.response.LiquidacionResponse;
import com.rrhh.contratos.service.ContratoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ContratoController {
    private final ContratoService contratoService;

    public ContratoController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @GetMapping("/contratos")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<List<ContratoResponse>> listar() {
        return ApiResponse.ok(contratoService.listar(), "Contratos del tenant");
    }

    @GetMapping("/contratos/trabajador/{trabajador_id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','TRABAJADOR')")
    public ApiResponse<List<ContratoResponse>> porTrabajador(@PathVariable("trabajador_id") String trabajadorId) {
        return ApiResponse.ok(contratoService.porTrabajador(trabajadorId), "Contratos del trabajador");
    }

    @GetMapping("/contratos/{contrato_id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','TRABAJADOR')")
    public ApiResponse<ContratoResponse> obtener(@PathVariable("contrato_id") String contratoId) {
        return ApiResponse.ok(contratoService.obtener(contratoId), "Contrato encontrado");
    }

    @PostMapping("/contratos")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<ContratoResponse>> crear(@Valid @RequestBody CrearContratoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(contratoService.crear(request), "Contrato creado"));
    }

    @PatchMapping("/contratos/{contrato_id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<ContratoResponse> actualizar(
            @PathVariable("contrato_id") String contratoId,
            @RequestBody ActualizarContratoRequest request
    ) {
        return ApiResponse.ok(contratoService.actualizar(contratoId, request), "Contrato actualizado");
    }

    @PostMapping("/contratos/{contrato_id}/finalizar")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<ContratoResponse> finalizar(@PathVariable("contrato_id") String contratoId) {
        return ApiResponse.ok(contratoService.finalizar(contratoId), "Contrato finalizado");
    }

    @PostMapping("/liquidaciones/calcular")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','TRABAJADOR')")
    public ApiResponse<LiquidacionResponse> calcular(@Valid @RequestBody CalcularLiquidacionRequest request) {
        return ApiResponse.ok(contratoService.calcularLiquidacion(request), "Liquidación calculada");
    }
}
