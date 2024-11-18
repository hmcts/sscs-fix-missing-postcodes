package uk.gov.hmcts;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CcdDb {
    private static final String URL = "jdbc:postgresql://localhost:5440/ccd_data_store";
    private static final String USER = "DTS JIT Access ccd DB Reader SC";
    private static final String PASSWORD = System.getenv("PGPASSWORD");
    private static Connection connection;
    private static PreparedStatement statement;

    public CcdDb() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            statement = connection.prepareStatement("""
                SELECT
                    CASE
                        WHEN data->'appeal'->'rep'->'name'->>'firstName' = ? AND data->'appeal'->'rep'->'name'->>'lastName' = ? THEN data->'appeal'->'rep'->'address'->>'postcode'
                        WHEN data->'appeal'->'appellant'->'name'->>'firstName' = ? AND data->'appeal'->'appellant'->'name'->>'lastName' = ? THEN data->'appeal'->'appellant'->'address'->>'postcode'
                        WHEN data->'appeal'->'appellant'->'appointee'->'name'->>'firstName' = ? AND data->'appeal'->'appellant'->'appointee'->'name'->>'lastName' = ? THEN data->'appeal'->'appellant'->'appointee'->'address'->>'postcode'
                        WHEN data->'appeal'->>'signer' = ? THEN data->'appeal'->'appellant'->'address'->>'postcode'
                        WHEN 'Sir / Madam' = ? AND data->'appeal'->'rep'->>'hasRepresentative' = 'Yes' THEN data->'appeal'->'rep'->'address'->>'postcode'
                        ELSE null
                    END as postcode
                FROM case_data
                WHERE case_type_id = 'Benefit'
                AND reference = ?
            """);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getPostcodeAndDuplicateField(String caseId, String name) throws SQLException {
        var parts = name.split(" ");
        var lastName = parts[parts.length - 1];
        var firstName = name.replace(" " + lastName, "");

        statement.setString(1, firstName);
        statement.setString(2, lastName);
        statement.setString(3, firstName);
        statement.setString(4, lastName);
        statement.setString(5, firstName);
        statement.setString(6, lastName);
        statement.setString(7, name);
        statement.setString(8, name);
        statement.setBigDecimal(9, BigDecimal.valueOf(Long.parseLong(caseId)));
        var resultSet = statement.executeQuery();
        var found = resultSet.next();

        if (!found) {
            return null;
        }

        return resultSet.getString("postcode");
    }

    public void close() {
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}