package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.PaymentAttention;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Explicit financial-attention query model, never an aggregate repository or write path. */
@Mapper
public interface PaymentAttentionMapper {
    long countAttention();

    List<PaymentAttention> pageAttention(@Param("limit") int limit, @Param("offset") long offset);
}
