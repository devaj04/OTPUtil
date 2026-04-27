package com.trigyn.OTPUtil.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

/**
 * Cassandra configuration.
 * Keyspace: sunbird
 * Entity scan: com.trigyn.OTPUtil.entity
 */
@Configuration
@EnableCassandraRepositories(basePackages = "com.trigyn.OTPUtil.repository")
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspaceName;

    @Override
    protected String getKeyspaceName() {
        return keyspaceName;
    }

    @Override
    public String[] getEntityBasePackages() {
        return new String[]{"com.trigyn.OTPUtil.entity"};
    }

    @Override
    public SchemaAction getSchemaAction() {
        return SchemaAction.NONE;
    }

    /**
     * Exposes CassandraOperations bean for TTL-aware inserts.
     */
    @Bean
    public CassandraOperations cassandraOperations(CqlSession cqlSession) {
        return new CassandraTemplate(cqlSession);
    }
}

