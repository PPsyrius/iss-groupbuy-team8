package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.model.ProductDisplay;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.groupbuy.gateway.ListingDisplayGateway.ListingDisplay;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Catalog side of the groupbuy display port: one batch read, display fields only. */
@ExtendWith(MockitoExtension.class)
class GroupbuyListingDisplayAdapterTest {

    private static final UUID TEA = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID MUG = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID GONE = UUID.fromString("a0000000-0000-7000-8000-000000000003");

    @Mock private ProductRepository productRepository;

    @InjectMocks private GroupbuyListingDisplayAdapter adapter;

    @Test
    void mapsEachFoundProductToItsDisplayFieldsWithOneBatchRead() {
        Set<UUID> ids = Set.of(TEA, MUG, GONE);
        when(productRepository.findDisplaysByIds(ids))
                .thenReturn(
                        List.of(
                                new ProductDisplay(TEA, "Green Tea", "tea.jpg"),
                                new ProductDisplay(MUG, "Mug", null)));

        var result = adapter.findByProductIds(ids);

        verify(productRepository).findDisplaysByIds(ids);
        assertThat(result)
                .containsOnlyKeys(TEA, MUG)
                .containsEntry(TEA, new ListingDisplay(TEA, "Green Tea", "tea.jpg"))
                .containsEntry(MUG, new ListingDisplay(MUG, "Mug", null));
    }

    @Test
    void nullOrEmptyInputReturnsEmptyWithoutQuery() {
        assertThat(adapter.findByProductIds(null)).isEmpty();
        assertThat(adapter.findByProductIds(List.of())).isEmpty();
        verifyNoInteractions(productRepository);
    }
}
