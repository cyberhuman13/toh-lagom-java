package com.chariotsolutions.tohlagom.cassandra;

import akka.Done;
import java.util.List;
import javax.inject.Inject;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import static com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide.completedStatements;
import com.chariotsolutions.tohlagom.impl.HeroEvent.*;
import com.chariotsolutions.tohlagom.impl.*;

public class HeroEventProcessor extends com.chariotsolutions.tohlagom.common.HeroEventProcessor {
    private final CassandraReadSide readSide;
    private final CassandraSession session;

    // These are initialized in builder.setPrepare below.
    // This is safe to prepare the statements this way, because
    // Cassandra driver's PreparedStatement class is threadsafe.
    private PreparedStatement stmtInsert;
    private PreparedStatement stmtUpdate;
    private PreparedStatement stmtDelete;

    @Inject
    public HeroEventProcessor(CassandraReadSide readSide, CassandraSession session) {
        this.readSide = readSide;
        this.session = session;
    }

    @Override
    public ReadSideHandler<HeroEvent> buildHandler() {
        final CassandraReadSide.ReadSideHandlerBuilder<HeroEvent> builder =
                readSide.builder(EVENT_PROCESSOR_ID);

        builder.setGlobalPrepare(() ->
                session.executeCreateTable(
                        "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                                "  " + ID_COLUMN + " TEXT," +
                                "  " + NAME_COLUMN + " TEXT," +
                                "  PRIMARY KEY (" + ID_COLUMN + ")" +
                                ")"
                ).thenCompose(__ ->
                        session.executeWrite(
                                "CREATE CUSTOM INDEX IF NOT EXISTS fn_prefix" +
                                        "  ON " + TABLE + " (" + NAME_COLUMN + ")" +
                                        "  USING 'org.apache.cassandra.index.sasi.SASIIndex'")
                ).thenApply(__ -> Done.getInstance())
        );

        builder.setPrepare(__ -> prepareInsert()
                .thenCompose(___ -> prepareUpdate())
                .thenCompose(___ -> prepareDelete()));

        builder.setEventHandler(HeroCreated.class, this::processCreate);
        builder.setEventHandler(HeroChanged.class, this::processChange);
        builder.setEventHandler(HeroDeleted.class, this::processDelete);

        return builder.build();
    }

    private CompletionStage<Done> prepareInsert() {
        return session.prepare("INSERT INTO " + TABLE + " (" + ID_COLUMN + ", " + NAME_COLUMN + ") VALUES (?, ?)")
                .thenApply(stmt -> {
                    this.stmtInsert = stmt;
                    return Done.getInstance();
                });
    }

    private CompletionStage<Done> prepareUpdate() {
        return session.prepare("UPDATE " + TABLE + " SET " + NAME_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?")
                .thenApply(stmt -> {
                    this.stmtUpdate = stmt;
                    return Done.getInstance();
                });
    }

    private CompletionStage<Done> prepareDelete() {
        return session.prepare("DELETE FROM " + TABLE + " WHERE " + ID_COLUMN + " = ?")
                .thenApply(stmt -> {
                    this.stmtDelete = stmt;
                    return Done.getInstance();
                });
    }

    private CompletionStage<List<BoundStatement>> processCreate(HeroCreated event) {
        final BoundStatement bindInsert = stmtInsert.bind();
        bindInsert.setString(ID_COLUMN, event.id);
        bindInsert.setString(NAME_COLUMN, event.name);
        return completedStatements(Collections.singletonList(bindInsert));
    }

    private CompletionStage<List<BoundStatement>> processChange(HeroChanged event) {
        final BoundStatement bindUpdate = stmtUpdate.bind();
        bindUpdate.setString(ID_COLUMN, event.id);
        bindUpdate.setString(NAME_COLUMN, event.newName);
        return completedStatements(Collections.singletonList(bindUpdate));
    }

    private CompletionStage<List<BoundStatement>> processDelete(HeroDeleted event) {
        final BoundStatement bindDelete = stmtDelete.bind();
        bindDelete.setString(ID_COLUMN, event.id);
        return completedStatements(Collections.singletonList(bindDelete));
    }
}
