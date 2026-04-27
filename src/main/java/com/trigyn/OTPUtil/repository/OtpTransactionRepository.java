package com.trigyn.OTPUtil.repository;

import com.trigyn.OTPUtil.entity.OtpTransaction;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Cassandra repository for OTP transaction persistence.
 * Primary key is 'key' (email or phone number).
 */
@Repository
public interface OtpTransactionRepository extends CassandraRepository<OtpTransaction, String> {

    Optional<OtpTransaction> findByKey(String key);

    void deleteByKey(String key);
}

