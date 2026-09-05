package com.blocbuy.groupbuy.web;

import com.blocbuy.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GroupDeal resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct cache manipulation. Endpoints land with their use cases. TODO(TEAM8-groupbuy): add
 * endpoints; write endpoints define idempotency ({@code Idempotency-Key}).
 */
@Tag(name = "group-deals", description = "GroupDeal resource")
@RestController
@RequestMapping(ApiConstants.API_V1 + "/group-deals")
public class GroupDealController {}
