package com.blocbuy.catalog.persistence;

import lombok.Getter;
import lombok.Setter;

/** Single-row aggregate projection; never crosses the persistence boundary. */
@Getter
@Setter
public class BizListingCountsDO {
    private long draft;
    private long onSale;
    private long offSale;
}
