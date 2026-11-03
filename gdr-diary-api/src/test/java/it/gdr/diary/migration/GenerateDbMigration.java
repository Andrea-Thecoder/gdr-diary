package it.gdr.diary.migration;

import io.ebean.annotation.Platform;
import io.ebean.dbmigration.DbMigration;

import java.io.IOException;

public class GenerateDbMigration {

    static void main(String[] args) throws IOException {
        generatePostgresqlMigration();
    }

    private static void generatePostgresqlMigration() throws IOException {
        DbMigration d = DbMigration.create();
        d.setPathToResources("src/main/resources");
        d.addPlatform(Platform.POSTGRES);
        d.setVersion("V1.0");
        d.setName("schema");
        d.generateInitMigration();
    }
}
