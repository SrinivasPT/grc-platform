package com.grcplatform.api.config;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.grcplatform.core.context.SessionContextHolder;

/**
 * Wraps the auto-configured HikariCP DataSource with a proxy that sets SQL Server's SESSION_CONTEXT
 * on every connection checkout. This makes the org_id available for row-level security policies
 * without parameter threading through every query.
 *
 * SESSION_CONTEXT is set WITHOUT @read_only = 1 so it can be overwritten on each checkout (needed
 * for connection pool reuse across different org requests).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource sessionContextDataSource(
            @Qualifier("hikariDataSource") DataSource hikariDataSource) {
        return new SessionContextDataSourceProxy(hikariDataSource);
    }

    /** Proxies getConnection() to inject SESSION_CONTEXT before returning the connection. */
    static class SessionContextDataSourceProxy implements DataSource {

        private final DataSource delegate;

        SessionContextDataSourceProxy(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            var conn = delegate.getConnection();
            injectSessionContext(conn);
            return conn;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            var conn = delegate.getConnection(username, password);
            injectSessionContext(conn);
            return conn;
        }

        private void injectSessionContext(Connection conn) throws SQLException {
            if (!SessionContextHolder.SESSION.isBound()) return;
            var orgId = SessionContextHolder.SESSION.get().orgId();
            try (var ps = conn.prepareStatement("EXEC sp_set_session_context N'org_id', ?")) {
                ps.setString(1, orgId.toString());
                ps.execute();
            }
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter pw) throws SQLException {
            delegate.setLogWriter(pw);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
