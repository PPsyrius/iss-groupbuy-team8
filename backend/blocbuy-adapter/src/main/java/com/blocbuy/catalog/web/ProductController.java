package com.blocbuy.catalog.web;

import com.blocbuy.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation. Endpoints land with their use cases. TODO(TEAM8-catalog): add
 * endpoints; write endpoints define idempotency ({@code Idempotency-Key}).
 */
@Tag(name = "products", description = "Product resource")
@RestController
@RequestMapping(ApiConstants.API_V1 + "/products")
public class ProductController {}
