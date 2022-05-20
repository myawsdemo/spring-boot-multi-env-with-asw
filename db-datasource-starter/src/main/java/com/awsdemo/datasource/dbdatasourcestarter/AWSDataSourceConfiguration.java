package com.awsdemo.datasource.dbdatasourcestarter;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

@Slf4j
@Configuration
public class AWSDataSourceConfiguration {

    public static String jdbcUrl = "jdbc:mysql://%s:3306/order-service";

    @Value("${aws.secret.enabled}")
    private boolean awsSecretEnabled;

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource(Environment env) {

        log.info("===========================>initial datasource from : {}<========================", awsSecretEnabled);
        DataSource datasource;
        HikariConfig config = new HikariConfig();
        if (awsSecretEnabled) {
            DBParameter secret = getSecret(env);
            config.setJdbcUrl(String.format(jdbcUrl, secret.getHost()));
            config.setUsername(secret.getUsername());
            config.setPassword(secret.getPassword());
            datasource = new HikariDataSource(config);
        } else {
            final String datasourceUsername = env.getRequiredProperty("spring.datasource.username");
            final String datasourcePassword = env.getRequiredProperty("spring.datasource.password");
            final String datasourceUrl = env.getRequiredProperty("spring.datasource.url");
            datasource = DataSourceBuilder
                    .create()
                    .username(datasourceUsername)
                    .password(datasourcePassword)
                    .url(datasourceUrl)
                    .build();
        }
        try {
            log.info("Datasource:{}", datasource.getConnection());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return datasource;
    }

    public DBParameter getSecret(Environment env) {
        String secretName = env.getRequiredProperty("aws.secret.name");
        String endpoints = env.getRequiredProperty("aws.secret.endpoints");
        String region = env.getRequiredProperty("aws.secret.region");
        AwsClientBuilder.EndpointConfiguration config = new AwsClientBuilder.EndpointConfiguration(endpoints, region);
        AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
        clientBuilder.setEndpointConfiguration(config);
        AWSSecretsManager client = clientBuilder.build();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode secretsJson = null;
        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest().withSecretId(secretName);
        GetSecretValueResult getSecretValueResponse = null;

        try {
            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
        } catch (ResourceNotFoundException e) {
            log.error("The requested secret " + secretName + " was not found");
        } catch (InvalidRequestException e) {
            log.error("The request was invalid due to: " + e.getMessage());
        } catch (InvalidParameterException e) {
            log.error("The request had invalid params: " + e.getMessage());
        }
        if (getSecretValueResponse == null) {
            throw new RuntimeException("get secret failure");
        }  // Decrypted secret using the associated KMS key // Depending on whether the secret was a string or binary, one of these fields will be populated
        String secret = getSecretValueResponse.getSecretString();
        if (secret != null) {
            try {
                secretsJson = objectMapper.readTree(secret);
            } catch (IOException e) {
                log.error("Exception while retrieving secret values: " + e.getMessage());
            }
        } else {
            log.error("The Secret String returned is null");
            throw new RuntimeException("get secret failure");
        }
        log.debug("secret json ==> {}", secretsJson);
        assert secretsJson != null;
        String host = secretsJson.get("host").textValue();
        String username = secretsJson.get("username").textValue();
        String password = secretsJson.get("password").textValue();

        log.info("host===>{}",host);
        log.info("username===>{}",username);
        return DBParameter.builder().host(host).username(username).password(password).build();
    }
}
