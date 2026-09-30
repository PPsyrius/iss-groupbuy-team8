package com.blocbuy.bootstrap;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Test-only capture of prepared MyBatis SQL on the current request thread. */
@Intercepts(
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}))
class SqlStatementCapture implements Interceptor {
    private final ThreadLocal<List<String>> statements = new ThreadLocal<>();

    void start() {
        statements.set(new ArrayList<>());
    }

    List<String> finish() {
        var captured = List.copyOf(statements.get());
        statements.remove();
        return captured;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        var captured = statements.get();
        if (captured != null) {
            captured.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
        }
        return invocation.proceed();
    }

    /** Register through MyBatis's normal Spring Boot plugin discovery. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        SqlStatementCapture sqlStatementCapture() {
            return new SqlStatementCapture();
        }
    }
}
