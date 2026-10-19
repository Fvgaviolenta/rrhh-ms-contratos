package com.rrhh.contratos.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.contratos.model.OutboxEvent;
import com.rrhh.contratos.repository.OutboxEventRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.time.Instant; import java.util.*;
@Service public class OutboxPublisher {
 private final OutboxEventRepository repo; private final ObjectMapper json; private final ObjectProvider<SqsClient> sqs; private final boolean enabled; private final String queueUrl;
 public OutboxPublisher(OutboxEventRepository repo,ObjectMapper json,ObjectProvider<SqsClient> sqs,@Value("${rrhh.sqs.enabled:false}") boolean enabled,@Value("${rrhh.sqs.events-queue-url:}") String queueUrl){this.repo=repo;this.json=json;this.sqs=sqs;this.enabled=enabled;this.queueUrl=queueUrl;}
 @Transactional public void registrar(String type,String tenant,String aggregate,Map<String,Object> payload){try{OutboxEvent e=new OutboxEvent();e.setId(UUID.randomUUID().toString());e.setTenantId(tenant);e.setEventType(type);e.setAggregateId(aggregate);e.setPayload(json.writeValueAsString(payload));e.setEstado("PENDIENTE");e.setCreadoEn(Instant.now());repo.save(e);}catch(Exception ex){throw new IllegalStateException("No fue posible serializar el evento",ex);}}
 @Scheduled(fixedDelayString="${rrhh.sqs.publish-delay-ms:5000}") @Transactional public void publicarPendientes(){SqsClient client=sqs.getIfAvailable();if(!enabled||queueUrl.isBlank()||client==null)return;for(OutboxEvent e:repo.findTop20ByEstadoOrderByCreadoEnAsc("PENDIENTE")){try{String body=json.writeValueAsString(Map.of("eventId",e.getId(),"eventType",e.getEventType(),"tenantId",e.getTenantId(),"occurredAt",e.getCreadoEn().toString(),"aggregateId",e.getAggregateId(),"payload",json.readTree(e.getPayload())));client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());e.setEstado("PUBLICADO");e.setPublicadoEn(Instant.now());e.setUltimoError(null);}catch(Exception ex){e.setIntentos(e.getIntentos()+1);e.setUltimoError(ex.getMessage());}}}
}
