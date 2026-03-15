package simple.simple_webapp;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;

@Component
public class LogTablesAfterMigration implements CommandLineRunner {

    private final DataSource dataSource;

    public LogTablesAfterMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema='public'"
             )) {

            System.out.println("Tables in database:");

            while (rs.next()) {
                System.out.println(rs.getString("table_name"));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}