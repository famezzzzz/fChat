package ru.top.server.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.id.PostInsertIdentityPersister;
import org.hibernate.id.insert.GetGeneratedKeysDelegate;

public class SQLiteDialect extends Dialect {

    public SQLiteDialect() {
        super();
    }

    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        return new SQLiteIdentityColumnSupport();
    }

    private static class SQLiteIdentityColumnSupport implements IdentityColumnSupport {
        @Override
        public boolean supportsIdentityColumns() {
            return true;
        }

        @Override
        public boolean supportsInsertSelectIdentity() {
            return false;
        }

        @Override
        public String getIdentitySelectString(String table, String column, int type) {
            return "select last_insert_rowid()";
        }

        @Override
        public String getIdentityColumnString(int type) {
            return "integer";
        }

        @Override
        public String getIdentityInsertString() {
            return "";
        }

        @Override
        public GetGeneratedKeysDelegate buildGetGeneratedKeysDelegate(PostInsertIdentityPersister postInsertIdentityPersister, Dialect dialect) {
            return null;
        }

        @Override
        public boolean hasDataTypeInIdentityColumn() {
            return false;
        }

        @Override
        public String appendIdentitySelectToInsert(String s) {
            return "";
        }
    }
}