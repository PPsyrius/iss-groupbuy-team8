package com.blocbuy.catalog.persistence;

import lombok.Getter;
import lombok.Setter;

/** Single-row historical counts; stays inside the persistence adapter. */
@Getter
@Setter
public class PriceReferencesDO {
    private long quotes;
    private long policies;
}
