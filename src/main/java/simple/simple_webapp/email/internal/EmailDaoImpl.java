package simple.simple_webapp.email.internal;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class EmailDaoImpl implements EmailDao {

    private final DSLContext dsl;

    EmailDaoImpl(DSLContext dsl) {
        this.dsl = dsl;
    }
}
