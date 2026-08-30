package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.GroupDeal;
import java.util.Optional;

/**
 * Repository port for the GroupDeal aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 * TODO(TEAM8-groupbuy): add intent-named methods (e.g. findById, saveIfVersion) per use case.
 */
public interface GroupDealRepository {

  Optional<GroupDeal> findById(String id);
}
