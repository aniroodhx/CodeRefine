package com.coderefine.verify.counter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class QueryCountingDataSource implements DataSource {

    private final DataSource delegate;
    private final QueryCounter counter;

    public QueryCountingDataSource(DataSource delegate, QueryCounter counter) {
        this.delegate = delegate;
        this.counter = counter;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
    }

    private Connection wrapConnection(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Connection.class},
                new ConnectionHandler(real, counter)
        );
    }

    private static class ConnectionHandler implements InvocationHandler {
        private final Connection real;
        private final QueryCounter counter;

        ConnectionHandler(Connection real, QueryCounter counter) {
            this.real = real;
            this.counter = counter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(real, args);

            if (method.getName().equals("prepareStatement") ||
                    method.getName().equals("prepareCall")) {
                counter.increment();
            } else if (method.getName().equals("createStatement")) {
                return wrapStatement((Statement) result);
            }

            return result;
        }

        private Statement wrapStatement(Statement real) {
            return (Statement) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Statement.class},
                    (proxy, method, args) -> {
                        if (method.getName().startsWith("execute")) {
                            counter.increment();
                        }
                        return method.invoke(real, args);
                    }
            );
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override
    public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override
    public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override
    public Logger getParentLogger() { return Logger.getLogger(getClass().getName()); }
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
}
