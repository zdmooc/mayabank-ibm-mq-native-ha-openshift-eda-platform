package fr.mayabank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** The simulated business effect and deduplication record commit together in PostgreSQL. */
final class PaymentLedger {
    record Result(String outcome, String response) {}

    static Connection connect() throws Exception {
        String password = Files.readString(Path.of(Mq.env("DB_PASSWORD_FILE", "/etc/mayabank/db/password"))).strip();
        if (password.isEmpty()) throw new IllegalStateException("empty database password");
        return DriverManager.getConnection(Mq.env("DB_URL", "jdbc:postgresql://payments-db:5432/payments?connectTimeout=5&socketTimeout=15"), "payments", password);
    }

    static Result record(Payment payment) throws Exception {
        try (Connection db = connect()) {
            db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            db.setAutoCommit(false);
            try {
                int inserted;
                String response = "SIMULATED_PROCESSED|" + payment.id();
                try (PreparedStatement sql = db.prepareStatement("INSERT INTO payment_inbox(payment_id,payload,response) VALUES (?,?,?) ON CONFLICT(payment_id) DO NOTHING")) {
                    sql.setString(1, payment.id()); sql.setString(2, payment.encode()); sql.setString(3, response);
                    inserted = sql.executeUpdate();
                }
                if (inserted == 1) {
                    try (PreparedStatement sql = db.prepareStatement("INSERT INTO payment_effect(payment_id,amount,currency) VALUES (?,?,?)")) {
                        sql.setString(1, payment.id()); sql.setBigDecimal(2, payment.amount()); sql.setString(3, payment.currency());
                        sql.executeUpdate();
                    }
                    db.commit();
                    return new Result("NEW", response);
                }
                // Separate statement obtains a fresh READ COMMITTED snapshot after any competing INSERT.
                try (PreparedStatement sql = db.prepareStatement("SELECT payload,response FROM payment_inbox WHERE payment_id=?")) {
                    sql.setString(1, payment.id());
                    try (ResultSet row = sql.executeQuery()) {
                        if (!row.next()) throw new SQLException("missing committed inbox record");
                        boolean same = payment.encode().equals(row.getString(1));
                        String stored = row.getString(2);
                        db.commit();
                        return new Result(same ? "DUPLICATE" : "CONFLICT", same ? stored : "CONFLICT|" + payment.id());
                    }
                }
            } catch (Exception failure) {
                try { db.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    static void assertSingleEffect(Payment payment) throws Exception {
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(
                "SELECT i.payload,e.amount,e.currency FROM payment_inbox i JOIN payment_effect e ON i.payment_id=e.payment_id WHERE i.payment_id=?")) {
            sql.setString(1, payment.id());
            try (ResultSet rows = sql.executeQuery()) {
                if (!rows.next() || !payment.encode().equals(rows.getString(1))
                        || payment.amount().compareTo(rows.getBigDecimal(2)) != 0 || !payment.currency().equals(rows.getString(3)) || rows.next())
                    throw new IllegalStateException("FAIL: expected exactly one unchanged business effect for " + payment.id());
            }
        }
    }
}
