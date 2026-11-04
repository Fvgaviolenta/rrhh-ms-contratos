package com.rrhh.contratos.controller;

import com.rrhh.contratos.dto.ApiResponse;
import com.rrhh.contratos.dto.request.ActualizarContratoRequest;
import com.rrhh.contratos.dto.request.CamposRequest;
import com.rrhh.contratos.dto.request.RechazarContratoRequest;
import com.rrhh.contratos.dto.request.CalcularLiquidacionRequest;
import com.rrhh.contratos.dto.request.CrearContratoRequest;
import com.rrhh.contratos.dto.request.EmitirContratoRequest;
import com.rrhh.contratos.dto.response.CampoPlantillaResponse;
import com.rrhh.contratos.dto.response.ContratoResponse;
import com.rrhh.contratos.dto.response.LiquidacionResponse;
import com.rrhh.contratos.dto.response.MiContratoResponse;
import com.rrhh.contratos.service.ContratoService;
import com.rrhh.contratos.service.FlujoContratoService;
import com.rrhh.contratos.service.PlantillaContratoService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ContratoController {
    private final ContratoService contratoService;
    private final FlujoContratoService flujoContratoService;
    private final PlantillaContratoService plantillaContratoService;

    public ContratoController(ContratoService contratoService, FlujoContratoService flujoContratoService,
                              PlantillaContratoService plantillaContratoService) {
        this.contratoService = contratoService;
        this.flujoContratoService = flujoContratoService;
        this.plantillaContratoService = plantillaContratoService;
    }

    @GetMapping("/contratos")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<List<ContratoResponse>> listar() {
        return ApiResponse.ok(contratoService.listar(), "Contratos del tenant");
    }

    @GetMapping("/plataforma/tenants/{tenant_id}/contratos")
    @PreAuthorize("hasRole('OPERADOR_SAAS')")
    public ApiResponse<List<ContratoResponse>> listarPorTenantPlataforma(
            @PathVariable("tenant_id") String tenantId) {
        return ApiResponse.ok(contratoService.listarPorTenantPlataforma(tenantId), "Contratos del tenant solicitado");
    }

    @GetMapping("/contratos/plantilla/campos")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','TRABAJADOR','JEFATURA')")
    public ApiResponse<List<CampoPlantillaResponse>> camposPlantilla() {
        return ApiResponse.ok(plantillaContratoService.campos(), "Campos de la plantilla de contrato");
    }

    @PostMapping("/contratos/emitir")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<ContratoResponse>> emitir(@Valid @RequestBody EmitirContratoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(flujoContratoService.emitir(request), "Contrato emitido, en espera de firma"));
    }

    @GetMapping("/contratos/mio")
    @PreAuthorize("hasAnyRole('TRABAJADOR','JEFATURA')")
    public ApiResponse<MiContratoResponse> mio() {
        return ApiResponse.ok(flujoContratoService.mio(), "Mi contrato");
    }

    @PutMapping("/contratos/mio/campos")
    @PreAuthorize("hasAnyRole('TRABAJADOR','JEFATURA')")
    public ApiResponse<MiContratoResponse> guardarCampos(@Valid @RequestBody CamposRequest request) {
        return ApiResponse.ok(flujoContratoService.guardarCampos(request.campos()), "Campos guardados");
    }

    @GetMapping("/contratos/mio/pdf")
    @PreAuthorize("hasAnyRole('TRABAJADOR','JEFATURA')")
    public ResponseEntity<byte[]> pdfMio() {
        return pdf(flujoContratoService.pdfMio());
    }

    @PostMapping("/contratos/mio/aprobar")
    @PreAuthorize("hasAnyRole('TRABAJADOR','JEFATURA')")
    public ApiResponse<MiContratoResponse> aprobar() {
        return ApiResponse.ok(flujoContratoService.aprobar(), "Contrato firmado");
    }

    @PostMapping("/contratos/mio/rechazar")
    @PreAuthorize("hasAnyRole('TRABAJADOR','JEFATURA')")
    public ApiResponse<MiContratoResponse> rechazar(@Valid @RequestBody(required = false) RechazarContratoRequest request) {
        return ApiResponse.ok(flujoContratoService.rechazar(request == null ? null : request.motivo()), "Contrato rechazado");
    }

    @GetMapping("/contratos/{contrato_id}/pdf")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<byte[]> pdfAdmin(@PathVariable("contrato_id") String contratoId) {
        return pdf(flujoContratoService.pdfAdmin(contratoId));
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

    private static ResponseEntity<byte[]> pdf(FlujoContratoService.PdfContrato pdf) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename("contrato-" + pdf.fechaInicio() + ".pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf.pdf());
    }

    @PostMapping("/liquidaciones/calcular")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','TRABAJADOR')")
    public ApiResponse<LiquidacionResponse> calcular(@Valid @RequestBody CalcularLiquidacionRequest request) {
        return ApiResponse.ok(contratoService.calcularLiquidacion(request), "Liquidación calculada");
    }
}
