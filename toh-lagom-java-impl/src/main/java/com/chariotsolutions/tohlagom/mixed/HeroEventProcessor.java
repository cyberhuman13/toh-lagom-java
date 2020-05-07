package com.chariotsolutions.tohlagom.mixed;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import com.chariotsolutions.tohlagom.impl.HeroEvent;
import com.chariotsolutions.tohlagom.impl.HeroEvent.*;
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcReadSide;

public class HeroEventProcessor extends com.chariotsolutions.tohlagom.common.HeroEventProcessor {
    private final JdbcReadSide readSide;

    @Inject
    public HeroEventProcessor(JdbcReadSide readSide) {
        this.readSide = readSide;
    }

    @Override
    public ReadSideHandler<HeroEvent> buildHandler() {
        final JdbcReadSide.ReadSideHandlerBuilder<HeroEvent> builder =
                readSide.builder(EVENT_PROCESSOR_ID);

        builder.setGlobalPrepare(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                            "  " + ID_COLUMN + " VARCHAR(10)," +
                            "  " + NAME_COLUMN + " VARCHAR(256)," +
                            "  PRIMARY KEY (" + ID_COLUMN + ")" +
                            ")")) {
                ps.execute();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE EXTENSION IF NOT EXISTS pg_trgm")) {
                ps.execute();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS fn_prefix" +
                            "  ON " + TABLE + " USING gin (" + NAME_COLUMN + " gin_trgm_ops)")) {
                ps.execute();
            }
        });

        builder.setEventHandler(HeroCreated.class, this::processCreate);
        builder.setEventHandler(HeroChanged.class, this::processChange);
        builder.setEventHandler(HeroDeleted.class, this::processDelete);

        return builder.build();
    }

    private void processCreate(Connection connection, HeroCreated event) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + TABLE + " (" + ID_COLUMN + ", " + NAME_COLUMN + ") VALUES (?, ?)");
        statement.setString(1, event.id);
        statement.setString(2, event.name);
        statement.execute();
    }

    private void processChange(Connection connection, HeroChanged event) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + TABLE + " SET " + NAME_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?");
        statement.setString(1, event.newName);
        statement.setString(2, event.id);
        statement.execute();
    }

    private void processDelete(Connection connection, HeroDeleted event) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + TABLE + " WHERE " + ID_COLUMN + " = ?");
        statement.setString(1, event.id);
        statement.execute();
    }
}
