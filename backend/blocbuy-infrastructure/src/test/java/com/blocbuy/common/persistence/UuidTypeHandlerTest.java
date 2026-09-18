package com.blocbuy.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** UUID persistence: setObject writes and typed getObject reads across JDBC shapes. */
@ExtendWith(MockitoExtension.class)
class UuidTypeHandlerTest {

    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private CallableStatement callableStatement;

    private final UuidTypeHandler handler = new UuidTypeHandler();

    @Test
    void setNonNullParameterUsesSetObject() throws Exception {
        handler.setNonNullParameter(preparedStatement, 2, ID, JdbcType.OTHER);

        verify(preparedStatement).setObject(2, ID);
    }

    @Test
    void getNullableResultByColumnNameRequestsUuid() throws Exception {
        when(resultSet.getObject("user_id", UUID.class)).thenReturn(ID);

        assertThat(handler.getNullableResult(resultSet, "user_id")).isEqualTo(ID);
        verify(resultSet).getObject("user_id", UUID.class);
    }

    @Test
    void getNullableResultByColumnIndexRequestsUuid() throws Exception {
        when(resultSet.getObject(3, UUID.class)).thenReturn(ID);

        assertThat(handler.getNullableResult(resultSet, 3)).isEqualTo(ID);
        verify(resultSet).getObject(3, UUID.class);
    }

    @Test
    void getNullableCallableResultRequestsUuid() throws Exception {
        when(callableStatement.getObject(4, UUID.class)).thenReturn(ID);

        assertThat(handler.getNullableResult(callableStatement, 4)).isEqualTo(ID);
        verify(callableStatement).getObject(4, UUID.class);
    }
}
