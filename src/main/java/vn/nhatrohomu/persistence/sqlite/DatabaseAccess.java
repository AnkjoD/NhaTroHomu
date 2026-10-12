package vn.nhatrohomu.persistence.sqlite;

import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** Shared lifecycle gate: restore cannot replace a database while any caller uses it. */
public final class DatabaseAccess {
  private static final ConcurrentMap<Path, ReentrantReadWriteLock> LOCKS =
      new ConcurrentHashMap<>();

  private DatabaseAccess() {}

  private static ReentrantReadWriteLock gate(Path file) throws SQLException {
    try {
      Path absolute = file.toAbsolutePath().normalize();
      Path key = absolute.getParent().toRealPath().resolve(absolute.getFileName());
      return LOCKS.computeIfAbsent(key, unused -> new ReentrantReadWriteLock(true));
    } catch (Exception ex) {
      throw new SQLException("Không xác định được thư mục dữ liệu.", ex);
    }
  }

  public static Connection open(Path file) throws SQLException {
    Lock lock = gate(file).readLock();
    lock.lock();
    Connection delegate = null;
    try {
      delegate = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath().normalize());
      try (Statement s = delegate.createStatement()) {
        s.execute("PRAGMA foreign_keys=ON");
        s.execute("PRAGMA busy_timeout=5000");
        s.execute("PRAGMA trusted_schema=OFF");
      }
      Connection connection = delegate;
      return (Connection)
          Proxy.newProxyInstance(
              DatabaseAccess.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              new InvocationHandler() {
                boolean closed;

                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                  if (method.getName().equals("close")) {
                    if (!closed) {
                      closed = true;
                      try {
                        connection.close();
                      } finally {
                        lock.unlock();
                      }
                    }
                    return null;
                  }
                  try {
                    return method.invoke(connection, args);
                  } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                  }
                }
              });
    } catch (Exception ex) {
      if (delegate != null)
        try {
          delegate.close();
        } catch (Exception ignored) {
        }
      lock.unlock();
      if (ex instanceof SQLException sql) throw sql;
      throw new SQLException(ex);
    }
  }

  public interface Action<T> {
    T run() throws Exception;
  }

  public static <T> T exclusive(Path file, Action<T> action) throws Exception {
    Lock lock = gate(file).writeLock();
    lock.lock();
    try {
      return action.run();
    } finally {
      lock.unlock();
    }
  }
}
