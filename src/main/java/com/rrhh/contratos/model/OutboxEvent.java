package com.rrhh.contratos.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Getter @Setter @Entity @Table(name="contratos_outbox_event") public class OutboxEvent {
 @Id @Column(length=36) private String id; @Column(name="tenant_id",nullable=false,length=36) private String tenantId;
 @Column(name="event_type",nullable=false,length=80) private String eventType; @Column(name="aggregate_id",nullable=false,length=36) private String aggregateId;
 @Column(nullable=false,columnDefinition="TEXT") private String payload; @Column(nullable=false,length=20) private String estado;
 @Column(nullable=false) private int intentos; @Column(name="creado_en",nullable=false) private Instant creadoEn; @Column(name="publicado_en") private Instant publicadoEn;
 @Column(name="ultimo_error",columnDefinition="TEXT") private String ultimoError;
}
