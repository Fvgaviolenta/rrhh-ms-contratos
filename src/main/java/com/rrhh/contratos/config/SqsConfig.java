package com.rrhh.contratos.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
@Configuration public class SqsConfig {
 @Bean @ConditionalOnProperty(prefix="rrhh.sqs",name="enabled",havingValue="true")
 SqsClient sqsClient(@Value("${rrhh.sqs.region}") String region){return SqsClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create()).build();}
}
