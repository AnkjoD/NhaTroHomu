package vn.nhatrohomu.persistence.sqlite;

import java.nio.file.*;
import java.sql.*;
import java.util.UUID;

/** Atomic schema upgrades; preserve released v1/v2/v3 books. */
final class Schema {
  static final int VERSION = 6;
  static final String BILL_COLUMNS =
      "id,room_id,room_name,month,e_old,e_new,w_old,w_new,water_mode,water_used,e_rate,w_rate,total,paid,note,e_photo,w_photo,contract_id,tenant_name,rent,due_date";

  private Schema() {}

  static void initialize(Path file) throws Exception {
    DatabaseAccess.exclusive(
        file,
        () -> {
          try (Connection c = DatabaseAccess.open(file);
              Statement s = c.createStatement()) {
            int version;
            try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
              r.next();
              version = r.getInt(1);
            }
            if (version > VERSION)
              throw new SQLException("Database thuộc phiên bản mới hơn. Hãy cập nhật ứng dụng.");
            if (version == VERSION) return null;
            if (version > 0) {
              try (PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
                p.setString(
                    1,
                    file.resolveSibling("before-upgrade-" + UUID.randomUUID() + ".db").toString());
                p.execute();
              }
            }
            s.execute("PRAGMA foreign_keys=OFF");
            c.setAutoCommit(false);
            try {
              if (version < 4) {
                s.execute(
                    "CREATE TABLE IF NOT EXISTS rooms(id INTEGER PRIMARY KEY,name TEXT NOT NULL"
                        + " UNIQUE,tenant TEXT NOT NULL,e_start TEXT NOT NULL,w_start TEXT NOT"
                        + " NULL,water_mode TEXT NOT NULL,fixed_water TEXT,active INTEGER NOT NULL"
                        + " DEFAULT 1)");
                s.execute(
                    "CREATE TABLE IF NOT EXISTS rates(id INTEGER PRIMARY KEY"
                        + " CHECK(id=1),electricity TEXT NOT NULL,water TEXT NOT NULL,default_water"
                        + " TEXT NOT NULL)");
                s.execute("INSERT OR IGNORE INTO rates VALUES(1,'3500','15000','3')");
                s.execute(
                    "CREATE TABLE IF NOT EXISTS bills(id INTEGER PRIMARY KEY,room_id INTEGER NOT"
                        + " NULL REFERENCES rooms(id),room_name TEXT NOT NULL,month TEXT NOT"
                        + " NULL,e_old TEXT NOT NULL,e_new TEXT NOT NULL,w_old TEXT NOT NULL,w_new"
                        + " TEXT NOT NULL,water_mode TEXT NOT NULL,water_used TEXT NOT NULL,e_rate"
                        + " TEXT NOT NULL,w_rate TEXT NOT NULL,total TEXT NOT NULL,paid INTEGER NOT"
                        + " NULL DEFAULT 0,note TEXT NOT NULL,e_photo BLOB,w_photo"
                        + " BLOB,UNIQUE(room_id,month))");
                s.execute(
                    "CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY,bill_id INTEGER NOT"
                        + " NULL REFERENCES bills(id) ON DELETE CASCADE,amount TEXT NOT"
                        + " NULL,received_date TEXT NOT NULL,note TEXT NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS payments_bill ON payments(bill_id)");
                if (version < 2)
                  s.execute(
                      "INSERT INTO payments(bill_id,amount,received_date,note) SELECT"
                          + " id,total,'','Đã thu trước khi nâng cấp; chưa có ngày thu' FROM bills"
                          + " WHERE paid=1 AND total<>'0'");
                if (version < 3) RentalStore.migrate(c);
                s.execute("ALTER TABLE contracts ADD COLUMN cancelled_at TEXT");
                s.execute("ALTER TABLE contracts ADD COLUMN cancelled_reason TEXT");
                // Rebuild instead of changing month strings to bypass the old unique constraint.
                s.execute(
                    "CREATE TABLE bills_new(id INTEGER PRIMARY KEY AUTOINCREMENT,room_id INTEGER"
                        + " NOT NULL REFERENCES rooms(id),room_name TEXT NOT NULL,month TEXT NOT"
                        + " NULL,e_old TEXT NOT NULL,e_new TEXT NOT NULL,w_old TEXT NOT NULL,w_new"
                        + " TEXT NOT NULL,water_mode TEXT NOT NULL CHECK(water_mode IN"
                        + " ('METER','FIXED')),water_used TEXT NOT NULL,e_rate TEXT NOT NULL,w_rate"
                        + " TEXT NOT NULL,total TEXT NOT NULL,paid INTEGER NOT NULL DEFAULT 0"
                        + " CHECK(paid IN(0,1)),note TEXT NOT NULL,e_photo BLOB,w_photo"
                        + " BLOB,contract_id INTEGER REFERENCES contracts(id),tenant_name TEXT NOT"
                        + " NULL DEFAULT '',rent TEXT NOT NULL DEFAULT '0',due_date TEXT NOT NULL"
                        + " DEFAULT '',created_at TEXT NOT NULL DEFAULT '',voided_at"
                        + " TEXT,void_reason TEXT)");
                s.execute(
                    "INSERT INTO bills_new("
                        + BILL_COLUMNS
                        + ") SELECT "
                        + BILL_COLUMNS
                        + " FROM bills");
                s.execute("DROP TABLE bills");
                s.execute("ALTER TABLE bills_new RENAME TO bills");
                s.execute(
                    "CREATE UNIQUE INDEX bills_active_period ON bills(room_id,month) WHERE"
                        + " voided_at IS NULL");
                s.execute("CREATE INDEX bills_period ON bills(month DESC,id DESC)");
                s.execute("ALTER TABLE payments ADD COLUMN created_at TEXT NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE payments ADD COLUMN request_key TEXT");
                s.execute(
                    "CREATE UNIQUE INDEX payments_request ON payments(request_key) WHERE"
                        + " request_key IS NOT NULL");
                s.execute(
                    "CREATE TABLE payment_reversals(id INTEGER PRIMARY KEY AUTOINCREMENT,payment_id"
                        + " INTEGER NOT NULL UNIQUE REFERENCES payments(id),reversed_at TEXT NOT"
                        + " NULL,reason TEXT NOT NULL CHECK(length(trim(reason))>0))");
                s.execute(
                    "CREATE TABLE water_transitions(id INTEGER PRIMARY KEY AUTOINCREMENT,room_id"
                        + " INTEGER NOT NULL REFERENCES rooms(id),effective_month TEXT NOT"
                        + " NULL,old_mode TEXT NOT NULL CHECK(old_mode"
                        + " IN('METER','FIXED')),new_mode TEXT NOT NULL CHECK(new_mode"
                        + " IN('METER','FIXED')),opening_reading TEXT NOT NULL,fixed_water"
                        + " TEXT,reason TEXT NOT NULL,created_at TEXT NOT"
                        + " NULL,UNIQUE(room_id,effective_month))");
                s.execute(
                    "CREATE TABLE audit_events(id INTEGER PRIMARY KEY AUTOINCREMENT,occurred_at"
                        + " TEXT NOT NULL,action TEXT NOT NULL,entity_type TEXT NOT NULL,entity_id"
                        + " INTEGER NOT NULL,detail TEXT NOT NULL)");
                s.execute("CREATE INDEX audit_date ON audit_events(occurred_at,id)");
                s.execute(
                    "CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,room_id INTEGER"
                        + " REFERENCES rooms(id),room_name TEXT NOT NULL,category TEXT NOT NULL"
                        + " CHECK(category"
                        + " IN('POWER','WATER','MAINTENANCE','CLEANING','OTHER')),payee TEXT NOT"
                        + " NULL,amount_vnd INTEGER NOT NULL CHECK(typeof(amount_vnd)='integer' AND"
                        + " amount_vnd>0 AND amount_vnd<=1000000000),paid_date TEXT NOT NULL,method"
                        + " TEXT NOT NULL CHECK(method IN('CASH','BANK','OTHER')),note TEXT NOT"
                        + " NULL,request_key TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)");
                s.execute("CREATE INDEX expenses_date ON expenses(paid_date,id)");
                s.execute(
                    "CREATE TABLE expense_reversals(id INTEGER PRIMARY KEY AUTOINCREMENT,expense_id"
                        + " INTEGER NOT NULL UNIQUE REFERENCES expenses(id),reversed_at TEXT NOT"
                        + " NULL,reason TEXT NOT NULL)");
                for (String sql : triggers(5).values()) s.execute(sql);
              }
              if (version < 5) {
                s.execute("ALTER TABLE tenants ADD COLUMN zalo TEXT NOT NULL DEFAULT ''");
                s.execute(
                    "ALTER TABLE tenants ADD COLUMN zalo_auto INTEGER NOT NULL DEFAULT 1"
                        + " CHECK(zalo_auto IN(0,1))");
                s.execute("ALTER TABLE tenants ADD COLUMN identity_no TEXT NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE tenants ADD COLUMN birthday TEXT NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE tenants ADD COLUMN address TEXT NOT NULL DEFAULT ''");
                s.execute(
                    "CREATE TABLE residents(id INTEGER PRIMARY KEY AUTOINCREMENT,tenant_id INTEGER"
                        + " NOT NULL REFERENCES tenants(id),name TEXT NOT NULL,relationship TEXT"
                        + " NOT NULL,birthday TEXT NOT NULL,identity_no TEXT NOT NULL,phone TEXT"
                        + " NOT NULL)");
                s.execute("CREATE INDEX residents_tenant ON residents(tenant_id,id)");
              }
              s.execute("ALTER TABLE bills ADD COLUMN water_amount TEXT");
              s.execute("DROP TRIGGER bills_no_rewrite");
              s.execute(triggers().get("bills_no_rewrite"));
              try (ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
                if (r.next()) throw new SQLException("Migration có quan hệ dữ liệu không hợp lệ.");
              }
              s.execute("PRAGMA user_version=" + VERSION);
              c.commit();
            } catch (Exception ex) {
              c.rollback();
              throw ex;
            } finally {
              c.setAutoCommit(true);
              s.execute("PRAGMA foreign_keys=ON");
            }
          }
          return null;
        });
  }

  static java.util.Map<String, String> triggers() {
    return triggers(VERSION);
  }

  static java.util.Map<String, String> triggers(int version) {
    java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
    for (String table :
        new String[] {
          "audit_events", "payment_reversals", "water_transitions", "expenses", "expense_reversals"
        })
      for (String op : new String[] {"UPDATE", "DELETE"}) {
        String name = table + "_no_" + op.toLowerCase();
        result.put(
            name,
            "CREATE TRIGGER "
                + name
                + " BEFORE "
                + op
                + " ON "
                + table
                + " BEGIN SELECT RAISE(ABORT,'History is immutable'); END");
      }
    result.put(
        "payments_no_update",
        "CREATE TRIGGER payments_no_update BEFORE UPDATE ON payments BEGIN SELECT RAISE(ABORT,'Use"
            + " a payment reversal'); END");
    result.put(
        "payments_no_delete",
        "CREATE TRIGGER payments_no_delete BEFORE DELETE ON payments BEGIN SELECT RAISE(ABORT,'Use"
            + " a payment reversal'); END");
    result.put(
        "bills_no_delete",
        "CREATE TRIGGER bills_no_delete BEFORE DELETE ON bills BEGIN SELECT RAISE(ABORT,'Void the"
            + " invoice'); END");
    for (String table : new String[] {"bill_charges", "deposits"}) {
      for (String op : new String[] {"UPDATE", "DELETE"}) {
        String name = table + "_no_" + op.toLowerCase();
        result.put(
            name,
            "CREATE TRIGGER "
                + name
                + " BEFORE "
                + op
                + " ON "
                + table
                + " BEGIN SELECT RAISE(ABORT,'Financial history is immutable'); END");
      }
    }
    result.put(
        "bills_no_rewrite",
        "CREATE TRIGGER bills_no_rewrite BEFORE UPDATE OF "
            + BILL_COLUMNS
            + (version >= 6 ? ",water_amount" : "")
            + ",created_at ON bills WHEN "
            + java.util.Arrays.stream(
                    (BILL_COLUMNS + (version >= 6 ? ",water_amount" : "") + ",created_at")
                        .split(","))
                .filter(column -> !column.equals("paid"))
                .map(column -> "NEW." + column + " IS NOT OLD." + column)
                .collect(java.util.stream.Collectors.joining(" OR "))
            + " BEGIN SELECT RAISE(ABORT,'Invoice fields are immutable'); END");
    result.put(
        "bills_no_unvoid",
        "CREATE TRIGGER bills_no_unvoid BEFORE UPDATE OF voided_at,void_reason ON bills WHEN"
            + " OLD.voided_at IS NOT NULL OR NEW.voided_at IS NULL OR"
            + " length(trim(NEW.void_reason))=0 BEGIN SELECT RAISE(ABORT,'Voiding is"
            + " irreversible'); END");
    return result;
  }
}
